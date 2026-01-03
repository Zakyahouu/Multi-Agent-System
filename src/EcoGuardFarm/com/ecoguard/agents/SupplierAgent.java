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
 * SupplierAgent - Cognitive agent for supply negotiation.
 * 
 * Location: Main-Container
 * Architecture: Cognitive (CNP participant)
 * 
 * Responsibilities:
 * - Respond to CFP with PROPOSE or REFUSE
 * - Supply specific items based on specialization
 * 
 * Specialization:
 * - Supplier-1: WATER, PESTICIDE_A
 * - Supplier-2: FUNGICIDE_X, ANTIBIOTIC_Z
 */
public class SupplierAgent extends Agent {

    private int supplierId;
    private ItemType[] supportedItems;
    private int salesCount = 0;
    private double totalRevenue = 0;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            supplierId = (Integer) args[0];
        } else {
            supplierId = 1;
        }

        // Set specialization based on ID
        if (supplierId == 1) {
            supportedItems = new ItemType[] { ItemType.WATER, ItemType.PESTICIDE_A };
        } else {
            supportedItems = new ItemType[] { ItemType.FUNGICIDE_X, ItemType.ANTIBIOTIC_Z };
        }

        System.out.println("[Supplier-" + supplierId + "] Cognitive agent started.");
        System.out.print("[Supplier-" + supplierId + "] Specialization: ");
        for (ItemType item : supportedItems) {
            System.out.print(item.getEmoji() + " " + item.getDisplayName() + " ");
        }
        System.out.println();

        // Register with DF
        registerWithDF();

        // Add CNP responder behavior
        addBehaviour(new CNPResponder());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // Ignore
        }
        System.out.println(
                "[Supplier-" + supplierId + "] Agent terminated. Sales: " + salesCount + ", Revenue: $" + totalRevenue);
    }

    /**
     * Register supplier service with DF.
     */
    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());

            ServiceDescription sd = new ServiceDescription();
            sd.setType("supplier");
            sd.setName("Supplier-" + supplierId);
            dfd.addServices(sd);

            DFService.register(this, dfd);
            System.out.println("[Supplier-" + supplierId + "] Registered with DF.");
        } catch (FIPAException e) {
            System.err.println("[Supplier-" + supplierId + "] DF registration failed: " + e.getMessage());
        }
    }

    /**
     * Check if this supplier supports the given item.
     */
    private boolean supportsItem(ItemType item) {
        for (ItemType supported : supportedItems) {
            if (supported == item)
                return true;
        }
        return false;
    }

    /**
     * Generate a price proposal for an item.
     * Price varies ±20% from base price.
     */
    private double generatePrice(ItemType item, int quantity) {
        double basePrice = item.getBasePrice();
        double variation = 0.8 + Math.random() * 0.4; // 0.8 to 1.2
        return basePrice * quantity * variation;
    }

    /**
     * Contract Net Protocol responder.
     */
    private class CNPResponder extends CyclicBehaviour {

        @Override
        public void action() {
            // Listen for CFP messages
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage cfp = receive(mt);

            if (cfp != null) {
                String content = cfp.getContent();

                // Parse: SUPPLY:itemType:quantity
                if (content.startsWith("SUPPLY:")) {
                    String[] parts = content.split(":");
                    ItemType requestedItem = ItemType.valueOf(parts[1]);
                    int quantity = Integer.parseInt(parts[2]);

                    ACLMessage reply = cfp.createReply();

                    if (supportsItem(requestedItem)) {
                        // Generate proposal
                        double price = generatePrice(requestedItem, quantity);

                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent("PROPOSE:" + requestedItem.name() + ":" + quantity + ":"
                                + String.format("%.2f", price));

                        System.out
                                .println("[Supplier-" + supplierId + "] 💰 Proposing $" + String.format("%.2f", price) +
                                        " for " + quantity + "x " + requestedItem.getDisplayName());

                        // Broadcast to frontend
                        String proposalJson = String.format(
                                "{\"supplier\":\"Supplier-%d\",\"item\":\"%s\",\"quantity\":%d,\"price\":%.2f,\"type\":\"PROPOSE\"}",
                                supplierId, requestedItem.name(), quantity, price);
                        WebServer.broadcast("MARKET_EVENT", proposalJson);

                    } else {
                        // Refuse - item not supported
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("NOT_SUPPORTED:" + requestedItem.name());

                        System.out.println("[Supplier-" + supplierId + "] ❌ Refused - " +
                                requestedItem.getDisplayName() + " not in catalog");
                    }

                    send(reply);
                }

            } else {
                // Check for accept/reject
                MessageTemplate acceptMt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
                ACLMessage accept = receive(acceptMt);

                if (accept != null) {
                    // Order accepted - deliver item
                    String[] parts = accept.getContent().split(":");
                    ItemType item = ItemType.valueOf(parts[1]);
                    int quantity = Integer.parseInt(parts[2]);
                    double price = Double.parseDouble(parts[3]);

                    salesCount++;
                    totalRevenue += price;

                    System.out.println("[Supplier-" + supplierId + "] ✅ Order accepted! Delivering " +
                            quantity + "x " + item.getDisplayName() + " for $" + String.format("%.2f", price));

                    // Confirm delivery
                    ACLMessage confirm = accept.createReply();
                    confirm.setPerformative(ACLMessage.INFORM);
                    confirm.setContent("DELIVERED:" + item.name() + ":" + quantity);
                    send(confirm);

                    // Broadcast to frontend
                    String saleJson = String.format(
                            "{\"supplier\":\"Supplier-%d\",\"item\":\"%s\",\"quantity\":%d,\"price\":%.2f,\"type\":\"SALE\"}",
                            supplierId, item.name(), quantity, price);
                    WebServer.broadcast("MARKET_EVENT", saleJson);

                } else {
                    MessageTemplate rejectMt = MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL);
                    ACLMessage reject = receive(rejectMt);

                    if (reject != null) {
                        System.out.println("[Supplier-" + supplierId + "] 😔 Proposal rejected.");
                    } else {
                        block();
                    }
                }
            }
        }
    }
}
