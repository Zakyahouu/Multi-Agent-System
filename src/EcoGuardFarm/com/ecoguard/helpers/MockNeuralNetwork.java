package com.ecoguard.helpers;

import com.ecoguard.models.DiseaseType;
import java.io.Serializable;

/**
 * MockNeuralNetwork - AI component for disease diagnosis.
 * Used by DroneAgent to classify field diseases.
 * 
 * This simulates a trained neural network that would analyze
 * sensor data (moisture, health, visual patterns) to diagnose diseases.
 */
public class MockNeuralNetwork implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String modelName;
    private final String version;

    public MockNeuralNetwork() {
        this.modelName = "EcoGuard-DiseaseNet";
        this.version = "1.0.0";
    }

    /**
     * Diagnosis result containing disease type and confidence.
     */
    public static class DiagnosisResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private final DiseaseType disease;
        private final int confidence; // 0-100%
        private final String explanation;

        public DiagnosisResult(DiseaseType disease, int confidence, String explanation) {
            this.disease = disease;
            this.confidence = confidence;
            this.explanation = explanation;
        }

        public DiseaseType getDisease() {
            return disease;
        }

        public int getConfidence() {
            return confidence;
        }

        public String getExplanation() {
            return explanation;
        }

        public String toJson() {
            return String.format(
                    "{\"disease\":%s,\"confidence\":%d,\"explanation\":\"%s\"}",
                    disease != null ? "\"" + disease.name() + "\"" : "null",
                    confidence,
                    explanation);
        }

        @Override
        public String toString() {
            if (disease == null) {
                return "No disease detected (Confidence: " + confidence + "%)";
            }
            return disease.getDisplayName() + " (Confidence: " + confidence + "%) - " + explanation;
        }
    }

    /**
     * Diagnose a field based on sensor data.
     * 
     * @param moisture      Current moisture level (0-100)
     * @param health        Current health level (0-100)
     * @param scanLevel     Current scan level (0-100)
     * @param actualDisease The actual disease (if any) - simulates ground truth
     * @return Diagnosis result with disease type and confidence
     */
    public DiagnosisResult diagnose(int moisture, int health, int scanLevel, DiseaseType actualDisease) {
        System.out.println("[AI] MockNeuralNetwork v" + version + " analyzing field...");
        System.out.println(
                "[AI] Input sensors: moisture=" + moisture + ", health=" + health + ", scanLevel=" + scanLevel);

        // If no actual disease, return healthy diagnosis
        if (actualDisease == null) {
            int confidence = calculateHealthyConfidence(health);
            return new DiagnosisResult(null, confidence, "No disease patterns detected");
        }

        // Simulate AI classification based on symptoms
        int confidence = calculateDiseaseConfidence(actualDisease, moisture, health);
        String explanation = generateExplanation(actualDisease, moisture, health);

        System.out
                .println("[AI] Diagnosis: " + actualDisease.getDisplayName() + " with " + confidence + "% confidence");

        return new DiagnosisResult(actualDisease, confidence, explanation);
    }

    /**
     * Calculate confidence when no disease is present.
     */
    private int calculateHealthyConfidence(int health) {
        if (health >= 90)
            return 95;
        if (health >= 70)
            return 85;
        if (health >= 50)
            return 70;
        return 60; // Low health but no disease - uncertain
    }

    /**
     * Calculate confidence for disease diagnosis.
     * Lower health = higher confidence (symptoms are clearer).
     */
    private int calculateDiseaseConfidence(DiseaseType disease, int moisture, int health) {
        int baseConfidence = 70;

        // Lower health = clearer symptoms = higher confidence
        if (health < 50)
            baseConfidence += 20;
        else if (health < 70)
            baseConfidence += 10;

        // Disease-specific adjustments
        switch (disease) {
            case APHIDS:
                // Aphids are easy to detect visually
                baseConfidence += 10;
                break;
            case FUNGAL_BLIGHT:
                // Fungal shows clear patterns when advanced
                if (health < 60)
                    baseConfidence += 15;
                break;
            case ROOT_ROT:
                // Root rot is harder to detect early
                if (health < 50)
                    baseConfidence += 5;
                else
                    baseConfidence -= 10;
                break;
        }

        return Math.min(99, Math.max(50, baseConfidence));
    }

    /**
     * Generate human-readable explanation for diagnosis.
     */
    private String generateExplanation(DiseaseType disease, int moisture, int health) {
        switch (disease) {
            case APHIDS:
                return "Detected insect damage patterns on leaf surfaces";
            case FUNGAL_BLIGHT:
                return "Identified fungal spore signatures in visual analysis";
            case ROOT_ROT:
                return "Root system stress indicators suggest bacterial infection";
            default:
                return "Disease pattern matched in neural network";
        }
    }

    public String getModelName() {
        return modelName;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return modelName + " v" + version;
    }
}
