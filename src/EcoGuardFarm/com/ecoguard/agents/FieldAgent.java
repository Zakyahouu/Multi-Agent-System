package com.ecoguard.agents;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.ecoguard.models.CropType;
import com.ecoguard.models.DiseaseType;
import com.ecoguard.models.FieldState;
import com.ecoguard.web.WebServer;

/**
 * FieldAgent - Reactive agent that manages a single field.
 * 
 * Location: Field-Container-X (stationary)
 * Architecture: Reactive (no planning, responds to state changes)
 * 
 * Behavior:
 * - Decreases scanLevel and moisture per tick
 * - Increases growth if conditions are met
 * - Generates diseases randomly (5% chance)
 * - Applies disease damage
 * - Sends requests to FarmManager when thresholds are reached
 */
public class FieldAgent extends Agent {

    private FieldState fieldState;
    private boolean scanRequested = false;
    private boolean waterRequested = false;
    private boolean diagnosisRequested = false;
    private boolean harvestRequested = false;

    @Override
    protected void setup() {
        // Get arguments
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            int fieldId = (Integer) args[0];
            CropType cropType = (CropType) args[1];
            fieldState = new FieldState(fieldId, cropType);
        } else {
            fieldState = new FieldState(1, CropType.CORN);
        }

        System.out.println("[Field-" + fieldState.getFieldId() + "] Reactive agent started.");
        System.out.println("[Field-" + fieldState.getFieldId() + "] Crop: " + fieldState.getCropType().getEmoji() + " "
                + fieldState.getCropType().getDisplayName());
        System.out.println("[Field-" + fieldState.getFieldId() + "] Container: " + here().getName());

        // Broadcast initial state
        broadcastState();

        // Add tick behavior (every 1 second as specified)
        addBehaviour(new FieldTickBehaviour(this, 1000));

