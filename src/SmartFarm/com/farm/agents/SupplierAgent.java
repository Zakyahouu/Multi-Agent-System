package com.farm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetResponder;

import java.util.Random;

import com.farm.gui.WebServer;

/**
 * SupplierAgent - A cognitive agent that participates in water supply
 * negotiations.
 * 
 * Behavior:
 * - Registers with DF as "water-supplier"
 * - Uses ContractNetResponder to handle CFP messages
 * - Generates random prices ($10-$50) for water supply
 * - Responds with PROPOSE and handles ACCEPT/REJECT
 */
public class SupplierAgent extends Agent {

    private static final double MIN_PRICE = 10.0;
    private static final double MAX_PRICE = 50.0;
    private Random random = new Random();
    private double lastProposedPrice = 0.0;
    private int contractsWon = 0;
    private int contractsLost = 0;

    @Override
    protected void setup() {
        System.out.println("[SupplierAgent] " + getLocalName() + " starting in container: " + here().getName());

        // Register with Directory Facilitator
        registerWithDF();

        // Set up Contract Net Responder
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchProtocol("fipa-contract-net"),
                MessageTemplate.MatchPerformative(ACLMessage.CFP));

        // Also accept CFP without protocol for flexibility
        MessageTemplate cfpTemplate = MessageTemplate.MatchPerformative(ACLMessage.CFP);

        addBehaviour(new WaterSupplyResponder(this, cfpTemplate));

        WebServer.broadcast("AGENT_START",
                "{\"agent\":\"" + getLocalName() + "\",\"type\":\"Supplier\",\"container\":\"" + here().getName()
                        + "\"}");
    }

    @Override
    protected void takeDown() {
        // Deregister from DF
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("[SupplierAgent] " + getLocalName() + " shutting down. Stats: Won=" + contractsWon
                + ", Lost=" + contractsLost);
        WebServer.broadcast("AGENT_STOP",
                "{\"agent\":\"" + getLocalName() + "\",\"contractsWon\":" + contractsWon + "}");
    }

    private void registerWithDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("water-supplier");
        sd.setName(getLocalName() + "-Water-Supply");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("[SupplierAgent] " + getLocalName() + " registered with DF as 'water-supplier'");
        } catch (FIPAException e) {
            System.err.println("[SupplierAgent] " + getLocalName() + " failed to register with DF: " + e.getMessage());
        }
    }

    /**
     * Contract Net Responder for water supply negotiations.
     */
    private class WaterSupplyResponder extends ContractNetResponder {

        public WaterSupplyResponder(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            String content = cfp.getContent();
            System.out.println("[SupplierAgent] " + getLocalName() + " received CFP: " + content);

            // Generate random price between MIN_PRICE and MAX_PRICE
            lastProposedPrice = MIN_PRICE + (random.nextDouble() * (MAX_PRICE - MIN_PRICE));
            lastProposedPrice = Math.round(lastProposedPrice * 100.0) / 100.0; // Round to 2 decimals

            System.out.println("[SupplierAgent] " + getLocalName() + " proposing price: $"
                    + String.format("%.2f", lastProposedPrice));

            // Create PROPOSE reply
            ACLMessage propose = cfp.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent("PRICE:" + lastProposedPrice);

            WebServer.broadcast("SUPPLIER_PROPOSAL",
                    "{\"supplier\":\"" + getLocalName() + "\",\"price\":" + lastProposedPrice
                            + ",\"status\":\"PROPOSED\"}");

            return propose;
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) {
            System.out.println("[SupplierAgent] 🎉 " + getLocalName() + " WON the contract at $"
                    + String.format("%.2f", lastProposedPrice));
            contractsWon++;

            // Simulate water delivery
            ACLMessage inform = accept.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            inform.setContent("DELIVERY_COMPLETE:WATER_UNITS:100");

            WebServer.broadcast("SUPPLIER_WON",
                    "{\"supplier\":\"" + getLocalName() + "\",\"price\":" + lastProposedPrice + ",\"totalWins\":"
                            + contractsWon + ",\"message\":\"Contract won! Delivering water...\"}");

            return inform;
        }

        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            System.out.println("[SupplierAgent] ❌ " + getLocalName() + " LOST the contract. Price was: $"
                    + String.format("%.2f", lastProposedPrice));
            contractsLost++;

            WebServer.broadcast("SUPPLIER_LOST",
                    "{\"supplier\":\"" + getLocalName() + "\",\"price\":" + lastProposedPrice + ",\"totalLosses\":"
                            + contractsLost + "}");
        }
    }

    // Getters for statistics
    public double getLastProposedPrice() {
        return lastProposedPrice;
    }

    public int getContractsWon() {
        return contractsWon;
    }

    public int getContractsLost() {
        return contractsLost;
    }
}
