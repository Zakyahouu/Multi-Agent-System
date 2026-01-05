package com.smartfarm.agents;

import com.smartfarm.web.WebServer;
import com.smartfarm.models.Inventory;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

/**
 * PlannerAgent - BDI-style intelligent planner for farm resources.
 * 
 * Beliefs: Current inventory levels
 * Desires: Keep resources above thresholds
 * Intentions: Buy supplies when needed
 */
public class PlannerAgent extends Agent {

    private WebServer webServer;

    // Thresholds for auto-ordering
    private static final int WATER_THRESHOLD = 50;
    private static final int FUNGICIDE_THRESHOLD = 5;
    private static final int SEEDS_THRESHOLD = 3;

    // Order quantities
    private static final int WATER_ORDER_QTY = 100;
    private static final int FUNGICIDE_ORDER_QTY = 10;
    private static final int SEEDS_ORDER_QTY = 5;

    // Prices (must match SupplierAgent)
    private static final int WATER_PRICE_PER_UNIT = 5;
    private static final int FUNGICIDE_PRICE = 20;
    private static final int SEEDS_PRICE = 10;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 1) {
            webServer = (WebServer) args[0];
        }

        System.out.println("[Planner] BDI Planner started. Monitoring inventory...");
        broadcastLog("BDI Planner activated - auto-ordering enabled");

        // Main planning loop - check inventory every 5 seconds
        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                checkAndPlanOrders();
            }
        });
    }

    /**
     * BDI Planning Loop:
     * 1. Update Beliefs (read inventory)
     * 2. Check Desires (thresholds)
     * 3. Execute Intentions (order supplies)
     */
    private void checkAndPlanOrders() {
        int water = Inventory.getWater();
        int fungicide = Inventory.getFungicide();
        int seeds = Inventory.getSeeds();
        int money = Inventory.getMoney();

        // === WATER CHECK ===
        if (water < WATER_THRESHOLD) {
            int cost = (WATER_ORDER_QTY / 50) * WATER_PRICE_PER_UNIT; // $5 per 50 units
            if (money >= cost) {
                orderWater(WATER_ORDER_QTY, cost);
            } else {
                System.out.println("[Planner] Need water but insufficient funds ($" + money + " < $" + cost + ")");
            }
        }

        // === FUNGICIDE CHECK ===
        if (fungicide < FUNGICIDE_THRESHOLD) {
            int cost = FUNGICIDE_ORDER_QTY * FUNGICIDE_PRICE;
            if (money >= cost) {
                orderFungicide(FUNGICIDE_ORDER_QTY, cost);
            } else {
                System.out.println("[Planner] Need fungicide but insufficient funds ($" + money + " < $" + cost + ")");
            }
        }

        // === SEEDS CHECK ===
        if (seeds < SEEDS_THRESHOLD) {
            int cost = SEEDS_ORDER_QTY * SEEDS_PRICE;
            if (money >= cost) {
                orderSeeds(SEEDS_ORDER_QTY, cost);
            } else {
                System.out.println("[Planner] Need seeds but insufficient funds ($" + money + " < $" + cost + ")");
            }
        }
    }

    private void orderWater(int quantity, int cost) {
        System.out.println("[Planner] AUTO-ORDER: " + quantity + " water for $" + cost);
        broadcastLog("Planner: Ordering " + quantity + " water ($" + cost + ")");

        // Directly process (since suppliers are offering)
        if (Inventory.spendMoney(cost)) {
            Inventory.addWater(quantity);
            System.out.println("[Planner] Water delivered: +" + quantity);
        }
    }

    private void orderFungicide(int quantity, int cost) {
        System.out.println("[Planner] AUTO-ORDER: " + quantity + " fungicide for $" + cost);
        broadcastLog("Planner: Ordering " + quantity + " fungicide ($" + cost + ")");

        if (Inventory.spendMoney(cost)) {
            Inventory.addFungicide(quantity);
            System.out.println("[Planner] Fungicide delivered: +" + quantity);
        }
    }

    private void orderSeeds(int quantity, int cost) {
        System.out.println("[Planner] AUTO-ORDER: " + quantity + " seeds for $" + cost);
        broadcastLog("Planner: Ordering " + quantity + " seeds ($" + cost + ")");

        if (Inventory.spendMoney(cost)) {
            Inventory.addSeeds(quantity);
            System.out.println("[Planner] Seeds delivered: +" + quantity);
        }
    }

    private void broadcastLog(String message) {
        if (webServer == null)
            return;
        webServer.broadcast("LOG", "{\"message\":\"" + message + "\"}");
    }

    @Override
    protected void takeDown() {
        System.out.println("[Planner] BDI Planner terminated.");
    }
}
