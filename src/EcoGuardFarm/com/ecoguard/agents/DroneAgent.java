package com.ecoguard.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.ContainerID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import com.ecoguard.models.DiseaseType;
import com.ecoguard.models.ItemType;
import com.ecoguard.helpers.MockNeuralNetwork;
import com.ecoguard.web.WebServer;

/**
 * DroneAgent - Mobile agent with AI for disease diagnosis.
 * 
 * Location: Starts in Main-Container
 * Architecture: Hybrid (Mobile + AI)
 * 
 * Responsibilities:
 * - Field scanning
 * - Disease diagnosis using MockNeuralNetwork
 * - NO spraying or chemical transport
 * 
 * Battery: -10% per movement/scan, recharge at base when <20%
 */
public class DroneAgent extends Agent {

    private int droneId;
    private int battery = 100;
    private String currentLocation = "Main-Container";
    private String state = "idle";
    private boolean isCharging = false;

    private MockNeuralNetwork aiModel;

    @Override
    protected void setup() {
        // Get drone ID from arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            droneId = (Integer) args[0];
        } else {
            droneId = 1;
        }

        // Initialize AI model
        aiModel = new MockNeuralNetwork();

        System.out.println("[Drone-" + droneId + "] Mobile+AI agent started with " + aiModel);
        System.out.println("[Drone-" + droneId + "] Battery: " + battery + "%, Location: " + currentLocation);

        // Broadcast initial state
        broadcastState();

        // Add message handler
        addBehaviour(new MessageHandler());

