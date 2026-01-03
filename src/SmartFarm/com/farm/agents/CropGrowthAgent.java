package com.farm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;

import com.farm.gui.WebServer;
import com.farm.models.*;

/**
 * CropGrowthAgent - Hybrid agent that manages crop lifecycle for a specific
 * field.
 * 
 * Responsibilities:
 * - Track crop growth over time
 * - Respond to environmental conditions (moisture, weather)
 * - Announce when crops are ready for harvest
 * - Handle replanting after harvest
 * 
 * Agent Type: HYBRID (reactive responses + proactive growth management)
 */
public class CropGrowthAgent extends Agent {

    private int fieldId;
    private FieldState fieldState;
    private long growthStartTime;
    private boolean isPaused = false;

    @Override
    protected void setup() {
        // Get field ID from arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            fieldId = (Integer) args[0];
            CropType cropType = args.length > 1 ? (CropType) args[1] : CropType.WHEAT;
            fieldState = new FieldState(fieldId, cropType);
        } else {
            fieldId = 1;
            fieldState = new FieldState(fieldId, CropType.WHEAT);
        }

        growthStartTime = System.currentTimeMillis();
        System.out.println("[CropGrowth-" + fieldId + "] Hybrid agent starting for Field-" + fieldId);
        System.out.println("[CropGrowth-" + fieldId + "] Crop: " + fieldState.getCropType().getDisplayName());

        // Register with DF
        registerWithDF();

        // Add growth simulation behavior (6 seconds for readable pacing)
        addBehaviour(new GrowthSimulation(this, 6000));

        // Add message handler for environmental updates
        addBehaviour(new EnvironmentHandler());

