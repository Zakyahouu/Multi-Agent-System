package SimpleAgent;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class SimpleLauncher {
    public static void main(String[] args) {
        try {
            System.out.println("Starting Simple JADE Agent System...\n");

            Runtime rt = Runtime.instance();

            // Main container (with JADE GUI)
            Profile mainProfile = new ProfileImpl();
            mainProfile.setParameter(Profile.MAIN_HOST, "127.0.0.1");
            mainProfile.setParameter(Profile.MAIN_PORT, "1099");
            mainProfile.setParameter(Profile.GUI, "true");
            ContainerController mainContainer = rt.createMainContainer(mainProfile);
            System.out.println("Main container created");

            // Create Container-1
            Profile c1Profile = new ProfileImpl();
            c1Profile.setParameter(Profile.MAIN_HOST, "127.0.0.1");
            c1Profile.setParameter(Profile.MAIN_PORT, "1099");
            c1Profile.setParameter(Profile.CONTAINER_NAME, "Container-1");
            ContainerController container1 = rt.createAgentContainer(c1Profile);
            System.out.println("Container-1 created");

            // Create Container-2
            Profile c2Profile = new ProfileImpl();
            c2Profile.setParameter(Profile.MAIN_HOST, "127.0.0.1");
            c2Profile.setParameter(Profile.MAIN_PORT, "1099");
            c2Profile.setParameter(Profile.CONTAINER_NAME, "Container-2");
            ContainerController container2 = rt.createAgentContainer(c2Profile);
            System.out.println("Container-2 created");

            // Small pause to ensure containers are ready
            Thread.sleep(5000);

            // Agents in Container-1
            AgentController c1a1 = container1.createNewAgent(
                "C1AgentOne", "SimpleAgent.SimpleAgentClass", null);
            AgentController c1a2 = container1.createNewAgent(
                "C1AgentTwo", "SimpleAgent.SimpleAgentClass", null);

            // Agents in Container-2
            AgentController c2a1 = container2.createNewAgent(
                "C2AgentThree", "SimpleAgent.SimpleAgentClass", null);
            AgentController c2a2 = container2.createNewAgent(
                "C2AgentFour", "SimpleAgent.SimpleAgentClass", null);

            c1a1.start();
			c1a2.start();
			c2a1.start();
			c2a2.start();
			System.out.println("Started 4 agents with GUIs");
			
			// Let them fully register before starting Sniffer
			Thread.sleep(5000);
			
			// Start Sniffer tool and attach to the 4 agents
			Object[] sniffArgs = new Object[] {
				    new String[] { "C1AgentOne", "C1AgentTwo", "C2AgentThree", "C2AgentFour" }
				};
			AgentController sniffer = mainContainer.createNewAgent(
			    "Sniffer", "jade.tools.sniffer.Sniffer", sniffArgs);
			sniffer.start();
			System.out.println("Sniffer started (sniffing C1Agent1, C1Agent2, C2Agent1, C2Agent2)");

            System.out.println("\nAll set!");

        } catch (Exception e) {
            System.err.println("Error starting system: " + e.getMessage());
            e.printStackTrace();
        }
    }
}