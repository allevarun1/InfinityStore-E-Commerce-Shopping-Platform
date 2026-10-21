package com.example.ecommerce.repository;

import com.example.ecommerce.model.StoreVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreVideoRepository extends JpaRepository<StoreVideo, Long> {
    List<StoreVideo> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<StoreVideo> findAllByOrderBySortOrderAscIdAsc();
}
