package com.example.ecommerce.repository;

import com.example.ecommerce.model.AutomationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationSettingRepository extends JpaRepository<AutomationSetting, String> {
}
