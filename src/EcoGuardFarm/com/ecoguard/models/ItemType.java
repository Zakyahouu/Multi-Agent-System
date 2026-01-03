package com.ecoguard.models;

/**
 * ItemType - All items that can be stored in inventory or traded.
 */
public enum ItemType {
    // Resources
    WATER("Water", "💧", true),
    PESTICIDE_A("Pesticide A", "🧪", true),
    FUNGICIDE_X("Fungicide X", "🧫", true),
    ANTIBIOTIC_Z("Antibiotic Z", "💊", true),

    // Crops (harvested products)
    CORN_CROP("Corn", "🌽", false),
    WHEAT_CROP("Wheat", "🌾", false),
    RICE_CROP("Rice", "🍚", false);

    private final String displayName;
    private final String emoji;
    private final boolean isChemical;

    ItemType(String displayName, String emoji, boolean isChemical) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.isChemical = isChemical;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public boolean isChemical() {
        return isChemical;
    }

    public boolean isCrop() {
        return this == CORN_CROP || this == WHEAT_CROP || this == RICE_CROP;
    }

    /**
     * Get the base price for this item.
     */
    public double getBasePrice() {
        switch (this) {
            case WATER:
                return 10.0;
            case PESTICIDE_A:
                return 25.0;
            case FUNGICIDE_X:
                return 40.0;
            case ANTIBIOTIC_Z:
                return 35.0;
            case CORN_CROP:
                return 50.0;
            case WHEAT_CROP:
                return 40.0;
            case RICE_CROP:
                return 60.0;
            default:
                return 10.0;
        }
    }
}