        // Initial broadcast
        broadcastFieldState();
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // Ignore
        }
        System.out.println("[CropGrowth-" + fieldId + "] Agent terminated.");
    }

    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("crop-growth");
            sd.setName("CropGrowth-Field-" + fieldId);
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            System.err.println("[CropGrowth-" + fieldId + "] DF registration failed");
        }
    }

    /**
     * Simulates crop growth over time based on conditions.
     */
    private class GrowthSimulation extends TickerBehaviour {
        public GrowthSimulation(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (isPaused)
                return;
            if (fieldState.getStage() == CropStage.HARVESTED)
                return;

            // Calculate growth rate based on conditions
            double growthRate = calculateGrowthRate();

            // Apply growth
            if (growthRate > 0) {
                int currentGrowth = fieldState.getGrowth();
                int newGrowth = Math.min(100, currentGrowth + (int) (growthRate * 2));
                fieldState.setGrowth(newGrowth);

                // Check for stage change
                CropStage oldStage = fieldState.getStage();
                CropStage newStage = CropStage.fromGrowth(newGrowth);

                if (newStage != oldStage) {
                    fieldState.setStage(newStage);
                    announceStageChange(oldStage, newStage);
                }
            }

            // Decrease moisture over time (evaporation)
            int currentMoisture = fieldState.getMoisture();
            fieldState.setMoisture(currentMoisture - 1);

            // Random pest chance (5%)
            if (!fieldState.hasPest() && Math.random() < 0.02) {
                fieldState.setHasPest(true);
                announcePestInfestation();
            }

            // Broadcast updated state
            broadcastFieldState();
        }
    }

    private double calculateGrowthRate() {
        double rate = 1.0;

        // Moisture affects growth
        int moisture = fieldState.getMoisture();
        int threshold = fieldState.getCropType().getWaterThreshold();

        System.out.println("[CropGrowth-" + fieldId + "] DEBUG: moisture=" + moisture +
                ", threshold=" + threshold + " (min: " + (threshold * 0.5) + ")");

        if (moisture < threshold * 0.5) {
            rate = 0; // Too dry, no growth
            fieldState.setHealth(fieldState.getHealth() - 1);
            System.out.println("[CropGrowth-" + fieldId + "] Too dry! No growth (rate=0)");
        } else if (moisture < threshold) {
            rate = 0.5; // Suboptimal
            System.out.println("[CropGrowth-" + fieldId + "] Suboptimal water (rate=0.5)");
        } else if (moisture > 80) {
            rate = 0.8; // Too wet
            System.out.println("[CropGrowth-" + fieldId + "] Too wet (rate=0.8)");
        } else {
            System.out.println("[CropGrowth-" + fieldId + "] Optimal water (rate=1.0)");
        }

        // Health affects growth
        if (fieldState.getHealth() < 50) {
            rate *= 0.5;
            System.out.println("[CropGrowth-" + fieldId + "] Low health penalty");
        }

        // Pest affects growth
        if (fieldState.hasPest()) {
            rate *= 0.3;
            fieldState.setHealth(fieldState.getHealth() - 2);
            System.out.println("[CropGrowth-" + fieldId + "] Pest penalty");
        }

        System.out.println("[CropGrowth-" + fieldId + "] Final growth rate: " + rate);
        return rate;
    }

    private void announceStageChange(CropStage oldStage, CropStage newStage) {
        System.out.println("[CropGrowth-" + fieldId + "] Stage: " + oldStage.getDisplayName() +
                " -> " + newStage.getDisplayName());

        WebServer.broadcastAgentInteraction(
                "CropGrowth-" + fieldId, "hybrid",
                "FarmerBDI", "bdi",
                "INFORM", fieldState.getCropType().getDisplayName() + " reached " + newStage.getDisplayName(),
                1);

        // Notify FarmerBDI
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("FarmerBDI", AID.ISLOCALNAME));
        msg.setContent("CROP_STAGE:" + fieldId + ":" + newStage.name());
        send(msg);

        // If ready to harvest, send special notification
        if (newStage == CropStage.READY) {
            announceReadyForHarvest();
        }
    }

    private void announceReadyForHarvest() {
        System.out.println("[CropGrowth-" + fieldId + "] Crop ready for harvest! Value: $" +
                fieldState.getHarvestValue());

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("FarmerBDI", AID.ISLOCALNAME));
        msg.addReceiver(new AID("Controller", AID.ISLOCALNAME));
        msg.setContent("HARVEST_READY:" + fieldId + ":" + fieldState.getHarvestValue());
        send(msg);

        WebServer.broadcastAgentInteraction(
                "CropGrowth-" + fieldId, "hybrid",
                "FarmerBDI", "bdi",
                "INFORM", "Harvest ready! Value: $" + fieldState.getHarvestValue(),
                2);
    }

    private void announcePestInfestation() {
        System.out.println("[CropGrowth-" + fieldId + "] PEST INFESTATION DETECTED!");

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("FarmerBDI", AID.ISLOCALNAME));
        msg.setContent("PEST_ALERT:" + fieldId);
        send(msg);

        WebServer.broadcastAgentInteraction(
                "CropGrowth-" + fieldId, "hybrid",
                "FarmerBDI", "bdi",
                "INFORM", "PEST INFESTATION! Need pesticide",
                1);
    }

    /**
     * Handles environmental updates (irrigation, weather, pesticide).
     */
    private class EnvironmentHandler extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                processMessage(msg);
            } else {
                block();
            }
        }
    }

    private void processMessage(ACLMessage msg) {
        String content = msg.getContent();
        if (content == null)
            return;

        // Handle irrigation
        if (content.startsWith("IRRIGATE:") || content.startsWith("WATER:")) {
            int amount = 30; // Default water amount
            try {
                String[] parts = content.split(":");
                if (parts.length > 1) {
                    amount = Integer.parseInt(parts[1]);
                }
            } catch (Exception e) {
                // Use default
            }

            fieldState.setMoisture(fieldState.getMoisture() + amount);
            fieldState.setSprinklerOn(true);

            System.out.println("[CropGrowth-" + fieldId + "] Irrigated: +" + amount + "% moisture");

            // Sprinkler turns off after 3 seconds
            addBehaviour(new WakerBehaviour(this, 3000) {
                @Override
                protected void onWake() {
                    fieldState.setSprinklerOn(false);
                    broadcastFieldState();
                }
            });

            // Send confirmation
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent("IRRIGATED:" + fieldId);
            send(reply);

            broadcastFieldState();
        }

        // Handle weather update
        if (content.startsWith("WEATHER:")) {
            String weather = content.substring(8);
            applyWeatherEffect(weather);
        }

        // Handle pesticide application
        if (content.startsWith("PESTICIDE:")) {
            fieldState.setHasPest(false);
            fieldState.setHealth(Math.min(100, fieldState.getHealth() + 20));
            System.out.println("[CropGrowth-" + fieldId + "] Pesticide applied. Pest eliminated.");

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent("PEST_TREATED:" + fieldId);
            send(reply);

            broadcastFieldState();
        }

        // Handle harvest completion
        if (content.startsWith("HARVESTED:")) {
            fieldState.harvest();
            System.out.println("[CropGrowth-" + fieldId + "] Harvest complete. Preparing for replanting.");

            // Auto-replant after 5 seconds
            addBehaviour(new WakerBehaviour(this, 5000) {
                @Override
                protected void onWake() {
                    replant(fieldState.getCropType());
                }
            });

            broadcastFieldState();
        }

        // Handle replant request
        if (content.startsWith("REPLANT:")) {
            try {
                String cropId = content.substring(8);
                CropType newCrop = CropType.fromId(cropId);
                replant(newCrop);
            } catch (Exception e) {
                replant(CropType.WHEAT);
            }
        }

        // Handle query for field state
        if (msg.getPerformative() == ACLMessage.QUERY_IF) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent("FIELD_STATE:" + fieldState.toJson());
            send(reply);
        }
    }

    private void applyWeatherEffect(String weather) {
        switch (weather.toUpperCase()) {
            case "RAIN":
                // Rain adds moisture
                fieldState.setMoisture(fieldState.getMoisture() + 15);
                break;
            case "STORM":
                // Storm adds lots of moisture but may damage
                fieldState.setMoisture(fieldState.getMoisture() + 30);
                if (Math.random() < 0.2) {
                    fieldState.setHealth(fieldState.getHealth() - 10);
                    System.out.println("[CropGrowth-" + fieldId + "] Storm damage!");
                }
                break;
            case "CLEAR":
                // Clear weather increases evaporation (handled in growth cycle)
                break;
        }
    }

    private void replant(CropType cropType) {
        fieldState.replant(cropType);
        growthStartTime = System.currentTimeMillis();
        System.out.println("[CropGrowth-" + fieldId + "] Replanted with " + cropType.getDisplayName());

        WebServer.broadcastAgentInteraction(
                "CropGrowth-" + fieldId, "hybrid",
                "Field-" + fieldId, "field",
                "ACTION", "Replanted with " + cropType.getDisplayName(),
                1);

        broadcastFieldState();
    }

    private void broadcastFieldState() {
        // Broadcast to GUI
        WebServer.broadcastFieldUpdate(
                fieldState.getFieldId(),
                fieldState.getCropType().getId(),
                fieldState.getMoisture(),
                fieldState.getGrowth(),
                fieldState.getStage().getDisplayName(),
                fieldState.isSprinklerOn());

        // Send update to FarmerBDI for belief update
        ACLMessage update = new ACLMessage(ACLMessage.INFORM);
        update.addReceiver(new AID("FarmerBDI", AID.ISLOCALNAME));
        update.setContent("FIELD_UPDATE:id:" + fieldId +
                ",crop:" + fieldState.getCropType().getId() +
                ",moisture:" + fieldState.getMoisture() +
                ",growth:" + fieldState.getGrowth() +
                ",health:" + fieldState.getHealth());
        send(update);
    }

    // ==================== PUBLIC METHODS ====================

    public FieldState getFieldState() {
        return fieldState;
    }

    public void pause() {
        isPaused = true;
    }

    public void resume() {
        isPaused = false;
    }
}
