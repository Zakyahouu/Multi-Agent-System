package com.smartfarm.agents;

import com.smartfarm.web.WebServer;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * ClientAgent - Buys crops from the farm.
 * Periodically checks if farm has crops to sell and purchases them.
 */
public class ClientAgent extends Agent {

    private String clientId;
    private WebServer webServer;

    // Buying prices
    private static final int CORN_PRICE = 15;
    private static final int WHEAT_PRICE = 12;
    private static final int TOMATO_PRICE = 20;
    private static final int DEFAULT_PRICE = 10;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            clientId = (String) args[0];
            webServer = (WebServer) args[1];
        } else {
            clientId = getLocalName();
        }

        System.out.println("[" + clientId + "] Client started. Ready to buy crops!");

        // Handle SELL requests
        addBehaviour(new SellHandler());

        // Periodically try to buy crops automatically
        addBehaviour(new TickerBehaviour(this, 15000) { // Every 15 seconds
            @Override
            protected void onTick() {
                autoBuyCrops();
            }
        });

        broadcastState();
    }

    /**
     * Auto-buy crops if available
     */
    private void autoBuyCrops() {
        int availableCrops = com.smartfarm.models.Inventory.getStoredCrops();

        if (availableCrops >= 10) {
            // Buy 10 crops at a time
            int quantity = Math.min(10, availableCrops);
            int payment = quantity * DEFAULT_PRICE;

            if (com.smartfarm.models.Inventory.sellCrops(quantity)) {
                com.smartfarm.models.Inventory.addMoney(payment);
                System.out.println("[" + clientId + "] BOUGHT " + quantity + " crops for $" + payment);
                broadcastLog(clientId + " purchased " + quantity + " crops (+$" + payment + ")");
            }
        }
    }

    /**
     * Handle sell requests from farm
     */
    private class SellHandler extends jade.core.behaviours.CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                // Format: SELL:CORN:10 or SELL:WHEAT:5
                if (content.startsWith("SELL:")) {
                    String[] parts = content.substring(5).split(":");
                    String cropType = parts[0];
                    int quantity = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

                    processSale(cropType, quantity, msg.getSender());
                }
            } else {
                block();
            }
        }
    }

    private void processSale(String cropType, int quantity, AID seller) {
        int pricePerUnit;

        switch (cropType.toUpperCase()) {
            case "CORN":
                pricePerUnit = CORN_PRICE;
                break;
            case "WHEAT":
                pricePerUnit = WHEAT_PRICE;
                break;
            case "TOMATO":
                pricePerUnit = TOMATO_PRICE;
                break;
            default:
                pricePerUnit = DEFAULT_PRICE;
        }

        int payment = quantity * pricePerUnit;

        // Always buy if crops available
        if (com.smartfarm.models.Inventory.sellCrops(quantity)) {
            com.smartfarm.models.Inventory.addMoney(payment);

            System.out.println("[" + clientId + "] BOUGHT " + quantity + " " + cropType + " for $" + payment);
            broadcastLog(clientId + " bought " + quantity + " " + cropType + " (+$" + payment + ")");

            // Confirm purchase
            ACLMessage confirm = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            confirm.addReceiver(seller);
            confirm.setContent("SOLD:" + cropType + ":" + quantity + ":" + payment);
            send(confirm);
        } else {
            System.out.println("[" + clientId + "] Cannot buy - no crops in inventory!");

            ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
            reject.addReceiver(seller);
            reject.setContent("NO_CROPS");
            send(reject);
        }
    }

    private void broadcastState() {
        if (webServer == null)
            return;
        String json = String.format(
                "{\"id\":\"%s\",\"type\":\"client\",\"buying\":true}",
                clientId);
        webServer.broadcast("CLIENT_UPDATE", json);
    }

    private void broadcastLog(String message) {
        if (webServer == null)
            return;
        webServer.broadcast("LOG", "{\"message\":\"" + message + "\"}");
    }

    @Override
    protected void takeDown() {
        System.out.println("[" + clientId + "] Client terminated.");
    }
}
