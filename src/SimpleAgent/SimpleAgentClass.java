package SimpleAgent;

import jade.core.Agent;
import jade.core.AID;
import jade.core.Location;
import jade.core.ContainerID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.domain.JADEAgentManagement.QueryPlatformLocationsAction;
import jade.domain.JADEAgentManagement.QueryAgentsOnLocation;
import jade.domain.JADEAgentManagement.CreateAgent;
import jade.content.lang.sl.SLCodec;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.util.leap.List;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

import javax.swing.JOptionPane;

public class SimpleAgentClass extends Agent {
    private transient SimpleGui.SimpleGuiClass gui;
    private transient SimpleGui.ContainerViewGui containerView;
    private Color agentColor;
    private ArrayList<String> logHistory;
    // Track last opened container view and whether to reopen it after move
    private String lastContainerViewName;
    private boolean reopenContainerAfterMove = false;
    
    @Override
    protected void setup() {
        System.out.println("Agent " + getLocalName() + " started in container: " + here().getName());
        
        // Initialize log history
        logHistory = new ArrayList<>();
        
        // Generate unique color for this agent
        agentColor = generateUniqueColor();
        
        // Create GUI
        javax.swing.SwingUtilities.invokeLater(() -> {
            gui = new SimpleGui.SimpleGuiClass(this);
            gui.setAgentColor(agentColor); // Set the color indicator
            restoreLogHistory(); // Restore previous logs
            gui.log("Agent " + getLocalName() + " ready in container: " + here().getName());
        });
        
        // Add behaviour to listen for commands from GUI
        addBehaviour(new CommandListener());
        
        // Add behaviour to listen for messages from other agents
        addBehaviour(new MessageReceiver());
    }
    
