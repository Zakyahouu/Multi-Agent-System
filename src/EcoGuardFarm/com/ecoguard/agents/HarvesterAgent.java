package com.ecoguard.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.ContainerID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import com.ecoguard.models.CropType;
import com.ecoguard.web.WebServer;

/**
 * HarvesterAgent - Mobile agent for harvesting crops.
 * 
 * Location: Main-Container (base)
 * Architecture: Mobile worker
 * 
 * Responsibilities:
 * - Move to field
 * - Harvest (reset growth to 0)
 * - Add crop to inventory
 * - Return to base
 * 
 * Constraint: Cannot harvest if storage is full
 */
public class HarvesterAgent extends Agent {

    private int harvesterId;
    private int battery = 100;
    private String currentLocation = "Main-Container";
    private String state = "idle";
    private boolean isCharging = false;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            harvesterId = (Integer) args[0];
        } else {
            harvesterId = 1;
        }

        System.out.println("[Harvester-" + harvesterId + "] Mobile worker agent started.");
        System.out.println("[Harvester-" + harvesterId + "] Battery: " + battery + "%, Location: " + currentLocation);

        broadcastState();

        addBehaviour(new MessageHandler());
        addBehaviour(new BatteryCheckBehaviour(this, 2000));
    }

    @Override
    protected void takeDown() {
        System.out.println("[Harvester-" + harvesterId + "] Agent terminated.");
    }

    @Override
    protected void afterMove() {
        currentLocation = here().getName();
        System.out.println("[Harvester-" + harvesterId + "] 🚜 Arrived at " + currentLocation);
        broadcastState();
    }

    /**
     * Handle harvest commands.
     */
    private class MessageHandler extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String content = msg.getContent();

                if (content.startsWith("HARVEST_FIELD:")) {
                    // Format: HARVEST_FIELD:fieldId:cropType
                    String[] parts = content.split(":");
                    int fieldId = Integer.parseInt(parts[1]);
                    CropType cropType = CropType.valueOf(parts[2]);

                    if (battery < 20) {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("LOW_BATTERY");
                        send(reply);
                        System.out.println("[Harvester-" + harvesterId + "] ⚠️ Refused - low battery");
                    } else {
                        state = "dispatched";
                        broadcastState();
                        addBehaviour(new HarvestMission(fieldId, cropType, msg.getSender()));
                    }
                }

            } else {
                block();
            }
        }
    }

    /**
     * Harvest mission - move, harvest, return.
     */
    private class HarvestMission extends OneShotBehaviour {
        private int fieldId;
        private CropType cropType;
        private AID requester;

        public HarvestMission(int fieldId, CropType cropType, AID requester) {
            this.fieldId = fieldId;
            this.cropType = cropType;
            this.requester = requester;
        }

        @Override
        public void action() {
            try {
                System.out.println("[Harvester-" + harvesterId + "] 🌾 Starting harvest mission for Field-" + fieldId);
                System.out.println("[Harvester-" + harvesterId + "] Crop: " + cropType.getEmoji() + " "
                        + cropType.getDisplayName());

                // Move to field
                state = "driving";
                broadcastState();

                String targetContainer = "Field-Container-" + fieldId;
                ContainerID destination = new ContainerID(targetContainer, null);

                System.out.println("[Harvester-" + harvesterId + "] 🚜 Moving to " + targetContainer + "...");
                battery -= 5;

                doMove(destination);
                Thread.sleep(2500); // Harvester is slower

                currentLocation = targetContainer;
                state = "harvesting";
                broadcastState();

                // Perform harvesting
                System.out
                        .println("[Harvester-" + harvesterId + "] 🌾 Harvesting " + cropType.getDisplayName() + "...");
                Thread.sleep(2000);

                battery -= 10;

                // Notify field agent
                ACLMessage harvestComplete = new ACLMessage(ACLMessage.INFORM);
                harvestComplete.addReceiver(new AID("Field-" + fieldId, AID.ISLOCALNAME));
                harvestComplete.setContent("HARVESTED");
                send(harvestComplete);

                // Broadcast harvest event
                String harvestJson = String.format(
                        "{\"harvesterId\":\"Harvester-%d\",\"fieldId\":%d,\"crop\":\"%s\"}",
                        harvesterId, fieldId, cropType.name());
                WebServer.broadcast("HARVEST_EVENT", harvestJson);

                // Return to base
                state = "returning";
                broadcastState();

                ContainerID mainContainer = new ContainerID("Main-Container", null);
                System.out.println(
                        "[Harvester-" + harvesterId + "] 🚜 Returning to Main-Container with " + cropType.getEmoji());
                battery -= 5;

                doMove(mainContainer);
                Thread.sleep(2500);

                currentLocation = "Main-Container";
                state = "idle";
                broadcastState();

                // Report completion with crop info
                ACLMessage report = new ACLMessage(ACLMessage.INFORM);
                report.addReceiver(requester);
                report.setContent("HARVEST_COMPLETE:" + fieldId + ":" + cropType.name());
                send(report);

                System.out.println("[Harvester-" + harvesterId + "] ✅ Harvest complete. Battery: " + battery + "%");

            } catch (Exception e) {
                System.err.println("[Harvester-" + harvesterId + "] Error: " + e.getMessage());
                state = "idle";
                broadcastState();
            }
        }
    }

    /**
     * Battery check behavior.
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
                System.out.println("[Harvester-" + harvesterId + "] 🔋 Low battery! Charging...");

                addBehaviour(new OneShotBehaviour() {
                    @Override
                    public void action() {
                        try {
                            Thread.sleep(5000);
                            battery = 100;
                            isCharging = false;
                            state = "idle";
                            broadcastState();
                            System.out.println("[Harvester-" + harvesterId + "] ✅ Fully charged (100%)");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
        }
    }

    private void broadcastState() {
        String json = String.format(
                "{\"harvesterId\":\"Harvester-%d\",\"battery\":%d,\"location\":\"%s\",\"state\":\"%s\"}",
                harvesterId, battery, currentLocation, state);
        WebServer.broadcast("HARVESTER_MOVE", json);
    }
}
