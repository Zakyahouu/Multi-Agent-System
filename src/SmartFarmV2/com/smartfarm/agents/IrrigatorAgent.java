package com.smartfarm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import com.smartfarm.web.WebServer;

/**
 * IrrigatorAgent - Mobile worker that waters fields when they need it.
 * 
 * Features:
 * - Battery system (0-100%)
 * - Listens for WATER_REQUEST messages from fields
 * - Moves to field, waters, returns to base
 * - Charges when idle at base
 */
public class IrrigatorAgent extends Agent {

    private String agentId;
    private WebServer webServer;

    // State
    private int battery = 100;
    private String currentLocation = "Base-Container";
    private String status = "Idle";
    private boolean isBusy = false;

    // Costs
    private static final int MOVE_COST = 5;
    private static final int WATER_COST = 15;
    private static final int CHARGE_RATE = 5;
    private static final int LOW_BATTERY = 25;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            agentId = (String) args[0];
            webServer = (WebServer) args[1];
        } else {
            agentId = "Irrigator";
        }

        System.out.println("[" + agentId + "] Worker agent started.");

        // Add behaviors
        addBehaviour(new BatteryBehaviour(this, 3000));
        addBehaviour(new WaterRequestHandler());

        broadcastState();
    }

    @Override
    protected void takeDown() {
        System.out.println("[" + agentId + "] Agent terminated.");
    }

    /**
     * Battery management - charges at base
     */
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
                returnToBase();
            }

            broadcastState();
        }
    }

    /**
     * Listen for water requests from fields
     */
    private class WaterRequestHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                if (content.startsWith("WATER:")) {
                    String fieldId = content.substring(6);
                    handleWaterRequest(fieldId, msg.getSender());
                }
            } else {
                block();
            }
        }
    }

    /**
     * Handle water request from a field
     */
    private void handleWaterRequest(String fieldId, AID requester) {
        if (isBusy) {
            System.out.println("[" + agentId + "] Busy, ignoring water request from " + fieldId);
            return;
        }

        if (battery < MOVE_COST + WATER_COST + MOVE_COST) {
            System.out.println("[" + agentId + "] Not enough battery for watering mission.");
            return;
        }

        // FIXED: Check water availability BEFORE moving
        if (com.smartfarm.models.Inventory.getWater() < 50) {
            System.out.println("[" + agentId + "] No water available in warehouse!");
            broadcastLog(agentId + ": No water in warehouse!");
            return;
        }

        isBusy = true;
        String fieldContainer = "Field-Container-" + fieldId.replace("Field-", "");
        status = "Watering " + fieldId;
        broadcastState();

        System.out.println("[" + agentId + "] Accepting water request from " + fieldId);
        broadcastLog(agentId + " going to water " + fieldId);

        // Execute watering mission
        addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
            @Override
            public void action() {
                try {
                    // Move to field
                    moveToContainer(fieldContainer);
                    Thread.sleep(1500);

                    // Water the field
                    performWatering(fieldId, requester);
                    Thread.sleep(1000);

                    // Return to base
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
        if (battery < MOVE_COST)
            return;

        String previousLocation = currentLocation;
        currentLocation = containerName;
        battery -= MOVE_COST;
        status = "Moving to " + containerName;

        System.out.println("[" + agentId + "] " + previousLocation + " → " + containerName);
        broadcastMove(previousLocation, containerName);
        broadcastState();
    }

    private void performWatering(String fieldId, AID fieldAgent) {
        // FIXED: Consume water from inventory
        if (!com.smartfarm.models.Inventory.useWater(50)) {
            System.out.println("[" + agentId + "] Cannot water - no water in warehouse!");
            broadcastLog(agentId + ": No water available!");
            return;
        }

        battery -= WATER_COST;
        status = "Watering " + fieldId;

        System.out.println("[" + agentId + "] Watering " + fieldId + "...");
        broadcastLog(agentId + " watering " + fieldId + " (+50%)");

        // Send water to field agent
        ACLMessage waterMsg = new ACLMessage(ACLMessage.INFORM);
        waterMsg.addReceiver(fieldAgent);
        waterMsg.setContent("WATER_DONE:50");
        send(waterMsg);

        broadcastState();
    }

    private void returnToBase() {
        if (!currentLocation.equals("Base-Container")) {
            moveToContainer("Base-Container");
            status = "Returning";
        }
    }

    private void broadcastState() {
        if (webServer == null)
            return;
        String json = String.format(
                "{\"id\":\"%s\",\"type\":\"irrigator\",\"battery\":%d,\"location\":\"%s\",\"status\":\"%s\",\"busy\":%b}",
                agentId, battery, currentLocation, status, isBusy);
        webServer.broadcast("AGENT_UPDATE", json);
    }

    private void broadcastMove(String from, String to) {
        if (webServer == null)
            return;
        String json = String.format("{\"agent\":\"%s\",\"from\":\"%s\",\"to\":\"%s\"}", agentId, from, to);
        webServer.broadcast("AGENT_MOVE", json);
    }

    private void broadcastLog(String message) {
        if (webServer == null)
            return;
        webServer.broadcast("LOG", "{\"message\":\"" + message + "\"}");
    }
}
