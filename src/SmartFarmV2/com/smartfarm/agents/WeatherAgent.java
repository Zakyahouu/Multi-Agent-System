package com.smartfarm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import com.smartfarm.web.WebServer;

/**
 * WeatherAgent - Controls weather conditions for the entire farm.
 * 
 * Weather cycles: SUNNY → CLOUDY → RAINY → STORM → SUNNY...
 * Effects:
 * - SUNNY: Normal water loss
 * - CLOUDY: Reduced water loss
 * - RAINY: Fields gain moisture
 * - STORM: Fields gain moisture, chance of disease
 */
public class WeatherAgent extends Agent {

    private WebServer webServer;

    // Weather states
    private enum Weather {
        SUNNY("☀️", "Sunny", 0, 1.5),
        CLOUDY("☁️", "Cloudy", 0, 0.8),
        RAINY("🌧️", "Rainy", 5, 0.3),
        STORM("⛈️", "Storm", 10, 0.2);

        private final String icon;
        private final String name;
        private final int moistureBonus; // Added to fields each tick
        private final double evaporationRate; // Multiplier for water loss

        Weather(String icon, String name, int moistureBonus, double evaporationRate) {
            this.icon = icon;
            this.name = name;
            this.moistureBonus = moistureBonus;
            this.evaporationRate = evaporationRate;
        }

        public String getIcon() {
            return icon;
        }

        public String getName() {
            return name;
        }

        public int getMoistureBonus() {
            return moistureBonus;
        }

        public double getEvaporationRate() {
            return evaporationRate;
        }
    }

    private Weather currentWeather = Weather.SUNNY;
    private int weatherDuration = 0;
    private int day = 1;
    private int hour = 6; // Start at 6 AM

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 1) {
            webServer = (WebServer) args[0];
        }

        System.out.println("[Weather] Agent started. Current: " + currentWeather.name);

        // Weather tick every 4 seconds (simulates 1 hour)
        addBehaviour(new WeatherBehaviour(this, 4000));

        broadcastWeather();
        broadcastTime();
    }

    /**
     * Weather behavior - changes weather and time
     */
    private class WeatherBehaviour extends TickerBehaviour {
        public WeatherBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            // Advance time
            hour++;
            if (hour >= 24) {
                hour = 0;
                day++;
                broadcastLog("🌅 Day " + day + " begins!");
            }

            // Update weather duration
            weatherDuration--;

            // Weather change?
            if (weatherDuration <= 0) {
                changeWeather();
            }

            // Send weather effects to fields
            if (currentWeather.getMoistureBonus() > 0) {
                sendMoistureBonus();
            }

            broadcastTime();
        }
    }

    /**
     * Change to a new weather condition
     */
    private void changeWeather() {
        Weather[] weathers = Weather.values();

        // Weighted random: more sunny, less storms
        double rand = Math.random();
        if (rand < 0.35) {
            currentWeather = Weather.SUNNY;
        } else if (rand < 0.60) {
            currentWeather = Weather.CLOUDY;
        } else if (rand < 0.85) {
            currentWeather = Weather.RAINY;
        } else {
            currentWeather = Weather.STORM;
        }

        // Random duration: 3-8 hours
        weatherDuration = 3 + (int) (Math.random() * 5);

        System.out.println("[Weather] Changed to: " + currentWeather.icon + " " + currentWeather.name + " (duration: "
                + weatherDuration + "h)");
        broadcastLog("Weather: " + currentWeather.icon + " " + currentWeather.name);
        broadcastWeather();

        // FIXED: Send evaporation rate to all fields
        sendEvaporationRate();
    }

    /**
     * FIXED: Send evaporation rate to fields
     */
    private void sendEvaporationRate() {
        for (int i = 1; i <= 2; i++) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID("Field-" + i, AID.ISLOCALNAME));
            msg.setContent("WEATHER_EVAP:" + currentWeather.getEvaporationRate());
            send(msg);
        }
        System.out.println("[Weather] Evaporation rate: " + currentWeather.getEvaporationRate() + "x");
    }

    /**
     * Send moisture bonus to fields during rain/storm
     */
    private void sendMoistureBonus() {
        // Send to Field-1 and Field-2
        for (int i = 1; i <= 2; i++) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID("Field-" + i, AID.ISLOCALNAME));
            msg.setContent("WEATHER_MOISTURE:" + currentWeather.getMoistureBonus());
            send(msg);
        }

        if (currentWeather == Weather.RAINY) {
            broadcastLog("🌧️ Rain watering fields (+5%)");
        } else if (currentWeather == Weather.STORM) {
            broadcastLog("⛈️ Storm flooding fields (+10%)");
        }
    }

    /**
     * Broadcast current weather to GUI
     */
    private void broadcastWeather() {
        if (webServer == null)
            return;
        String json = String.format(
                "{\"icon\":\"%s\",\"name\":\"%s\",\"evaporation\":%.1f}",
                currentWeather.getIcon(), currentWeather.getName(), currentWeather.getEvaporationRate());
        webServer.broadcast("WEATHER_UPDATE", json);
    }

    /**
     * Broadcast current time to GUI
     */
    private void broadcastTime() {
        if (webServer == null)
            return;
        String period = (hour >= 6 && hour < 18) ? "AM" : "PM";
        int displayHour = hour % 12;
        if (displayHour == 0)
            displayHour = 12;
        String timeStr = String.format("Day %d - %02d:00", day, hour);

        String json = String.format("{\"day\":%d,\"hour\":%d,\"display\":\"%s\"}", day, hour, timeStr);
        webServer.broadcast("TIME_UPDATE", json);
    }

    private void broadcastLog(String message) {
        if (webServer == null)
            return;
        webServer.broadcast("LOG", "{\"message\":\"" + message + "\"}");
    }

    // Getters for other agents
    public Weather getCurrentWeather() {
        return currentWeather;
    }

    public double getEvaporationRate() {
        return currentWeather.getEvaporationRate();
    }
}
