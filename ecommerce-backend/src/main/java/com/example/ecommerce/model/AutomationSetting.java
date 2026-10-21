package com.example.ecommerce.model;

import jakarta.persistence.*;

/** The owner's choice for one Autopilot rule: OFF, PREVIEW (draft only, owner sends) or AUTO (sends itself). */
@Entity
@Table(name = "automation_settings")
public class AutomationSetting {
    @Id
    @Column(name = "rule_key", length = 40)
    private String ruleKey;

    @Column(nullable = false, length = 10)
    private String mode;

    public AutomationSetting() {}

    public AutomationSetting(String ruleKey, String mode) {
        this.ruleKey = ruleKey;
        this.mode = mode;
    }

    public String getRuleKey() { return ruleKey; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
