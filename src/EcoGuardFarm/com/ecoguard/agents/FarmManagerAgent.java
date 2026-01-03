package com.ecoguard.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.*;

import com.ecoguard.models.*;
import com.ecoguard.helpers.Inventory;
import com.ecoguard.web.WebServer;

/**
 * FarmManagerAgent - BDI agent that coordinates all farm operations.
 * 
 * Location: Main-Container
 * Architecture: Belief-Desire-Intention
 * 
 * Beliefs: Field states, inventory, agent availability
 * Desires: Keep fields healthy, maximize profit
 * Intentions: Priority queue of actions to execute
 */
public class FarmManagerAgent extends Agent {

    // ==================== BELIEFS ====================
    private Map<Integer, FieldState> fieldBeliefs = new HashMap<>();
    private Map<Integer, Boolean> fieldDiseaseKnown = new HashMap<>();
    private Inventory inventory;
    private double budget = 1000.0;
    private Set<String> availableDrones = new HashSet<>();
    private Set<String> availableHarvesters = new HashSet<>();
    private Set<String> availableSprayers = new HashSet<>();

    // ==================== DESIRES ====================
    // Implicit: healthy fields, maximize profit, avoid starvation

    // ==================== INTENTIONS ====================
    private Queue<Intention> intentionQueue = new LinkedList<>();
    private Intention currentIntention = null;

    // Pending requests to avoid duplicates
    private Set<Integer> pendingScan = new HashSet<>();
    private Set<Integer> pendingDiagnosis = new HashSet<>();
    private Set<Integer> pendingTreatment = new HashSet<>();
    private Set<Integer> pendingHarvest = new HashSet<>();
    private Set<Integer> pendingWater = new HashSet<>();

    @Override
    protected void setup() {
        System.out.println("[FarmManager] BDI agent started.");

        // Initialize inventory
        inventory = new Inventory(100);
        inventory.addItem(ItemType.WATER, 20);
        inventory.addItem(ItemType.PESTICIDE_A, 5);
        inventory.addItem(ItemType.FUNGICIDE_X, 3);
        inventory.addItem(ItemType.ANTIBIOTIC_Z, 3);

        System.out.println("[FarmManager] Initial inventory: " + inventory);
        System.out.println("[FarmManager] Initial budget: $" + budget);

        // Initialize available agents
        availableDrones.add("Drone-1");
        availableDrones.add("Drone-2");
        availableHarvesters.add("Harvester-1");
        availableSprayers.add("Sprayer-1");

        // Add behaviors
        addBehaviour(new RequestHandler());
        addBehaviour(new ResultHandler());
        addBehaviour(new IntentionExecutor(this, 2000));
        addBehaviour(new BDIBroadcaster(this, 3000));
    }

    @Override
    protected void takeDown() {
        System.out.println("[FarmManager] Agent terminated.");
    }

    // ==================== INTENTION CLASS ====================
    private enum IntentionType {
        TREAT_DISEASE, // Priority 1: Emergency - cure diseases
        WATER_FIELD, // Priority 2: Critical - water before crops die
        SCAN_FIELD, // Priority 3: Information
        HARVEST_FIELD, // Priority 4: Production
        SELL_CROPS, // Priority 5: Economy
        BUY_SUPPLIES // Priority 6: Maintenance
    }

    private class Intention implements Comparable<Intention> {
        IntentionType type;
        int fieldId;
        Object data;
        int priority;

        Intention(IntentionType type, int fieldId, Object data) {
            this.type = type;
            this.fieldId = fieldId;
            this.data = data;
            this.priority = type.ordinal(); // Lower ordinal = higher priority
        }

        @Override
        public int compareTo(Intention other) {
            return Integer.compare(this.priority, other.priority);
        }

        @Override
        public String toString() {
            return type + " for Field-" + fieldId;
        }
    }

    // ==================== REQUEST HANDLER ====================
    /**
     * Handles incoming requests from FieldAgents.
     */
    private class RequestHandler extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                System.out.println("[FarmManager] Received request: " + content);

