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

import java.util.*;

/**
 * WeatherServiceAgent - Reactive agent that simulates weather conditions
 * and broadcasts updates to subscribed agents using FIPA-Subscribe protocol.
 * 
 * Weather States:
 * - CLEAR (0): No rain, high evaporation
 * - CLOUDY (1): Moderate conditions
 * - RAIN (2): Natural irrigation, reduced water need
 * - STORM (3): Heavy rain, possible damage
 * 
 * Agent Type: REACTIVE
 */
public class WeatherServiceAgent extends Agent {

    // Weather states
    public enum Weather {
        CLEAR("Clear", "☀️", 0),
        CLOUDY("Cloudy", "⛅", 1),
        RAIN("Rain", "🌧️", 2),
        STORM("Storm", "⛈️", 3);

        private final String name;
        private final String emoji;
        private final int code;

        Weather(String name, String emoji, int code) {
            this.name = name;
            this.emoji = emoji;
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public String getEmoji() {
            return emoji;
        }

        public int getCode() {
            return code;
        }
    }

    private Weather currentWeather = Weather.CLEAR;
    private List<AID> subscribers = new ArrayList<>();
    private Random random = new Random();

    @Override
    protected void setup() {
        System.out.println("[WeatherService] Reactive agent starting...");

        // Register with DF
        registerWithDF();

        // Add weather simulation behavior
        addBehaviour(new WeatherSimulation(this, 10000)); // Update every 10 seconds

        // Add subscription handler
        addBehaviour(new SubscriptionHandler());

        System.out.println("[WeatherService] Weather service active. Current: " + currentWeather.getName());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // Ignore
        }
        System.out.println("[WeatherService] Agent terminated.");
    }

    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("weather-service");
            sd.setName("Weather-Broadcaster");
            dfd.addServices(sd);
            DFService.register(this, dfd);
            System.out.println("[WeatherService] Registered with DF as 'weather-service'");
        } catch (FIPAException e) {
            System.err.println("[WeatherService] DF registration failed: " + e.getMessage());
        }
    }

    /**
     * Simulates weather changes over time.
     */
    private class WeatherSimulation extends TickerBehaviour {
        public WeatherSimulation(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            // 30% chance of weather change
            if (random.nextDouble() < 0.3) {
                changeWeather();
            }

            // Broadcast weather to all subscribers
            broadcastWeather();
        }
    }

    private void changeWeather() {
        Weather oldWeather = currentWeather;

        // Weather transition probabilities
        double rand = random.nextDouble();

        switch (currentWeather) {
            case CLEAR:
                if (rand < 0.6)
                    currentWeather = Weather.CLEAR;
                else if (rand < 0.9)
                    currentWeather = Weather.CLOUDY;
                else
                    currentWeather = Weather.RAIN;
                break;

            case CLOUDY:
                if (rand < 0.3)
                    currentWeather = Weather.CLEAR;
                else if (rand < 0.6)
                    currentWeather = Weather.CLOUDY;
                else if (rand < 0.9)
                    currentWeather = Weather.RAIN;
                else
                    currentWeather = Weather.STORM;
                break;

            case RAIN:
                if (rand < 0.2)
                    currentWeather = Weather.CLEAR;
                else if (rand < 0.5)
                    currentWeather = Weather.CLOUDY;
                else if (rand < 0.8)
                    currentWeather = Weather.RAIN;
                else
                    currentWeather = Weather.STORM;
                break;

            case STORM:
                if (rand < 0.4)
                    currentWeather = Weather.RAIN;
                else if (rand < 0.7)
                    currentWeather = Weather.CLOUDY;
                else
                    currentWeather = Weather.STORM;
                break;
        }

        if (currentWeather != oldWeather) {
            System.out.println(
                    "[WeatherService] Weather changed: " + oldWeather.getName() + " -> " + currentWeather.getName());

            // Log interaction
            WebServer.broadcastAgentInteraction(
                    "Weather", "reactive",
                    "All Agents", "all",
                    "INFORM", "Weather changed to " + currentWeather.getName(), 1);
        }
    }

    private void broadcastWeather() {
        // Create weather update message
        String weatherData = String.format(
                "{\"weather\":\"%s\",\"emoji\":\"%s\",\"code\":%d,\"rainChance\":%d}",
                currentWeather.getName(),
                currentWeather.getEmoji(),
                currentWeather.getCode(),
                calculateRainChance());

        // Send to all subscribers
        for (AID subscriber : subscribers) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(subscriber);
            msg.setContent("WEATHER:" + currentWeather.getName());
            msg.setConversationId("weather-update");
            send(msg);
        }

        // Also send to known important agents
        sendToAgent("FarmerBDI");
        sendToAgent("Controller");
        sendToAgent("Predictor");

        // Broadcast to GUI
        WebServer.broadcast("WEATHER_UPDATE", weatherData);
    }

    private void sendToAgent(String agentName) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        msg.setContent("WEATHER:" + currentWeather.getName());
        msg.setConversationId("weather-update");
        send(msg);
    }

    private int calculateRainChance() {
        switch (currentWeather) {
            case CLEAR:
                return 10;
            case CLOUDY:
                return 40;
            case RAIN:
                return 80;
            case STORM:
                return 95;
            default:
                return 20;
        }
    }

    /**
     * Handles subscription requests from other agents.
     */
    private class SubscriptionHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.SUBSCRIBE);
            ACLMessage msg = receive(mt);

            if (msg != null) {
                AID subscriber = msg.getSender();
                if (!subscribers.contains(subscriber)) {
                    subscribers.add(subscriber);
                    System.out.println("[WeatherService] New subscriber: " + subscriber.getLocalName());

                    // Send current weather immediately
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.AGREE);
                    reply.setContent("WEATHER:" + currentWeather.getName());
                    send(reply);

                    // Log interaction
                    WebServer.broadcastAgentInteraction(
                            subscriber.getLocalName(), "unknown",
                            "Weather", "reactive",
                            "SUBSCRIBE", "Subscribed to weather updates", 1);
                }
            } else {
                // Also handle QUERY messages
                MessageTemplate queryMt = MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF);
                ACLMessage queryMsg = receive(queryMt);

                if (queryMsg != null) {
                    ACLMessage reply = queryMsg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("WEATHER:" + currentWeather.getName());
                    send(reply);
                } else {
                    block();
                }
            }
        }
    }

    // ==================== PUBLIC METHODS ====================

    public Weather getCurrentWeather() {
        return currentWeather;
    }

    public boolean isRaining() {
        return currentWeather == Weather.RAIN || currentWeather == Weather.STORM;
    }

    public int getRainChance() {
        return calculateRainChance();
    }
}
