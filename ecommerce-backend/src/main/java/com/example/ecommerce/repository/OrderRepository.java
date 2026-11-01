package com.example.ecommerce.repository;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserOrderByOrderDateDesc(User user);

    List<Order> findAllByOrderByOrderDateDesc();

    Optional<Order> findByUserAndIdempotencyKey(User user, String idempotencyKey);

    boolean existsByUser(User user);

    @Query("select count(o) from Order o where o.user.id = :userId and o.couponCode = :code and o.status <> 'CANCELLED'")
    long countActiveByUserAndCoupon(@Param("userId") Long userId, @Param("code") String code);

    /** Orders by this customer that still count against a coupon (not cancelled). */
    @Query("select count(o) from Order o where o.user.id = :userId and o.status <> 'CANCELLED'")
    long countActiveByUser(@Param("userId") Long userId);

    Optional<Order> findByTrackingNumber(String trackingNumber);

    long countByStatus(String status);

    @Query("select count(o) > 0 from Order o join o.orderItems i where o.user.id = :userId and i.product.id = :productId and o.status = 'DELIVERED'")
    boolean hasDelivered(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("select distinct o from Order o left join fetch o.orderItems where o.orderDate >= :from order by o.orderDate")
    List<Order> findSince(@Param("from") java.time.LocalDateTime from);

    boolean existsByUserAndOrderDateAfter(User user, java.time.LocalDateTime after);

    List<Order> findByStatus(String status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);

    Optional<Order> findByPaymentReference(String paymentReference);

    /** Online orders still waiting for payment that were placed before the cut-off. */
    @Query("select o from Order o where o.paymentMethod = 'ONLINE' and o.paymentStatus = 'PENDING' and o.status = 'PENDING' and o.orderDate < :before")
    List<Order> findUnpaidOnlineBefore(@Param("before") java.time.LocalDateTime before);
}
