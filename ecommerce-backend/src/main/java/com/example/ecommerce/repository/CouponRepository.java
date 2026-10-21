package com.example.ecommerce.repository;

import com.example.ecommerce.model.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);

    List<Coupon> findAllByOrderByCreatedAtDesc();

    Optional<Coupon> findFirstByDescriptionStartingWith(String prefix);

    List<Coupon> findByDescriptionStartingWithOrderByCreatedAtDesc(String prefix);

    /** Row lock so concurrent checkouts cannot exceed a usage limit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where upper(c.code) = upper(:code)")
    Optional<Coupon> lockByCode(@Param("code") String code);
}
