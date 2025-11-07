package Agents;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;

public class PeripheralContainerApp {
    
    public static void main(String[] args) {
        Runtime rt = Runtime.instance();
        
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "127.0.0.1");
        p.setParameter(Profile.MAIN_PORT, "1099");
        p.setParameter(Profile.CONTAINER_NAME, "ContainerOne11");
        
        ContainerController peripheralContainer = rt.createAgentContainer(p);
        
        // Start ManagementAgent in this peripheral container
        try {
            peripheralContainer.createNewAgent("ManagementAgent", "Agents.ManagementAgent", null).start();
            System.out.println("✅ ManagementAgent started in peripheral container");
        } catch (ControllerException e) {
            System.err.println("ERROR: Failed to start ManagementAgent in peripheral container: " + e.getMessage());
        }
        
        System.out.println("✅ Peripheral container 'ContainerOne11' is ready and waiting...");
        System.out.println("This container will stay alive to receive agents.");
        System.out.println("Press Ctrl+C to stop.");
        
        java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down container...");
            try {
                peripheralContainer.kill();
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }));
        
        try {
            while (true) {
                Thread.sleep(10000);
                System.out.println("Container still alive...");
            }
        } catch (InterruptedException e) {
            System.out.println("Container interrupted");
        }
    }
}