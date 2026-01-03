package com.farm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;

import com.farm.gui.WebServer;
import com.farm.models.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FarmerBDIAgent - The central decision-making agent implementing BDI
 * architecture.
 * 
 * BDI Components:
 * - BELIEFS: What the agent knows about the world (field states, weather,
 * budget, market)
 * - DESIRES: Goals the agent wants to achieve (healthy crops, profit, efficient
 * water usage)
 * - INTENTIONS: Committed plans to achieve desires (prioritized action queue)
 * 
 * This agent coordinates all farm operations based on its mental state.
 * Agent Type: BDI
 */
public class FarmerBDIAgent extends Agent {

    // ==================== BELIEFS ====================
    // What the agent believes to be true about the world

    private Map<Integer, FieldState> fieldBeliefs = new ConcurrentHashMap<>();
    private String weatherBelief = "Clear";
    private double budgetBelief = 500.0;
    private double incomeBelief = 0.0;
    private double expensesBelief = 0.0;
    private Map<String, Double> marketPriceBelief = new ConcurrentHashMap<>();
    private List<AID> knownDrones = new ArrayList<>();
    private List<AID> knownSuppliers = new ArrayList<>();
    private int predictedWaterNeed = 0;
    private int predictionConfidence = 0;

    // ==================== DESIRES ====================
    // What the agent wants to achieve (goals)

    private static final int DESIRED_MIN_MOISTURE = 40; // All fields above this
    private static final int DESIRED_MIN_HEALTH = 70; // All crops healthy
    private static final double DESIRED_MAX_SPENDING = 100.0; // Per cycle
    private static final double DESIRED_PROFIT_MARGIN = 1.5; // 50% profit on investment

    // Desire flags
    private boolean desireHealthyCrops = true;
    private boolean desireMaximizeProfit = true;
    private boolean desireEfficientWaterUse = true;
    private boolean desireTimelyHarvest = true;

    // ==================== INTENTIONS ====================
    // Committed plans (priority queue of actions)

    private PriorityQueue<Intention> intentionQueue = new PriorityQueue<>(
            Comparator.comparingInt(Intention::getPriority).reversed());

    private boolean processingIntention = false;
    private Intention currentIntention = null;

    // ==================== AGENT LIFECYCLE ====================

