package com.smartfarm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import com.smartfarm.models.CropType;
import com.smartfarm.web.WebServer;

/**
 * FieldAgent - Reactive agent representing a single field.
 * 
 * FIXED: Disease flow now goes through Drone (AI) for diagnosis
 * - Field detects symptoms (not disease type)
 * - Field requests Drone scan
 * - Drone diagnoses and alerts Sprayer
 */
public class FieldAgent extends Agent {

    private int fieldId;
    private CropType cropType;
    private WebServer webServer;

    // Field state
    private int moisture = 80;
    private int health = 100;
    private int growth = 0;
    private int scanLevel = 100;
    private boolean isPlanted = true;

    // FIXED: Field only knows symptoms, not disease type
    private boolean hasSymptoms = false;
    private String confirmedDisease = null; // Set by Drone after scan

    // Weather effect
    private double weatherEvaporation = 1.0;

    // Request tracking
    private boolean waterRequested = false;
    private boolean harvestRequested = false;
    private boolean scanRequested = false;

    // Cooldown timers to prevent spam
    private int waterRequestCooldown = 0; // Ticks to wait before re-requesting water
    private int symptomImmunity = 0; // Ticks of immunity after scan

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 3) {
            fieldId = (Integer) args[0];
            cropType = CropType.valueOf((String) args[1]);
            webServer = (WebServer) args[2];
        } else {
            fieldId = 1;
            cropType = CropType.CORN;
        }

        System.out.println("[Field-" + fieldId + "] Agent started.");
        System.out.println("[Field-" + fieldId + "] Crop: " + cropType.getEmoji() + " " + cropType.getDisplayName());

        addBehaviour(new FieldTickBehaviour(this, 2000));
        addBehaviour(new ResponseHandler());

        broadcastState();
    }

    @Override
    protected void takeDown() {
        System.out.println("[Field-" + fieldId + "] Agent terminated.");
    }

    /**
     * Main tick behavior - simulates time passing
     */
    private class FieldTickBehaviour extends TickerBehaviour {
        public FieldTickBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (!isPlanted) {
                broadcastState();
                return;
            }

            // If fully grown, request harvest
            if (growth >= 100) {
                if (!harvestRequested) {
                    requestHarvest();
                }
                broadcastState();
                return;
            }

            // FIXED: Use weather evaporation rate
            double waterLoss = 2 * cropType.getWaterConsumption() * weatherEvaporation;
            moisture = Math.max(0, moisture - (int) waterLoss);

            // Decrease scan level
            scanLevel = Math.max(0, scanLevel - 1);

            // Disease damage (only if confirmed by Drone)
            if (confirmedDisease != null) {
                health = Math.max(0, health - 2);
            }

            // Grow if conditions good
            if (moisture > 30 && health > 50) {
                double growthAmount = 2 * cropType.getGrowthMultiplier();
                growth = Math.min(100, growth + (int) growthAmount);
            }

            // Decrease cooldown timers
            if (waterRequestCooldown > 0)
                waterRequestCooldown--;
            if (symptomImmunity > 0)
                symptomImmunity--;

            // FIXED: Random symptoms (3%), but only if not immune
            if (!hasSymptoms && confirmedDisease == null && symptomImmunity == 0 && Math.random() < 0.03) {
                hasSymptoms = true;
                health -= 5;
                System.out.println("[Field-" + fieldId + "] SYMPTOMS detected! Requesting drone scan...");
                broadcastLog("Field-" + fieldId + ": Symptoms detected! Requesting scan...");
                requestDroneScan();
            }

            // Request water if low (with cooldown to prevent spam)
            if (moisture < 25 && !waterRequested && waterRequestCooldown == 0) {
                requestWater();
                waterRequestCooldown = 5; // Wait 5 ticks before re-requesting
            }

            // FIXED: Request scan if scanLevel low
            if (scanLevel < 30 && !scanRequested && !hasSymptoms) {
                System.out.println("[Field-" + fieldId + "] Scan level low, requesting drone...");
                requestDroneScan();
            }

            broadcastState();

            if (growth >= 100) {
                broadcastLog("Field-" + fieldId + " ready for harvest!");
            }
        }
    }

    /**
     * Handle responses from worker agents and drones
     */
    private class ResponseHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();

                if (content.startsWith("WATER_DONE:")) {
                    int amount = Integer.parseInt(content.substring(11));
                    moisture = Math.min(100, moisture + amount);
                    waterRequested = false;
                    System.out.println("[Field-" + fieldId + "] Watered +" + amount + "%, now " + moisture + "%");
                    broadcastLog("Field-" + fieldId + " watered! (" + moisture + "%)");
                    broadcastState();

                } else if (content.startsWith("WEATHER_MOISTURE:")) {
                    int amount = Integer.parseInt(content.substring(17));
                    moisture = Math.min(100, moisture + amount);
                    broadcastState();

                } else if (content.startsWith("WEATHER_EVAP:")) {
                    // FIXED: Receive evaporation rate from Weather
                    weatherEvaporation = Double.parseDouble(content.substring(13));
                    System.out.println("[Field-" + fieldId + "] Weather evaporation: " + weatherEvaporation + "x");

                } else if (content.startsWith("SCAN_DONE:")) {
                    // FIXED: Drone reports disease type (or NONE)
                    String disease = content.substring(10);
                    scanLevel = 100;
                    scanRequested = false;
                    symptomImmunity = 10; // Immunity for 10 ticks after scan

                    if (disease.equals("NONE")) {
                        hasSymptoms = false;
                        System.out.println(
                                "[Field-" + fieldId + "] Scan complete: No disease found! (immune for 10 ticks)");
                        broadcastLog("Field-" + fieldId + ": Healthy! No disease.");
                    } else {
                        confirmedDisease = disease;
                        System.out.println("[Field-" + fieldId + "] Scan complete: " + disease + " confirmed!");
                        broadcastLog("Field-" + fieldId + ": " + disease + " confirmed by drone!");
                    }
                    broadcastState();

                } else if (content.equals("HARVEST_DONE")) {
                    String cropName = cropType.getDisplayName();
                    System.out.println("[Field-" + fieldId + "] Harvested: " + cropName);
                    broadcastLog("Field-" + fieldId + " harvested: " + cropName);

                    // AUTO-REPLANT
                    growth = 0;
                    moisture = 50;
                    health = 100;
                    scanLevel = 100;
                    isPlanted = true;
                    harvestRequested = false;
                    hasSymptoms = false;
                    confirmedDisease = null;
                    System.out.println("[Field-" + fieldId + "] Auto-replanted: " + cropType.getDisplayName());
                    broadcastLog("Field-" + fieldId + " replanted: " + cropType.getDisplayName());
                    broadcastState();

                } else if (content.equals("TREATMENT_DONE")) {
                    String disease = confirmedDisease;
                    confirmedDisease = null;
                    hasSymptoms = false;
                    health = Math.min(100, health + 20);
                    System.out.println("[Field-" + fieldId + "] Disease cured: " + disease);
                    broadcastLog("Field-" + fieldId + ": " + disease + " cured!");
                    broadcastState();
                }
            } else {
                block();
            }
        }
    }

    /**
     * Request water from Irrigator
     */
    private void requestWater() {
        waterRequested = true;
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.addReceiver(new AID("Irrigator", AID.ISLOCALNAME));
        request.setContent("WATER:Field-" + fieldId);
        send(request);
        System.out.println("[Field-" + fieldId + "] Requesting water...");
        broadcastLog("Field-" + fieldId + " requesting water (" + moisture + "%)");
    }

    /**
     * Request harvest from Harvester
     */
    private void requestHarvest() {
        harvestRequested = true;
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.addReceiver(new AID("Harvester", AID.ISLOCALNAME));
        request.setContent("HARVEST:Field-" + fieldId);
        send(request);
        System.out.println("[Field-" + fieldId + "] Requesting harvest...");
        broadcastLog("Field-" + fieldId + " requesting harvest");
    }

    /**
     * FIXED: Request scan from Drone (NOT treatment from Sprayer)
     */
    private void requestDroneScan() {
        scanRequested = true;
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        // Try Drone-1 first
        request.addReceiver(new AID("Drone-1", AID.ISLOCALNAME));
        request.setContent("SCAN:Field-" + fieldId);
        send(request);
        System.out.println("[Field-" + fieldId + "] Requesting drone scan...");
        broadcastLog("Field-" + fieldId + " requesting drone scan");
    }

    /**
     * Broadcast field state to GUI
     */
    private void broadcastState() {
        if (webServer == null)
            return;

        String diseaseJson = confirmedDisease != null ? "\"" + confirmedDisease + "\""
                : hasSymptoms ? "\"SYMPTOMS\"" : "null";

        String json = String.format(
                "{\"id\":%d,\"crop\":\"%s\",\"cropIcon\":\"%s\",\"moisture\":%d,\"health\":%d,\"growth\":%d,\"scanLevel\":%d,\"disease\":%s,\"planted\":%b}",
                fieldId, cropType.getDisplayName(), cropType.getEmoji(),
                moisture, health, growth, scanLevel,
                diseaseJson, isPlanted);
        webServer.broadcast("FIELD_UPDATE", json);
    }

    private void broadcastLog(String message) {
        if (webServer == null)
            return;
        webServer.broadcast("LOG", "{\"message\":\"" + message + "\"}");
    }

    public int getFieldId() {
        return fieldId;
    }
}