                if (content.startsWith("SCAN:")) {
                    int fieldId = Integer.parseInt(content.split(":")[1]);
                    if (!pendingScan.contains(fieldId)) {
                        intentionQueue.add(new Intention(IntentionType.SCAN_FIELD, fieldId, null));
                        pendingScan.add(fieldId);
                        System.out.println("[FarmManager] Added intention: SCAN Field-" + fieldId);
                    }

                } else if (content.startsWith("WATER:")) {
                    String[] parts = content.split(":");
                    int fieldId = Integer.parseInt(parts[1]);
                    int amount = Integer.parseInt(parts[2]);
                    if (!pendingWater.contains(fieldId)) {
                        intentionQueue.add(new Intention(IntentionType.WATER_FIELD, fieldId, amount));
                        pendingWater.add(fieldId);
                        System.out.println("[FarmManager] Added intention: WATER Field-" + fieldId);
                    }

                } else if (content.startsWith("DIAGNOSE:")) {
                    // Format: DIAGNOSE:fieldId:disease:moisture:health
                    String[] parts = content.split(":");
                    int fieldId = Integer.parseInt(parts[1]);

                    // Parse disease info from request
                    if (parts.length >= 5) {
                        String disease = parts[2];
                        int moisture = Integer.parseInt(parts[3]);
                        int health = Integer.parseInt(parts[4]);

                        // Update belief about this field's disease
                        FieldState tempState = fieldBeliefs.get(fieldId);
                        if (tempState == null) {
                            tempState = new FieldState(fieldId, CropType.CORN);
                        }
                        tempState.setCurrentDisease(DiseaseType.valueOf(disease));
                        tempState.setMoisture(moisture);
                        tempState.setHealth(health);
                        fieldBeliefs.put(fieldId, tempState);
                    }

                    if (!pendingDiagnosis.contains(fieldId)) {
                        intentionQueue.add(new Intention(IntentionType.SCAN_FIELD, fieldId, "diagnose"));
                        pendingDiagnosis.add(fieldId);
                        System.out.println("[FarmManager] Added intention: DIAGNOSE Field-" + fieldId);
                    }

                } else if (content.startsWith("HARVEST:")) {
                    int fieldId = Integer.parseInt(content.split(":")[1]);
                    if (!pendingHarvest.contains(fieldId)) {
                        if (!inventory.isFull()) {
                            intentionQueue.add(new Intention(IntentionType.HARVEST_FIELD, fieldId, null));
                            pendingHarvest.add(fieldId);
                            System.out.println("[FarmManager] Added intention: HARVEST Field-" + fieldId);
                        } else {
                            System.out.println("[FarmManager] ⚠️ Cannot harvest - storage full!");
                        }
                    }
                }

            } else {
                block();
            }
        }
    }

    // ==================== RESULT HANDLER ====================
    /**
     * Handles results from Drone, Sprayer, Harvester agents.
     */
    private class ResultHandler extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                String sender = msg.getSender().getLocalName();

                if (content.startsWith("SCAN_COMPLETE:")) {
                    int fieldId = Integer.parseInt(content.split(":")[1]);
                    pendingScan.remove(fieldId);
                    availableDrones.add(sender);
                    System.out.println("[FarmManager] " + sender + " completed scan of Field-" + fieldId);

                } else if (content.startsWith("DIAGNOSIS_RESULT:")) {
                    String[] parts = content.split(":");
                    int fieldId = Integer.parseInt(parts[1]);
                    String disease = parts[2];
                    int confidence = Integer.parseInt(parts[3]);

                    pendingDiagnosis.remove(fieldId);
                    availableDrones.add(sender);
                    fieldDiseaseKnown.put(fieldId, true);

                    System.out.println(
                            "[FarmManager] Diagnosis for Field-" + fieldId + ": " + disease + " (" + confidence + "%)");

                    if (!disease.equals("NONE") && !pendingTreatment.contains(fieldId)) {
                        // Schedule treatment
                        DiseaseType diseaseType = DiseaseType.valueOf(disease);
                        ItemType cure = diseaseType.getCure();

                        if (inventory.hasItem(cure, 1)) {
                            // Have the cure - schedule treatment immediately
                            pendingTreatment.add(fieldId);
                            intentionQueue.add(new Intention(IntentionType.TREAT_DISEASE, fieldId, diseaseType));
                            System.out.println("[FarmManager] Scheduled treatment for Field-" + fieldId);
                        } else {
                            // Need to buy supplies - field will request diagnosis again after purchase
                            System.out.println(
                                    "[FarmManager] Need to buy " + cure.getDisplayName() + " for Field-" + fieldId);
                            intentionQueue.add(new Intention(IntentionType.BUY_SUPPLIES, fieldId, cure));
                        }
                    }

                } else if (content.startsWith("SPRAY_COMPLETE:")) {
                    int fieldId = Integer.parseInt(content.split(":")[1]);
                    pendingTreatment.remove(fieldId);
                    availableSprayers.add(sender);
                    fieldDiseaseKnown.put(fieldId, false);
                    System.out.println("[FarmManager] Treatment complete for Field-" + fieldId);

                } else if (content.startsWith("HARVEST_COMPLETE:")) {
                    String[] parts = content.split(":");
                    int fieldId = Integer.parseInt(parts[1]);
                    CropType cropType = CropType.valueOf(parts[2]);

                    pendingHarvest.remove(fieldId);
                    availableHarvesters.add(sender);

                    // Add crop to inventory
                    ItemType cropItem = cropType.getCropItem();
                    inventory.addItem(cropItem, 1);

                    System.out.println("[FarmManager] Harvested " + cropType.getEmoji() + " from Field-" + fieldId);
                    broadcastInventory();

                    // Schedule sale if we have crops
                    if (inventory.getQuantity(cropItem) >= 1) {
                        intentionQueue.add(new Intention(IntentionType.SELL_CROPS, fieldId, cropItem));
                    }

                } else if (content.startsWith("DELIVERED:")) {
                    String[] parts = content.split(":");
                    ItemType item = ItemType.valueOf(parts[1]);
                    int quantity = Integer.parseInt(parts[2]);
                    inventory.addItem(item, quantity);
                    System.out.println("[FarmManager] Received " + quantity + "x " + item.getDisplayName());
                    broadcastInventory();
                }

            } else {
                block();
            }
        }
    }

    // ==================== INTENTION EXECUTOR ====================
    /**
     * Executes intentions from the queue (prioritized).
     */
    private class IntentionExecutor extends TickerBehaviour {

        public IntentionExecutor(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            // Sort queue by priority
            if (!intentionQueue.isEmpty()) {
                List<Intention> sorted = new ArrayList<>(intentionQueue);
                Collections.sort(sorted);
                intentionQueue.clear();
                intentionQueue.addAll(sorted);
            }

            // Execute next intention if agents are available
            if (!intentionQueue.isEmpty() && currentIntention == null) {
                currentIntention = intentionQueue.poll();
                executeIntention(currentIntention);
            }
        }
    }

    /**
     * Execute a specific intention.
     */
    private void executeIntention(Intention intention) {
        System.out.println("[FarmManager] Executing intention: " + intention);

        switch (intention.type) {
            case SCAN_FIELD:
                dispatchDrone(intention.fieldId, intention.data != null);
                break;

            case WATER_FIELD:
                deliverWater(intention.fieldId, (Integer) intention.data);
                break;

            case TREAT_DISEASE:
                dispatchSprayer(intention.fieldId, (DiseaseType) intention.data);
                break;

            case HARVEST_FIELD:
                dispatchHarvester(intention.fieldId);
                break;

            case SELL_CROPS:
                startCropAuction((ItemType) intention.data);
                break;

            case BUY_SUPPLIES:
                startSupplyPurchase((ItemType) intention.data);
                break;
        }

        currentIntention = null;
    }

    // ==================== ACTION METHODS ====================

    private void dispatchDrone(int fieldId, boolean forDiagnosis) {
        String droneId = getAvailableAgent(availableDrones);
        if (droneId != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID(droneId, AID.ISLOCALNAME));

            FieldState field = fieldBeliefs.get(fieldId);
            if (forDiagnosis && field != null && field.hasDisease()) {
                msg.setContent("DIAGNOSE_FIELD:" + fieldId + ":" +
                        field.getCurrentDisease().name() + ":" +
                        field.getMoisture() + ":" + field.getHealth());
            } else {
                msg.setContent("SCAN_FIELD:" + fieldId);
            }

            send(msg);
            System.out.println("[FarmManager] Dispatched " + droneId + " to Field-" + fieldId);
        } else {
            // Re-queue intention
            intentionQueue.add(new Intention(IntentionType.SCAN_FIELD, fieldId, forDiagnosis ? "diagnose" : null));
        }
    }

    private void deliverWater(int fieldId, int neededAmount) {
        // Calculate how much water to use (fill to 100%)
        int waterToUse = (int) Math.ceil(neededAmount / 30.0); // Each water unit gives 30%
        waterToUse = Math.max(1, Math.min(waterToUse, 3)); // Use 1-3 water units

        if (inventory.removeItem(ItemType.WATER, waterToUse)) {
            int waterAmount = waterToUse * 30; // Each unit = 30% moisture
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID("Field-" + fieldId, AID.ISLOCALNAME));
            msg.setContent("WATERED:" + waterAmount);
            send(msg);
            pendingWater.remove(fieldId);
            System.out.println(
                    "[FarmManager] Delivered " + waterToUse + " water (" + waterAmount + "%) to Field-" + fieldId);
            broadcastInventory();
        } else {
            System.out.println("[FarmManager] ⚠️ No water in inventory! Ordering more...");
            intentionQueue.add(new Intention(IntentionType.BUY_SUPPLIES, 0, ItemType.WATER));
        }
    }

    private void dispatchSprayer(int fieldId, DiseaseType disease) {
        String sprayerId = getAvailableAgent(availableSprayers);
        if (sprayerId != null) {
            ItemType cure = disease.getCure();
            if (inventory.removeItem(cure, 1)) {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new AID(sprayerId, AID.ISLOCALNAME));
                msg.setContent("SPRAY_FIELD:" + fieldId + ":" + cure.name());
                send(msg);
                System.out.println("[FarmManager] Dispatched " + sprayerId + " with " + cure.getDisplayName());
                broadcastInventory();
            } else {
                // No cure in inventory - buy supplies and let field request diagnosis again
                System.out.println("[FarmManager] No " + cure.getDisplayName() + " available. Ordering...");
                availableSprayers.add(sprayerId); // Return sprayer to pool
                pendingTreatment.remove(fieldId); // Allow field to request treatment again after purchase
                intentionQueue.add(new Intention(IntentionType.BUY_SUPPLIES, fieldId, cure));
            }
        } else {
            intentionQueue.add(new Intention(IntentionType.TREAT_DISEASE, fieldId, disease));
        }
    }

    private void dispatchHarvester(int fieldId) {
        String harvesterId = getAvailableAgent(availableHarvesters);
        if (harvesterId != null) {
            FieldState field = fieldBeliefs.get(fieldId);
            CropType cropType = field != null ? field.getCropType() : CropType.CORN;

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID(harvesterId, AID.ISLOCALNAME));
            msg.setContent("HARVEST_FIELD:" + fieldId + ":" + cropType.name());
            send(msg);
            System.out.println("[FarmManager] Dispatched " + harvesterId + " to harvest Field-" + fieldId);
        } else {
            intentionQueue.add(new Intention(IntentionType.HARVEST_FIELD, fieldId, null));
        }
    }

    private void startSupplyPurchase(ItemType item) {
        System.out.println("[FarmManager] Starting CNP for " + item.getDisplayName());
        addBehaviour(new SupplyPurchaseCNP(item, 5));
    }

    private void startCropAuction(ItemType cropItem) {
        System.out.println("[FarmManager] Starting auction for " + cropItem.getDisplayName());
        addBehaviour(new CropAuction(cropItem, 1));
    }

    private String getAvailableAgent(Set<String> agents) {
        if (!agents.isEmpty()) {
            String agent = agents.iterator().next();
            agents.remove(agent);
            return agent;
        }
        return null;
    }

    // ==================== CNP FOR SUPPLY PURCHASE ====================
    private class SupplyPurchaseCNP extends OneShotBehaviour {
        private ItemType item;
        private int quantity;

        public SupplyPurchaseCNP(ItemType item, int quantity) {
            this.item = item;
            this.quantity = quantity;
        }

        @Override
        public void action() {
            try {
                // Find suppliers
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("supplier");
                template.addServices(sd);

                DFAgentDescription[] suppliers = DFService.search(myAgent, template);

                if (suppliers.length == 0) {
                    System.out.println("[FarmManager] No suppliers found!");
                    return;
                }

                // Send CFP to all suppliers
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                for (DFAgentDescription supplier : suppliers) {
                    cfp.addReceiver(supplier.getName());
                }
                cfp.setContent("SUPPLY:" + item.name() + ":" + quantity);
                send(cfp);

                System.out.println("[FarmManager] Sent CFP for " + quantity + "x " + item.getDisplayName());

                // Wait for proposals
                Thread.sleep(2000);

                // Collect proposals
                List<ACLMessage> proposals = new ArrayList<>();
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                ACLMessage reply;
                while ((reply = receive(mt)) != null) {
                    proposals.add(reply);
                }

                if (proposals.isEmpty()) {
                    System.out.println("[FarmManager] No proposals received");
                    return;
                }

                // Find best proposal (lowest price)
                ACLMessage bestProposal = null;
                double bestPrice = Double.MAX_VALUE;

                for (ACLMessage proposal : proposals) {
                    String[] parts = proposal.getContent().split(":");
                    double price = Double.parseDouble(parts[3]);
                    if (price < bestPrice && price <= budget) {
                        bestPrice = price;
                        bestProposal = proposal;
                    }
                }

                // Accept best, reject others
                for (ACLMessage proposal : proposals) {
                    ACLMessage response = proposal.createReply();
                    if (proposal == bestProposal) {
                        response.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        response.setContent(
                                "ACCEPT:" + item.name() + ":" + quantity + ":" + String.format("%.2f", bestPrice));
                        budget -= bestPrice;
                        System.out.println("[FarmManager] Accepted proposal: $" + String.format("%.2f", bestPrice));
                    } else {
                        response.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    }
                    send(response);
                }

            } catch (Exception e) {
                System.err.println("[FarmManager] CNP error: " + e.getMessage());
            }
        }
    }

    // ==================== CROP AUCTION ====================
    private class CropAuction extends OneShotBehaviour {
        private ItemType cropItem;
        private int quantity;

        public CropAuction(ItemType cropItem, int quantity) {
            this.cropItem = cropItem;
            this.quantity = quantity;
        }

        @Override
        public void action() {
            try {
                // Find clients
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("client");
                template.addServices(sd);

                DFAgentDescription[] clients = DFService.search(myAgent, template);

                if (clients.length == 0) {
                    System.out.println("[FarmManager] No clients found!");
                    return;
                }

                // Send CFP to all clients
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                for (DFAgentDescription client : clients) {
                    cfp.addReceiver(client.getName());
                }
                cfp.setContent("BUY:" + cropItem.name() + ":" + quantity);
                send(cfp);

                System.out.println("[FarmManager] Started auction for " + quantity + "x " + cropItem.getDisplayName());

                // Wait for bids
                Thread.sleep(2000);

                // Collect bids
                List<ACLMessage> bids = new ArrayList<>();
                List<Double> bidPrices = new ArrayList<>();
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                ACLMessage reply;
                while ((reply = receive(mt)) != null) {
                    bids.add(reply);
                    String[] parts = reply.getContent().split(":");
                    bidPrices.add(Double.parseDouble(parts[3]));
                }

                if (bids.isEmpty()) {
                    System.out.println("[FarmManager] No bids received");
                    return;
                }

                // Second-price auction: winner pays second-highest bid
                double highestBid = 0;
                double secondHighestBid = 0;
                ACLMessage winner = null;

                for (int i = 0; i < bids.size(); i++) {
                    double bid = bidPrices.get(i);
                    if (bid > highestBid) {
                        secondHighestBid = highestBid;
                        highestBid = bid;
                        winner = bids.get(i);
                    } else if (bid > secondHighestBid) {
                        secondHighestBid = bid;
                    }
                }

                // Payment price is second-highest (or highest if only one bidder)
                double paymentPrice = bids.size() > 1 ? secondHighestBid : highestBid;

                // Accept winner, reject others
                for (ACLMessage bid : bids) {
                    ACLMessage response = bid.createReply();
                    if (bid == winner) {
                        response.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        response.setContent(
                                "WIN:" + cropItem.name() + ":" + quantity + ":" + String.format("%.2f", paymentPrice));
                        inventory.removeItem(cropItem, quantity);
                        budget += paymentPrice;
                        System.out.println("[FarmManager] 🎉 Sold " + cropItem.getDisplayName() + " for $"
                                + String.format("%.2f", paymentPrice) + " (second-price)");

                        // Broadcast to frontend
                        String saleJson = String.format(
                                "{\"seller\":\"FarmManager\",\"buyer\":\"%s\",\"crop\":\"%s\",\"quantity\":%d,\"highBid\":%.2f,\"payment\":%.2f,\"type\":\"AUCTION_COMPLETE\"}",
                                bid.getSender().getLocalName(), cropItem.name(), quantity, highestBid, paymentPrice);
                        WebServer.broadcast("MARKET_EVENT", saleJson);
                    } else {
                        response.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    }
                    send(response);
                }

                broadcastInventory();

            } catch (Exception e) {
                System.err.println("[FarmManager] Auction error: " + e.getMessage());
            }
        }
    }

    // ==================== BROADCASTING ====================
    private class BDIBroadcaster extends TickerBehaviour {

        public BDIBroadcaster(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            broadcastBDIState();
            broadcastInventory();
        }
    }

    private void broadcastBDIState() {
        StringBuilder beliefs = new StringBuilder("[");
        beliefs.append("\"Fields: ").append(fieldBeliefs.size()).append("\",");
        beliefs.append("\"Budget: $").append(String.format("%.2f", budget)).append("\",");
        beliefs.append("\"Drones available: ").append(availableDrones.size()).append("\",");
        beliefs.append("\"Harvesters available: ").append(availableHarvesters.size()).append("\",");
        beliefs.append("\"Sprayers available: ").append(availableSprayers.size()).append("\"");
        beliefs.append("]");

        StringBuilder desires = new StringBuilder("[");
        desires.append("\"Keep all fields healthy\",");
        desires.append("\"Maximize profit\",");
        desires.append("\"Avoid resource starvation\"");
        desires.append("]");

        StringBuilder intentions = new StringBuilder("[");
        int count = 0;
        for (Intention i : intentionQueue) {
            if (count > 0)
                intentions.append(",");
            intentions.append("\"").append(i.type).append(": Field-").append(i.fieldId).append("\"");
            count++;
            if (count >= 5)
                break;
        }
        intentions.append("]");

        String json = String.format("{\"beliefs\":%s,\"desires\":%s,\"intentions\":%s}",
                beliefs, desires, intentions);

        WebServer.broadcast("BDI_UPDATE", json);
    }

    private void broadcastInventory() {
        WebServer.broadcast("INVENTORY_UPDATE", inventory.toJson());
    }

    /**
     * Update belief about a field.
     */
    public void updateFieldBelief(FieldState state) {
        fieldBeliefs.put(state.getFieldId(), state);
    }
}
