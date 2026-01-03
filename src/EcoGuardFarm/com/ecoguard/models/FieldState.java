package com.ecoguard.models;

import java.io.Serializable;

/**
 * FieldState - Represents the current state of a field.
 * MUST be Serializable for mobile agent transport.
 */
public class FieldState implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int fieldId;
    private CropType cropType;
    private int moisture; // 0-100
    private int health; // 0-100
    private int scanLevel; // 0-100
    private int growth; // 0-100
    private DiseaseType currentDisease; // null if healthy

    public FieldState(int fieldId, CropType cropType) {
        this.fieldId = fieldId;
        this.cropType = cropType;
        this.moisture = 80; // Start with good moisture
        this.health = 100; // Start healthy
        this.scanLevel = 100; // Start fully scanned
        this.growth = 0; // Start at 0 growth
        this.currentDisease = null;
    }

    // ==================== GETTERS ====================

    public int getFieldId() {
        return fieldId;
    }

    public CropType getCropType() {
        return cropType;
    }

    public int getMoisture() {
        return moisture;
    }

    public int getHealth() {
        return health;
    }

    public int getScanLevel() {
        return scanLevel;
    }

    public int getGrowth() {
        return growth;
    }

    public DiseaseType getCurrentDisease() {
        return currentDisease;
    }

    public boolean hasDisease() {
        return currentDisease != null;
    }

    // ==================== SETTERS ====================

    public void setMoisture(int moisture) {
        this.moisture = Math.max(0, Math.min(100, moisture));
    }

    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(100, health));
    }

    public void setScanLevel(int scanLevel) {
        this.scanLevel = Math.max(0, Math.min(100, scanLevel));
    }

    public void setGrowth(int growth) {
        this.growth = Math.max(0, Math.min(100, growth));
    }

    public void setCurrentDisease(DiseaseType disease) {
        this.currentDisease = disease;
    }

    public void clearDisease() {
        this.currentDisease = null;
    }

    // ==================== MODIFIERS ====================

    /**
     * Decrease moisture by crop's water consumption rate.
     */
    public void decreaseMoisture() {
        this.moisture = Math.max(0, moisture - cropType.getWaterConsume());
    }

    /**
     * Decrease scan level by crop's scan decay rate.
     */
    public void decreaseScanLevel() {
        this.scanLevel = Math.max(0, scanLevel - cropType.getScanDecay());
    }

    /**
     * Apply disease damage to health.
     */
    public void applyDiseaseDamage() {
        if (currentDisease != null) {
            this.health = Math.max(0, health - currentDisease.getDamagePerTick());
        }
    }

    /**
     * Increase growth if conditions are met.
     * Growth requires: moisture > 30 AND health > 50
     */
    public void tryGrow() {
        if (moisture > 30 && health > 50) {
            this.growth = Math.min(100, growth + cropType.getGrowthSpeed());
        }
    }

    /**
     * Add water to field.
     */
    public void addWater(int amount) {
        this.moisture = Math.min(100, moisture + amount);
    }

    /**
     * Restore health after treatment.
     */
    public void restoreHealth(int amount) {
        this.health = Math.min(100, health + amount);
    }

    /**
     * Full scan restores scan level to 100.
     */
    public void fullScan() {
        this.scanLevel = 100;
    }

    /**
     * Harvest resets growth to 0.
     */
    public void harvest() {
        this.growth = 0;
    }

    // ==================== STATUS CHECKS ====================

    public boolean needsScan() {
        return scanLevel < 20;
    }

    public boolean needsWater() {
        return moisture < 30;
    }

    public boolean isReadyForHarvest() {
        return growth >= 100;
    }

    public boolean needsTreatment() {
        return currentDisease != null;
    }

    // ==================== SERIALIZATION ====================

    public String toJson() {
        return String.format(
                "{\"fieldId\":%d,\"cropType\":\"%s\",\"moisture\":%d,\"health\":%d," +
                        "\"scanLevel\":%d,\"growth\":%d,\"disease\":%s}",
                fieldId, cropType.name(), moisture, health, scanLevel, growth,
                currentDisease != null ? "\"" + currentDisease.name() + "\"" : "null");
    }

    @Override
    public String toString() {
        return String.format("Field-%d [%s, moisture=%d%%, health=%d%%, scan=%d%%, growth=%d%%%s]",
                fieldId, cropType.getDisplayName(), moisture, health, scanLevel, growth,
                currentDisease != null ? ", DISEASE: " + currentDisease : "");
    }
}
