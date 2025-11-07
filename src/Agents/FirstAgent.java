package Agents;

import AgentsGui.AgentInterface;
import jade.core.Agent;
import java.util.Map;
import java.util.HashMap;

public class FirstAgent extends Agent {
    
    private transient AgentInterface gui;
    
    // Static map to hold references to all FirstAgent instances
    private static Map<String, FirstAgent> agents = new HashMap<>();
    
    @Override
    protected void setup() {
        super.setup();
        agents.put(getLocalName(), this);
        System.out.println("Hello World i am agent " + getAID().getName());
        
        // Launch the GUI
        javax.swing.SwingUtilities.invokeLater(() -> {
            gui = new AgentInterface(this);
        });
    }
    
    @Override
    protected void beforeMove() {
        super.beforeMove();
        System.out.println("FirstAgent beforeMove from " + here().getName());
        if (gui != null) {
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    gui.setVisible(false);
                    gui.dispose();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            gui = null;
        }
    }
    
    @Override
    protected void afterMove() {
        super.afterMove();
        System.out.println("FirstAgent afterMove to " + here().getName());
        
        // Recreate GUI in new container
        javax.swing.SwingUtilities.invokeLater(() -> {
            gui = new AgentInterface(this);
        });
    }
    
    @Override
    protected void takeDown() {
        super.takeDown();
        agents.remove(getLocalName());
        System.out.println("FirstAgent takeDown");
        if (gui != null) {
            gui.dispose();
        }
    }
    
    // Static method to get agent by name
    public static FirstAgent getAgent(String name) {
        return agents.get(name);
    }
    
    // Added this method to allow GUI to log messages to the agent
    public void logToGui(String message) {
        if (gui != null) {
            gui.log(message);
        }
    }
}