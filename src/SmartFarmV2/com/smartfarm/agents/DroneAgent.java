package com.smartfarm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import com.smartfarm.web.WebServer;

/**
 * DroneAgent - Intelligent mobile agent that scans fields for diseases.
 * 
 * FIXED: Now acts as AI diagnostic agent
 * - Receives SCAN request from Field
 * - Uses AI to diagnose disease type
 * - Sends SCAN_DONE to Field
 * - Sends TREAT request to Sprayer (if disease found)
 */
public class DroneAgent extends Agent {

    private String droneId;
    private WebServer webServer;

    // State
    private int battery = 100;
    private String currentLocation = "Base-Container";
    private String status = "Idle";
    private boolean isBusy = false;

    // Battery costs
    private static final int MOVE_COST = 5;
    private static final int SCAN_COST = 10;
    private static final int CHARGE_RATE = 5;
    private static final int LOW_BATTERY = 20;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            droneId = (String) args[0];
            webServer = (WebServer) args[1];
        } else {
            droneId = "Drone-1";
        }

        System.out.println("[" + droneId + "] Mobile agent started.");
        System.out.println("[" + droneId + "] Battery: " + battery + "%, Location: " + currentLocation);

        addBehaviour(new BatteryBehaviour(this, 3000));
        addBehaviour(new MessageHandler());

        broadcastState();
    }

    @Override
    protected void takeDown() {
        System.out.println("[" + droneId + "] Agent terminated.");
    }

    private class BatteryBehaviour extends TickerBehaviour {
        public BatteryBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (currentLocation.equals("Base-Container") && battery < 100 && !isBusy) {
                battery = Math.min(100, battery + CHARGE_RATE);
                status = "Charging";
            }

            if (battery <= LOW_BATTERY && !currentLocation.equals("Base-Container") && !isBusy) {
                System.out.println("[" + droneId + "] Low battery! Returning to base...");
                returnToBase();
            }

            broadcastState();
        }
    }

    private class MessageHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                System.out.println("[" + droneId + "] Received: " + content);

                if (content.startsWith("SCAN:")) {
                    String fieldId = content.substring(5); // "Field-1"
                    handleScanRequest(fieldId, msg.getSender());
                }
            } else {
                block();
            }
        }
    }

    private void handleScanRequest(String fieldId, AID requester) {
        if (isBusy) {
            System.out.println("[" + droneId + "] Busy, cannot accept scan request.");
            return;
        }

        if (battery < MOVE_COST + SCAN_COST + MOVE_COST) {
            System.out.println("[" + droneId + "] Not enough battery for mission.");
            return;
        }

        isBusy = true;
        String fieldContainer = "Field-Container-" + fieldId.replace("Field-", "");
        status = "Mission: " + fieldId;
        broadcastState();

        System.out.println("[" + droneId + "] Accepting scan request for " + fieldId);
        broadcastLog(droneId + " flying to " + fieldId + " for scan");

        // Store fieldId for use in behaviour
        final String targetField = fieldId;
        final AID fieldAgent = requester;

        addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
            @Override
            public void action() {
                try {
                    // Step 1: Move to field
                    moveToContainer(fieldContainer);
                    Thread.sleep(2000);

                    // Step 2: AI Scan and diagnose
                    String detectedDisease = performAIScan(targetField, fieldAgent);
                    Thread.sleep(1500);

                    // Step 3: Return to base
                    returnToBase();

                    isBusy = false;
                    status = "Idle";
                    broadcastState();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void moveToContainer(String containerName) {
        if (battery < MOVE_COST) {
            System.out.println("[" + droneId + "] Not enough battery to move!");
            return;
        }

        String previousLocation = currentLocation;
        currentLocation = containerName;
        battery -= MOVE_COST;
        status = "Moving to " + containerName;

        System.out.println("[" + droneId + "] Moving: " + previousLocation + " → " + containerName);
        broadcastMove(previousLocation, containerName);
        broadcastState();
    }

    /**
     * FIXED: AI Scan - diagnoses disease and alerts Sprayer
     */
    private String performAIScan(String fieldId, AID fieldAgent) {
        battery -= SCAN_COST;
        status = "AI Scanning " + fieldId;

        System.out.println("[" + droneId + "] AI Scanning " + fieldId + "...");
        broadcastLog(droneId + " scanning " + fieldId + " with AI...");
        broadcastState();

        // AI DIAGNOSIS: 60% chance disease, 40% chance healthy
        String detectedDisease;
        if (Math.random() < 0.6) {
            String[] diseases = { "FUNGAL_BLIGHT", "ROOT_ROT", "APHIDS" };
            detectedDisease = diseases[(int) (Math.random() * diseases.length)];
        } else {
            detectedDisease = "NONE";
        }

        System.out.println("[" + droneId + "] AI Diagnosis: " + detectedDisease);

        // FIXED: Send SCAN_DONE to Field
        ACLMessage scanResult = new ACLMessage(ACLMessage.INFORM);
        scanResult.addReceiver(fieldAgent);
        scanResult.setContent("SCAN_DONE:" + detectedDisease);
        send(scanResult);
        System.out.println("[" + droneId + "] Sent SCAN_DONE:" + detectedDisease + " to " + fieldId);

        // FIXED: If disease found, alert Sprayer
        if (!detectedDisease.equals("NONE")) {
            broadcastLog(droneId + " detected " + detectedDisease + " in " + fieldId + "!");

            ACLMessage treatRequest = new ACLMessage(ACLMessage.REQUEST);
            treatRequest.addReceiver(new AID("Sprayer", AID.ISLOCALNAME));
            treatRequest.setContent("TREAT:" + fieldId + ":" + detectedDisease);
            send(treatRequest);
            System.out.println("[" + droneId + "] Alerted Sprayer to treat " + detectedDisease + " on " + fieldId);
            broadcastLog(droneId + " alerted Sprayer for " + fieldId);
        } else {
            broadcastLog(droneId + ": " + fieldId + " is healthy!");
        }

        return detectedDisease;
    }

    private void returnToBase() {
        if (!currentLocation.equals("Base-Container")) {
            moveToContainer("Base-Container");
            status = "Returning to base";
        }
    }

    private void broadcastState() {
        if (webServer == null)
            return;
        String json = String.format(
                "{\"id\":\"%s\",\"type\":\"drone\",\"battery\":%d,\"location\":\"%s\",\"status\":\"%s\",\"busy\":%b}",
                droneId, battery, currentLocation, status, isBusy);
        webServer.broadcast("AGENT_UPDATE", json);
    }

    private void broadcastMove(String from, String to) {
        if (webServer == null)
            return;
        String json = String.format(
                "{\"agent\":\"%s\",\"from\":\"%s\",\"to\":\"%s\"}",
                droneId, from, to);
        webServer.broadcast("AGENT_MOVE", json);
    }

    private void broadcastLog(String message) {
        if (webServer == null)
            return;
        webServer.broadcast("LOG", "{\"message\":\"" + message + "\"}");
    }
}
