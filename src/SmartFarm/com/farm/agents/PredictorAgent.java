package com.farm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;

import com.farm.gui.WebServer;

import java.util.*;

/**
 * PredictorAgent - AI-powered agent that uses a simple neural network
 * to predict irrigation needs based on historical field data.
 * 
 * Neural Network Architecture:
 * - Input Layer: 4 neurons (moisture, growth, weather_code, season_code)
 * - Hidden Layer: 6 neurons (with ReLU activation)
 * - Output Layer: 1 neuron (predicted water need in liters)
 * 
 * Agent Type: AI/Cognitive
 */
public class PredictorAgent extends Agent {

    // Neural Network weights
    private double[][] inputToHidden; // 4x6 matrix
    private double[] hiddenBias; // 6 biases
    private double[] hiddenToOutput; // 6x1 matrix
    private double outputBias;

    // Training data
    private List<double[]> trainingInputs = new ArrayList<>();
    private List<Double> trainingOutputs = new ArrayList<>();
    private int trainingSamples = 0;
    private int trainingEpochs = 0;

    // Prediction state
    private int lastPrediction = 0;
    private int confidence = 50; // Starts at 50%

    // Learning rate
    private static final double LEARNING_RATE = 0.01;

    @Override
    protected void setup() {
        System.out.println("[PredictorAgent] AI Agent starting...");
        System.out.println("[PredictorAgent] Initializing neural network...");

        // Initialize neural network with random weights
        initializeNetwork();

        // Generate some initial training data
        generateInitialTrainingData();

        // Train the network
        trainNetwork(100);

        // Register with DF
        registerWithDF();

        // Add prediction behavior
        addBehaviour(new PredictionCycle(this, 5000));

        // Add learning behavior (receives field data to train on)
        addBehaviour(new LearningBehaviour());

        System.out.println("[PredictorAgent] Neural network ready. Trained on " + trainingSamples + " samples.");
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // Ignore
        }
        System.out.println("[PredictorAgent] AI Agent terminated.");
    }

    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("prediction-service");
            sd.setName("AI-Predictor");
            dfd.addServices(sd);
            DFService.register(this, dfd);
            System.out.println("[PredictorAgent] Registered with DF as 'prediction-service'");
        } catch (FIPAException e) {
            System.err.println("[PredictorAgent] DF registration failed: " + e.getMessage());
        }
    }

    // ==================== NEURAL NETWORK ====================

    private void initializeNetwork() {
        Random rand = new Random(42); // Seed for reproducibility

        // Initialize weights with small random values
        inputToHidden = new double[4][6];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 6; j++) {
                inputToHidden[i][j] = (rand.nextDouble() - 0.5) * 0.5;
            }
        }

        hiddenBias = new double[6];
        for (int i = 0; i < 6; i++) {
            hiddenBias[i] = (rand.nextDouble() - 0.5) * 0.2;
        }

        hiddenToOutput = new double[6];
        for (int i = 0; i < 6; i++) {
            hiddenToOutput[i] = (rand.nextDouble() - 0.5) * 0.5;
        }

        outputBias = (rand.nextDouble() - 0.5) * 0.2;
    }

    /**
     * Forward pass through the network.
     * 
     * @param input [moisture%, growth%, weatherCode, seasonCode]
     * @return predicted water need in liters
     */
    private double predict(double[] input) {
        // Normalize inputs
        double[] normalizedInput = normalize(input);

        // Input to hidden layer
        double[] hidden = new double[6];
        for (int j = 0; j < 6; j++) {
            double sum = hiddenBias[j];
            for (int i = 0; i < 4; i++) {
                sum += normalizedInput[i] * inputToHidden[i][j];
            }
            hidden[j] = relu(sum);
        }

        // Hidden to output
        double output = outputBias;
        for (int j = 0; j < 6; j++) {
            output += hidden[j] * hiddenToOutput[j];
        }

        // Denormalize output (scale to 0-200 liters range)
        return Math.max(0, Math.min(200, output * 100 + 50));
    }

    private double[] normalize(double[] input) {
        return new double[] {
                input[0] / 100.0, // moisture 0-100 -> 0-1
                input[1] / 100.0, // growth 0-100 -> 0-1
                input[2] / 3.0, // weather code 0-3 -> 0-1
                input[3] / 4.0 // season code 0-4 -> 0-1
        };
    }

    private double relu(double x) {
        return Math.max(0, x);
    }

    private double reluDerivative(double x) {
        return x > 0 ? 1 : 0;
    }

    /**
     * Train the network using backpropagation.
     */
    private void trainNetwork(int epochs) {
        if (trainingInputs.isEmpty())
            return;

        for (int epoch = 0; epoch < epochs; epoch++) {
            double totalError = 0;

            for (int sample = 0; sample < trainingInputs.size(); sample++) {
                double[] input = trainingInputs.get(sample);
                double target = trainingOutputs.get(sample);

                // Forward pass
                double[] normalizedInput = normalize(input);

                double[] hidden = new double[6];
                double[] hiddenPreActivation = new double[6];
                for (int j = 0; j < 6; j++) {
                    hiddenPreActivation[j] = hiddenBias[j];
                    for (int i = 0; i < 4; i++) {
                        hiddenPreActivation[j] += normalizedInput[i] * inputToHidden[i][j];
                    }
                    hidden[j] = relu(hiddenPreActivation[j]);
                }

                double output = outputBias;
                for (int j = 0; j < 6; j++) {
                    output += hidden[j] * hiddenToOutput[j];
                }
                double prediction = output * 100 + 50;

                // Calculate error
                double error = target - prediction;
                totalError += error * error;

                // Backpropagation
                double outputGradient = error / 100.0; // Scale

                // Update output weights
                for (int j = 0; j < 6; j++) {
                    hiddenToOutput[j] += LEARNING_RATE * outputGradient * hidden[j];
                }
                outputBias += LEARNING_RATE * outputGradient;

                // Update hidden weights
                for (int j = 0; j < 6; j++) {
                    double hiddenGradient = outputGradient * hiddenToOutput[j] * reluDerivative(hiddenPreActivation[j]);
                    for (int i = 0; i < 4; i++) {
                        inputToHidden[i][j] += LEARNING_RATE * hiddenGradient * normalizedInput[i];
                    }
                    hiddenBias[j] += LEARNING_RATE * hiddenGradient;
                }
            }

            trainingEpochs++;
        }

        // Update confidence based on training
        updateConfidence();
    }

    private void updateConfidence() {
        // Confidence increases with more training samples and epochs
        int sampleBonus = Math.min(30, trainingSamples / 5);
        int epochBonus = Math.min(20, trainingEpochs / 50);
        confidence = 50 + sampleBonus + epochBonus;
        confidence = Math.min(95, confidence); // Cap at 95%
    }

    private void generateInitialTrainingData() {
        Random rand = new Random();

        // Generate synthetic training data
        for (int i = 0; i < 50; i++) {
            double moisture = rand.nextDouble() * 100;
            double growth = rand.nextDouble() * 100;
            double weather = rand.nextInt(4); // 0=clear, 1=cloudy, 2=rain, 3=storm
            double season = rand.nextInt(4); // 0=spring, 1=summer, 2=fall, 3=winter

            double[] input = { moisture, growth, weather, season };

            // Generate target based on simple rules
            double target = calculateIdealWaterNeed(moisture, growth, weather, season);

            trainingInputs.add(input);
            trainingOutputs.add(target);
            trainingSamples++;
        }
    }

    private double calculateIdealWaterNeed(double moisture, double growth, double weather, double season) {
        // Base need inversely proportional to moisture
        double base = Math.max(0, (60 - moisture) * 1.5);

        // Growth stage affects need
        if (growth > 50 && growth < 80) {
            base *= 1.3; // Growing plants need more
        }

        // Weather affects need
        if (weather == 2)
            base *= 0.3; // Rain reduces need
        if (weather == 0)
            base *= 1.2; // Clear sky increases need

        // Season affects need
        if (season == 1)
            base *= 1.4; // Summer needs more
        if (season == 3)
            base *= 0.6; // Winter needs less

        return Math.max(0, Math.min(200, base));
    }

    // ==================== BEHAVIORS ====================

    /**
     * Periodic prediction cycle.
     */
    private class PredictionCycle extends TickerBehaviour {
        public PredictionCycle(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            // Get current conditions (simplified - would normally come from beliefs)
            double avgMoisture = 45; // Would be calculated from field data
            double avgGrowth = 60;
            double weather = 0; // Clear
            double season = 1; // Summer

            // Make prediction
            double[] input = { avgMoisture, avgGrowth, weather, season };
            double prediction = predict(input);
            lastPrediction = (int) Math.round(prediction);

            System.out.println("[PredictorAgent] Prediction: " + lastPrediction + "L water needed (confidence: "
                    + confidence + "%)");

            // Broadcast to GUI
            WebServer.broadcastPrediction(lastPrediction, confidence, trainingSamples);

            // Broadcast to FarmerBDI
            broadcastPrediction();

            // Broadcast interaction to GUI
            WebServer.broadcastAgentInteraction(
                    "Predictor", "ai",
                    "FarmerBDI", "bdi",
                    "INFORM", "Predicted water need: " + lastPrediction + "L", 1);
        }
    }

    /**
     * Receives field data for online learning.
     */
    private class LearningBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                processTrainingData(msg);
            } else {
                block();
            }
        }
    }

    private void processTrainingData(ACLMessage msg) {
        String content = msg.getContent();

        // Handle training data format:
        // "TRAIN:moisture,growth,weather,season,actual_water_used"
        if (content.startsWith("TRAIN:")) {
            try {
                String[] parts = content.substring(6).split(",");
                double[] input = {
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3])
                };
                double target = Double.parseDouble(parts[4]);

                // Add to training data
                trainingInputs.add(input);
                trainingOutputs.add(target);
                trainingSamples++;

                // Limit training data size
                if (trainingInputs.size() > 500) {
                    trainingInputs.remove(0);
                    trainingOutputs.remove(0);
                }

                // Online training
                trainNetwork(10);

                System.out.println("[PredictorAgent] Learned from new data. Samples: " + trainingSamples);

            } catch (Exception e) {
                System.err.println("[PredictorAgent] Error processing training data: " + e.getMessage());
            }
        }

        // Handle prediction request
        if (content.startsWith("PREDICT:")) {
            try {
                String[] parts = content.substring(8).split(",");
                double[] input = {
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3])
                };

                double prediction = predict(input);

                // Send response
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("PREDICTION:" + Math.round(prediction) + "," + confidence);
                send(reply);

            } catch (Exception e) {
                System.err.println("[PredictorAgent] Error processing prediction request: " + e.getMessage());
            }
        }
    }

    private void broadcastPrediction() {
        // Send prediction to FarmerBDI
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("FarmerBDI", AID.ISLOCALNAME));
        msg.setContent("PREDICTION:" + lastPrediction + "," + confidence);
        send(msg);
    }

    // ==================== PUBLIC METHODS ====================

    public int getLastPrediction() {
        return lastPrediction;
    }

    public int getConfidence() {
        return confidence;
    }

    public int getTrainingSamples() {
        return trainingSamples;
    }
}