    @Override
    protected void beforeMove() {
        super.beforeMove();
        System.out.println("Agent beforeMove from " + here().getName());
        
        // Close container view if open
        if (containerView != null) {
            // mark that we should reopen this view after moving
            reopenContainerAfterMove = true;
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    containerView.setVisible(false);
                    containerView.dispose();
                });
            } catch (Exception e) {
                System.out.println("Error disposing container view: " + e.getMessage());
            }
            containerView = null;
        }
        else {
            reopenContainerAfterMove = false;
        }
        
        // Close agent GUI
        if (gui != null) {
            saveLogHistory(); // Save logs before closing GUI
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
        System.out.println("Agent afterMove to " + here().getName());
        
        // Recreate GUI in new container
        javax.swing.SwingUtilities.invokeLater(() -> {
            gui = new SimpleGui.SimpleGuiClass(this);
            gui.setAgentColor(agentColor); // Set the color indicator
            restoreLogHistory(); // Restore logs after move
            gui.log("Agent moved to container: " + here().getName());
            // Restore last container view if it was open before moving
            if (reopenContainerAfterMove && lastContainerViewName != null && !lastContainerViewName.isEmpty()) {
                openContainerViewFor(lastContainerViewName);
            }
            reopenContainerAfterMove = false;
        });
    }
    
    @Override
    protected void afterClone() {
        super.afterClone();
        System.out.println("Agent afterClone: " + getLocalName() + " in " + here().getName());
        
        // Generate NEW color for cloned agent (different from original)
        agentColor = generateUniqueColor();
        logHistory = new ArrayList<>(); // Fresh logs for clone
        
        // Create GUI for cloned agent
        javax.swing.SwingUtilities.invokeLater(() -> {
            gui = new SimpleGui.SimpleGuiClass(this);
            gui.setAgentColor(agentColor); // Set the color indicator
            gui.log("Agent cloned as " + getLocalName() + " in container: " + here().getName());
        });
    }
    
    @Override
    protected void takeDown() {
        super.takeDown();
        System.out.println("Agent " + getLocalName() + " shutting down");
        if (gui != null) {
            gui.dispose();
        }
    }
    //-------------------------------------------------------------------------------------------------------------------------------
    // Method for GUI to send commands
    public void sendCommand(String command) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(getAID());
        msg.setContent(command);
        send(msg);
    }
    
    // Behaviour to listen for commands
    private class CommandListener extends CyclicBehaviour {
        @Override
        public void action() {
            // Only listen for INFORM messages from self (GUI commands)
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchSender(getAID())
            );
            ACLMessage msg = receive(mt);
            
            if (msg != null) {
                String command = msg.getContent();
                handleCommand(command);
            } else {
                block();
            }
        }
    }
    
    // Command handler - all logic here
    private void handleCommand(String command) {
        System.out.println("handleCommand received: " + command);
        switch (command) {
            case "1": // Show agent name
                showAgentName();
                break;
                
            case "2": // Show containers
                showContainers();
                break;
                
            case "3": // Create new container
                createNewContainer();
                break;
                
            case "4": // Transfer agent
                transferAgent();
                break;
                
            case "5": // Clone agent
                cloneAgent();
                break;
                
            case "6": // Kill agent
                killAgent();
                break;
                
            case "7": // Refresh container view
                refreshContainerView();
                break;
                
            case "8": // Create agent in container (ask for details)
                askCreateAgentInContainer();
                break;
                
            case "9": // Open container view
                openContainerView();
                break;
                
            default:
                if (command.startsWith("TRANSFER:")) {
                    String containerName = command.substring(9);
                    doTransfer(containerName);
                } else if (command.startsWith("CLONE:")) {
                    String[] parts = command.substring(6).split("\\|");
                    if (parts.length == 2) {
                        doClone(parts[0], parts[1]); // containerName|newName
                    }
                } else if (command.startsWith("CREATE_CONTAINER:")) {
                    String containerName = command.substring(17);
                    doCreateContainer(containerName);
                } else if (command.startsWith("CREATE_AGENT:")) {
                    String[] parts = command.substring(13).split("\\|");
                    if (parts.length == 3) {
                        doCreateAgentInContainer(parts[0], parts[1], parts[2]); // containerName|agentName|className
                    }
                } else if (command.startsWith("SEND_MESSAGE:")) {
                    String[] parts = command.substring(13).split("\\|", 2);
                    if (parts.length == 2) {
                        sendMessageToAgent(parts[0], parts[1]); // targetAgent|message
                    }
                } else if (command.startsWith("BROADCAST:")) {
                    String message = command.substring(10);
                    broadcastMessage(message);
                } else if (command.startsWith("REFRESH_CONTAINER:")) {
                    String containerName = command.substring(18);
                    refreshContainerViewFor(containerName);
                } else if (command.startsWith("CREATE_AGENT_IN:")) {
                    String containerName = command.substring(16);
                    // For now, we'll ask for agent creation details
                    // The container name is stored in the GUI, so we can use it there
                    askCreateAgentInContainer();
                }
                break;
        }
    }
     //-------------------------------------------------------------------------------------------------------------------------------
    // Command 1: Show agent name
    private void showAgentName() {
        if (gui != null) {
            gui.log("Agent Name: " + getLocalName());
            gui.log("Agent AID: " + getAID().getName());
        }
    }
    
    // Command 2: Show containers
    private void showContainers() {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    getContentManager().registerLanguage(new SLCodec());
                    getContentManager().registerOntology(JADEManagementOntology.getInstance());
                    
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(new AID("ams", AID.ISLOCALNAME));
                    request.setLanguage("fipa-sl");
                    request.setOntology("JADE-agent-management");
                    
                    QueryPlatformLocationsAction query = new QueryPlatformLocationsAction();
                    Action action = new Action(getAID(), query);
                    getContentManager().fillContent(request, action);
                    
                    send(request);
                    
                    MessageTemplate mt = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage response = blockingReceive(mt, 5000);
                    
                    if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                        Object content = getContentManager().extractContent(response);
                        if (content instanceof Result) {
                            Result result = (Result) content;
                            List locations = (List) result.getValue();
                            
                            if (gui != null) {
                                gui.log("=== Containers in Platform ===");
                                for (int i = 0; i < locations.size(); i++) {
                                    Location loc = (Location) locations.get(i);
                                    gui.log((i + 1) + ". " + loc.getName());
                                }
                                gui.log("Current container: " + here().getName());
                            }
                        }
                    } else {
                        if (gui != null) {
                            gui.log("Failed to query containers from AMS");
                        }
                    }
                } catch (Exception e) {
                    if (gui != null) {
                        gui.log("Error querying containers: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }
        });
    }
    
    // Command 3: Create new container (ask for name)
    private void createNewContainer() {
        if (gui != null) {
            gui.askForInput("Enter new container name:", "CREATE_CONTAINER:");
        }
    }
    
    // Command 3 actual: Do create container
    private void doCreateContainer(String containerName) {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    jade.core.Profile p = new jade.core.ProfileImpl();
                    p.setParameter(jade.core.Profile.MAIN_HOST, "127.0.0.1");
                    p.setParameter(jade.core.Profile.MAIN_PORT, "1099");
                    p.setParameter(jade.core.Profile.CONTAINER_NAME, containerName);
                    
                    jade.core.Runtime rt = jade.core.Runtime.instance();
                    rt.createAgentContainer(p);
                    
                    if (gui != null) {
                        gui.log("Container '" + containerName + "' created successfully");
                    }
                } catch (Exception e) {
                    if (gui != null) {
                        gui.log("Error creating container: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }
        });
    }
    
    // Command 4: Transfer agent (ask for container name)
    private void transferAgent() {
        if (gui != null) {
            gui.askForInput("Enter destination container name:", "TRANSFER:");
        }
    }
    
    // Command 4 actual: Do transfer
    private void doTransfer(String containerName) {
        try {
            Location dest = new ContainerID(containerName, null);
            if (gui != null) {
                gui.log("Transferring to container: " + containerName);
            }
            doMove(dest);
        } catch (Exception e) {
            if (gui != null) {
                gui.log("Error transferring agent: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }
    
    // Command 5: Clone agent (ask for container and name)
    private void cloneAgent() {
        if (gui != null) {
            gui.askForClone();
        }
    }
    
    // Command 5 actual: Do clone
    private void doClone(String containerName, String newName) {
        try {
            Location dest = new ContainerID(containerName, null);
            if (gui != null) {
                gui.log("Cloning as '" + newName + "' to container: " + containerName);
            }
            doClone(dest, newName);
        } catch (Exception e) {
            if (gui != null) {
                gui.log("Error cloning agent: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }
    
    // Command 6: Kill agent
    private void killAgent() {
        if (gui != null) {
            gui.log("Agent will be killed...");
        }
        doDelete();
    }
    
    // Command 7: Refresh container view
    private void refreshContainerView() {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    getContentManager().registerLanguage(new SLCodec());
                    getContentManager().registerOntology(JADEManagementOntology.getInstance());
                    
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(new AID("ams", AID.ISLOCALNAME));
                    request.setLanguage("fipa-sl");
                    request.setOntology("JADE-agent-management");
                    
                    // Query agents in current container
                    QueryAgentsOnLocation query = new QueryAgentsOnLocation();
                    query.setLocation(here());
                    Action action = new Action(getAID(), query);
                    getContentManager().fillContent(request, action);
                    
                    send(request);
                    
                    MessageTemplate mt = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage response = blockingReceive(mt, 5000);
                    
                    if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                        Object content = getContentManager().extractContent(response);
                        if (content instanceof Result) {
                            Result result = (Result) content;
                            List agentList = (List) result.getValue();
                            
                            String[] agents = new String[agentList.size()];
                            for (int i = 0; i < agentList.size(); i++) {
                                AID aid = (AID) agentList.get(i);
                                agents[i] = aid.getLocalName();
                            }
                            
                            if (containerView != null) {
                                containerView.updateAgentList(agents);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    // Refresh container view for a specific container (not just current location)
    private void refreshContainerViewFor(String containerName) {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    getContentManager().registerLanguage(new SLCodec());
                    getContentManager().registerOntology(JADEManagementOntology.getInstance());
                    
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(new AID("ams", AID.ISLOCALNAME));
                    request.setLanguage("fipa-sl");
                    request.setOntology("JADE-agent-management");
                    
                    // Query agents in specified container
                    QueryAgentsOnLocation query = new QueryAgentsOnLocation();
                    // Create ContainerID for the specified container name
                    jade.core.ContainerID containerID = new jade.core.ContainerID();
                    containerID.setName(containerName);
                    query.setLocation(containerID);
                    Action action = new Action(getAID(), query);
                    getContentManager().fillContent(request, action);
                    
                    send(request);
                    
                    MessageTemplate mt = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage response = blockingReceive(mt, 5000);
                    
                    if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                        Object content = getContentManager().extractContent(response);
                        if (content instanceof Result) {
                            Result result = (Result) content;
                            List agentList = (List) result.getValue();
                            
                            String[] agents = new String[agentList.size()];
                            for (int i = 0; i < agentList.size(); i++) {
                                AID aid = (AID) agentList.get(i);
                                agents[i] = aid.getLocalName();
                            }
                            
                            if (containerView != null) {
                                containerView.updateAgentList(agents);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    // Command 8: Ask for agent creation details
    private void askCreateAgentInContainer() {
        if (containerView != null) {
            containerView.askForAgentCreation();
        }
    }
    
    // Command 8 actual: Create agent in current container
    private void doCreateAgentInContainer(String containerName, String agentName, String className) {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    getContentManager().registerLanguage(new SLCodec());
                    getContentManager().registerOntology(JADEManagementOntology.getInstance());
                    
                    // Create the container ID
                    jade.core.ContainerID cid = new jade.core.ContainerID();
                    cid.setName(containerName);
                    
                    // Create agent using AMS
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(new AID("ams", AID.ISLOCALNAME));
                    request.setLanguage("fipa-sl");
                    request.setOntology("JADE-agent-management");
                    
                    CreateAgent ca = new CreateAgent();
                    ca.setAgentName(agentName);
                    ca.setClassName(className);
                    ca.setContainer(cid);
                    
                    Action action = new Action(getAID(), ca);
                    getContentManager().fillContent(request, action);
                    
                    send(request);
                    
                    // Wait for response
                    MessageTemplate mt = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage response = blockingReceive(mt, 5000);
                    
                    if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                        if (gui != null) {
                            gui.log("Agent '" + agentName + "' created in container: " + containerName);
                            gui.log("Class: " + className);
                        }
                        
                        // Refresh the container view for the specific container
                        refreshContainerViewFor(containerName);
                    } else {
                        if (gui != null) {
                            gui.log("Error creating agent - no response from AMS");
                        }
                    }
                    
                } catch (Exception e) {
                    if (gui != null) {
                        gui.log("Error creating agent: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }
        });
    }
    
    // Command 9: Open container view
    private void openContainerView() {
        System.out.println("openContainerView() called");
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    getContentManager().registerLanguage(new SLCodec());
                    getContentManager().registerOntology(JADEManagementOntology.getInstance());
                    
                    // Query AMS for all containers
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(new AID("ams", AID.ISLOCALNAME));
                    request.setLanguage("fipa-sl");
                    request.setOntology("JADE-agent-management");
                    
                    QueryPlatformLocationsAction query = new QueryPlatformLocationsAction();
                    Action action = new Action(getAID(), query);
                    getContentManager().fillContent(request, action);
                    
                    send(request);
                    
                    MessageTemplate mt = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage response = blockingReceive(mt, 5000);
                    
                    if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                        Object content = getContentManager().extractContent(response);
                        if (content instanceof Result) {
                            Result result = (Result) content;
                            List locations = (List) result.getValue();
                            
                            // Build list of container names
                            String[] containerNames = new String[locations.size()];
                            for (int i = 0; i < locations.size(); i++) {
                                Location loc = (Location) locations.get(i);
                                containerNames[i] = loc.getName();
                            }
                            
                            // Show selection dialog
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                String selectedContainer = (String) JOptionPane.showInputDialog(
                                    gui,
                                    "Select container to view:",
                                    "Container Selection",
                                    JOptionPane.QUESTION_MESSAGE,
                                    null,
                                    containerNames,
                                    containerNames[0]
                                );
                                
                                if (selectedContainer != null) {
                                    openContainerViewFor(selectedContainer);
                                }
                            });
                        }
                    } else {
                        if (gui != null) {
                            gui.log("Failed to query containers from AMS");
                        }
                    }
                } catch (Exception e) {
                    if (gui != null) {
                        gui.log("Error querying containers: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }
        });
    }
    
    // Actually open container view for specific container
    private void openContainerViewFor(String containerName) {
        System.out.println("Opening container view for: " + containerName);
        javax.swing.SwingUtilities.invokeLater(() -> {
            System.out.println("Creating ContainerViewGui...");
            if (containerView != null) {
                try {
                    containerView.setVisible(false);
                    containerView.dispose();
                } catch (Exception e) {
                    // Ignore disposal errors
                    System.out.println("Error disposing old container view: " + e.getMessage());
                }
                containerView = null;
            }
            containerView = new SimpleGui.ContainerViewGui(this, containerName);
            // remember last opened container view name
            lastContainerViewName = containerName;
            System.out.println("ContainerViewGui created");
            refreshContainerViewFor(containerName); // Load agent list for selected container
            if (gui != null) {
                gui.log("Container view opened for: " + here().getName());
            }
        });
    }
    
    // Public method for GUI to log messages (with history tracking)
    public void logToGui(String message) {
        logHistory.add(message); // Save to history
        if (gui != null) {
            gui.log(message);
        }
    }
    
    // Public method for GUI to log colored messages
    public void logColoredMessage(String message, Color color) {
        logHistory.add(message); // Save plain text to history
        if (gui != null) {
            gui.logColored(message, color);
        }
    }
    
    // Get agent's color
    public Color getAgentColor() {
        return agentColor;
    }
    
    // Behaviour to receive messages from other agents
    private class MessageReceiver extends CyclicBehaviour {
        @Override
        public void action() {
            // Listen for INFORM messages from other agents (not from self)
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.not(MessageTemplate.MatchSender(getAID()))
            );
            ACLMessage msg = receive(mt);
            
            if (msg != null) {
                String senderName = msg.getSender().getLocalName();
                String content = msg.getContent();
                
                // Try to extract color from message (format: "COLOR:R,G,B|message")
                Color senderColor = null;
                String actualMessage = content;
                
                if (content.startsWith("COLOR:")) {
                    int separatorIndex = content.indexOf("|");
                    if (separatorIndex > 0) {
                        String colorStr = content.substring(6, separatorIndex);
                        String[] rgb = colorStr.split(",");
                        if (rgb.length == 3) {
                            try {
                                int r = Integer.parseInt(rgb[0]);
                                int g = Integer.parseInt(rgb[1]);
                                int b = Integer.parseInt(rgb[2]);
                                senderColor = new Color(r, g, b);
                                actualMessage = content.substring(separatorIndex + 1);
                            } catch (Exception e) {
                                // Use default if parsing fails
                            }
                        }
                    }
                }
                
                String logMessage = ">>> MESSAGE from " + senderName + ": " + actualMessage;
                
                if (senderColor != null) {
                    logColoredMessage(logMessage, senderColor);
                } else {
                    logToGui(logMessage);
                }
                
                System.out.println("[" + getLocalName() + "] Received from " + senderName + ": " + actualMessage);
            } else {
                block();
            }
        }
    }
    
    // Send message to specific agent (with validation that the target exists on the platform)
    private void sendMessageToAgent(String targetAgent, String message) {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    // Discover all containers first
                    getContentManager().registerLanguage(new SLCodec());
                    getContentManager().registerOntology(JADEManagementOntology.getInstance());

                    ACLMessage reqLocations = new ACLMessage(ACLMessage.REQUEST);
                    reqLocations.addReceiver(new AID("ams", AID.ISLOCALNAME));
                    reqLocations.setLanguage("fipa-sl");
                    reqLocations.setOntology("JADE-agent-management");

                    QueryPlatformLocationsAction qpla = new QueryPlatformLocationsAction();
                    Action actLocations = new Action(getAID(), qpla);
                    getContentManager().fillContent(reqLocations, actLocations);
                    send(reqLocations);

                    MessageTemplate mtAms = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage respLocations = blockingReceive(mtAms, 5000);

                    boolean found = false;
                    if (respLocations != null && respLocations.getPerformative() == ACLMessage.INFORM) {
                        Object content = getContentManager().extractContent(respLocations);
                        if (content instanceof Result) {
                            Result res = (Result) content;
                            List locations = (List) res.getValue();

                            // Scan each container for the target agent
                            for (int i = 0; i < locations.size() && !found; i++) {
                                Location loc = (Location) locations.get(i);

                                ACLMessage reqAgents = new ACLMessage(ACLMessage.REQUEST);
                                reqAgents.addReceiver(new AID("ams", AID.ISLOCALNAME));
                                reqAgents.setLanguage("fipa-sl");
                                reqAgents.setOntology("JADE-agent-management");

                                QueryAgentsOnLocation q = new QueryAgentsOnLocation();
                                q.setLocation(loc);
                                Action a = new Action(getAID(), q);
                                getContentManager().fillContent(reqAgents, a);
                                send(reqAgents);

                                ACLMessage respAgents = blockingReceive(mtAms, 5000);
                                if (respAgents != null && respAgents.getPerformative() == ACLMessage.INFORM) {
                                    Object c2 = getContentManager().extractContent(respAgents);
                                    if (c2 instanceof Result) {
                                        Result r2 = (Result) c2;
                                        List agentList = (List) r2.getValue();
                                        for (int j = 0; j < agentList.size(); j++) {
                                            AID aid = (AID) agentList.get(j);
                                            if (aid.getLocalName().equals(targetAgent)) {
                                                found = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!found) {
                        // Target agent not found anywhere – report error
                        String err = "Error: agent '" + targetAgent + "' does not exist on the platform.";
                        logColoredMessage(err, Color.RED);
                        System.out.println("[" + getLocalName() + "] " + err);
                        return;
                    }

                    // Build and send the message now that target exists
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(new AID(targetAgent, AID.ISLOCALNAME));
                    String colorStr = agentColor.getRed() + "," + agentColor.getGreen() + "," + agentColor.getBlue();
                    msg.setContent("COLOR:" + colorStr + "|" + message);
                    send(msg);

                    if (gui != null) {
                        gui.log("Sent to " + targetAgent + ": " + message);
                    }
                    System.out.println("[" + getLocalName() + "] Sent to " + targetAgent + ": " + message);
                } catch (Exception e) {
                    String err = "Error sending message: " + e.getMessage();
                    if (gui != null) {
                        gui.log(err);
                    }
                    e.printStackTrace();
                }
            }
        });
    }
    
    // Broadcast message to all agents across ALL containers
    private void broadcastMessage(String message) {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    getContentManager().registerLanguage(new SLCodec());
                    getContentManager().registerOntology(JADEManagementOntology.getInstance());

                    // 1) Ask AMS for all platform locations (containers)
                    ACLMessage reqLocations = new ACLMessage(ACLMessage.REQUEST);
                    reqLocations.addReceiver(new AID("ams", AID.ISLOCALNAME));
                    reqLocations.setLanguage("fipa-sl");
                    reqLocations.setOntology("JADE-agent-management");

                    QueryPlatformLocationsAction qpla = new QueryPlatformLocationsAction();
                    Action actLocations = new Action(getAID(), qpla);
                    getContentManager().fillContent(reqLocations, actLocations);
                    send(reqLocations);

                    MessageTemplate mtAms = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage respLocations = blockingReceive(mtAms, 5000);

                    if (respLocations == null || respLocations.getPerformative() != ACLMessage.INFORM) {
                        if (gui != null) gui.log("Error broadcasting: no response from AMS for locations");
                        return;
                    }

                    Object content = getContentManager().extractContent(respLocations);
                    if (!(content instanceof Result)) {
                        if (gui != null) gui.log("Error broadcasting: unexpected AMS response format");
                        return;
                    }

                    Result res = (Result) content;
                    List locations = (List) res.getValue();

                    // Use a set to avoid accidental duplicates
                    Set<String> recipients = new HashSet<>();

                    // 2) For each location, query agents on that location
                    for (int i = 0; i < locations.size(); i++) {
                        Location loc = (Location) locations.get(i);

                        ACLMessage reqAgents = new ACLMessage(ACLMessage.REQUEST);
                        reqAgents.addReceiver(new AID("ams", AID.ISLOCALNAME));
                        reqAgents.setLanguage("fipa-sl");
                        reqAgents.setOntology("JADE-agent-management");

                        QueryAgentsOnLocation q = new QueryAgentsOnLocation();
                        q.setLocation(loc);
                        Action actAgents = new Action(getAID(), q);
                        getContentManager().fillContent(reqAgents, actAgents);
                        send(reqAgents);

                        ACLMessage respAgents = blockingReceive(mtAms, 5000);
                        if (respAgents != null && respAgents.getPerformative() == ACLMessage.INFORM) {
                            Object c2 = getContentManager().extractContent(respAgents);
                            if (c2 instanceof Result) {
                                Result r2 = (Result) c2;
                                List agentList = (List) r2.getValue();
                                for (int j = 0; j < agentList.size(); j++) {
                                    AID aid = (AID) agentList.get(j);
                                    String local = aid.getLocalName();
                                    // Skip self and system agents
                                    if (!aid.equals(getAID()) && !"ams".equals(local) && !"df".equals(local)) {
                                        recipients.add(local);
                                    }
                                }
                            }
                        }
                    }

                    // 3) Send the broadcast to all collected recipients
                    int sentCount = 0;
                    for (String localName : recipients) {
                        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                        msg.addReceiver(new AID(localName, AID.ISLOCALNAME));
                        String colorStr = agentColor.getRed() + "," + agentColor.getGreen() + "," + agentColor.getBlue();
                        msg.setContent("COLOR:" + colorStr + "|" + message);
                        send(msg);
                        sentCount++;
                    }

                    if (gui != null) {
                        gui.log("Broadcast (all containers) sent to " + sentCount + " agent(s): " + message);
                    }
                    System.out.println("[" + getLocalName() + "] Broadcast (all containers) to " + sentCount + " agents: " + message);

                } catch (Exception e) {
                    if (gui != null) {
                        gui.log("Error broadcasting: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }
        });
    }
    
    // Generate unique, well-separated color for agent
    private Color generateUniqueColor() {
        // Use name hash mapped via golden-ratio to distribute hues uniformly around the color wheel
        int hash = Math.abs(getLocalName().hashCode());
        float hue = (float) ((hash * 0.618033988749895) % 1.0); // Golden ratio conjugate spacing
        // Keep saturation/brightness in ranges that avoid pastels and very bright yellows
        float saturation = 0.75f;
        float brightness = 0.88f;
        Color c = Color.getHSBColor(hue, saturation, brightness);
        // Slightly adjust too-yellow hues toward orange/green to reduce confusion
        // (optional nudge based on hue range around pure yellow ~0.16)
        if (hue > 0.12f && hue < 0.2f) {
            c = Color.getHSBColor(hue + 0.05f, saturation, brightness); // nudge away from yellow cluster
        }
        return c;
    }
    
    // Save log history before GUI closes
    private void saveLogHistory() {
        if (gui != null) {
            // GUI will provide current logs
            // We already track them in logHistory
        }
    }
    
    // Restore log history when GUI reopens
    private void restoreLogHistory() {
        if (gui != null && logHistory != null) {
            for (String log : logHistory) {
                gui.log(log);
            }
        }
    }
}
