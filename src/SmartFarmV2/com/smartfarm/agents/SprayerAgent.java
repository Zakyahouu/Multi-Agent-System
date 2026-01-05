package com.smartfarm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import com.smartfarm.web.WebServer;

/**
 * SprayerAgent - Mobile worker that treats diseased fields.
 */
public class SprayerAgent extends Agent {

    private String agentId;
    private WebServer webServer;

    private int battery = 100;
    private String currentLocation = "Base-Container";
    private String status = "Idle";
    private boolean isBusy = false;

    private static final int MOVE_COST = 5;
    private static final int SPRAY_COST = 15;
    private static final int CHARGE_RATE = 5;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            agentId = (String) args[0];
            webServer = (WebServer) args[1];
        } else {
            agentId = "Sprayer";
        }

        System.out.println("[" + agentId + "] Worker agent started.");
        addBehaviour(new BatteryBehaviour(this, 3000));
        addBehaviour(new TreatmentRequestHandler());
        broadcastState();
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
            broadcastState();
        }
    }

    private class TreatmentRequestHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                if (content.startsWith("TREAT:")) {
                    String[] parts = content.substring(6).split(":");
                    String fieldId = parts[0];
                    String disease = parts.length > 1 ? parts[1] : "UNKNOWN";
                    handleTreatRequest(fieldId, disease, msg.getSender());
                }
            } else {
                block();
            }
        }
    }

    private void handleTreatRequest(String fieldId, String disease, AID requester) {
        if (isBusy || battery < MOVE_COST + SPRAY_COST + MOVE_COST)
            return;

        isBusy = true;
        String fieldContainer = "Field-Container-" + fieldId.replace("Field-", "");
        status = "Treating " + fieldId;
        broadcastState();

        System.out.println("[" + agentId + "] Going to treat " + disease + " on " + fieldId);
        broadcastLog(agentId + " treating " + disease + " on " + fieldId);

        addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
            @Override
            public void action() {
                try {
                    moveToContainer(fieldContainer);
                    Thread.sleep(1500);

                    performTreatment(fieldId, disease, requester);
                    Thread.sleep(1000);

                    moveToContainer("Base-Container");

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
        String prev = currentLocation;
        currentLocation = containerName;
        battery -= MOVE_COST;
        broadcastMove(prev, containerName);
        broadcastState();
    }

    private void performTreatment(String fieldId, String disease, AID requester) {
        // FIXED: Consume fungicide from inventory
        if (!com.smartfarm.models.Inventory.useFungicide()) {
            System.out.println("[" + agentId + "] Cannot treat - no fungicide!");
            broadcastLog(agentId + ": No fungicide available!");
            return;
        }

        battery -= SPRAY_COST;
        status = "Spraying";
        System.out.println("[" + agentId + "] Treating " + disease + " on " + fieldId + "...");
        broadcastLog(agentId + " cured " + disease + " on " + fieldId + "!");

        // FIXED: Send to Field agent, NOT the requester (which is Drone)
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID(fieldId, AID.ISLOCALNAME)); // e.g., "Field-1"
        msg.setContent("TREATMENT_DONE");
        send(msg);
        System.out.println("[" + agentId + "] Sent TREATMENT_DONE to " + fieldId);
        broadcastState();
    }

    private void broadcastState() {
        if (webServer == null)
            return;
        String json = String.format(
                "{\"id\":\"%s\",\"type\":\"sprayer\",\"battery\":%d,\"location\":\"%s\",\"status\":\"%s\",\"busy\":%b}",
                agentId, battery, currentLocation, status, isBusy);
        webServer.broadcast("AGENT_UPDATE", json);
    }

    private void broadcastMove(String from, String to) {
        if (webServer == null)
            return;
        webServer.broadcast("AGENT_MOVE",
                String.format("{\"agent\":\"%s\",\"from\":\"%s\",\"to\":\"%s\"}", agentId, from, to));
    }

    private void broadcastLog(String msg) {
        if (webServer == null)
            return;
        webServer.broadcast("LOG", "{\"message\":\"" + msg + "\"}");
    }
}
