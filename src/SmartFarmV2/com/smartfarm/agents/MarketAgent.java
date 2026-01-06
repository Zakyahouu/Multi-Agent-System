package com.smartfarm.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import com.smartfarm.models.Inventory;
import com.smartfarm.models.MarketItem;
import com.smartfarm.models.FieldType;
import com.smartfarm.web.WebServer;

/**
 * MarketAgent - Manage purchases and economy.
 * Validates funds and authorizes upgrades/expansions.
 */
public class MarketAgent extends Agent {

    private WebServer webServer;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            webServer = (WebServer) args[0];
        }

        System.out.println("[Market] Agent started. Ready for business!");
        addBehaviour(new MarketServer());
    }

    private class MarketServer extends CyclicBehaviour {
        @Override
        public void action() {
            // Listen for PURCHASE requests
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();

                if (content.startsWith("BUY_FIELD:")) {
                    handleFieldPurchase(msg, content);
                } else if (content.startsWith("BUY_ITEM:")) {
                    handleItemPurchase(msg, content);
                } else {
                    reply(msg, ACLMessage.NOT_UNDERSTOOD, "Unknown Request");
                }
            } else {
                block();
            }
        }
    }

    private void handleFieldPurchase(ACLMessage msg, String content) {
        String typeStr = content.substring(10);
        try {
            FieldType type = FieldType.valueOf(typeStr);
            if (Inventory.spendMoney(type.getCost())) {
                System.out.println("[Market] Field Sold: " + type.getDisplayName() + " to " + msg.getSender().getLocalName());
                reply(msg, ACLMessage.CONFIRM, "APPROVED");
            } else {
                System.out.println("[Market] Rejected purchase: Insufficient funds for " + type.getDisplayName());
                reply(msg, ACLMessage.REFUSE, "INSUFFICIENT_FUNDS");
            }
        } catch (IllegalArgumentException e) {
            reply(msg, ACLMessage.NOT_UNDERSTOOD, "Invalid Field Type");
        }
    }

    private void handleItemPurchase(ACLMessage msg, String content) {
        String itemStr = content.substring(9);
        try {
            MarketItem item = MarketItem.valueOf(itemStr);
            if (Inventory.spendMoney(item.getPrice())) {
                System.out.println("[Market] Upgrade Sold: " + item.getName());
                // In a real expanded scenario, we would trigger the upgrade effect here or notify agents.
                // For now, we just validate the transaction.
                reply(msg, ACLMessage.CONFIRM, "APPROVED");
            } else {
                reply(msg, ACLMessage.REFUSE, "INSUFFICIENT_FUNDS");
            }
        } catch (IllegalArgumentException e) {
            reply(msg, ACLMessage.NOT_UNDERSTOOD, "Invalid Item");
        }
    }

    private void reply(ACLMessage msg, int performative, String content) {
        ACLMessage reply = msg.createReply();
        reply.setPerformative(performative);
        reply.setContent(content);
        send(reply);
    }
}
