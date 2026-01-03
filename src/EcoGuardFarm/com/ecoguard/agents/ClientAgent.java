package com.ecoguard.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import com.ecoguard.models.ItemType;
import com.ecoguard.web.WebServer;

/**
 * ClientAgent - Cognitive agent for buying crops via auction.
 * 
 * Location: Main-Container
 * Architecture: Cognitive (auction participant)
 * 
 * Behavior:
 * - Has a budget
 * - Bids on crops
 * - Second-price auction: winner pays second-highest bid
 */
public class ClientAgent extends Agent {

    private int clientId;
    private double budget;
    private int purchaseCount = 0;
    private double totalSpent = 0;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            clientId = (Integer) args[0];
        } else {
            clientId = 1;
        }

        // Set initial budget (varies by client)
        budget = 500 + (clientId * 100) + Math.random() * 200;

        System.out.println("[Client-" + clientId + "] Cognitive agent started.");
        System.out.println("[Client-" + clientId + "] Budget: $" + String.format("%.2f", budget));

        // Register with DF
        registerWithDF();

        // Add auction responder behavior
        addBehaviour(new AuctionResponder());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // Ignore
        }
        System.out.println("[Client-" + clientId + "] Agent terminated. Purchases: " + purchaseCount +
                ", Spent: $" + String.format("%.2f", totalSpent));
    }

    /**
     * Register client service with DF.
     */
    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());

            ServiceDescription sd = new ServiceDescription();
            sd.setType("client");
            sd.setName("Client-" + clientId);
            dfd.addServices(sd);

            DFService.register(this, dfd);
            System.out.println("[Client-" + clientId + "] Registered with DF.");
        } catch (FIPAException e) {
            System.err.println("[Client-" + clientId + "] DF registration failed: " + e.getMessage());
        }
    }

    /**
     * Generate a bid for a crop.
     * Bids based on remaining budget and crop value.
     */
    private double generateBid(ItemType cropItem, int quantity) {
        double baseValue = cropItem.getBasePrice() * quantity;

        // Bid between 80% and 120% of base value
        double bidFactor = 0.8 + Math.random() * 0.4;
        double bid = baseValue * bidFactor;

        // Don't exceed budget
        bid = Math.min(bid, budget * 0.8); // Keep 20% reserve

        return bid;
    }

    /**
     * Auction responder - handles CFP for crops.
     */
    private class AuctionResponder extends CyclicBehaviour {

        @Override
        public void action() {
            // Listen for CFP messages (crop auctions)
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage cfp = receive(mt);

            if (cfp != null) {
                String content = cfp.getContent();

                // Parse: BUY:cropType:quantity
                if (content.startsWith("BUY:")) {
                    String[] parts = content.split(":");
                    ItemType cropItem = ItemType.valueOf(parts[1]);
                    int quantity = Integer.parseInt(parts[2]);

                    ACLMessage reply = cfp.createReply();

                    if (budget > 50) { // Minimum budget to participate
                        double bid = generateBid(cropItem, quantity);

                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent("BID:" + cropItem.name() + ":" + quantity + ":" + String.format("%.2f", bid));

                        System.out.println("[Client-" + clientId + "] 💵 Bidding $" + String.format("%.2f", bid) +
                                " for " + quantity + "x " + cropItem.getDisplayName());

                        // Broadcast to frontend
                        String bidJson = String.format(
                                "{\"client\":\"Client-%d\",\"crop\":\"%s\",\"quantity\":%d,\"bid\":%.2f,\"type\":\"BID\"}",
                                clientId, cropItem.name(), quantity, bid);
                        WebServer.broadcast("MARKET_EVENT", bidJson);

                    } else {
                        // Not enough budget
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("INSUFFICIENT_BUDGET");

                        System.out.println("[Client-" + clientId + "] ❌ Refused - insufficient budget");
                    }

                    send(reply);
                }

            } else {
                // Check for auction result
                MessageTemplate acceptMt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
                ACLMessage accept = receive(acceptMt);

                if (accept != null) {
                    // Won the auction!
                    String[] parts = accept.getContent().split(":");
                    ItemType cropItem = ItemType.valueOf(parts[1]);
                    int quantity = Integer.parseInt(parts[2]);
                    double paymentPrice = Double.parseDouble(parts[3]); // Second-price

                    budget -= paymentPrice;
                    purchaseCount++;
                    totalSpent += paymentPrice;

                    System.out.println("[Client-" + clientId + "] 🎉 WON AUCTION! Bought " + quantity + "x " +
                            cropItem.getDisplayName() + " for $" + String.format("%.2f", paymentPrice) +
                            " (second-price). Budget remaining: $" + String.format("%.2f", budget));

                    // Confirm receipt
                    ACLMessage confirm = accept.createReply();
                    confirm.setPerformative(ACLMessage.INFORM);
                    confirm.setContent("RECEIVED:" + cropItem.name() + ":" + quantity);
                    send(confirm);

                    // Broadcast to frontend
                    String purchaseJson = String.format(
                            "{\"client\":\"Client-%d\",\"crop\":\"%s\",\"quantity\":%d,\"price\":%.2f,\"type\":\"PURCHASE\"}",
                            clientId, cropItem.name(), quantity, paymentPrice);
                    WebServer.broadcast("MARKET_EVENT", purchaseJson);

                } else {
                    MessageTemplate rejectMt = MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL);
                    ACLMessage reject = receive(rejectMt);

                    if (reject != null) {
                        System.out.println("[Client-" + clientId + "] 😔 Lost auction.");
                    } else {
                        block();
                    }
                }
            }
        }
    }
}
