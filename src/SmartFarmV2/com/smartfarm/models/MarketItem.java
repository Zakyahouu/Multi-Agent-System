package com.smartfarm.models;

/**
 * MarketItem - Defines purchasable upgrades and items.
 */
public enum MarketItem {
    SMART_DRONE("Smart Drone", 200, "Faster field inspection", "upgrade_drone_speed"),
    ADVANCED_DRONE("Advanced Drone", 350, "AI Diagnosis + Efficiency", "upgrade_drone_ai"),
    HARVESTER_X2("Harvester Upgrade x2", 250, "Double harvesting speed", "upgrade_harvester_speed"),
    WATER_OPTIMIZER("Water Optimizer", 180, "Reduced water consumption (20%)", "upgrade_water_eff");

    private final String name;
    private final int price;
    private final String description;
    private final String effectId;

    MarketItem(String name, int price, String description, String effectId) {
        this.name = name;
        this.price = price;
        this.description = description;
        this.effectId = effectId;
    }

    public String getName() { return name; }
    public int getPrice() { return price; }
    public String getDescription() { return description; }
    public String getEffectId() { return effectId; }
}
