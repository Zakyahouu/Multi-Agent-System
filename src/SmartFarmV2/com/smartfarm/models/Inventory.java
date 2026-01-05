package com.smartfarm.models;

import com.smartfarm.web.WebServer;

/**
 * Inventory - Shared resource tracker for the farm.
 * All workers consume/deposit resources here.
 */
public class Inventory {

    private static int water = 100;
    private static int fungicide = 20;
    private static int seeds = 10;
    private static int storedCrops = 0;
    private static int money = 1000; // Starting money

    private static WebServer webServer;

    public static void setWebServer(WebServer ws) {
        webServer = ws;
    }

    /**
     * Use water for irrigation. Returns true if successful.
     */
    public static synchronized boolean useWater(int amount) {
        if (water >= amount) {
            water -= amount;
            System.out.println("[Inventory] Water used: -" + amount + " (remaining: " + water + ")");
            broadcastState();
            return true;
        }
        System.out.println("[Inventory] Not enough water! Need " + amount + ", have " + water);
        return false;
    }

    /**
     * Use fungicide for treatment. Returns true if successful.
     */
    public static synchronized boolean useFungicide() {
        if (fungicide > 0) {
            fungicide--;
            System.out.println("[Inventory] Fungicide used: -1 (remaining: " + fungicide + ")");
            broadcastState();
            return true;
        }
        System.out.println("[Inventory] No fungicide available!");
        return false;
    }

    /**
     * Use seeds for planting. Returns true if successful.
     */
    public static synchronized boolean useSeeds() {
        if (seeds > 0) {
            seeds--;
            System.out.println("[Inventory] Seeds used: -1 (remaining: " + seeds + ")");
            broadcastState();
            return true;
        }
        System.out.println("[Inventory] No seeds available!");
        return false;
    }

    /**
     * Add harvested crops to storage.
     */
    public static synchronized void addCrops(int amount) {
        storedCrops += amount;
        System.out.println("[Inventory] Crops added: +" + amount + " (total: " + storedCrops + ")");
        broadcastState();
    }

    /**
     * Sell crops. Returns true if successful.
     */
    public static synchronized boolean sellCrops(int amount) {
        if (storedCrops >= amount) {
            storedCrops -= amount;
            System.out.println("[Inventory] Crops sold: -" + amount + " (remaining: " + storedCrops + ")");
            broadcastState();
            return true;
        }
        return false;
    }

    // ==================== MONEY METHODS ====================

    public static int getMoney() {
        return money;
    }

    public static synchronized void addMoney(int amount) {
        money += amount;
        System.out.println("[Inventory] Money received: +$" + amount + " (total: $" + money + ")");
        broadcastState();
    }

    public static synchronized boolean spendMoney(int amount) {
        if (money >= amount) {
            money -= amount;
            System.out.println("[Inventory] Money spent: -$" + amount + " (remaining: $" + money + ")");
            broadcastState();
            return true;
        }
        System.out.println("[Inventory] Not enough money! Need $" + amount + ", have $" + money);
        return false;
    }

    // ==================== ADD RESOURCES (from Suppliers) ====================

    public static synchronized void addWater(int amount) {
        water += amount;
        System.out.println("[Inventory] Water added: +" + amount + " (total: " + water + ")");
        broadcastState();
    }

    public static synchronized void addFungicide(int amount) {
        fungicide += amount;
        System.out.println("[Inventory] Fungicide added: +" + amount + " (total: " + fungicide + ")");
        broadcastState();
    }

    public static synchronized void addSeeds(int amount) {
        seeds += amount;
        System.out.println("[Inventory] Seeds added: +" + amount + " (total: " + seeds + ")");
        broadcastState();
    }

    // ==================== GETTERS ====================

    public static int getWater() {
        return water;
    }

    public static int getFungicide() {
        return fungicide;
    }

    public static int getSeeds() {
        return seeds;
    }

    public static int getStoredCrops() {
        return storedCrops;
    }

    /**
     * Broadcast inventory to GUI
     */
    public static void broadcastState() {
        if (webServer == null)
            return;
        String json = String.format(
                "{\"water\":%d,\"fungicide\":%d,\"seeds\":%d,\"crops\":%d,\"money\":%d}",
                water, fungicide, seeds, storedCrops, money);
        webServer.broadcast("INVENTORY_UPDATE", json);
    }

    /**
     * JSON representation
     */
    public static String toJson() {
        return String.format(
                "{\"water\":%d,\"fungicide\":%d,\"seeds\":%d,\"crops\":%d,\"money\":%d}",
                water, fungicide, seeds, storedCrops, money);
    }
}
