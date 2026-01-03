package com.ecoguard.web;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebServer - Javalin server for WebSocket communication with frontend.
 * 
 * All GUI updates are pushed from backend via WebSocket.
 * Frontend is completely passive (no timers, no simulation).
 */
public class WebServer {

    private static Javalin app;
    private static Set<WsContext> clients = ConcurrentHashMap.newKeySet();
    private static volatile boolean isRunning = false;

    /**
     * Start the web server on port 8080.
     */
    public static void start() {
        if (isRunning) {
            System.out.println("[WebServer] Already running.");
            return;
        }

        app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        });

        // WebSocket endpoint
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                clients.add(ctx);
                System.out.println("[WebServer] Client connected. Total: " + clients.size());

                // Send welcome message
                ctx.send("{\"type\":\"CONNECTION\",\"data\":{\"message\":\"Welcome to EcoGuardFarm!\"},\"timestamp\":\""
                        +
                        java.time.Instant.now() + "\"}");
            });

            ws.onClose(ctx -> {
                clients.remove(ctx);
                System.out.println("[WebServer] Client disconnected. Total: " + clients.size());
            });

            ws.onError(ctx -> {
                System.err.println("[WebServer] WebSocket error: " + ctx.error());
            });
        });

        app.start(8080);
        isRunning = true;
        System.out.println("[WebServer] Started on http://localhost:8080");
        System.out.println("[WebServer] WebSocket at ws://localhost:8080/ws");
    }

    /**
     * Stop the web server.
     */
    public static void stop() {
        if (app != null) {
            try {
                broadcast("SERVER_SHUTDOWN", "{\"message\":\"Server shutting down\"}");
                Thread.sleep(100);
            } catch (Exception e) {
                // Ignore
            }
            app.stop();
            isRunning = false;
            System.out.println("[WebServer] Stopped.");
        }
    }

    /**
     * Broadcast a message to all connected clients.
     * 
     * @param type Event type (e.g., FIELD_UPDATE, DRONE_MOVE)
     * @param data JSON data payload
     */
    public static void broadcast(String type, String data) {
        if (!isRunning || clients.isEmpty()) {
            return;
        }

        String message = String.format(
                "{\"type\":\"%s\",\"data\":%s,\"timestamp\":\"%s\"}",
                type, data, java.time.Instant.now());

        for (WsContext client : clients) {
            try {
                client.send(message);
            } catch (Exception e) {
                // Client may have disconnected
                clients.remove(client);
            }
        }
    }

    /**
     * Get the number of connected clients.
     */
    public static int getClientCount() {
        return clients.size();
    }

    /**
     * Check if server is running.
     */
    public static boolean isRunning() {
        return isRunning;
    }
}
