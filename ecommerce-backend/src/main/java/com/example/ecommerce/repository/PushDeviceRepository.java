package com.example.ecommerce.repository;

import com.example.ecommerce.model.PushDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {
    Optional<PushDevice> findByEndpointHash(String endpointHash);

    List<PushDevice> findByAdminTrue();

    List<PushDevice> findByUserId(Long userId);

    long countByAdminTrue();
}
