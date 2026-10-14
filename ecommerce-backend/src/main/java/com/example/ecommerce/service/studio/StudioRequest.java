package com.example.ecommerce.service.studio;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The facts the owner supplies about a saree. Only these facts are ever sent to a text writer. */
public class StudioRequest {
    @NotNull private Long categoryId;
    @Size(max = 60) private String fabric;
    @Size(max = 60) private String weave;
    @Size(max = 60) private String occasion;
    @Size(max = 40) private String colour;
    @Size(max = 60) private String region;
    @DecimalMin("0") @DecimalMax("20") private BigDecimal lengthMeters;
    private Boolean blouseIncluded;
    @Size(max = 500) private String careInstructions;
    @DecimalMin("0") private BigDecimal costPrice;
    /** Free text the owner wants worked in, such as a story about the weaver. Limited and sent as supplied. */
    @Size(max = 500) private String notes;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long v) { this.categoryId = v; }
    public String getFabric() { return fabric; }
    public void setFabric(String v) { this.fabric = v; }
    public String getWeave() { return weave; }
    public void setWeave(String v) { this.weave = v; }
    public String getOccasion() { return occasion; }
    public void setOccasion(String v) { this.occasion = v; }
    public String getColour() { return colour; }
    public void setColour(String v) { this.colour = v; }
    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }
    public BigDecimal getLengthMeters() { return lengthMeters; }
    public void setLengthMeters(BigDecimal v) { this.lengthMeters = v; }
    public Boolean getBlouseIncluded() { return blouseIncluded; }
    public void setBlouseIncluded(Boolean v) { this.blouseIncluded = v; }
    public String getCareInstructions() { return careInstructions; }
    public void setCareInstructions(String v) { this.careInstructions = v; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal v) { this.costPrice = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