    @Override
    protected void setup() {
        System.out.println("[FarmerBDI] Agent starting...");
        System.out.println("[FarmerBDI] Initializing BDI mental state...");

        // Register with DF as farm-manager
        registerWithDF();

        // Initialize beliefs from startup
        initializeBeliefs();

        // Add BDI reasoning cycle behavior (6 seconds for readable pacing)
        addBehaviour(new BDIReasoningCycle(this, 6000));

        // Add belief update receiver
        addBehaviour(new BeliefUpdateReceiver());

        // Add intention executor (4 seconds for readable pacing)
        addBehaviour(new IntentionExecutor(this, 4000));

        // Broadcast initial BDI state to GUI
        broadcastBDIState();

        System.out.println("[FarmerBDI] Agent ready. BDI architecture active.");
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // Ignore
        }
        System.out.println("[FarmerBDI] Agent terminated.");
    }

    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("farm-manager");
            sd.setName("BDI-Farm-Manager");
            dfd.addServices(sd);
            DFService.register(this, dfd);
            System.out.println("[FarmerBDI] Registered with DF as 'farm-manager'");
        } catch (FIPAException e) {
            System.err.println("[FarmerBDI] DF registration failed: " + e.getMessage());
        }
    }

    private void initializeBeliefs() {
        // Initialize market prices
        marketPriceBelief.put("water", 15.0);
        marketPriceBelief.put("fertilizer", 25.0);
        marketPriceBelief.put("pesticide", 30.0);

        // Search for known agents
        discoverAgents();
    }

    private void discoverAgents() {
        // Find drones
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("inspector-drone");
            template.addServices(sd);
            DFAgentDescription[] results = DFService.search(this, template);
            for (DFAgentDescription dfd : results) {
                knownDrones.add(dfd.getName());
            }
            System.out.println("[FarmerBDI] Found " + knownDrones.size() + " drones");
        } catch (FIPAException e) {
            // Ignore
        }

        // Find suppliers
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("water-supplier");
            template.addServices(sd);
            DFAgentDescription[] results = DFService.search(this, template);
            for (DFAgentDescription dfd : results) {
                knownSuppliers.add(dfd.getName());
            }
            System.out.println("[FarmerBDI] Found " + knownSuppliers.size() + " suppliers");
        } catch (FIPAException e) {
            // Ignore
        }
    }

    // ==================== BDI REASONING CYCLE ====================

    /**
     * The core BDI reasoning cycle.
     * Runs periodically to:
     * 1. Update beliefs from percepts
     * 2. Generate new desires based on beliefs
     * 3. Deliberate and form intentions
     * 4. Execute intentions
     */
    private class BDIReasoningCycle extends TickerBehaviour {
        public BDIReasoningCycle(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            // Step 1: Belief Revision (BRF)
            reviseBeliefs();

            // Step 2: Option Generation (options)
            List<Intention> options = generateOptions();

            // Step 3: Deliberation (filter)
            List<Intention> selectedIntentions = deliberate(options);

            // Step 4: Add to intention queue
            for (Intention intention : selectedIntentions) {
                if (!intentionQueue.contains(intention)) {
                    intentionQueue.offer(intention);
                    System.out.println("[FarmerBDI] New intention: " + intention.getDescription());
                }
            }

            // Broadcast updated BDI state to GUI
            broadcastBDIState();
        }
    }

    /**
     * Belief Revision Function (BRF)
     * Updates beliefs based on current perceptions.
     */
    private void reviseBeliefs() {
        // Re-discover agents periodically
        if (knownDrones.isEmpty() || knownSuppliers.isEmpty()) {
            discoverAgents();
        }

        // Beliefs are also updated by incoming messages (BeliefUpdateReceiver)
    }

    /**
     * Generate possible options based on current beliefs and desires.
     */
    private List<Intention> generateOptions() {
        List<Intention> options = new ArrayList<>();

        // Check each field belief
        for (FieldState field : fieldBeliefs.values()) {

            // Option: Irrigate if field needs water
            if (field.needsWater() && desireHealthyCrops) {
                options.add(new Intention(
                        IntentionType.IRRIGATE,
                        "Irrigate Field-" + field.getFieldId(),
                        calculateIrrigationPriority(field),
                        field.getFieldId()));
            }

            // Option: Inspect if field might have issues
            if (field.needsInspection() && desireHealthyCrops) {
                options.add(new Intention(
                        IntentionType.INSPECT,
                        "Inspect Field-" + field.getFieldId(),
                        8,
                        field.getFieldId()));
            }

            // Option: Harvest if crop is ready
            if (field.canHarvest() && desireTimelyHarvest) {
                options.add(new Intention(
                        IntentionType.HARVEST,
                        "Harvest Field-" + field.getFieldId(),
                        10, // High priority
                        field.getFieldId()));
            }

            // Option: Apply pesticide if needed
            if (field.hasPest() && desireHealthyCrops) {
                options.add(new Intention(
                        IntentionType.APPLY_PESTICIDE,
                        "Apply pesticide to Field-" + field.getFieldId(),
                        9,
                        field.getFieldId()));
            }
        }

        // Option: Buy resources if budget allows and needed
        if (budgetBelief > 50 && needsResources() && desireEfficientWaterUse) {
            options.add(new Intention(
                    IntentionType.BUY_WATER,
                    "Purchase water supplies",
                    6,
                    -1));
        }

        return options;
    }

    /**
     * Deliberation: Filter options based on desires and constraints.
     */
    private List<Intention> deliberate(List<Intention> options) {
        List<Intention> selected = new ArrayList<>();

        for (Intention option : options) {
            // Check if intention is compatible with current state
            if (isIntentionFeasible(option)) {
                // Check if we don't already have this intention
                if (!hasIntention(option)) {
                    selected.add(option);
                }
            }
        }

        return selected;
    }

    private boolean isIntentionFeasible(Intention intention) {
        switch (intention.getType()) {
            case IRRIGATE:
            case BUY_WATER:
                return budgetBelief >= 10; // Minimum budget check
            case HARVEST:
                return !knownDrones.isEmpty() || hasHarvester();
            case INSPECT:
                return !knownDrones.isEmpty();
            default:
                return true;
        }
    }

    private boolean hasIntention(Intention newIntention) {
        for (Intention existing : intentionQueue) {
            if (existing.getType() == newIntention.getType()
                    && existing.getTargetFieldId() == newIntention.getTargetFieldId()) {
                return true;
            }
        }
        return false;
    }

    private int calculateIrrigationPriority(FieldState field) {
        // Higher priority for lower moisture
        int priority = 5;
        if (field.getMoisture() < 20)
            priority = 10;
        else if (field.getMoisture() < 30)
            priority = 8;
        else if (field.getMoisture() < 40)
            priority = 6;
        return priority;
    }

    private boolean needsResources() {
        for (FieldState field : fieldBeliefs.values()) {
            if (field.needsWater())
                return true;
        }
        return false;
    }

    private boolean hasHarvester() {
        // Check if harvester agent exists
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("harvester");
            template.addServices(sd);
            DFAgentDescription[] results = DFService.search(this, template);
            return results.length > 0;
        } catch (FIPAException e) {
            return false;
        }
    }

    // ==================== INTENTION EXECUTOR ====================

    /**
     * Executes intentions from the queue.
     */
    private class IntentionExecutor extends TickerBehaviour {
        public IntentionExecutor(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (processingIntention) {
                return; // Still working on current intention
            }

            Intention intention = intentionQueue.poll();
            if (intention != null) {
                executeIntention(intention);
            }
        }
    }

    private void executeIntention(Intention intention) {
        processingIntention = true;
        currentIntention = intention;

        System.out.println("[FarmerBDI] Executing intention: " + intention.getDescription());

        // Broadcast to GUI
        WebServer.broadcastAgentInteraction(
                "FarmerBDI", "bdi",
                getTargetAgent(intention), getTargetAgentType(intention),
                "INTENTION", intention.getDescription(), 1);

        // Execute based on type
        switch (intention.getType()) {
            case IRRIGATE:
                executeIrrigation(intention);
                break;
            case INSPECT:
                executeInspection(intention);
                break;
            case HARVEST:
                executeHarvest(intention);
                break;
            case BUY_WATER:
                executePurchase(intention);
                break;
            case APPLY_PESTICIDE:
                executePesticideApplication(intention);
                break;
            default:
                processingIntention = false;
        }

        broadcastBDIState();
    }

    private String getTargetAgent(Intention intention) {
        switch (intention.getType()) {
            case INSPECT:
                return "Drone";
            case HARVEST:
                return "Harvester";
            case BUY_WATER:
                return "Suppliers";
            default:
                return "Controller";
        }
    }

    private String getTargetAgentType(Intention intention) {
        switch (intention.getType()) {
            case INSPECT:
                return "mobile";
            case HARVEST:
                return "mobile";
            case BUY_WATER:
                return "cognitive";
            default:
                return "hybrid";
        }
    }

    private void executeIrrigation(Intention intention) {
        // Send irrigation request to controller
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(new AID("Controller", AID.ISLOCALNAME));
        msg.setContent("IRRIGATE:" + intention.getTargetFieldId());
        msg.setConversationId("irrigation-" + System.currentTimeMillis());
        send(msg);

        expensesBelief += 10; // Cost estimate
        processingIntention = false;
    }

    private void executeInspection(Intention intention) {
        if (!knownDrones.isEmpty()) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(knownDrones.get(0));
            msg.setContent("INSPECT:" + intention.getTargetFieldId());
            msg.setConversationId("inspect-" + System.currentTimeMillis());
            send(msg);
        }
        processingIntention = false;
    }

    private void executeHarvest(Intention intention) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(new AID("Harvester", AID.ISLOCALNAME));
        msg.setContent("HARVEST:" + intention.getTargetFieldId());
        msg.setConversationId("harvest-" + System.currentTimeMillis());
        send(msg);

        // Update income belief based on expected harvest value
        FieldState field = fieldBeliefs.get(intention.getTargetFieldId());
        if (field != null) {
            incomeBelief += field.getHarvestValue();
        }
        processingIntention = false;
    }

    private void executePurchase(Intention intention) {
        // Initiate CNP with suppliers via controller
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(new AID("Controller", AID.ISLOCALNAME));
        msg.setContent("BUY_WATER:20");
        msg.setConversationId("purchase-" + System.currentTimeMillis());
        send(msg);
        processingIntention = false;
    }

    private void executePesticideApplication(Intention intention) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(new AID("Controller", AID.ISLOCALNAME));
        msg.setContent("APPLY_PESTICIDE:" + intention.getTargetFieldId());
        msg.setConversationId("pesticide-" + System.currentTimeMillis());
        send(msg);

        expensesBelief += 30; // Pesticide cost
        processingIntention = false;
    }

    // ==================== BELIEF UPDATE RECEIVER ====================

    /**
     * Receives messages from other agents to update beliefs.
     */
    private class BeliefUpdateReceiver extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                processBeliefUpdate(msg);
            } else {
                block();
            }
        }
    }

    private void processBeliefUpdate(ACLMessage msg) {
        String content = msg.getContent();
        String sender = msg.getSender().getLocalName();

        // Handle different message types
        if (content.startsWith("FIELD_UPDATE:")) {
            updateFieldBelief(content, sender);
        } else if (content.startsWith("WEATHER:")) {
            weatherBelief = content.substring(8);
            System.out.println("[FarmerBDI] Belief updated: Weather = " + weatherBelief);
        } else if (content.startsWith("PREDICTION:")) {
            parsePredictionUpdate(content);
        } else if (content.startsWith("PRICE:")) {
            parseMarketPrice(content);
        } else if (msg.getPerformative() == ACLMessage.CONFIRM) {
            handleConfirmation(msg);
        }

        // Update GUI
        WebServer.broadcastEconomy(incomeBelief, expensesBelief);
    }

    private void updateFieldBelief(String content, String sender) {
        try {
            // DEBUG: Log the incoming message
            System.out.println("[FarmerBDI] DEBUG: Received FIELD_UPDATE from " + sender + ": " + content);

            // Parse: "FIELD_UPDATE:id:X,crop:Y,moisture:Z,growth:W,health:H" (from
            // CropGrowth)
            // or: "FIELD_UPDATE:id:X,moisture:Z" (from SoilSensor)
            String[] parts = content.substring(13).split(",");
            System.out.println("[FarmerBDI] DEBUG: Split into " + parts.length + " parts");
            for (int i = 0; i < parts.length; i++) {
                System.out.println("[FarmerBDI] DEBUG:   parts[" + i + "] = " + parts[i]);
            }

            int fieldId = Integer.parseInt(parts[0].split(":")[1]);

            FieldState field = fieldBeliefs.get(fieldId);

            // Determine if this is from CropGrowth (has crop field) or SoilSensor (moisture
            // only)
            boolean hasCropType = parts.length >= 5 && parts[1].startsWith("crop:");
            System.out.println("[FarmerBDI] DEBUG: hasCropType = " + hasCropType);

            if (hasCropType) {
                // Full update from CropGrowthAgent: id,crop,moisture,growth,health
                String cropId = parts[1].split(":")[1];
                int moisture = Integer.parseInt(parts[2].split(":")[1]);
                int growth = Integer.parseInt(parts[3].split(":")[1]);
                int health = Integer.parseInt(parts[4].split(":")[1]);

                CropType cropType = CropType.fromId(cropId);

                if (field == null) {
                    field = new FieldState(fieldId, cropType);
                    fieldBeliefs.put(fieldId, field);
                } else {
                    // Update crop type if it changed (shouldn't happen, but be safe)
                    if (field.getCropType() != cropType) {
                        field = new FieldState(fieldId, cropType);
                        field.setMoisture(moisture);
                        field.setGrowth(growth);
                        field.setHealth(health);
                        fieldBeliefs.put(fieldId, field);
                    } else {
                        field.setMoisture(moisture);
                        field.setGrowth(growth);
                        field.setHealth(health);
                    }
                }
            } else {
                // Moisture-only update from SoilSensorAgent: id,moisture
                int moisture = Integer.parseInt(parts[1].split(":")[1]);

                if (field == null) {
                    // Create with default WHEAT if we haven't seen this field yet
                    field = new FieldState(fieldId, CropType.WHEAT);
                    fieldBeliefs.put(fieldId, field);
                }
                field.setMoisture(moisture);
            }

            // Check if needs water
            if (field.needsWater()) {
                field.setNeedsInspection(true);
            }

            System.out.println("[FarmerBDI] Belief updated: " + field);

            // NOTE: CropGrowthAgent already broadcasts to GUI, no need to duplicate here
            // This prevents double-updates and ensures GUI gets data directly from source

        } catch (Exception e) {
            System.err.println("[FarmerBDI] ERROR parsing field update!");
            System.err.println("[FarmerBDI] Content was: " + content);
            System.err.println("[FarmerBDI] Sender was: " + sender);
            System.err.println("[FarmerBDI] Error message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void parsePredictionUpdate(String content) {
        try {
            String[] parts = content.substring(11).split(",");
            predictedWaterNeed = Integer.parseInt(parts[0]);
            predictionConfidence = Integer.parseInt(parts[1]);
            System.out.println("[FarmerBDI] Belief updated: Prediction = " + predictedWaterNeed + "L ("
                    + predictionConfidence + "%)");
        } catch (Exception e) {
            // Ignore parsing errors
        }
    }

    private void parseMarketPrice(String content) {
        try {
            String[] parts = content.substring(6).split(":");
            String resource = parts[0];
            double price = Double.parseDouble(parts[1]);
            marketPriceBelief.put(resource, price);
        } catch (Exception e) {
            // Ignore
        }
    }

    private void handleConfirmation(ACLMessage msg) {
        String content = msg.getContent();
        if (content.contains("HARVESTED")) {
            // Harvest completed - update income
            System.out.println("[FarmerBDI] Harvest confirmed!");
        } else if (content.contains("IRRIGATED")) {
            System.out.println("[FarmerBDI] Irrigation confirmed!");
        }
    }

    // ==================== GUI UPDATES ====================

    private void broadcastBDIState() {
        // Format beliefs
        String[] beliefs = {
                "Fields monitored: " + fieldBeliefs.size(),
                "Weather: " + weatherBelief,
                "Budget: $" + String.format("%.2f", budgetBelief),
                "Avg Water: " + calculateAverageWater() + "%",
                "Avg Health: " + calculateAverageHealth() + "%",
                "Drones available: " + knownDrones.size(),
                "Low moisture fields: " + countLowMoistureFields()
        };

        // Format desires
        String[] desires = {
                "All fields > " + DESIRED_MIN_MOISTURE + "% moisture",
                "Maximize harvest profit",
                "Efficient water usage",
                "Timely crop harvesting"
        };

        // Format intentions
        List<String> intentionStrings = new ArrayList<>();
        int i = 1;
        for (Intention intent : intentionQueue) {
            if (i <= 5) { // Limit to 5
                intentionStrings.add(intent.getDescription());
                i++;
            }
        }
        if (currentIntention != null) {
            intentionStrings.add(0, "[EXECUTING] " + currentIntention.getDescription());
        }

        String[] intentions = intentionStrings.toArray(new String[0]);

        WebServer.broadcastBDIUpdate(beliefs, desires, intentions);
    }

    private int countLowMoistureFields() {
        int count = 0;
        for (FieldState field : fieldBeliefs.values()) {
            if (field.needsWater())
                count++;
        }
        return count;
    }

    private int calculateAverageWater() {
        if (fieldBeliefs.isEmpty())
            return 0;
        int total = 0;
        for (FieldState field : fieldBeliefs.values()) {
            total += field.getMoisture();
        }
        return total / fieldBeliefs.size();
    }

    private int calculateAverageHealth() {
        if (fieldBeliefs.isEmpty())
            return 0;
        int total = 0;
        for (FieldState field : fieldBeliefs.values()) {
            total += field.getHealth();
        }
        return total / fieldBeliefs.size();
    }

    // ==================== PUBLIC METHODS ====================

    /**
     * Update a field belief directly (called by other agents).
     */
    public void updateFieldBelief(int fieldId, FieldState state) {
        fieldBeliefs.put(fieldId, state);
    }

    /**
     * Update budget belief.
     */
    public void updateBudget(double amount) {
        this.budgetBelief += amount;
    }

    // ==================== INNER CLASSES ====================

    /**
     * Represents an intention (committed action).
     */
    public static class Intention {
        private IntentionType type;
        private String description;
        private int priority; // Higher = more urgent
        private int targetFieldId;

        public Intention(IntentionType type, String description, int priority, int targetFieldId) {
            this.type = type;
            this.description = description;
            this.priority = priority;
            this.targetFieldId = targetFieldId;
        }

        public IntentionType getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public int getPriority() {
            return priority;
        }

        public int getTargetFieldId() {
            return targetFieldId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Intention intention = (Intention) o;
            return targetFieldId == intention.targetFieldId && type == intention.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, targetFieldId);
        }
    }

    /**
     * Types of intentions the agent can have.
     */
    public enum IntentionType {
        IRRIGATE,
        INSPECT,
        HARVEST,
        BUY_WATER,
        BUY_FERTILIZER,
        APPLY_PESTICIDE,
        REPLANT
    }
}
