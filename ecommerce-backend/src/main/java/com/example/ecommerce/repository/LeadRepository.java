package com.example.ecommerce.repository;

import com.example.ecommerce.model.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {
    boolean existsByEmailIgnoreCase(String email);
}
