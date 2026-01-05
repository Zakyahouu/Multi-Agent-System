package com.smartfarm.agents;

import com.smartfarm.web.WebServer;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * SupplierAgent - Sells resources to the farm.
 * Periodically offers goods and responds to BUY requests.
 */
public class SupplierAgent extends Agent {

    private String supplierId;
    private String supplierType; // "water", "fungicide", "seeds"
    private WebServer webServer;

    // Prices
    private int pricePerUnit;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 3) {
            supplierId = (String) args[0];
            supplierType = (String) args[1];
            webServer = (WebServer) args[2];
        } else {
            supplierId = getLocalName();
            supplierType = "water";
        }

        // Set prices based on type
        switch (supplierType.toLowerCase()) {
            case "water":
                pricePerUnit = 5; // $5 per 50 units of water
                break;
            case "fungicide":
                pricePerUnit = 20; // $20 per fungicide
                break;
            case "seeds":
                pricePerUnit = 10; // $10 per seed pack
                break;
            default:
                pricePerUnit = 10;
        }

        System.out
                .println("[" + supplierId + "] Supplier started. Type: " + supplierType + ", Price: $" + pricePerUnit);

        // Handle BUY requests
        addBehaviour(new PurchaseHandler());

        // Periodically broadcast available stock
        addBehaviour(new TickerBehaviour(this, 10000) { // Every 10 seconds
            @Override
            protected void onTick() {
                broadcastOffer();
            }
        });

        broadcastState();
    }

    /**
     * Handle purchase requests from farm
     */
    private class PurchaseHandler extends jade.core.behaviours.CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                // Format: BUY:water:50 or BUY:fungicide:5
                if (content.startsWith("BUY:")) {
                    String[] parts = content.substring(4).split(":");
                    String requestType = parts[0];
                    int quantity = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

                    if (requestType.equalsIgnoreCase(supplierType)) {
                        processPurchase(quantity, msg.getSender());
                    }
                }
            } else {
                block();
            }
        }
    }

    private void processPurchase(int quantity, AID buyer) {
        int cost = quantity * pricePerUnit;

        // Check if farm has enough money
        if (com.smartfarm.models.Inventory.getMoney() >= cost) {
            // Deduct money
            com.smartfarm.models.Inventory.spendMoney(cost);

            // Add resources
            switch (supplierType.toLowerCase()) {
                case "water":
                    com.smartfarm.models.Inventory.addWater(quantity);
                    break;
                case "fungicide":
                    com.smartfarm.models.Inventory.addFungicide(quantity);
                    break;
                case "seeds":
                    com.smartfarm.models.Inventory.addSeeds(quantity);
                    break;
            }

            System.out.println("[" + supplierId + "] SOLD " + quantity + " " + supplierType + " for $" + cost);
            broadcastLog(supplierId + " delivered " + quantity + " " + supplierType + " ($" + cost + ")");

            // Confirm to buyer
            ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
            confirm.addReceiver(buyer);
            confirm.setContent("PURCHASED:" + supplierType + ":" + quantity);
            send(confirm);
        } else {
            System.out.println("[" + supplierId + "] Sale failed - farm has no money!");
            broadcastLog(supplierId + ": Sale failed - insufficient funds!");

            // Reject
            ACLMessage reject = new ACLMessage(ACLMessage.REFUSE);
            reject.addReceiver(buyer);
            reject.setContent("NO_MONEY");
            send(reject);
        }
    }

    private void broadcastOffer() {
        // Broadcast availability to warehouse agent (or just log)
        System.out.println("[" + supplierId + "] Offering " + supplierType + " at $" + pricePerUnit + "/unit");
    }

    private void broadcastState() {
        if (webServer == null)
            return;
        String json = String.format(
                "{\"id\":\"%s\",\"type\":\"supplier\",\"supplierType\":\"%s\",\"price\":%d}",
                supplierId, supplierType, pricePerUnit);
        webServer.broadcast("SUPPLIER_UPDATE", json);
    }

    private void broadcastLog(String message) {
        if (webServer == null)
            return;
        webServer.broadcast("LOG", "{\"message\":\"" + message + "\"}");
    }

    @Override
    protected void takeDown() {
        System.out.println("[" + supplierId + "] Supplier terminated.");
    }
}
