package com.farm.models;

/**
 * CropStage - Represents the lifecycle stages of a crop.
 */
public enum CropStage {
    SEED("Seed", 0),
    SPROUT("Sprout", 20),
    GROWING("Growing", 50),
    MATURE("Mature", 80),
    READY("Ready", 100),
    HARVESTED("Harvested", -1);

    private final String displayName;
    private final int minGrowth; // Minimum growth percentage to reach this stage

    CropStage(String displayName, int minGrowth) {
        this.displayName = displayName;
        this.minGrowth = minGrowth;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinGrowth() {
        return minGrowth;
    }

    public static CropStage fromGrowth(int growthPercentage) {
        if (growthPercentage >= 100)
            return READY;
        if (growthPercentage >= 80)
            return MATURE;
        if (growthPercentage >= 50)
            return GROWING;
        if (growthPercentage >= 20)
            return SPROUT;
        return SEED;
    }

    public CropStage next() {
        switch (this) {
            case SEED:
                return SPROUT;
            case SPROUT:
                return GROWING;
            case GROWING:
                return MATURE;
            case MATURE:
                return READY;
            case READY:
                return HARVESTED;
            default:
                return this;
        }
    }

    public boolean canHarvest() {
        return this == READY;
    }
}
