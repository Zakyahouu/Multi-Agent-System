package com.farm;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

import com.farm.gui.WebServer;
import com.farm.models.CropType;

/**
 * Main V2 - Enhanced Application launcher for the Smart Farm Multi-Agent
 * Simulation.
 * 
 * Startup Sequence:
 * 1. Start Javalin WebServer on port 8080
 * 2. Get JADE Runtime instance
 * 3. Create Main-Container (hosts BDI, Controller, Predictor, Weather, Market,
 * Suppliers)
 * 4. Create Field-Containers dynamically (hosts Sensors and CropGrowth agents)
 * 5. Initialize and start all agents
 * 
 * Agent Types Demonstrated:
 * - BDI: FarmerBDIAgent
 * - AI: PredictorAgent
 * - Reactive: WeatherServiceAgent, SoilSensorAgent
 * - Hybrid: FarmControllerAgent, CropGrowthAgent
 * - Cognitive: MarketAgent, SupplierAgent
 * - Mobile: InspectorDroneAgent, HarvesterAgent
 */
public class Main {

    private static ContainerController mainContainer;
    private static ContainerController[] fieldContainers;

    // Configuration (can be changed via command line)
    private static int numFields = 3;
    private static int numDrones = 2;
    private static int numWaterSuppliers = 2;

