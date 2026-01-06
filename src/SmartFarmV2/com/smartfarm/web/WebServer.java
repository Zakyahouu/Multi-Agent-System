package com.smartfarm.web;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import io.javalin.http.staticfiles.Location;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebServer - Javalin server for SmartFarm V2 GUI
 * 
 * Handles WebSocket connections and broadcasts farm state to all connected
 * clients.
 */
public class WebServer {

    private Javalin app;
    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();

    public void start(int port) {
        app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
        });

        // WebSocket endpoint
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                clients.add(ctx);
                System.out.println("[WebServer] Client connected. Total: " + clients.size());
                // Send initial state
                ctx.send("{\"type\":\"CONNECTED\",\"message\":\"SmartFarm V2 Connected!\"}");
            });

            ws.onClose(ctx -> {
                clients.remove(ctx);
                System.out.println("[WebServer] Client disconnected. Total: " + clients.size());
            });

            ws.onMessage(ctx -> {
                try {
                    String msg = ctx.message();
                    System.out.println("[WebServer] Received: " + msg);
                    
                    if (msg.contains("BUY_FIELD")) {
                        // Extract payload nicely
                        String payload = "WHEAT"; // Default
                        if (msg.contains("CORN")) payload = "CORN";
                        else if (msg.contains("EXPERIMENTAL")) payload = "EXPERIMENTAL";
                        else if (msg.contains("WHEAT")) payload = "WHEAT";
                        
                        System.out.println("[WebServer] Routing command BUY_FIELD:" + payload + " to FarmManager");
                        
                        // Send to FarmManager via O2A
                        jade.wrapper.AgentController ac = com.smartfarm.Main.getFarmManager();
                        if (ac != null) {
                            ac.putO2AObject("BUY_FIELD:" + payload, jade.wrapper.AgentController.ASYNC);
                        } else {
                            System.err.println("[WebServer] FarmManager AgentController is null!");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[WebServer] Error handling message: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            ws.onError(ctx -> {
                System.err.println("[WebServer] WebSocket error: " + ctx.error());
            });
        });

        app.start(port);
        System.out.println("[WebServer] Started on http://localhost:" + port);
        System.out.println("[WebServer] WebSocket at ws://localhost:" + port + "/ws");
    }

    public void stop() {
        if (app != null) {
            app.stop();
            System.out.println("[WebServer] Stopped.");
        }
    }

    /**
     * Broadcast a message to all connected clients.
     */
    public void broadcast(String message) {
        for (WsContext client : clients) {
            try {
                client.send(message);
            } catch (Exception e) {
                System.err.println("[WebServer] Failed to send to client: " + e.getMessage());
            }
        }
    }

    /**
     * Broadcast a typed JSON message.
     */
    public void broadcast(String type, String data) {
        String json = String.format("{\"type\":\"%s\",\"data\":%s}", type, data);
        broadcast(json);
    }

    /**
     * Get the number of connected clients.
     */
    public int getClientCount() {
        return clients.size();
    }
}
