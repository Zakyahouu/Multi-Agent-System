package com.ecoguard.models;

/**
 * DiseaseType - Defines diseases that can affect fields.
 * Each disease has a specific cure and damage per tick.
 */
public enum DiseaseType {
    APHIDS(ItemType.PESTICIDE_A, 1), // Cure: PESTICIDE_A, Damage: 1 per tick
    FUNGAL_BLIGHT(ItemType.FUNGICIDE_X, 3), // Cure: FUNGICIDE_X, Damage: 3 per tick
    ROOT_ROT(ItemType.ANTIBIOTIC_Z, 2); // Cure: ANTIBIOTIC_Z, Damage: 2 per tick

    private final ItemType cure;
    private final int damagePerTick;

    DiseaseType(ItemType cure, int damagePerTick) {
        this.cure = cure;
        this.damagePerTick = damagePerTick;
    }

    public ItemType getCure() {
        return cure;
    }

    public int getDamagePerTick() {
        return damagePerTick;
    }

    public String getDisplayName() {
        return name().replace("_", " ");
    }

    public String getEmoji() {
        switch (this) {
            case APHIDS:
                return "🐛";
            case FUNGAL_BLIGHT:
                return "🍄";
            case ROOT_ROT:
                return "🦠";
            default:
                return "⚠️";
        }
    }

    /**
     * Get a random disease for field infection.
     */
    public static DiseaseType getRandomDisease() {
        DiseaseType[] diseases = values();
        int index = (int) (Math.random() * diseases.length);
        return diseases[index];
    }
}
