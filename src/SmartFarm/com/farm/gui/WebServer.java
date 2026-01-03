package com.farm.gui;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebServer V2 - Enhanced Javalin-based web server with advanced agent
 * visualization support.
 * 
 * Features:
 * - HTTP server on port 8080
 * - Static file serving from /public folder
 * - WebSocket endpoint at /stream for real-time updates
 * - Agent interaction logging with type badges
 * - GUI command system for agent-controlled elements
 * - BDI mind viewer updates
 * - AI prediction updates
 * - Economic tracking
 */
public class WebServer {

    private static Javalin app;
    private static final Set<WsContext> connectedClients = ConcurrentHashMap.newKeySet();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private static boolean isRunning = false;

    // Simulation state
    private static int simulationSpeed = 1;
    private static boolean simulationRunning = false;

    /**
     * Start the web server on port 8080.
     */
    public static void start() {
        if (isRunning) {
            System.out.println("[WebServer] Already running!");
            return;
        }

        app = Javalin.create(config -> {
            // Serve static files from resources/public folder
            config.staticFiles.add(staticConfig -> {
                staticConfig.hostedPath = "/";
                staticConfig.directory = "/public";
                staticConfig.location = Location.CLASSPATH;
            });

            // Also try external path for development
            config.staticFiles.add(staticConfig -> {
                staticConfig.hostedPath = "/";
                staticConfig.directory = "src/SmartFarm/resources/public";
                staticConfig.location = Location.EXTERNAL;
            });
        });

        // WebSocket endpoint for real-time updates
        app.ws("/stream", ws -> {
            ws.onConnect(ctx -> {
                connectedClients.add(ctx);
                System.out.println("[WebServer] Client connected. Total clients: " + connectedClients.size());

                // Send welcome message
                String welcomeMsg = buildJsonMessage("CONNECTION",
                        "{\"status\":\"connected\",\"message\":\"Welcome to Smart Farm V2!\",\"clients\":"
                                + connectedClients.size() + "}");
                ctx.send(welcomeMsg);
            });

            ws.onClose(ctx -> {
                connectedClients.remove(ctx);
                System.out.println("[WebServer] Client disconnected. Total clients: " + connectedClients.size());
            });

            ws.onMessage(ctx -> {
                String message = ctx.message();
                System.out.println("[WebServer] Received from client: " + message);
                handleClientMessage(message);
            });

            ws.onError(ctx -> {
                System.err.println("[WebServer] WebSocket error: " + ctx.error());
                connectedClients.remove(ctx);
            });
        });

        // API endpoint for system info
        app.get("/api/status", ctx -> {
            ctx.json(new SystemStatus(connectedClients.size(), isRunning));
        });

        // Start server
        app.start(8080);
        isRunning = true;

        System.out.println("============================================================");
        System.out.println("           SMART FARM V2 WEB SERVER STARTED                 ");
        System.out.println("                                                            ");
        System.out.println("   Dashboard: http://localhost:8080                         ");
        System.out.println("   WebSocket: ws://localhost:8080/stream                    ");
        System.out.println("                                                            ");
        System.out.println("============================================================");
    }

    /**
     * Handle messages from the client (commands)
     */
    private static void handleClientMessage(String message) {
        try {
            // Simple JSON parsing for commands
            if (message.contains("START_SIMULATION")) {
                simulationRunning = true;
                System.out.println("[WebServer] Simulation started");
            } else if (message.contains("PAUSE_SIMULATION")) {
                simulationRunning = false;
                System.out.println("[WebServer] Simulation paused");
            } else if (message.contains("SET_SPEED")) {
                // Extract speed from message
                if (message.contains("\"speed\":1"))
                    simulationSpeed = 1;
                else if (message.contains("\"speed\":2"))
                    simulationSpeed = 2;
                else if (message.contains("\"speed\":5"))
                    simulationSpeed = 5;
                System.out.println("[WebServer] Simulation speed: " + simulationSpeed + "x");
            }
        } catch (Exception e) {
            System.err.println("[WebServer] Error handling client message: " + e.getMessage());
        }
    }

    /**
     * Stop the web server.
     */
    public static void stop() {
        if (app != null && isRunning) {
            // Notify all clients
            broadcast("SERVER_SHUTDOWN", "{\"message\":\"Server shutting down...\"}");

            app.stop();
            isRunning = false;
            System.out.println("[WebServer] Server stopped.");
        }
    }

    /**
     * Broadcast a message to all connected WebSocket clients.
     * This is the bridge method that agents call to send events to the UI.
     * 
     * @param eventType Type of event (e.g., "MOISTURE_READING", "DRONE_MOVING",
     *                  "CNP_PROPOSAL")
     * @param data      JSON data string
     */
    public static void broadcast(String eventType, String data) {
        if (!isRunning || connectedClients.isEmpty()) {
            return;
        }

        String jsonMessage = buildJsonMessage(eventType, data);

        // Thread-safe iteration and sending
        connectedClients.removeIf(ctx -> {
            try {
                ctx.send(jsonMessage);
                return false; // Keep this client
            } catch (Exception e) {
                System.err.println("[WebServer] Failed to send to client, removing: " + e.getMessage());
                return true; // Remove this client
            }
        });
    }

