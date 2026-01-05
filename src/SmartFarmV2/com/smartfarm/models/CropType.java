package com.smartfarm.models;

/**
 * CropType enum for different crop varieties.
 * Each crop has different growth speed and water consumption.
 */
public enum CropType {
    CORN("Corn", "🌽", 1.0, 1.0),
    WHEAT("Wheat", "🌾", 0.8, 0.7),
    RICE("Rice", "🍚", 0.6, 1.5),
    SOY("Soy", "🌱", 0.9, 0.8),
    TOMATO("Tomato", "🍅", 1.2, 1.3);

    private final String displayName;
    private final String emoji;
    private final double growthMultiplier;
    private final double waterConsumption;

    CropType(String displayName, String emoji, double growthMultiplier, double waterConsumption) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.growthMultiplier = growthMultiplier;
        this.waterConsumption = waterConsumption;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public double getGrowthMultiplier() {
        return growthMultiplier;
    }

    public double getWaterConsumption() {
        return waterConsumption;
    }
}
