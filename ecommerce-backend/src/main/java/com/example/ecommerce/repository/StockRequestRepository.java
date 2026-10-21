package com.example.ecommerce.repository;

import com.example.ecommerce.model.StockRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRequestRepository extends JpaRepository<StockRequest, Long> {
    Optional<StockRequest> findByLeadIdAndProductId(Long leadId, Long productId);

    List<StockRequest> findByNotifiedAtIsNull();

    @Query("select s.productId, count(s) from StockRequest s where s.notifiedAt is null group by s.productId")
    List<Object[]> countWaitingByProduct();
}
