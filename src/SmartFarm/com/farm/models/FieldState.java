package com.farm.models;

import java.io.Serializable;

/**
 * FieldState - Represents the current state of a farm field.
 * This is a serializable data class that can be passed between agents.
 */
public class FieldState implements Serializable {
    private static final long serialVersionUID = 1L;

    private int fieldId;
    private CropType cropType;
    private CropStage stage;
    private int moisture; // 0-100%
    private int growth; // 0-100%
    private int health; // 0-100%
    private boolean sprinklerOn;
    private boolean needsInspection;
    private boolean hasPest;
    private long lastUpdateTime;

    public FieldState(int fieldId, CropType cropType) {
        this.fieldId = fieldId;
        this.cropType = cropType;
        this.stage = CropStage.SEED;
        this.moisture = 50;
        this.growth = 0;
        this.health = 100;
        this.sprinklerOn = false;
        this.needsInspection = false;
        this.hasPest = false;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    // Getters
    public int getFieldId() {
        return fieldId;
    }

    public CropType getCropType() {
        return cropType;
    }

    public CropStage getStage() {
        return stage;
    }

    public int getMoisture() {
        return moisture;
    }

    public int getGrowth() {
        return growth;
    }

    public int getHealth() {
        return health;
    }

    public boolean isSprinklerOn() {
        return sprinklerOn;
    }

    public boolean needsInspection() {
        return needsInspection;
    }

    public boolean hasPest() {
        return hasPest;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    // Setters
    public void setCropType(CropType cropType) {
        this.cropType = cropType;
    }

    public void setStage(CropStage stage) {
        this.stage = stage;
    }

    public void setMoisture(int moisture) {
        this.moisture = Math.max(0, Math.min(100, moisture));
    }

    public void setGrowth(int growth) {
        this.growth = Math.max(0, Math.min(100, growth));
        this.stage = CropStage.fromGrowth(this.growth);
    }

    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(100, health));
    }

    public void setSprinklerOn(boolean on) {
        this.sprinklerOn = on;
    }

    public void setNeedsInspection(boolean needs) {
        this.needsInspection = needs;
    }

    public void setHasPest(boolean hasPest) {
        this.hasPest = hasPest;
    }

    public void updateTimestamp() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    // Logic methods
    public boolean needsWater() {
        return moisture < cropType.getWaterThreshold();
    }

    public boolean canGrow() {
        return moisture >= cropType.getWaterThreshold() && health > 20 && !hasPest;
    }

    public boolean canHarvest() {
        return stage.canHarvest();
    }

    public int getHarvestValue() {
        // Value is affected by health
        double healthMultiplier = health / 100.0;
        return (int) (cropType.getHarvestValue() * healthMultiplier);
    }

    public void harvest() {
        this.stage = CropStage.HARVESTED;
        this.growth = 0;
    }

    public void replant(CropType newCrop) {
        this.cropType = newCrop;
        this.stage = CropStage.SEED;
        this.growth = 0;
        this.health = 100;
        this.hasPest = false;
    }

    @Override
    public String toString() {
        return String.format("Field-%d [%s, %s, moisture=%d%%, growth=%d%%, health=%d%%]",
                fieldId, cropType.getDisplayName(), stage.getDisplayName(), moisture, growth, health);
    }

    /**
     * Create a JSON representation for the GUI.
     */
    public String toJson() {
        return String.format(
                "{\"id\":%d,\"crop\":\"%s\",\"moisture\":%d,\"growth\":%d,\"stage\":\"%s\",\"health\":%d,\"sprinklerOn\":%b,\"hasPest\":%b}",
                fieldId, cropType.getId(), moisture, growth, stage.getDisplayName(), health, sprinklerOn, hasPest);
    }
}
