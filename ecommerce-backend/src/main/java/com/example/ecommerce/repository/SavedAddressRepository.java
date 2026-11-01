package com.example.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.ecommerce.model.SavedAddress;
import com.example.ecommerce.model.User;

@Repository
public interface SavedAddressRepository extends JpaRepository<SavedAddress, Long> {
    List<SavedAddress> findByUserOrderByLastUsedAtDesc(User user);

    Optional<SavedAddress> findByIdAndUser(Long id, User user);

    void deleteByUser(User user);
}
