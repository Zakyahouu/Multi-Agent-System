package com.smartfarm;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import com.smartfarm.web.WebServer;

/**
 * SmartFarm V2 - Main Entry Point
 * 
 * Phase 1.2: Container Architecture
 * Phase 1.3: Field Agents
 */
public class Main {

    private static WebServer webServer;
    private static AgentContainer mainContainer;
    private static AgentContainer baseContainer;
    private static AgentContainer[] fieldContainers;

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("           SMARTFARM V2 - MULTI-AGENT SIMULATION");
        System.out.println("============================================================");
        System.out.println();

        try {
            // Step 1: Start WebServer
            System.out.println("[Main] Step 1: Starting WebServer on port 8080...");
            webServer = new WebServer();
            webServer.start(8080);
            System.out.println("[Main] WebServer started successfully!");

            // Initialize Inventory with WebServer
            com.smartfarm.models.Inventory.setWebServer(webServer);
            System.out.println("[Main] Inventory system initialized.");
            System.out.println();

            // Step 2: Initialize JADE Runtime
            System.out.println("[Main] Step 2: Initializing JADE Runtime...");
            Runtime runtime = Runtime.instance();

            // Step 3: Create Main-Container
            System.out.println("[Main] Step 3: Creating Main-Container...");
            Profile mainProfile = new ProfileImpl();
            mainProfile.setParameter(Profile.MAIN_HOST, "localhost");
            mainProfile.setParameter(Profile.GUI, "false");
            mainContainer = runtime.createMainContainer(mainProfile);
            System.out.println("[Main] Main-Container created.");

            // Step 4: Create Base-Container (agent home)
            System.out.println("[Main] Step 4: Creating Base-Container...");
            Profile baseProfile = new ProfileImpl();
            baseProfile.setParameter(Profile.MAIN_HOST, "localhost");
            baseProfile.setParameter(Profile.CONTAINER_NAME, "Base-Container");
            baseContainer = runtime.createAgentContainer(baseProfile);
            System.out.println("[Main] Base-Container created.");

            // Step 5: Create Field-Containers
            System.out.println("[Main] Step 5: Creating Field-Containers...");
            int numFields = 2; // Start with 2 fields
            fieldContainers = new AgentContainer[numFields];

            for (int i = 1; i <= numFields; i++) {
                Profile fieldProfile = new ProfileImpl();
                fieldProfile.setParameter(Profile.MAIN_HOST, "localhost");
                fieldProfile.setParameter(Profile.CONTAINER_NAME, "Field-Container-" + i);
                fieldContainers[i - 1] = runtime.createAgentContainer(fieldProfile);
                System.out.println("[Main] Field-Container-" + i + " created.");
            }

            // Step 6: Create Field Agents (one per field container)
            System.out.println("[Main] Step 6: Creating Field Agents...");
            String[] cropTypes = { "CORN", "WHEAT" };

            for (int i = 1; i <= numFields; i++) {
                Object[] fieldArgs = new Object[] { i, cropTypes[i - 1], webServer };
                AgentController fieldAgent = fieldContainers[i - 1].createNewAgent(
                        "Field-" + i,
                        "com.smartfarm.agents.FieldAgent",
                        fieldArgs);
                fieldAgent.start();
                System.out.println("[Main] Field-" + i + " agent started (" + cropTypes[i - 1] + ")");
            }

            // Step 7: Create Mobile Agents in Base-Container
            System.out.println("[Main] Step 7: Creating Mobile Agents...");

            // Create Drone agents
            for (int i = 1; i <= 2; i++) {
                Object[] droneArgs = new Object[] { "Drone-" + i, webServer };
                AgentController droneAgent = baseContainer.createNewAgent(
                        "Drone-" + i,
                        "com.smartfarm.agents.DroneAgent",
                        droneArgs);
                droneAgent.start();
                System.out.println("[Main] Drone-" + i + " started");
            }

            // Create Irrigator agent
            Object[] irrigatorArgs = new Object[] { "Irrigator", webServer };
            AgentController irrigator = baseContainer.createNewAgent(
                    "Irrigator",
                    "com.smartfarm.agents.IrrigatorAgent",
                    irrigatorArgs);
            irrigator.start();
            System.out.println("[Main] Irrigator started");

            // Create Harvester agent
            Object[] harvesterArgs = new Object[] { "Harvester", webServer };
            AgentController harvester = baseContainer.createNewAgent(
                    "Harvester",
                    "com.smartfarm.agents.HarvesterAgent",
                    harvesterArgs);
            harvester.start();
            System.out.println("[Main] Harvester started");

            // Create Sprayer agent
            Object[] sprayerArgs = new Object[] { "Sprayer", webServer };
            AgentController sprayer = baseContainer.createNewAgent(
                    "Sprayer",
                    "com.smartfarm.agents.SprayerAgent",
                    sprayerArgs);
            sprayer.start();
            System.out.println("[Main] Sprayer started");

            // Step 8: Create Weather Agent in Main-Container
            System.out.println("[Main] Step 8: Creating Weather Agent...");
            Object[] weatherArgs = new Object[] { webServer };
            AgentController weather = mainContainer.createNewAgent(
                    "Weather",
                    "com.smartfarm.agents.WeatherAgent",
                    weatherArgs);
            weather.start();
            System.out.println("[Main] Weather agent started");

            // Step 9: Create Supplier and Client Agents
            System.out.println("[Main] Step 9: Creating Market Agents...");

            // Water Supplier
            Object[] waterSupArgs = new Object[] { "WaterSupplier", "water", webServer };
            AgentController waterSup = mainContainer.createNewAgent(
                    "WaterSupplier",
                    "com.smartfarm.agents.SupplierAgent",
                    waterSupArgs);
            waterSup.start();
            System.out.println("[Main] WaterSupplier started");

            // Fungicide Supplier
            Object[] fungSupArgs = new Object[] { "FungicideSupplier", "fungicide", webServer };
            AgentController fungSup = mainContainer.createNewAgent(
                    "FungicideSupplier",
                    "com.smartfarm.agents.SupplierAgent",
                    fungSupArgs);
            fungSup.start();
            System.out.println("[Main] FungicideSupplier started");

            // Client Agent (buys crops)
            Object[] clientArgs = new Object[] { "CropBuyer", webServer };
            AgentController client = mainContainer.createNewAgent(
                    "CropBuyer",
                    "com.smartfarm.agents.ClientAgent",
                    clientArgs);
            client.start();
            System.out.println("[Main] CropBuyer started");

            // Step 10: Create BDI Planner Agent
            System.out.println("[Main] Step 10: Creating BDI Planner...");
            Object[] plannerArgs = new Object[] { webServer };
            AgentController planner = mainContainer.createNewAgent(
                    "Planner",
                    "com.smartfarm.agents.PlannerAgent",
                    plannerArgs);
            planner.start();
            System.out.println("[Main] BDI Planner started");

            System.out.println();
            System.out.println("============================================================");
            System.out.println("              SMARTFARM V2 - ALL SYSTEMS READY");
            System.out.println("============================================================");
            System.out.println("  Containers:");
            System.out.println("    Main-Container      (Platform Admin)");
            System.out.println("    Base-Container      (Agent Home)");
            for (int i = 1; i <= numFields; i++) {
                System.out.println("    Field-Container-" + i + "   (Field-" + i + ")");
            }
            System.out.println();
            System.out.println("  Market Agents:");
            System.out.println("    WaterSupplier       (sells water)");
            System.out.println("    FungicideSupplier   (sells fungicide)");
            System.out.println("    CropBuyer           (buys crops)");
            System.out.println();
            System.out.println("  Dashboard: http://localhost:8080");
            System.out.println("============================================================");

            // Register shutdown hook
            java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Main] Shutting down...");
                if (webServer != null) {
                    webServer.stop();
                }
            }));

        } catch (Exception e) {
            System.err.println("[Main] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static WebServer getWebServer() {
        return webServer;
    }

    public static AgentContainer getMainContainer() {
        return mainContainer;
    }

    public static AgentContainer getBaseContainer() {
        return baseContainer;
    }
}
