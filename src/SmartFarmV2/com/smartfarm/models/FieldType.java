package com.smartfarm.models;

/**
 * FieldType - Defines the types of fields available for purchase.
 */
public enum FieldType {
    WHEAT("Wheat Field", 100, "🌾", "Balanced crop, medium yield.", CropType.WHEAT),
    CORN("Corn Field", 150, "🌽", "High yield but needs more water.", CropType.CORN),
    EXPERIMENTAL("Experimental Field", 500, "🧪", "High risk, potential for super yields.", CropType.WHEAT); // Defaults to wheat initially

    private final String displayName;
    private final int cost;
    private final String icon;
    private final String description;
    private final CropType cropType;

    FieldType(String displayName, int cost, String icon, String description, CropType cropType) {
        this.displayName = displayName;
        this.cost = cost;
        this.icon = icon;
        this.description = description;
        this.cropType = cropType;
    }

    public String getDisplayName() { return displayName; }
    public int getCost() { return cost; }
    public String getIcon() { return icon; }
    public String getDescription() { return description; }
    public CropType getCropType() { return cropType; }
}