        // Add battery check behavior
        addBehaviour(new BatteryCheckBehaviour(this, 2000));
    }

    @Override
    protected void takeDown() {
        System.out.println("[Drone-" + droneId + "] Agent terminated.");
    }

    @Override
    protected void afterMove() {
        // Called after agent migrates to a new container
        currentLocation = here().getName();
        System.out.println("[Drone-" + droneId + "] ✈️ Arrived at " + currentLocation);
        broadcastState();
    }

    /**
     * Handle incoming messages (dispatch commands).
     */
    private class MessageHandler extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String content = msg.getContent();

                if (content.startsWith("SCAN_FIELD:")) {
                    // Dispatch command to scan a field
                    int fieldId = Integer.parseInt(content.split(":")[1]);

                    if (battery < 20) {
                        // Low battery - refuse task
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("LOW_BATTERY");
                        send(reply);
                        System.out.println("[Drone-" + droneId + "] ⚠️ Refused scan - low battery (" + battery + "%)");
                    } else {
                        // Accept and execute scan mission
                        state = "dispatched";
                        broadcastState();
                        addBehaviour(new ScanMission(fieldId, msg.getSender()));
                    }

                } else if (content.startsWith("DIAGNOSE_FIELD:")) {
                    // Dispatch command for AI diagnosis
                    String[] parts = content.split(":");
                    int fieldId = Integer.parseInt(parts[1]);
                    DiseaseType actualDisease = parts.length > 2 ? DiseaseType.valueOf(parts[2]) : null;
                    int moisture = parts.length > 3 ? Integer.parseInt(parts[3]) : 50;
                    int health = parts.length > 4 ? Integer.parseInt(parts[4]) : 50;

                    if (battery < 20) {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("LOW_BATTERY");
                        send(reply);
                    } else {
                        state = "dispatched";
                        broadcastState();
                        addBehaviour(new DiagnosisMission(fieldId, actualDisease, moisture, health, msg.getSender()));
                    }
                }

            } else {
                block();
            }
        }
    }

    /**
     * Scan mission - move to field, scan, return.
     */
    private class ScanMission extends OneShotBehaviour {
        private int fieldId;
        private AID requester;

        public ScanMission(int fieldId, AID requester) {
            this.fieldId = fieldId;
            this.requester = requester;
        }

        @Override
        public void action() {
            try {
                System.out.println("[Drone-" + droneId + "] 📡 Starting scan mission for Field-" + fieldId);

                // Move to field container
                state = "flying";
                broadcastState();

                String targetContainer = "Field-Container-" + fieldId;
                ContainerID destination = new ContainerID(targetContainer, null);

                System.out.println("[Drone-" + droneId + "] ✈️ Moving to " + targetContainer + "...");
                battery -= 10; // Movement cost
                broadcastState();

                // Perform actual migration
                doMove(destination);

                // Wait for arrival (simulated travel time)
                Thread.sleep(1500);

                // Perform scan
                state = "scanning";
                currentLocation = targetContainer;
                broadcastState();
                System.out.println("[Drone-" + droneId + "] 🔍 Scanning Field-" + fieldId + "...");

                battery -= 10; // Scan cost
                Thread.sleep(1000);

                // Notify field agent
                ACLMessage scanComplete = new ACLMessage(ACLMessage.INFORM);
                scanComplete.addReceiver(new AID("Field-" + fieldId, AID.ISLOCALNAME));
                scanComplete.setContent("SCANNED");
                send(scanComplete);

                // Return to base
                state = "returning";
                broadcastState();

                ContainerID mainContainer = new ContainerID("Main-Container", null);
                System.out.println("[Drone-" + droneId + "] ✈️ Returning to Main-Container...");
                battery -= 10;

                doMove(mainContainer);
                Thread.sleep(1500);

                currentLocation = "Main-Container";
                state = "idle";
                broadcastState();

                // Report completion
                ACLMessage report = new ACLMessage(ACLMessage.INFORM);
                report.addReceiver(requester);
                report.setContent("SCAN_COMPLETE:" + fieldId);
                send(report);

                System.out.println("[Drone-" + droneId + "] ✅ Scan mission complete. Battery: " + battery + "%");

            } catch (Exception e) {
                System.err.println("[Drone-" + droneId + "] Error in scan mission: " + e.getMessage());
                state = "idle";
                broadcastState();
            }
        }
    }

    /**
     * Diagnosis mission - move to field, run AI, return with results.
     */
    private class DiagnosisMission extends OneShotBehaviour {
        private int fieldId;
        private DiseaseType actualDisease;
        private int moisture;
        private int health;
        private AID requester;

        public DiagnosisMission(int fieldId, DiseaseType actualDisease, int moisture, int health, AID requester) {
            this.fieldId = fieldId;
            this.actualDisease = actualDisease;
            this.moisture = moisture;
            this.health = health;
            this.requester = requester;
        }

        @Override
        public void action() {
            try {
                System.out.println("[Drone-" + droneId + "] 🔬 Starting diagnosis mission for Field-" + fieldId);

                // Move to field
                state = "flying";
                broadcastState();

                String targetContainer = "Field-Container-" + fieldId;
                ContainerID destination = new ContainerID(targetContainer, null);

                System.out.println("[Drone-" + droneId + "] ✈️ Moving to " + targetContainer + "...");
                battery -= 10;

                doMove(destination);
                Thread.sleep(1500);

                currentLocation = targetContainer;
                state = "diagnosing";
                broadcastState();

                // Run AI diagnosis
                System.out.println("[Drone-" + droneId + "] 🧠 Running AI diagnosis...");
                MockNeuralNetwork.DiagnosisResult result = aiModel.diagnose(moisture, health, 50, actualDisease);

                battery -= 10; // AI processing cost
                Thread.sleep(1000);

                // Broadcast AI result to frontend
                String aiResultJson = String.format(
                        "{\"droneId\":\"Drone-%d\",\"fieldId\":%d,\"disease\":%s,\"confidence\":%d,\"explanation\":\"%s\"}",
                        droneId, fieldId,
                        result.getDisease() != null ? "\"" + result.getDisease().name() + "\"" : "null",
                        result.getConfidence(),
                        result.getExplanation());
                WebServer.broadcast("AI_RESULT", aiResultJson);

                // Return to base
                state = "returning";
                broadcastState();

                ContainerID mainContainer = new ContainerID("Main-Container", null);
                System.out.println("[Drone-" + droneId + "] ✈️ Returning to Main-Container...");
                battery -= 10;

                doMove(mainContainer);
                Thread.sleep(1500);

                currentLocation = "Main-Container";
                state = "idle";
                broadcastState();

                // Report diagnosis result to FarmManager
                ACLMessage report = new ACLMessage(ACLMessage.INFORM);
                report.addReceiver(requester);
                report.setContent("DIAGNOSIS_RESULT:" + fieldId + ":" +
                        (result.getDisease() != null ? result.getDisease().name() : "NONE") + ":" +
                        result.getConfidence());
                send(report);

                System.out.println("[Drone-" + droneId + "] ✅ Diagnosis complete: " + result);

            } catch (Exception e) {
                System.err.println("[Drone-" + droneId + "] Error in diagnosis mission: " + e.getMessage());
                state = "idle";
                broadcastState();
            }
        }
    }

    /**
     * Battery check - return to base and charge if low.
     */
    private class BatteryCheckBehaviour extends TickerBehaviour {

        public BatteryCheckBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (battery < 20 && !isCharging && state.equals("idle")) {
                isCharging = true;
                state = "charging";
                broadcastState();
                System.out.println("[Drone-" + droneId + "] 🔋 Low battery! Charging...");

                addBehaviour(new OneShotBehaviour() {
                    @Override
                    public void action() {
                        try {
                            Thread.sleep(5000); // 5 second charge time
                            battery = 100;
                            isCharging = false;
                            state = "idle";
                            broadcastState();
                            System.out.println("[Drone-" + droneId + "] ✅ Fully charged (100%)");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
        }
    }

    /**
     * Broadcast drone state to frontend.
     */
    private void broadcastState() {
        String json = String.format(
                "{\"droneId\":\"Drone-%d\",\"battery\":%d,\"location\":\"%s\",\"state\":\"%s\"}",
                droneId, battery, currentLocation, state);
        WebServer.broadcast("DRONE_MOVE", json);
    }
}
