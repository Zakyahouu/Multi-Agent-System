package com.ecoguard.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.ContainerID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import com.ecoguard.models.ItemType;
import com.ecoguard.web.WebServer;

/**
 * SprayerAgent - Mobile agent for chemical transport and spraying.
 * 
 * Location: Main-Container (base)
 * Architecture: Mobile executor
 * 
 * Responsibilities:
 * - Load chemical from inventory
 * - Move to field
 * - Cure disease
 * - Return to base
 */
public class SprayerAgent extends Agent {

    private int sprayerId;
    private int battery = 100;
    private String currentLocation = "Main-Container";
    private String state = "idle";
    private ItemType carrying = null;
    private boolean isCharging = false;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            sprayerId = (Integer) args[0];
        } else {
            sprayerId = 1;
        }

        System.out.println("[Sprayer-" + sprayerId + "] Mobile executor agent started.");
        System.out.println("[Sprayer-" + sprayerId + "] Battery: " + battery + "%, Location: " + currentLocation);

        broadcastState();

        addBehaviour(new MessageHandler());
        addBehaviour(new BatteryCheckBehaviour(this, 2000));
    }

    @Override
    protected void takeDown() {
        System.out.println("[Sprayer-" + sprayerId + "] Agent terminated.");
    }

    @Override
    protected void afterMove() {
        currentLocation = here().getName();
        System.out.println("[Sprayer-" + sprayerId + "] 🚜 Arrived at " + currentLocation);
        broadcastState();
    }

    /**
     * Handle spray commands.
     */
    private class MessageHandler extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String content = msg.getContent();

                if (content.startsWith("SPRAY_FIELD:")) {
                    // Format: SPRAY_FIELD:fieldId:chemicalType
                    String[] parts = content.split(":");
                    int fieldId = Integer.parseInt(parts[1]);
                    ItemType chemical = ItemType.valueOf(parts[2]);

                    if (battery < 20) {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("LOW_BATTERY");
                        send(reply);
                        System.out.println("[Sprayer-" + sprayerId + "] ⚠️ Refused - low battery");
                    } else {
                        state = "loading";
                        carrying = chemical;
                        broadcastState();
                        addBehaviour(new SprayMission(fieldId, chemical, msg.getSender()));
                    }
                }

            } else {
                block();
            }
        }
    }

    /**
     * Spray mission - load, move, spray, return.
     */
    private class SprayMission extends OneShotBehaviour {
        private int fieldId;
        private ItemType chemical;
        private AID requester;

        public SprayMission(int fieldId, ItemType chemical, AID requester) {
            this.fieldId = fieldId;
            this.chemical = chemical;
            this.requester = requester;
        }

        @Override
        public void action() {
            try {
                System.out.println("[Sprayer-" + sprayerId + "] 🧪 Starting spray mission for Field-" + fieldId);
                System.out.println(
                        "[Sprayer-" + sprayerId + "] Loaded: " + chemical.getEmoji() + " " + chemical.getDisplayName());

                // Move to field
                state = "driving";
                broadcastState();

                String targetContainer = "Field-Container-" + fieldId;
                ContainerID destination = new ContainerID(targetContainer, null);

                System.out.println("[Sprayer-" + sprayerId + "] 🚜 Moving to " + targetContainer + "...");
                battery -= 5; // Slower drain than drone

                doMove(destination);
                Thread.sleep(2000); // Slower than drone

                currentLocation = targetContainer;
                state = "spraying";
                broadcastState();

                // Perform spraying
                System.out.println("[Sprayer-" + sprayerId + "] 💨 Spraying " + chemical.getDisplayName() + "...");
                Thread.sleep(1500);

                battery -= 5;

                // Notify field agent
                ACLMessage treatmentComplete = new ACLMessage(ACLMessage.INFORM);
                treatmentComplete.addReceiver(new AID("Field-" + fieldId, AID.ISLOCALNAME));
                treatmentComplete.setContent("TREATED");
                send(treatmentComplete);

                // Broadcast spray event
                String sprayJson = String.format(
                        "{\"sprayerId\":\"Sprayer-%d\",\"fieldId\":%d,\"chemical\":\"%s\"}",
                        sprayerId, fieldId, chemical.name());
                WebServer.broadcast("SPRAYER_MOVE", sprayJson);

                // Return to base
                carrying = null;
                state = "returning";
                broadcastState();

                ContainerID mainContainer = new ContainerID("Main-Container", null);
                System.out.println("[Sprayer-" + sprayerId + "] 🚜 Returning to Main-Container...");
                battery -= 5;

                doMove(mainContainer);
                Thread.sleep(2000);

                currentLocation = "Main-Container";
                state = "idle";
                broadcastState();

                // Report completion
                ACLMessage report = new ACLMessage(ACLMessage.INFORM);
                report.addReceiver(requester);
                report.setContent("SPRAY_COMPLETE:" + fieldId);
                send(report);

                System.out.println("[Sprayer-" + sprayerId + "] ✅ Spray mission complete. Battery: " + battery + "%");

            } catch (Exception e) {
                System.err.println("[Sprayer-" + sprayerId + "] Error: " + e.getMessage());
                state = "idle";
                carrying = null;
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
                System.out.println("[Sprayer-" + sprayerId + "] 🔋 Low battery! Charging...");

                addBehaviour(new OneShotBehaviour() {
                    @Override
                    public void action() {
                        try {
                            Thread.sleep(5000);
                            battery = 100;
                            isCharging = false;
                            state = "idle";
                            broadcastState();
                            System.out.println("[Sprayer-" + sprayerId + "] ✅ Fully charged (100%)");
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
                "{\"sprayerId\":\"Sprayer-%d\",\"battery\":%d,\"location\":\"%s\",\"state\":\"%s\",\"carrying\":%s}",
                sprayerId, battery, currentLocation, state,
                carrying != null ? "\"" + carrying.name() + "\"" : "null");
        WebServer.broadcast("SPRAYER_MOVE", json);
    }
}