    // ==================== NEW V2 METHODS ====================

    /**
     * Broadcast an agent interaction with full type information.
     * Used for visualizing agent communication in the interaction graph.
     */
    public static void broadcastAgentInteraction(
            String fromAgent, String fromType,
            String toAgent, String toType,
            String messageType, String content, int step) {

        String data = String.format(
                "{\"from\":\"%s\",\"fromType\":\"%s\",\"to\":\"%s\",\"toType\":\"%s\",\"messageType\":\"%s\",\"content\":\"%s\",\"step\":%d}",
                fromAgent, fromType, toAgent, toType, messageType, escapeJson(content), step);
        broadcast("AGENT_INTERACTION", data);
    }

    /**
     * Update the BDI mind viewer with current beliefs, desires, and intentions.
     */
    public static void broadcastBDIUpdate(String[] beliefs, String[] desires, String[] intentions) {
        StringBuilder data = new StringBuilder("{");

        data.append("\"beliefs\":[");
        for (int i = 0; i < beliefs.length; i++) {
            data.append("\"").append(escapeJson(beliefs[i])).append("\"");
            if (i < beliefs.length - 1)
                data.append(",");
        }
        data.append("],");

        data.append("\"desires\":[");
        for (int i = 0; i < desires.length; i++) {
            data.append("\"").append(escapeJson(desires[i])).append("\"");
            if (i < desires.length - 1)
                data.append(",");
        }
        data.append("],");

        data.append("\"intentions\":[");
        for (int i = 0; i < intentions.length; i++) {
            data.append("\"").append(escapeJson(intentions[i])).append("\"");
            if (i < intentions.length - 1)
                data.append(",");
        }
        data.append("]}");

        broadcast("BDI_UPDATE", data.toString());
    }

    /**
     * Update a field's state in the GUI.
     */
    public static void broadcastFieldUpdate(int fieldId, String crop, int moisture, int growth,
            String stage, boolean sprinklerOn) {
        String data = String.format(
                "{\"id\":%d,\"crop\":\"%s\",\"moisture\":%d,\"growth\":%d,\"stage\":\"%s\",\"sprinklerOn\":%b}",
                fieldId, crop, moisture, growth, stage, sprinklerOn);
        broadcast("FIELD_UPDATE", data);
    }

    /**
     * Update AI prediction display.
     */
    public static void broadcastPrediction(int predictedLiters, int confidence, int trainingSamples) {
        String data = String.format(
                "{\"prediction\":%d,\"confidence\":%d,\"samples\":%d}",
                predictedLiters, confidence, trainingSamples);
        broadcast("PREDICTION_UPDATE", data);
    }

    /**
     * Update economic statistics.
     */
    public static void broadcastEconomy(double income, double expenses) {
        String data = String.format(
                "{\"income\":%.2f,\"expenses\":%.2f}",
                income, expenses);
        broadcast("ECONOMY_UPDATE", data);
    }

    /**
     * Broadcast drone movement for animation.
     */
    public static void broadcastDroneMove(String droneId, String from, String to, String state) {
        String data = String.format(
                "{\"droneId\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"state\":\"%s\"}",
                droneId, from, to, state);
        broadcast("DRONE_MOVE", data);
    }

    /**
     * Broadcast supplier market update.
     */
    public static void broadcastSuppliers(String suppliersJson) {
        broadcast("SUPPLIER_UPDATE", suppliersJson);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Build a JSON message with type, data, and timestamp.
     */
    private static String buildJsonMessage(String eventType, String data) {
        String timestamp = dateFormat.format(new Date());
        return "{\"type\":\"" + eventType + "\",\"data\":" + data + ",\"timestamp\":\"" + timestamp + "\"}";
    }

    /**
     * Escape special characters in JSON strings.
     */
    private static String escapeJson(String input) {
        if (input == null)
            return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Get the number of connected clients.
     */
    public static int getClientCount() {
        return connectedClients.size();
    }

    /**
     * Check if server is running.
     */
    public static boolean isServerRunning() {
        return isRunning;
    }

    /**
     * Get current simulation speed multiplier.
     */
    public static int getSimulationSpeed() {
        return simulationSpeed;
    }

    /**
     * Check if simulation is running.
     */
    public static boolean isSimulationRunning() {
        return simulationRunning;
    }

    /**
     * Simple status class for JSON serialization.
     */
    static class SystemStatus {
        public int connectedClients;
        public boolean serverRunning;
        public String timestamp;
        public String version = "2.0";

        public SystemStatus(int clients, boolean running) {
            this.connectedClients = clients;
            this.serverRunning = running;
            this.timestamp = dateFormat.format(new Date());
        }
    }
}
