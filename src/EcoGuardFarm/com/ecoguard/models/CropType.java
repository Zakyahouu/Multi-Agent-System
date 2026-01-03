package com.ecoguard.models;

/**
 * CropType - Defines crop characteristics that affect field behavior.
 * Each crop has different growth speed, water consumption, and scan decay
 * rates.
 */
public enum CropType {
    CORN(1, 2, 3), // growthSpeed=SLOW(1), waterConsume=MEDIUM(2), scanDecay=FAST(3)
    WHEAT(3, 1, 1), // growthSpeed=FAST(3), waterConsume=LOW(1), scanDecay=SLOW(1)
    RICE(2, 3, 2); // growthSpeed=MEDIUM(2), waterConsume=HIGH(3), scanDecay=MEDIUM(2)

    private final int growthSpeed; // 1=Slow, 2=Medium, 3=Fast
    private final int waterConsume; // 1=Low, 2=Medium, 3=High
    private final int scanDecay; // 1=Slow, 2=Medium, 3=Fast

    CropType(int growthSpeed, int waterConsume, int scanDecay) {
        this.growthSpeed = growthSpeed;
        this.waterConsume = waterConsume;
        this.scanDecay = scanDecay;
    }

    public int getGrowthSpeed() {
        return growthSpeed;
    }

    public int getWaterConsume() {
        return waterConsume;
    }

    public int getScanDecay() {
        return scanDecay;
    }

    /**
     * Get the corresponding crop item type for harvesting.
     */
    public ItemType getCropItem() {
        switch (this) {
            case CORN:
                return ItemType.CORN_CROP;
            case WHEAT:
                return ItemType.WHEAT_CROP;
            case RICE:
                return ItemType.RICE_CROP;
            default:
                return ItemType.CORN_CROP;
        }
    }

    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public String getEmoji() {
        switch (this) {
            case CORN:
                return "🌽";
            case WHEAT:
                return "🌾";
            case RICE:
                return "🍚";
            default:
                return "🌱";
        }
    }
}
