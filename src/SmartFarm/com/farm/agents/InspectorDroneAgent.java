package com.farm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.ContainerID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.Serializable;

import com.farm.gui.WebServer;

/**
 * InspectorDroneAgent - A mobile agent that physically migrates between
 * containers.
 * 
 * Mobility Flow:
 * 1. Receives INSPECT request from FarmControllerAgent
 * 2. Migrates from Main-Container to Field-Container using doMove()
 * 3. Performs 2-second inspection simulation
 * 4. Migrates back to Main-Container
 * 5. Sends CONFIRM to FarmControllerAgent
 * 
 * IMPORTANT: This agent implements Serializable for mobility support.
 */
public class InspectorDroneAgent extends Agent implements Serializable {

    private static final long serialVersionUID = 1L;

    // Drone states
    private static final int STATE_IDLE = 0;
    private static final int STATE_GOING_TO_FIELD = 1;
    private static final int STATE_INSPECTING = 2;
    private static final int STATE_RETURNING_HOME = 3;

    private int currentState = STATE_IDLE;
    private String homeContainer;
    private String fieldContainer = "Field-Container";
    private transient AID controllerAID; // transient to handle serialization

    @Override
    protected void setup() {
        homeContainer = here().getName();
        controllerAID = new AID("Controller", AID.ISLOCALNAME);

        System.out.println("[InspectorDroneAgent] " + getLocalName() + " initialized in container: " + homeContainer);
        System.out.println("[InspectorDroneAgent] Ready for inspection missions.");

        // Add behavior to listen for inspection requests
        addBehaviour(new InspectionRequestHandler());

        WebServer.broadcast("AGENT_START",
                "{\"agent\":\"" + getLocalName() + "\",\"type\":\"InspectorDrone\",\"container\":\"" + homeContainer
                        + "\",\"state\":\"IDLE\"}");
    }

    @Override
    protected void takeDown() {
        System.out.println("[InspectorDroneAgent] " + getLocalName() + " shutting down.");
        WebServer.broadcast("AGENT_STOP", "{\"agent\":\"" + getLocalName() + "\"}");
    }

    /**
     * Called before the agent moves to a new container.
     */
    @Override
    protected void beforeMove() {
        String destination = (currentState == STATE_GOING_TO_FIELD) ? fieldContainer : homeContainer;
        System.out.println("[InspectorDroneAgent] 🚁 Preparing to move to " + destination + "...");

        WebServer.broadcast("DRONE_MOVING",
                "{\"from\":\"" + here().getName() + "\",\"to\":\"" + destination + "\",\"status\":\"departing\"}");
    }

    /**
     * Called after the agent arrives at the new container.
     */
    @Override
    protected void afterMove() {
        String currentContainer = here().getName();
        System.out.println("[InspectorDroneAgent] 🚁 Arrived at " + currentContainer);

        // Re-initialize transient fields after deserialization
        if (controllerAID == null) {
            controllerAID = new AID("Controller", AID.ISLOCALNAME);
        }

        WebServer.broadcast("DRONE_ARRIVED",
                "{\"container\":\"" + currentContainer + "\",\"state\":\"" + getStateString() + "\"}");

        if (currentState == STATE_GOING_TO_FIELD) {
            // Just arrived at field, start inspection
            currentState = STATE_INSPECTING;
            startFieldInspection();
        } else if (currentState == STATE_RETURNING_HOME) {
            // Arrived back home, confirm to controller
            currentState = STATE_IDLE;
            confirmInspectionComplete();
        }
    }

    /**
     * Handles incoming inspection requests from the controller.
     */
    private class InspectionRequestHandler extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchContent("INSPECT_FIELD"));
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                if (currentState == STATE_IDLE) {
                    System.out.println(
                            "[InspectorDroneAgent] Received inspection request from " + msg.getSender().getLocalName());
                    currentState = STATE_GOING_TO_FIELD;
                    moveToField();
                } else {
                    System.out.println("[InspectorDroneAgent] Already on a mission, ignoring request.");
                }
            } else {
                block();
            }
        }
    }

    /**
     * Initiates movement to Field-Container.
     */
    private void moveToField() {
        System.out.println("[InspectorDroneAgent] 🚁 Departing to Field-Container for inspection...");

        WebServer.broadcast("DRONE_DISPATCH",
                "{\"destination\":\"" + fieldContainer + "\",\"mission\":\"FIELD_INSPECTION\"}");

        // Create ContainerID for destination and move
        ContainerID destination = new ContainerID(fieldContainer, null);
        doMove(destination);
    }

    /**
     * Simulates field inspection (2 second delay).
     */
    private void startFieldInspection() {
        System.out.println("[InspectorDroneAgent] 🔍 Starting field inspection...");

        WebServer.broadcast("DRONE_INSPECTING",
                "{\"container\":\"" + here().getName()
                        + "\",\"duration\":2000,\"message\":\"Analyzing soil conditions...\"}");

        // Add a WakerBehaviour for 2-second inspection delay
        addBehaviour(new WakerBehaviour(this, 2000) {
            @Override
            protected void onWake() {
                System.out.println("[InspectorDroneAgent] ✅ Inspection complete. Returning to base...");

                WebServer.broadcast("DRONE_INSPECTION_DONE",
                        "{\"result\":\"NEEDS_WATER\",\"message\":\"Field inspection complete. Soil requires irrigation.\"}");

                currentState = STATE_RETURNING_HOME;
                returnHome();
            }
        });
    }

    /**
     * Initiates movement back to Main-Container.
     */
    private void returnHome() {
        System.out.println("[InspectorDroneAgent] 🚁 Returning to " + homeContainer + "...");

        WebServer.broadcast("DRONE_RETURNING",
                "{\"destination\":\"" + homeContainer + "\"}");

        ContainerID destination = new ContainerID(homeContainer, null);
        doMove(destination);
    }

    /**
     * Sends confirmation to FarmControllerAgent.
     */
    private void confirmInspectionComplete() {
        System.out.println("[InspectorDroneAgent] Sending inspection confirmation to Controller...");

        ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
        confirm.addReceiver(controllerAID);
        confirm.setContent("INSPECTION_COMPLETE:NEEDS_WATER");
        confirm.setConversationId("inspection-confirm-" + System.currentTimeMillis());

        send(confirm);

        WebServer.broadcast("DRONE_CONFIRMATION_SENT",
                "{\"to\":\"Controller\",\"result\":\"NEEDS_WATER\",\"message\":\"Mission complete. Drone back at base.\"}");
    }

    /**
     * Returns string representation of current state.
     */
    private String getStateString() {
        switch (currentState) {
            case STATE_IDLE:
                return "IDLE";
            case STATE_GOING_TO_FIELD:
                return "GOING_TO_FIELD";
            case STATE_INSPECTING:
                return "INSPECTING";
            case STATE_RETURNING_HOME:
                return "RETURNING_HOME";
            default:
                return "UNKNOWN";
        }
    }
}
