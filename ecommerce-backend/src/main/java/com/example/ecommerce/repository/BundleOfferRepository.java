package com.example.ecommerce.repository;

import com.example.ecommerce.model.BundleOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BundleOfferRepository extends JpaRepository<BundleOffer, Long> {
    List<BundleOffer> findByActiveTrueOrderByMinItemsAsc();

    List<BundleOffer> findAllByOrderByMinItemsAsc();

    boolean existsByMinItems(int minItems);
}
