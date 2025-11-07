package Agents;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;

public class MainGuiApp {
    
    public static void main(String[] args) {
        
        Runtime rt = Runtime.instance();
        
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "127.0.0.1");
        p.setParameter(Profile.MAIN_PORT, "1099");
        p.setParameter(Profile.GUI, "true");//Jade GUI on
        
        try {
            ContainerController mainContainer = rt.createMainContainer(p);
            System.out.println(" Main container created");
            
            AgentController ac = mainContainer.createNewAgent("Agent1", "Agents.FirstAgent", null);
            ac.start();
            System.out.println("Agent started - GUI will launch automatically\n");
            
            // Launch Management Agent and GUI
            AgentController mgmtAgent = mainContainer.createNewAgent("ManagementAgent", "Agents.ManagementAgent", null);
            mgmtAgent.start();
            System.out.println("Management Agent started");
            
            // Note: ManagementGUI will be created by the agent or here, but since it needs the agent, perhaps delay
            // for simplicity, assume ManagementGUI is created after agent setup
            // But to keep it simple, we can create it here, but need the agent instance
            // Actually, since it's a separate class, perhaps create it after starting the agent
            // But JADE agents are asynchronous, so create GUI in a separate thread or after a delay
            // For now, add a small delay
            try {
                Thread.sleep(1000); // Wait for agent to setup
            } catch (InterruptedException ie) {
                // Ignore
            }
            // But we can't get the agent instance easily. Perhaps modify to create GUI from agent.
            // To simplify, create ManagementGUI here, but pass null or something. Wait, better to have ManagementAgent create the GUI in setup.
            
        } catch (ControllerException e) {
            System.err.println("ERROR: " + e.getMessage());
        }
    }
}