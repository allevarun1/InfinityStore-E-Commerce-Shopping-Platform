package com.example.ecommerce.repository;

import com.example.ecommerce.model.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {
    boolean existsByEmailIgnoreCase(String email);

    Optional<Lead> findFirstByEmailIgnoreCase(String email);

    Optional<Lead> findFirstByPhone(String phone);

    List<Lead> findAllByOrderByLastActivityAtDesc();
}
