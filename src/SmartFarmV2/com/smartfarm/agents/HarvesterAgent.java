package com.smartfarm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import com.smartfarm.web.WebServer;

/**
 * HarvesterAgent - Mobile worker that harvests fields at 100% growth.
 */
public class HarvesterAgent extends Agent {

    private String agentId;
    private WebServer webServer;

    private int battery = 100;
    private String currentLocation = "Base-Container";
    private String status = "Idle";
    private boolean isBusy = false;

    private static final int MOVE_COST = 5;
    private static final int HARVEST_COST = 20;
    private static final int CHARGE_RATE = 5;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            agentId = (String) args[0];
            webServer = (WebServer) args[1];
        } else {
            agentId = "Harvester";
        }

        System.out.println("[" + agentId + "] Worker agent started.");
        addBehaviour(new BatteryBehaviour(this, 3000));
        addBehaviour(new HarvestRequestHandler());
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

    private class HarvestRequestHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                if (content.startsWith("HARVEST:")) {
                    String fieldId = content.substring(8);
                    handleHarvestRequest(fieldId, msg.getSender());
                }
            } else {
                block();
            }
        }
    }

    private void handleHarvestRequest(String fieldId, AID requester) {
        if (isBusy || battery < MOVE_COST + HARVEST_COST + MOVE_COST)
            return;

        isBusy = true;
        String fieldContainer = "Field-Container-" + fieldId.replace("Field-", "");
        status = "Harvesting " + fieldId;
        broadcastState();

        System.out.println("[" + agentId + "] Going to harvest " + fieldId);
        broadcastLog(agentId + " going to harvest " + fieldId);

        addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
            @Override
            public void action() {
                try {
                    moveToContainer(fieldContainer);
                    Thread.sleep(1500);

                    performHarvest(fieldId, requester);
                    Thread.sleep(1500);

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

    private void performHarvest(String fieldId, AID fieldAgent) {
        battery -= HARVEST_COST;
        status = "Harvesting";
        System.out.println("[" + agentId + "] Harvesting " + fieldId + "...");

        // FIXED: Add crops to inventory
        com.smartfarm.models.Inventory.addCrops(10);
        broadcastLog(agentId + " harvested " + fieldId + "! (+10 crops)");

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(fieldAgent);
        msg.setContent("HARVEST_DONE");
        send(msg);
        broadcastState();
    }

    private void broadcastState() {
        if (webServer == null)
            return;
        String json = String.format(
                "{\"id\":\"%s\",\"type\":\"harvester\",\"battery\":%d,\"location\":\"%s\",\"status\":\"%s\",\"busy\":%b}",
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
