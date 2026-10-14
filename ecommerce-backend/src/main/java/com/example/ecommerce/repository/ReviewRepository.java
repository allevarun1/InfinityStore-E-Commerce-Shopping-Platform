package com.example.ecommerce.repository;

import com.example.ecommerce.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    @Query("select r from Review r join fetch r.user where r.product.id = :productId and r.hidden = false order by r.createdAt desc")
    List<Review> findVisibleByProduct(@Param("productId") Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /** Rows of [productId, count, average] for visible reviews. */
    @Query("select r.product.id, count(r), avg(r.rating) from Review r where r.hidden = false and r.product.id in :ids group by r.product.id")
    List<Object[]> summaries(@Param("ids") Collection<Long> ids);
}
