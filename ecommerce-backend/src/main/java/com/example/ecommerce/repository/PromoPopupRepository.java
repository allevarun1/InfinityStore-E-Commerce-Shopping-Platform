package com.example.ecommerce.repository;

import com.example.ecommerce.model.PromoPopup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromoPopupRepository extends JpaRepository<PromoPopup, Long> {
    List<PromoPopup> findByActiveTrueOrderByUpdatedAtDescIdDesc();

    List<PromoPopup> findAllByOrderByUpdatedAtDescIdDesc();
}
