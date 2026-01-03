package com.farm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetInitiator;

import java.util.Date;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

import com.farm.gui.WebServer;

/**
 * FarmControllerAgent - A deliberative/hybrid agent that orchestrates farm
 * operations.
 * 
 * Responsibilities:
 * - Receives moisture alerts from SoilSensorAgent
 * - Checks weather forecast (mocked)
 * - Dispatches InspectorDroneAgent for field inspection
 * - Initiates Contract Net Protocol to purchase water from suppliers
 */
public class FarmControllerAgent extends Agent {

    private boolean isRaining = false;
    private Random random = new Random();
    private boolean awaitingDroneReturn = false;
    private int pendingMoistureLevel = 0;

    @Override
    protected void setup() {
        System.out.println("[FarmControllerAgent] " + getLocalName() + " starting in container: " + here().getName());

        // Add behavior to listen for sensor requests
        addBehaviour(new SensorRequestHandler());

        // Add behavior to handle drone confirmations
        addBehaviour(new DroneConfirmationHandler());

        WebServer.broadcast("AGENT_START", "{\"agent\":\"" + getLocalName()
                + "\",\"type\":\"FarmController\",\"container\":\"" + here().getName() + "\"}");
    }

    @Override
    protected void takeDown() {
        System.out.println("[FarmControllerAgent] " + getLocalName() + " shutting down.");
        WebServer.broadcast("AGENT_STOP", "{\"agent\":\"" + getLocalName() + "\"}");
    }

    /**
     * Handles incoming REQUEST messages from soil sensors.
     */
    private class SensorRequestHandler extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();

