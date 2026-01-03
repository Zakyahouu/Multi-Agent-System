package com.ecoguard;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import com.ecoguard.web.WebServer;
import com.ecoguard.models.CropType;

/**
 * Main - Entry point for EcoGuardFarm multi-agent system.
 * 
 * Creates:
 * - Main-Container with FarmManager, Drones, Harvesters, Sprayers, Suppliers,
 * Clients
 * - Field-Container-1, 2, 3 with one FieldAgent each
 */
public class Main {

    // Configuration
    private static final int NUM_FIELDS = 3;
    private static final int NUM_DRONES = 2;
    private static final int NUM_HARVESTERS = 1;
    private static final int NUM_SPRAYERS = 1;
    private static final int NUM_SUPPLIERS = 2;
    private static final int NUM_CLIENTS = 2;

    // Container references
    private static ContainerController mainContainer;
    private static ContainerController[] fieldContainers;

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("          ECOGUARDFARM - MULTI-AGENT SYSTEM                 ");
        System.out.println("                Starting All Components...                  ");
        System.out.println("============================================================");
        System.out.println();

        try {
            // Step 1: Start Web Server
            System.out.println("[Main] Step 1: Starting WebServer on port 8080...");
            WebServer.start();
            Thread.sleep(1000);

            // Step 2: Initialize JADE Runtime
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
            System.out.println("[Main] Main-Container created.");
            Thread.sleep(500);

            // Step 4: Create Field-Containers
            System.out.println("[Main] Step 4: Creating " + NUM_FIELDS + " Field-Containers...");
            fieldContainers = new ContainerController[NUM_FIELDS];

            for (int i = 0; i < NUM_FIELDS; i++) {
                Profile fieldProfile = new ProfileImpl();
                fieldProfile.setParameter(Profile.MAIN_HOST, "127.0.0.1");
                fieldProfile.setParameter(Profile.MAIN_PORT, "1099");
                fieldProfile.setParameter(Profile.CONTAINER_NAME, "Field-Container-" + (i + 1));

                fieldContainers[i] = runtime.createAgentContainer(fieldProfile);
                System.out.println("[Main]   Field-Container-" + (i + 1) + " created.");
                Thread.sleep(200);
            }

            // Step 5: Start Agents
            System.out.println("[Main] Step 5: Starting Agents...");
            System.out.println();

            // === MAIN CONTAINER AGENTS ===

            // FarmManagerAgent (BDI)
            System.out.println("[Main] Creating FarmManagerAgent [BDI]...");
            AgentController farmManager = mainContainer.createNewAgent(
                    "FarmManager",
                    "com.ecoguard.agents.FarmManagerAgent",
                    null);
            farmManager.start();
            Thread.sleep(300);

            // DroneAgents (Mobile + AI)
            for (int i = 1; i <= NUM_DRONES; i++) {
                System.out.println("[Main] Creating DroneAgent-" + i + " [MOBILE+AI]...");
                Object[] droneArgs = new Object[] { i };
                AgentController drone = mainContainer.createNewAgent(
                        "Drone-" + i,
                        "com.ecoguard.agents.DroneAgent",
                        droneArgs);
                drone.start();
                Thread.sleep(200);
            }

            // HarvesterAgents (Mobile)
            for (int i = 1; i <= NUM_HARVESTERS; i++) {
                System.out.println("[Main] Creating HarvesterAgent-" + i + " [MOBILE]...");
                Object[] harvesterArgs = new Object[] { i };
                AgentController harvester = mainContainer.createNewAgent(
                        "Harvester-" + i,
                        "com.ecoguard.agents.HarvesterAgent",
                        harvesterArgs);
                harvester.start();
                Thread.sleep(200);
            }

            // SprayerAgents (Mobile)
            for (int i = 1; i <= NUM_SPRAYERS; i++) {
                System.out.println("[Main] Creating SprayerAgent-" + i + " [MOBILE]...");
                Object[] sprayerArgs = new Object[] { i };
                AgentController sprayer = mainContainer.createNewAgent(
                        "Sprayer-" + i,
                        "com.ecoguard.agents.SprayerAgent",
                        sprayerArgs);
                sprayer.start();
                Thread.sleep(200);
            }

            // SupplierAgents (Cognitive)
            for (int i = 1; i <= NUM_SUPPLIERS; i++) {
                System.out.println("[Main] Creating SupplierAgent-" + i + " [COGNITIVE]...");
                Object[] supplierArgs = new Object[] { i };
                AgentController supplier = mainContainer.createNewAgent(
                        "Supplier-" + i,
                        "com.ecoguard.agents.SupplierAgent",
                        supplierArgs);
                supplier.start();
                Thread.sleep(200);
            }

            // ClientAgents (Cognitive)
            for (int i = 1; i <= NUM_CLIENTS; i++) {
                System.out.println("[Main] Creating ClientAgent-" + i + " [COGNITIVE]...");
                Object[] clientArgs = new Object[] { i };
                AgentController client = mainContainer.createNewAgent(
                        "Client-" + i,
                        "com.ecoguard.agents.ClientAgent",
                        clientArgs);
                client.start();
                Thread.sleep(200);
            }

            // === FIELD CONTAINER AGENTS ===
            CropType[] cropTypes = { CropType.CORN, CropType.WHEAT, CropType.RICE };

            for (int i = 0; i < NUM_FIELDS; i++) {
                int fieldId = i + 1;
                CropType cropType = cropTypes[i % cropTypes.length];

                System.out.println(
                        "[Main] Creating FieldAgent-" + fieldId + " [REACTIVE] in Field-Container-" + fieldId + "...");
                Object[] fieldArgs = new Object[] { fieldId, cropType };
                AgentController fieldAgent = fieldContainers[i].createNewAgent(
                        "Field-" + fieldId,
                        "com.ecoguard.agents.FieldAgent",
                        fieldArgs);
                fieldAgent.start();
                Thread.sleep(200);
            }

            // All agents started
            System.out.println();
            System.out.println("============================================================");
            System.out.println("              ALL SYSTEMS OPERATIONAL                       ");
            System.out.println("============================================================");
            System.out.println("  Agents Running:");
            System.out.println("    [BDI]       FarmManager     (Main-Container)");
            for (int i = 1; i <= NUM_DRONES; i++) {
                System.out.println("    [MOBILE]    Drone-" + i + "          (Main-Container)");
            }
            for (int i = 1; i <= NUM_HARVESTERS; i++) {
                System.out.println("    [MOBILE]    Harvester-" + i + "      (Main-Container)");
            }
            for (int i = 1; i <= NUM_SPRAYERS; i++) {
                System.out.println("    [MOBILE]    Sprayer-" + i + "        (Main-Container)");
            }
            for (int i = 1; i <= NUM_SUPPLIERS; i++) {
                System.out.println("    [COGNITIVE] Supplier-" + i + "       (Main-Container)");
            }
            for (int i = 1; i <= NUM_CLIENTS; i++) {
                System.out.println("    [COGNITIVE] Client-" + i + "         (Main-Container)");
            }
            for (int i = 1; i <= NUM_FIELDS; i++) {
                System.out.println("    [REACTIVE]  Field-" + i + "          (Field-Container-" + i + ")");
            }
            System.out.println("============================================================");
            System.out.println("  Dashboard: http://localhost:8080");
            System.out.println("============================================================");

            // Add shutdown hook
            java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Main] Shutting down EcoGuardFarm...");
                WebServer.stop();
                System.out.println("[Main] Goodbye!");
            }));

        } catch (Exception e) {
            System.err.println("[Main] FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Get the main container for agent lookups.
     */
    public static ContainerController getMainContainer() {
        return mainContainer;
    }

    /**
     * Get a field container by ID (1-indexed).
     */
    public static ContainerController getFieldContainer(int fieldId) {
        if (fieldId < 1 || fieldId > fieldContainers.length) {
            return null;
        }
        return fieldContainers[fieldId - 1];
    }
}
