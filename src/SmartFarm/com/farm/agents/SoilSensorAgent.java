package com.farm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.Random;

import com.farm.gui.WebServer;

/**
 * SoilSensorAgent V2 - A reactive agent that monitors soil moisture levels for
 * a specific field.
 * 
 * Behavior:
 * - Uses TickerBehaviour to check moisture every 3 seconds
 * - Generates realistic moisture values based on previous reading
 * - Sends updates to FarmerBDI for belief updates
 * - Sends alerts to Controller when moisture is critical
 * - Registers with DF as "moisture-sensor" service
 * 
 * Agent Type: REACTIVE
 */
public class SoilSensorAgent extends Agent {

    private static final long TICK_INTERVAL = 8000; // 8 seconds - slower for readability
    private static final int MOISTURE_THRESHOLD = 30;
    private Random random = new Random();
    private int lastMoistureReading = 50;
    private int fieldId = 1;

    @Override
    protected void setup() {
        // Get field ID from arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            fieldId = (Integer) args[0];
        }

        System.out.println("[Sensor-" + fieldId + "] Reactive agent starting in container: " + here().getName());

        // Register with Directory Facilitator
        registerWithDF();

        // Add moisture monitoring behavior
        addBehaviour(new MoistureSensingBehaviour(this, TICK_INTERVAL));

        // Add message handler for queries
        addBehaviour(new QueryHandler());

        // Broadcast startup
        WebServer.broadcast("AGENT_START", "{\"agent\":\"Sensor-" + fieldId
                + "\",\"type\":\"SoilSensor\",\"agentType\":\"reactive\",\"container\":\"" + here().getName() + "\"}");

        WebServer.broadcastAgentInteraction(
                "Sensor-" + fieldId, "reactive",
                "System", "system",
                "START", "Soil sensor initialized for Field-" + fieldId, 0);
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // Ignore
        }
        System.out.println("[Sensor-" + fieldId + "] Agent shutting down.");
        WebServer.broadcast("AGENT_STOP", "{\"agent\":\"Sensor-" + fieldId + "\"}");
    }

    private void registerWithDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("moisture-sensor");
        sd.setName("Soil-Moisture-Sensor-Field-" + fieldId);
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("[Sensor-" + fieldId + "] Registered with DF as 'moisture-sensor'");
        } catch (FIPAException e) {
            System.err.println("[Sensor-" + fieldId + "] Failed to register with DF: " + e.getMessage());
        }
    }

    /**
     * Inner class for moisture sensing behavior.
     * Runs every TICK_INTERVAL milliseconds.
     */
    private class MoistureSensingBehaviour extends TickerBehaviour {

        public MoistureSensingBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            // Generate realistic moisture reading based on previous value
            // Moisture naturally decreases over time (evaporation)
            int change = random.nextInt(11) - 5; // -5 to +5
            change -= 2; // Slight bias toward decreasing (evaporation)
            lastMoistureReading = Math.max(0, Math.min(100, lastMoistureReading + change));

            System.out.println("[Sensor-" + fieldId + "] Moisture reading: " + lastMoistureReading + "%");

            // Send update to FarmerBDI for belief update
            ACLMessage update = new ACLMessage(ACLMessage.INFORM);
            update.addReceiver(new AID("FarmerBDI", AID.ISLOCALNAME));
            update.setContent("FIELD_UPDATE:id:" + fieldId + ",moisture:" + lastMoistureReading);
            send(update);

            // Also inform the CropGrowthAgent in same container
            ACLMessage cropUpdate = new ACLMessage(ACLMessage.INFORM);
            cropUpdate.addReceiver(new AID("CropGrowth-" + fieldId, AID.ISLOCALNAME));
            cropUpdate.setContent("MOISTURE:" + lastMoistureReading);
            send(cropUpdate);

            // Broadcast reading to web UI
            WebServer.broadcast("MOISTURE_READING",
                    "{\"fieldId\":" + fieldId + ",\"value\":" + lastMoistureReading +
                            ",\"threshold\":" + MOISTURE_THRESHOLD +
                            ",\"timestamp\":\"" + System.currentTimeMillis() + "\"}");

            // Check if moisture is below threshold
            if (lastMoistureReading < MOISTURE_THRESHOLD) {
                System.out.println("[Sensor-" + fieldId + "] LOW MOISTURE DETECTED! Sending alert...");
                sendLowMoistureRequest();
            }

            // Broadcast interaction to GUI
            WebServer.broadcastAgentInteraction(
                    "Sensor-" + fieldId, "reactive",
                    "FarmerBDI", "bdi",
                    "INFORM", "Moisture: " + lastMoistureReading + "%", 1);
        }
    }

    private void sendLowMoistureRequest() {
        // Send to Controller for immediate action
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.addReceiver(new AID("Controller", AID.ISLOCALNAME));
        request.setContent("LOW_MOISTURE:" + fieldId + ":" + lastMoistureReading);
        request.setConversationId("moisture-alert-" + System.currentTimeMillis());
        request.setReplyWith("moisture-" + System.currentTimeMillis());
        send(request);

        // Also send to FarmerBDI for belief update
        ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
        alert.addReceiver(new AID("FarmerBDI", AID.ISLOCALNAME));
        alert.setContent("ALERT:LOW_MOISTURE:" + fieldId + ":" + lastMoistureReading);
        send(alert);

        // Broadcast alert to web UI
        WebServer.broadcast("SENSOR_ALERT",
                "{\"fieldId\":" + fieldId + ",\"type\":\"LOW_MOISTURE\",\"value\":" + lastMoistureReading
                        + ",\"message\":\"Field-" + fieldId + " moisture below threshold!\"}");

        // Log interaction
        WebServer.broadcastAgentInteraction(
                "Sensor-" + fieldId, "reactive",
                "Controller", "hybrid",
                "REQUEST", "LOW_MOISTURE ALERT: " + lastMoistureReading + "%", 1);
    }

    /**
     * Handles query requests from other agents.
     */
    private class QueryHandler extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.QUERY_IF) {
                    // Respond with current moisture reading
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("MOISTURE:" + fieldId + ":" + lastMoistureReading);
                    send(reply);
                }
                // Handle irrigation notification (moisture increase)
                if (msg.getContent() != null && msg.getContent().startsWith("IRRIGATED:")) {
                    int waterAmount = 30;
                    try {
                        String[] parts = msg.getContent().split(":");
                        if (parts.length > 1) {
                            waterAmount = Integer.parseInt(parts[1]);
                        }
                    } catch (Exception e) {
                        // Use default
                    }
                    lastMoistureReading = Math.min(100, lastMoistureReading + waterAmount);
                    System.out.println("[Sensor-" + fieldId + "] Irrigation detected: +" + waterAmount + "% moisture");
                }
            } else {
                block();
            }
        }
    }

    // Getter for current moisture level
    public int getLastMoistureReading() {
        return lastMoistureReading;
    }

    public int getFieldId() {
        return fieldId;
    }
}