        // Add message handler
        addBehaviour(new MessageHandler());
    }

    @Override
    protected void takeDown() {
        System.out.println("[Field-" + fieldState.getFieldId() + "] Agent terminated.");
    }

    /**
     * Main tick behavior - runs every second.
     */
    private class FieldTickBehaviour extends TickerBehaviour {

        public FieldTickBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            // If growth is 100%, field is ready for harvest - pause all activity
            if (fieldState.isReadyForHarvest()) {
                // Only send harvest request if not already requested
                checkAndSendRequests();
                broadcastState();
                return; // Skip all other processing - wait for harvest
            }

            // 1. Decrease scan level by crop's scan decay rate
            fieldState.decreaseScanLevel();

            // 2. Decrease moisture by crop's water consumption rate
            fieldState.decreaseMoisture();

            // 3. Try to grow (only if moisture > 30 and health > 50)
            fieldState.tryGrow();

            // 4. Disease generation (5% chance)
            if (!fieldState.hasDisease() && Math.random() < 0.05) {
                DiseaseType disease = DiseaseType.getRandomDisease();
                fieldState.setCurrentDisease(disease);
                System.out.println("[Field-" + fieldState.getFieldId() + "] DISEASE OUTBREAK: " + disease.getEmoji()
                        + " " + disease.getDisplayName());
                diagnosisRequested = false; // Reset request flag for new disease
            }

            // 5. Apply disease damage every tick
            if (fieldState.hasDisease()) {
                fieldState.applyDiseaseDamage();
            }

            // 6. Send requests to FarmManager based on thresholds
            checkAndSendRequests();

            // 7. Broadcast state to frontend
            broadcastState();
        }
    }

    /**
     * Check thresholds and send requests to FarmManager.
     */
    private void checkAndSendRequests() {
        AID farmManager = new AID("FarmManager", AID.ISLOCALNAME);

        // REQUEST_SCAN when scanLevel < 20
        if (fieldState.needsScan() && !scanRequested) {
            ACLMessage scanRequest = new ACLMessage(ACLMessage.REQUEST);
            scanRequest.addReceiver(farmManager);
            scanRequest.setContent("SCAN:" + fieldState.getFieldId());
            send(scanRequest);
            scanRequested = true;
            System.out.println("[Field-" + fieldState.getFieldId() + "] 📡 Requesting scan (scanLevel="
                    + fieldState.getScanLevel() + "%)");
        }

        // REQUEST_WATER when moisture < 30
        if (fieldState.needsWater() && !waterRequested) {
            ACLMessage waterRequest = new ACLMessage(ACLMessage.REQUEST);
            waterRequest.addReceiver(farmManager);
            waterRequest.setContent("WATER:" + fieldState.getFieldId() + ":" + (100 - fieldState.getMoisture()));
            send(waterRequest);
            waterRequested = true;
            System.out.println("[Field-" + fieldState.getFieldId() + "] 💧 Requesting water (moisture="
                    + fieldState.getMoisture() + "%)");
        }

        // REQUEST_DIAGNOSIS when disease detected
        if (fieldState.needsTreatment() && !diagnosisRequested) {
            ACLMessage diagnosisRequest = new ACLMessage(ACLMessage.REQUEST);
            diagnosisRequest.addReceiver(farmManager);
            // Include disease info so FarmManager can update its beliefs
            diagnosisRequest.setContent("DIAGNOSE:" + fieldState.getFieldId() + ":" +
                    fieldState.getCurrentDisease().name() + ":" +
                    fieldState.getMoisture() + ":" + fieldState.getHealth());
            send(diagnosisRequest);
            diagnosisRequested = true;
            System.out.println("[Field-" + fieldState.getFieldId() + "] Requesting diagnosis (disease: " +
                    fieldState.getCurrentDisease().getDisplayName() + ")");
        }

        // REQUEST_HARVEST when growth = 100
        if (fieldState.isReadyForHarvest() && !harvestRequested) {
            ACLMessage harvestRequest = new ACLMessage(ACLMessage.REQUEST);
            harvestRequest.addReceiver(farmManager);
            harvestRequest.setContent("HARVEST:" + fieldState.getFieldId());
            send(harvestRequest);
            harvestRequested = true;
            System.out.println("[Field-" + fieldState.getFieldId() + "] 🌾 Ready for harvest! (growth=100%)");
        }
    }

    /**
     * Handle incoming messages.
     */
    private class MessageHandler extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String content = msg.getContent();

                if (content.startsWith("SCANNED")) {
                    // Drone completed scan
                    fieldState.fullScan();
                    scanRequested = false;
                    System.out.println("[Field-" + fieldState.getFieldId() + "] ✅ Scan complete (scanLevel=100%)");

                } else if (content.startsWith("WATERED:")) {
                    // Water delivered
                    int amount = Integer.parseInt(content.split(":")[1]);
                    fieldState.addWater(amount);
                    waterRequested = false;
                    System.out.println("[Field-" + fieldState.getFieldId() + "] ✅ Watered +" + amount + " (moisture="
                            + fieldState.getMoisture() + "%)");

                } else if (content.startsWith("TREATED")) {
                    // Disease treated
                    fieldState.clearDisease();
                    fieldState.restoreHealth(30); // Partial health restore
                    diagnosisRequested = false;
                    System.out.println("[Field-" + fieldState.getFieldId() + "] ✅ Disease treated (health="
                            + fieldState.getHealth() + "%)");

                } else if (content.startsWith("HARVESTED")) {
                    // Crop harvested
                    fieldState.harvest();
                    harvestRequested = false;
                    System.out.println("[Field-" + fieldState.getFieldId() + "] ✅ Harvested (growth=0%)");

                } else if (content.startsWith("GET_STATE")) {
                    // Return current state
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("STATE:" + fieldState.toJson());
                    send(reply);
                }

                // Broadcast updated state
                broadcastState();

            } else {
                block();
            }
        }
    }

    /**
     * Broadcast field state to frontend via WebServer.
     */
    private void broadcastState() {
        WebServer.broadcast("FIELD_UPDATE", fieldState.toJson());
    }

    /**
     * Get current field state (for mobile agents).
     */
    public FieldState getFieldState() {
        return fieldState;
    }
}
