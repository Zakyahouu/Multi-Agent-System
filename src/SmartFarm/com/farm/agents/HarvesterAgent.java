package com.farm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.ContainerID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;

import com.farm.gui.WebServer;

import java.io.Serializable;

/**
 * HarvesterAgent - Mobile agent that moves to field containers to harvest
 * mature crops.
 * 
 * Similar to InspectorDroneAgent but specialized for harvesting operations.
 * Uses JADE mobility to physically migrate between containers.
 * 
 * Agent Type: MOBILE
 */
public class HarvesterAgent extends Agent implements Serializable {
    private static final long serialVersionUID = 1L;

    // State machine
    public enum HarvesterState {
        IDLE, // Waiting at base
        GOING_TO_FIELD, // Migrating to field container
        HARVESTING, // Collecting crops
        RETURNING // Going back to base
    }

    private HarvesterState state = HarvesterState.IDLE;
    private String homeContainer;
    private String targetField;
    private int targetFieldId = -1;
    private double harvestedValue = 0;
    private AID requester;

    @Override
    protected void setup() {
        homeContainer = here().getName();
        System.out.println("[Harvester] Mobile agent starting in container: " + homeContainer);

        // Register with DF
        registerWithDF();

        // Add harvest request handler
        addBehaviour(new HarvestRequestHandler());

        // Broadcast agent start to GUI
        WebServer.broadcast("AGENT_START",
                "{\"agent\":\"Harvester\",\"type\":\"harvester\"}");

        // Initial state broadcast
        WebServer.broadcastDroneMove(
                "Harvester",
                "Base",
                "Base",
                "idle");

        System.out.println("[Harvester] Ready for harvest operations.");
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // Ignore
        }
        System.out.println("[Harvester] Agent terminated.");
    }

    @Override
    protected void beforeMove() {
        System.out.println("[Harvester] Preparing to move from " + here().getName());

        // Broadcast movement start
        WebServer.broadcastDroneMove(
                "Harvester",
                here().getName(),
                targetField,
                state.name());

        WebServer.broadcastAgentInteraction(
                "Harvester", "mobile",
                targetField, "container",
                "MOVE", "Migrating to " + targetField, 1);
    }

    @Override
    protected void afterMove() {
        System.out.println("[Harvester] Arrived at " + here().getName());

        // Broadcast arrival
        WebServer.broadcastDroneMove(
                "Harvester",
                "",
                here().getName(),
                state.name());

        if (state == HarvesterState.GOING_TO_FIELD) {
            // Start harvesting
            state = HarvesterState.HARVESTING;
            addBehaviour(new HarvestOperation());
        } else if (state == HarvesterState.RETURNING) {
            // Back at base
            state = HarvesterState.IDLE;
            reportHarvestComplete();
        }
    }

    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("harvester");
            sd.setName("Mobile-Harvester");
            dfd.addServices(sd);
            DFService.register(this, dfd);
            System.out.println("[Harvester] Registered with DF as 'harvester'");
        } catch (FIPAException e) {
            System.err.println("[Harvester] DF registration failed: " + e.getMessage());
        }
    }

    /**
     * Handles incoming harvest requests.
     */
    private class HarvestRequestHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = receive(mt);

            if (msg != null) {
                String content = msg.getContent();

                if (content != null && content.startsWith("HARVEST:")) {
                    if (state == HarvesterState.IDLE) {
                        // Accept harvest request
                        targetFieldId = Integer.parseInt(content.substring(8));
                        targetField = "Field-Container-" + targetFieldId;
                        requester = msg.getSender();

                        System.out.println("[Harvester] Accepted harvest request for Field-" + targetFieldId);

                        // Send agree
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.AGREE);
                        reply.setContent("HARVEST_ACCEPTED:" + targetFieldId);
                        send(reply);

                        // Start moving to field
                        state = HarvesterState.GOING_TO_FIELD;
                        moveToField();

                        WebServer.broadcastAgentInteraction(
                                "Harvester", "mobile",
                                requester.getLocalName(), "bdi",
                                "AGREE", "Harvest request accepted", 2);
                    } else {
                        // Busy, refuse
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("BUSY:Currently " + state.name());
                        send(reply);
                    }
                }
            } else {
                block();
            }
        }
    }

    private void moveToField() {
        try {
            ContainerID destination = new ContainerID(targetField, null);
            doMove(destination);
        } catch (Exception e) {
            System.err.println("[Harvester] Failed to move: " + e.getMessage());
            // Try simpler container name
            try {
                ContainerID destination = new ContainerID("Field-Container", null);
                doMove(destination);
            } catch (Exception e2) {
                System.err.println("[Harvester] Move failed completely: " + e2.getMessage());
                state = HarvesterState.IDLE;
            }
        }
    }

    /**
     * Performs the harvest operation at the field.
     */
    private class HarvestOperation extends WakerBehaviour {
        public HarvestOperation() {
            super(HarvesterAgent.this, 3000); // 3 second harvest time
        }

        @Override
        protected void onWake() {
            System.out.println("[Harvester] Harvesting crops at Field-" + targetFieldId);

            // Simulate harvest value (would normally get from field state)
            harvestedValue = 80 + Math.random() * 120; // $80-200

            WebServer.broadcastAgentInteraction(
                    "Harvester", "mobile",
                    "Field-" + targetFieldId, "field",
                    "HARVEST", "Collected crops worth $" + String.format("%.2f", harvestedValue), 3);

            // Return home
            state = HarvesterState.RETURNING;
            returnToBase();
        }
    }

    private void returnToBase() {
        try {
            ContainerID home = new ContainerID(homeContainer, null);
            doMove(home);
        } catch (Exception e) {
            System.err.println("[Harvester] Failed to return: " + e.getMessage());
            state = HarvesterState.IDLE;
        }
    }

    private void reportHarvestComplete() {
        System.out.println("[Harvester] Harvest complete. Value: $" + String.format("%.2f", harvestedValue));

        if (requester != null) {
            ACLMessage report = new ACLMessage(ACLMessage.CONFIRM);
            report.addReceiver(requester);
            report.setContent("HARVESTED:" + targetFieldId + ":" + harvestedValue);
            send(report);

            WebServer.broadcastAgentInteraction(
                    "Harvester", "mobile",
                    requester.getLocalName(), "bdi",
                    "CONFIRM", "Harvest complete: $" + String.format("%.2f", harvestedValue), 4);
        }

        // Broadcast economy update
        WebServer.broadcast("HARVEST_COMPLETE",
                "{\"fieldId\":" + targetFieldId + ",\"value\":" + harvestedValue + "}");

        // Reset state
        targetFieldId = -1;
        harvestedValue = 0;
        requester = null;
    }

    // ==================== PUBLIC METHODS ====================

    public HarvesterState getHarvesterState() {
        return state;
    }

    public boolean isAvailable() {
        return state == HarvesterState.IDLE;
    }
}