    public static void main(String[] args) {
        // Parse command line arguments
        parseArguments(args);

        System.out.println("============================================================");
        System.out.println("        SMART FARM V2 MULTI-AGENT SIMULATION                ");
        System.out.println("              Starting System Components...                 ");
        System.out.println("============================================================");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  - Fields: " + numFields);
        System.out.println("  - Drones: " + numDrones);
        System.out.println("  - Water Suppliers: " + numWaterSuppliers);
        System.out.println();

        try {
            // Step 1: Start Web Server
            System.out.println("[Main] Step 1: Starting Web Server...");
            WebServer.start();
            Thread.sleep(1000);

            // Step 2: Get JADE Runtime
            System.out.println("[Main] Step 2: Initializing JADE Runtime...");
            Runtime runtime = Runtime.instance();

            // Step 3: Create Main-Container
            System.out.println("[Main] Step 3: Creating Main-Container...");
            Profile mainProfile = new ProfileImpl();
            mainProfile.setParameter(Profile.MAIN_HOST, "127.0.0.1");
            mainProfile.setParameter(Profile.MAIN_PORT, "1099");
            mainProfile.setParameter(Profile.GUI, "false");
            mainProfile.setParameter(Profile.CONTAINER_NAME, "Main-Container");

            mainContainer = runtime.createMainContainer(mainProfile);
            System.out.println("[Main] Main-Container created successfully.");
            Thread.sleep(500);

            // Step 4: Create Field-Containers
            System.out.println("[Main] Step 4: Creating " + numFields + " Field-Containers...");
            fieldContainers = new ContainerController[numFields];

            for (int i = 0; i < numFields; i++) {
                Profile fieldProfile = new ProfileImpl();
                fieldProfile.setParameter(Profile.MAIN_HOST, "127.0.0.1");
                fieldProfile.setParameter(Profile.MAIN_PORT, "1099");
                fieldProfile.setParameter(Profile.CONTAINER_NAME, "Field-Container-" + (i + 1));

                fieldContainers[i] = runtime.createAgentContainer(fieldProfile);
                System.out.println("[Main] Field-Container-" + (i + 1) + " created.");
                Thread.sleep(200);
            }

            // Also create the generic Field-Container for backward compatibility
            Profile fieldProfile = new ProfileImpl();
            fieldProfile.setParameter(Profile.MAIN_HOST, "127.0.0.1");
            fieldProfile.setParameter(Profile.MAIN_PORT, "1099");
            fieldProfile.setParameter(Profile.CONTAINER_NAME, "Field-Container");
            ContainerController genericFieldContainer = runtime.createAgentContainer(fieldProfile);
            System.out.println("[Main] Generic Field-Container created.");
            Thread.sleep(300);

            // Step 5: Create and start agents
            System.out.println("[Main] Step 5: Starting Agents...");
            System.out.println();

            // === MAIN CONTAINER AGENTS ===

            // FarmerBDI Agent (BDI Architecture)
            System.out.println("[Main] Creating FarmerBDIAgent [BDI]...");
            AgentController farmerBDI = mainContainer.createNewAgent(
                    "FarmerBDI",
                    "com.farm.agents.FarmerBDIAgent",
                    null);
            farmerBDI.start();
            System.out.println("[Main] FarmerBDIAgent started.");
            Thread.sleep(300);

            // Predictor Agent (AI)
            System.out.println("[Main] Creating PredictorAgent [AI]...");
            AgentController predictor = mainContainer.createNewAgent(
                    "Predictor",
                    "com.farm.agents.PredictorAgent",
                    null);
            predictor.start();
            System.out.println("[Main] PredictorAgent started.");
            Thread.sleep(300);

            // Weather Service Agent (Reactive)
            System.out.println("[Main] Creating WeatherServiceAgent [REACTIVE]...");
            AgentController weather = mainContainer.createNewAgent(
                    "Weather",
                    "com.farm.agents.WeatherServiceAgent",
                    null);
            weather.start();
            System.out.println("[Main] WeatherServiceAgent started.");
            Thread.sleep(300);

            // Farm Controller Agent (Hybrid)
            System.out.println("[Main] Creating FarmControllerAgent [HYBRID]...");
            AgentController controller = mainContainer.createNewAgent(
                    "Controller",
                    "com.farm.agents.FarmControllerAgent",
                    null);
            controller.start();
            System.out.println("[Main] FarmControllerAgent started.");
            Thread.sleep(300);

            // Harvester Agent (Mobile)
            System.out.println("[Main] Creating HarvesterAgent [MOBILE]...");
            AgentController harvester = mainContainer.createNewAgent(
                    "Harvester",
                    "com.farm.agents.HarvesterAgent",
                    null);
            harvester.start();
            System.out.println("[Main] HarvesterAgent started.");
            Thread.sleep(300);

            // Inspector Drones (Mobile)
            for (int i = 1; i <= numDrones; i++) {
                System.out.println("[Main] Creating InspectorDroneAgent-" + i + " [MOBILE]...");
                AgentController drone = mainContainer.createNewAgent(
                        "Drone-" + i,
                        "com.farm.agents.InspectorDroneAgent",
                        null);
                drone.start();
                System.out.println("[Main] InspectorDroneAgent-" + i + " started.");
                Thread.sleep(200);
            }

            // Water Suppliers (Cognitive)
            for (int i = 1; i <= numWaterSuppliers; i++) {
                System.out.println("[Main] Creating SupplierAgent-" + i + " [COGNITIVE]...");
                Object[] supplierArgs = new Object[] { "water" };
                AgentController supplier = mainContainer.createNewAgent(
                        "Supplier-" + i,
                        "com.farm.agents.SupplierAgent",
                        supplierArgs);
                supplier.start();
                System.out.println("[Main] SupplierAgent-" + i + " started.");
                Thread.sleep(200);
            }

            // === FIELD CONTAINER AGENTS ===

            CropType[] cropTypes = { CropType.WHEAT, CropType.CORN, CropType.VEGETABLES, CropType.RICE, CropType.WHEAT,
                    CropType.CORN };

            for (int i = 0; i < numFields; i++) {
                int fieldId = i + 1;
                CropType cropType = cropTypes[i % cropTypes.length];

                // Soil Sensor Agent (Reactive) - in field container
                System.out.println("[Main] Creating SoilSensorAgent-" + fieldId + " [REACTIVE] in Field-Container-"
                        + fieldId + "...");
                Object[] sensorArgs = new Object[] { fieldId };
                AgentController sensor = fieldContainers[i].createNewAgent(
                        "Sensor-" + fieldId,
                        "com.farm.agents.SoilSensorAgent",
                        sensorArgs);
                sensor.start();
                Thread.sleep(200);

                // Crop Growth Agent (Hybrid) - in field container
                System.out.println("[Main] Creating CropGrowthAgent-" + fieldId + " [HYBRID] in Field-Container-"
                        + fieldId + "...");
                Object[] cropArgs = new Object[] { fieldId, cropType };
                AgentController cropGrowth = fieldContainers[i].createNewAgent(
                        "CropGrowth-" + fieldId,
                        "com.farm.agents.CropGrowthAgent",
                        cropArgs);
                cropGrowth.start();
                Thread.sleep(200);
            }

            // All agents started
            System.out.println();
            System.out.println("============================================================");
            System.out.println("          ALL SYSTEMS OPERATIONAL                          ");
            System.out.println("                                                            ");
            System.out.println("   Agents Running:                                          ");
            System.out.println("     [BDI] FarmerBDI (Main-Container)                       ");
            System.out.println("     [AI] Predictor (Main-Container)                        ");
            System.out.println("     [REACTIVE] Weather (Main-Container)                    ");
            System.out.println("     [HYBRID] Controller (Main-Container)                   ");
            System.out.println("     [MOBILE] Harvester (Main-Container)                    ");
            for (int i = 1; i <= numDrones; i++) {
                System.out.println("     [MOBILE] Drone-" + i + " (Main-Container)                         ");
            }
            for (int i = 1; i <= numWaterSuppliers; i++) {
                System.out.println("     [COGNITIVE] Supplier-" + i + " (Main-Container)                   ");
            }
            for (int i = 1; i <= numFields; i++) {
                System.out.println("     [REACTIVE] Sensor-" + i + " (Field-Container-" + i + ")                ");
                System.out.println("     [HYBRID] CropGrowth-" + i + " (Field-Container-" + i + ")              ");
            }
            System.out.println("                                                            ");
            System.out.println("   Open http://localhost:8080 to view the dashboard         ");
            System.out.println("                                                            ");
            System.out.println("   Press Ctrl+C to stop the simulation                      ");
            System.out.println("============================================================");

            // Add shutdown hook for clean exit
            java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Main] Shutting down Smart Farm V2...");
                WebServer.stop();
                System.out.println("[Main] Goodbye!");
            }));

            // Keep main thread alive
            while (true) {
                Thread.sleep(1000);
            }

        } catch (StaleProxyException e) {
            System.err.println("[Main] JADE Agent Error: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("[Main] Interrupted, shutting down...");
        } catch (Exception e) {
            System.err.println("[Main] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--fields":
                case "-f":
                    if (i + 1 < args.length) {
                        numFields = Integer.parseInt(args[++i]);
                        numFields = Math.max(1, Math.min(6, numFields));
                    }
                    break;
                case "--drones":
                case "-d":
                    if (i + 1 < args.length) {
                        numDrones = Integer.parseInt(args[++i]);
                        numDrones = Math.max(1, Math.min(3, numDrones));
                    }
                    break;
                case "--suppliers":
                case "-s":
                    if (i + 1 < args.length) {
                        numWaterSuppliers = Integer.parseInt(args[++i]);
                        numWaterSuppliers = Math.max(1, Math.min(4, numWaterSuppliers));
                    }
                    break;
                case "--help":
                case "-h":
                    printHelp();
                    System.exit(0);
            }
        }
    }

    private static void printHelp() {
        System.out.println("Smart Farm V2 - Multi-Agent Simulation");
        System.out.println();
        System.out.println("Usage: java com.farm.Main [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -f, --fields <n>     Number of fields (1-6, default: 3)");
        System.out.println("  -d, --drones <n>     Number of drones (1-3, default: 2)");
        System.out.println("  -s, --suppliers <n>  Number of water suppliers (1-4, default: 2)");
        System.out.println("  -h, --help           Show this help message");
    }
}
