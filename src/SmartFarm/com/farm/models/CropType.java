package com.farm.models;

/**
 * CropType - Defines different crop types and their characteristics.
 * Each crop has different water needs, growth time, and harvest value.
 */
public enum CropType {
    WHEAT("Wheat", "wheat", 40, 60000, 100, false),
    CORN("Corn", "corn", 55, 90000, 150, false),
    VEGETABLES("Vegetables", "vegetables", 35, 45000, 80, true),
    RICE("Rice", "rice", 70, 120000, 200, false);

    private final String displayName;
    private final String id;
    private final int waterThreshold; // Minimum moisture % needed
    private final int growthTimeMs; // Time to fully grow (at 1x speed)
    private final int harvestValue; // $ value when harvested
    private final boolean needsPesticide; // Whether crop needs pesticide

    CropType(String displayName, String id, int waterThreshold,
            int growthTimeMs, int harvestValue, boolean needsPesticide) {
        this.displayName = displayName;
        this.id = id;
        this.waterThreshold = waterThreshold;
        this.growthTimeMs = growthTimeMs;
        this.harvestValue = harvestValue;
        this.needsPesticide = needsPesticide;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return id;
    }

    public int getWaterThreshold() {
        return waterThreshold;
    }

    public int getGrowthTimeMs() {
        return growthTimeMs;
    }

    public int getHarvestValue() {
        return harvestValue;
    }

    public boolean needsPesticide() {
        return needsPesticide;
    }

    public String getEmoji() {
        switch (this) {
            case WHEAT:
                return "🌾";
            case CORN:
                return "🌽";
            case VEGETABLES:
                return "🥬";
            case RICE:
                return "🌾";
            default:
                return "🌱";
        }
    }

    public String getWaterNeedDescription() {
        if (waterThreshold >= 60)
            return "Very High";
        if (waterThreshold >= 50)
            return "High";
        if (waterThreshold >= 40)
            return "Medium";
        return "Low";
    }

    public static CropType fromId(String id) {
        for (CropType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return WHEAT; // Default
    }
}