                if (content != null && content.startsWith("LOW_MOISTURE:")) {
                    int moistureLevel = Integer.parseInt(content.split(":")[1]);
                    System.out.println("[FarmControllerAgent] Received low moisture alert: " + moistureLevel + "%");

                    WebServer.broadcast("CONTROLLER_EVENT",
                            "{\"event\":\"MOISTURE_ALERT_RECEIVED\",\"moisture\":" + moistureLevel + "}");

                    // Check weather forecast
                    checkWeatherAndDecide(moistureLevel);
                }
            } else {
                block();
            }
        }
    }

    /**
     * Checks mocked weather forecast and decides action.
     * 20% chance of rain prediction.
     */
    private void checkWeatherAndDecide(int moistureLevel) {
        // Simulate weather forecast (20% chance of rain)
        isRaining = random.nextInt(100) < 20;

        System.out.println("[FarmControllerAgent] Checking weather forecast...");
        WebServer.broadcast("WEATHER_CHECK", "{\"checking\":true}");

        // Small delay to simulate API call
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }

                if (isRaining) {
                    System.out.println("[FarmControllerAgent] 🌧️ Rain predicted! Ignoring irrigation request.");
                    WebServer.broadcast("WEATHER_RESULT",
                            "{\"isRaining\":true,\"action\":\"IGNORE\",\"message\":\"Rain predicted - no irrigation needed\"}");
                } else {
                    System.out.println("[FarmControllerAgent] ☀️ No rain predicted. Initiating inspection...");
                    WebServer.broadcast("WEATHER_RESULT",
                            "{\"isRaining\":false,\"action\":\"INSPECT\",\"message\":\"Clear weather - initiating drone inspection\"}");

                    pendingMoistureLevel = moistureLevel;
                    awaitingDroneReturn = true;
                    requestDroneInspection();
                }
            }
        });
    }

    /**
     * Sends inspection request to InspectorDroneAgent.
     */
    private void requestDroneInspection() {
        ACLMessage inspectRequest = new ACLMessage(ACLMessage.REQUEST);
        inspectRequest.addReceiver(new AID("Drone", AID.ISLOCALNAME));
        inspectRequest.setContent("INSPECT_FIELD");
        inspectRequest.setConversationId("inspection-" + System.currentTimeMillis());

        send(inspectRequest);
        System.out.println("[FarmControllerAgent] Sent inspection request to Drone.");

        WebServer.broadcast("DRONE_REQUEST",
                "{\"action\":\"INSPECT\",\"message\":\"Requesting drone field inspection\"}");
    }

    /**
     * Handles CONFIRM messages from the drone after inspection.
     */
    private class DroneConfirmationHandler extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && awaitingDroneReturn) {
                String content = msg.getContent();

                if (content != null && content.startsWith("INSPECTION_COMPLETE")) {
                    System.out
                            .println("[FarmControllerAgent] Drone inspection confirmed. Initiating water purchase...");

                    WebServer.broadcast("INSPECTION_COMPLETE",
                            "{\"status\":\"complete\",\"message\":\"Field inspection completed successfully\"}");

                    awaitingDroneReturn = false;
                    initiateWaterPurchase();
                }
            } else {
                block();
            }
        }
    }

    /**
     * Initiates Contract Net Protocol to purchase water from suppliers.
     */
    private void initiateWaterPurchase() {
        System.out.println("[FarmControllerAgent] Searching for water suppliers...");

        // Find all water suppliers from DF
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("water-supplier");
        template.addServices(sd);

        try {
            DFAgentDescription[] results = DFService.search(this, template);

            if (results.length == 0) {
                System.out.println("[FarmControllerAgent] No water suppliers found!");
                WebServer.broadcast("CNP_ERROR", "{\"error\":\"No suppliers found\"}");
                return;
            }

            System.out.println("[FarmControllerAgent] Found " + results.length + " water suppliers. Sending CFP...");

            // Create CFP message
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (DFAgentDescription supplier : results) {
                cfp.addReceiver(supplier.getName());
            }
            cfp.setContent("WATER_NEEDED:" + pendingMoistureLevel);
            cfp.setConversationId("water-purchase-" + System.currentTimeMillis());
            cfp.setReplyByDate(new Date(System.currentTimeMillis() + 10000)); // 10 second deadline

            WebServer.broadcast("CNP_START",
                    "{\"suppliers\":" + results.length + ",\"message\":\"Sending CFP to " + results.length
                            + " suppliers\"}");

            // Add ContractNetInitiator behavior
            addBehaviour(new WaterPurchaseInitiator(this, cfp, results.length));

        } catch (FIPAException e) {
            System.err.println("[FarmControllerAgent] DF search failed: " + e.getMessage());
        }
    }

    /**
     * Contract Net Protocol Initiator for water purchase.
     */
    private class WaterPurchaseInitiator extends ContractNetInitiator {

        private int expectedProposals;
        private String bestSupplier = null;
        private double highestBid = 0;
        private double secondHighestBid = 0;
        private java.util.List<ProposalInfo> allProposals = new java.util.ArrayList<>();

        // Helper class to track proposals
        private class ProposalInfo {
            String supplier;
            double price;

            ProposalInfo(String supplier, double price) {
                this.supplier = supplier;
                this.price = price;
            }
        }

        public WaterPurchaseInitiator(Agent a, ACLMessage cfp, int expectedProposals) {
            super(a, cfp);
            this.expectedProposals = expectedProposals;
        }

        @Override
        protected void handlePropose(ACLMessage propose, Vector acceptances) {
            String supplierName = propose.getSender().getLocalName();
            double price = Double.parseDouble(propose.getContent().split(":")[1]);

            System.out.println("[FarmControllerAgent] Received proposal from " + supplierName + ": $"
                    + String.format("%.2f", price));

            // Store proposal
            allProposals.add(new ProposalInfo(supplierName, price));

            WebServer.broadcast("CNP_PROPOSAL",
                    "{\"supplier\":\"" + supplierName + "\",\"price\":" + price + "}");

            // Track highest and second-highest bids
            if (price > highestBid) {
                secondHighestBid = highestBid;
                highestBid = price;
                bestSupplier = supplierName;
            } else if (price > secondHighestBid) {
                secondHighestBid = price;
            }
        }

        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances) {
            System.out.println("[FarmControllerAgent] Received all " + responses.size() + " proposals.");

            if (bestSupplier != null) {
                // Second-price auction: winner pays second-highest bid
                double paymentPrice = allProposals.size() > 1 ? secondHighestBid : highestBid;

                System.out.println("[FarmControllerAgent] 🏆 AUCTION RESULT:");
                System.out.println("[FarmControllerAgent]   Winner: " + bestSupplier);
                System.out.println("[FarmControllerAgent]   Winning Bid: $" + String.format("%.2f", highestBid));
                System.out.println(
                        "[FarmControllerAgent]   Payment (2nd price): $" + String.format("%.2f", paymentPrice));

                // Broadcast detailed auction results
                StringBuilder allBidsJson = new StringBuilder("{\"winner\":\"" + bestSupplier + "\"");
                allBidsJson.append(",\"winningBid\":").append(highestBid);
                allBidsJson.append(",\"payment\":").append(paymentPrice);
                allBidsJson.append(",\"bids\":[");

                for (int i = 0; i < allProposals.size(); i++) {
                    ProposalInfo p = allProposals.get(i);
                    if (i > 0)
                        allBidsJson.append(",");
                    allBidsJson.append("{\"supplier\":\"").append(p.supplier).append("\"");
                    allBidsJson.append(",\"bid\":").append(p.price).append("}");
                }
                allBidsJson.append("]}");

                WebServer.broadcast("CNP_ACCEPT", allBidsJson.toString());

                // Send ACCEPT to winner, REJECT to others
                Enumeration e = responses.elements();
                while (e.hasMoreElements()) {
                    ACLMessage msg = (ACLMessage) e.nextElement();
                    if (msg.getPerformative() == ACLMessage.PROPOSE) {
                        ACLMessage reply = msg.createReply();

                        if (msg.getSender().getLocalName().equals(bestSupplier)) {
                            reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                            reply.setContent("ACCEPTED:" + paymentPrice); // Send payment price

                        } else {
                            reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                            reply.setContent("REJECTED");

                            WebServer.broadcast("CNP_REJECT",
                                    "{\"supplier\":\"" + msg.getSender().getLocalName() + "\"}");
                        }
                        acceptances.add(reply);
                    }
                }
            }
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(
                    "[FarmControllerAgent] ✅ Water delivery confirmed from " + inform.getSender().getLocalName());

            WebServer.broadcast("WATER_DELIVERED",
                    "{\"supplier\":\"" + inform.getSender().getLocalName()
                            + "\",\"message\":\"Water delivery complete!\"}");
        }

        @Override
        protected void handleRefuse(ACLMessage refuse) {
            System.out.println("[FarmControllerAgent] Supplier refused: " + refuse.getSender().getLocalName());
        }

        @Override
        protected void handleFailure(ACLMessage failure) {
            System.out.println("[FarmControllerAgent] Supplier failed: " + failure.getSender().getLocalName());
        }
    }
}
