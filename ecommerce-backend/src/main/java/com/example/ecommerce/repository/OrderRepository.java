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

    @Query("select count(o) > 0 from Order o join o.orderItems i where o.user.id = :userId and i.product.id = :productId and o.status = 'DELIVERED'")
    boolean hasDelivered(@Param("userId") Long userId, @Param("productId") Long productId);
}
