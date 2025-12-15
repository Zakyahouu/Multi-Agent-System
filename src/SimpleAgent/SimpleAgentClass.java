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
// DF imports for registering/searching services
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
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
    
    // Chat DF service toggle:
    // - When true, the agent registers a DF service (type "chat") so others can discover it
    // - When false, the agent deregisters from DF and cannot be discovered as a chat provider
    private boolean chatEnabled = true;
    private static final String CHAT_SERVICE_TYPE = "chat";

    // ================= MORSE SERVICE FEATURE (teacher demo) =================
    // A dedicated DF service that converts plain text to Morse code on request.
    // Toggled independently from chat so it is easy to explain and showcase.
    private boolean morseServiceEnabled = false;
    private static final String MORSE_SERVICE_TYPE = "morse-translator";
    private static final String MORSE_SERVICE_ONTOLOGY = "morse-ontology";
    private MorseProviderBehaviour morseProviderBehaviour;
    private static final java.util.Map<Character, String> MORSE_MAP = buildMorseMap();
    
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
            gui.updateChatToggle(chatEnabled);
            gui.updateMorseToggle(morseServiceEnabled);
        });
        
        // Add behaviour to listen for commands from GUI
        addBehaviour(new CommandListener());
        
        // Add behaviour to listen for messages from other agents
        addBehaviour(new MessageReceiver());

        // Register any active DF services (chat / morse) so other agents can discover us
        try {
            refreshDfRegistration();
        } catch (FIPAException fe) {
            System.err.println("DF register failed: " + fe.getMessage());
        }
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
            gui.updateChatToggle(chatEnabled);
            gui.updateMorseToggle(morseServiceEnabled);
            // Restore last container view if it was open before moving
            if (reopenContainerAfterMove && lastContainerViewName != null && !lastContainerViewName.isEmpty()) {
                openContainerViewFor(lastContainerViewName);
            }
            reopenContainerAfterMove = false;
        });

        // After moving, refresh DF registration so services remain discoverable
        try { refreshDfRegistration(); } catch (FIPAException ignored) {}
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
            gui.updateChatToggle(chatEnabled);
            gui.updateMorseToggle(morseServiceEnabled);
        });

        // For clones, refresh DF registration so services stay consistent
        try { refreshDfRegistration(); } catch (FIPAException ignored) {}
    }
    
    @Override
    protected void takeDown() {
        super.takeDown();
        System.out.println("Agent " + getLocalName() + " shutting down");
        if (morseProviderBehaviour != null) {
            removeBehaviour(morseProviderBehaviour);
            morseProviderBehaviour = null;
        }
        // Clean all DF entries so the directory doesn't keep stale records
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
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
            
            case "TOGGLE_CHAT": // Toggle DF chat service registration on/off
                toggleChatService();
                break;
            
            case "LIST_SERVICES": // List all DF services grouped by type
                listDfServices();
                break;

            case "TOGGLE_MORSE": // Toggle Morse translator service
                toggleMorseService();
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
                } else if (command.startsWith("REQUEST_MORSE:")) {
                    handleMorseRequestCommand(command.substring(14));
                }
                break;
        }
    }
    //---------------------------------------------------------------------------------------------------------------------------------------------------
    /**
     * Handle GUI requests to translate some plain text into Morse.
     * The request is routed through the DF so we only proceed when a provider is actually registered.
     * When the selected provider is this agent (service enabled locally) we short-circuit and translate immediately;
     * otherwise we issue an ACL REQUEST using the dedicated ontology and wait for the INFORM reply.
     */
    private void handleMorseRequestCommand(String payload) {
        final String raw = payload == null ? "" : payload;
        final String[] split = raw.split("\\|", 2);
        final String requestedProvider = split.length == 2 ? split[0].trim() : "";
        final String plainText = (split.length == 2 ? split[1] : split[0]).trim();

        if (plainText.isEmpty()) {
            logColoredMessage("Morse request ignored: no text provided.", Color.RED);
            return;
        }

        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    AID provider = findMorseProvider(requestedProvider);
                    if (provider == null) {
                        if (requestedProvider == null || requestedProvider.isEmpty()) {
                            logColoredMessage("No Morse translator registered in DF. Ask another agent to enable it first.", Color.RED);
                        } else {
                            logColoredMessage("Agent '" + requestedProvider + "' is not advertising the Morse service.", Color.RED);
                        }
                        return;
                    }

                    if (provider.equals(getAID())) {
                        if (!morseServiceEnabled) {
                            logColoredMessage("Local Morse service is disabled. Toggle it ON before processing requests.", Color.RED);
                            return;
                        }
                        String morse = translateToMorse(plainText);
                        logColoredMessage("Local Morse translation: " + morse, agentColor);
                        if (gui != null) {
                            gui.log("Morse (self) " + plainText + " => " + morse);
                        }
                        return;
                    }

                    final String convId = "morse-" + System.currentTimeMillis() + "-" + getLocalName();
                    ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                    req.addReceiver(provider);
                    req.setOntology(MORSE_SERVICE_ONTOLOGY);
                    req.setConversationId(convId);
                    req.setReplyWith(convId + "-req");
                    req.setContent(plainText);
                    send(req);

                    String providerName = provider.getLocalName();
                    logColoredMessage("Requested Morse translation from " + providerName + "...", agentColor);

                    MessageTemplate mt = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(convId),
                        MessageTemplate.MatchOntology(MORSE_SERVICE_ONTOLOGY)
                    );

                    ACLMessage resp = blockingReceive(mt, 5000);
                    if (resp == null) {
                        logColoredMessage("No Morse reply from " + providerName + " (timeout).", Color.RED);
                        return;
                    }

                    if (resp.getPerformative() == ACLMessage.INFORM) {
                        String morse = resp.getContent();
                        logColoredMessage("Morse reply from " + providerName + ": " + morse, agentColor);
                        if (gui != null) {
                            gui.log("Morse (" + providerName + ") " + plainText + " => " + morse);
                        }
                    } else {
                        logColoredMessage("Morse request failed: " + resp.getContent(), Color.RED);
                    }
                } catch (FIPAException fe) {
                    logColoredMessage("DF lookup for Morse service failed: " + fe.getMessage(), Color.RED);
                } catch (Exception e) {
                    logColoredMessage("Unexpected Morse request error: " + e.getMessage(), Color.RED);
                    e.printStackTrace();
                }
            }
        });
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
        // If this sender disabled chat, block sending to demonstrate the toggle effect clearly
        if (!chatEnabled) {
            String err = "Cannot send: chat service is OFF (not registered in DF).";
            logColoredMessage(err, Color.RED);
            return;
        }
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
        // Block broadcasting when chat is disabled
        if (!chatEnabled) {
            String err = "Cannot broadcast: chat service is OFF (not registered in DF).";
            logColoredMessage(err, Color.RED);
            return;
        }
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

    // ---------------- DF: Register/deregister chat service and toggle support ----------------
    /**
     * Register this agent in the DF as a provider of service type "chat".
     * This makes the agent discoverable by other agents searching the DF.
     * Only registers when chatEnabled == true.
     */
    private void registerChatServiceIfEnabled() throws FIPAException {
        // Legacy helper retained for backward compatibility. All DF state is centralized via refreshDfRegistration().
        refreshDfRegistration();
    }

    /**
     * Toggle the chat service availability:
     * - If currently enabled: deregister from DF and mark OFF
     * - If currently disabled: register in DF and mark ON
     */
    private void toggleChatService() {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    if (chatEnabled) {
                        // Turn OFF: remove DF entry so others can't discover this agent by chat service
                        chatEnabled = false;
                        try { refreshDfRegistration(); } catch (FIPAException ignored) {}
                        if (gui != null) gui.updateChatToggle(false);
                        logColoredMessage("Chat service disabled (DF entry removed).", Color.ORANGE);
                    } else {
                        // Turn ON: register DF entry to become discoverable again
                        chatEnabled = true;
                        try {
                            refreshDfRegistration();
                            if (gui != null) gui.updateChatToggle(true);
                            logColoredMessage("Chat service enabled (registered in DF).", agentColor);
                        } catch (FIPAException e) {
                            chatEnabled = false; // rollback on failure
                            logColoredMessage("Chat enable failed: " + e.getMessage(), Color.RED);
                            if (gui != null) gui.updateChatToggle(false);
                        }
                    }
                } catch (Exception ex) {
                    logColoredMessage("Chat toggle error: " + ex.getMessage(), Color.RED);
                }
            }
        });
    }

    /**
     * Toggle the dedicated Morse translator DF service. When enabled we spin up a provider behaviour
     * (listening for ontology-scoped requests) and register the new capability in the DF.
     */
    private void toggleMorseService() {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                if (morseServiceEnabled) {
                    morseServiceEnabled = false;
                    if (morseProviderBehaviour != null) {
                        removeBehaviour(morseProviderBehaviour);
                        morseProviderBehaviour = null;
                    }
                    try { refreshDfRegistration(); } catch (FIPAException ignored) {}
                    if (gui != null) gui.updateMorseToggle(false);
                    logColoredMessage("Morse translator disabled (DF entry removed).", Color.ORANGE);
                    return;
                }

                morseServiceEnabled = true;
                morseProviderBehaviour = new MorseProviderBehaviour();
                addBehaviour(morseProviderBehaviour);
                try {
                    refreshDfRegistration();
                    if (gui != null) gui.updateMorseToggle(true);
                    logColoredMessage("Morse translator enabled and registered in DF.", agentColor);
                } catch (FIPAException fe) {
                    morseServiceEnabled = false;
                    if (morseProviderBehaviour != null) {
                        removeBehaviour(morseProviderBehaviour);
                        morseProviderBehaviour = null;
                    }
                    logColoredMessage("Failed to register Morse service: " + fe.getMessage(), Color.RED);
                    if (gui != null) gui.updateMorseToggle(false);
                }
            }
        });
    }

    /**
     * Centralized DF registration helper: deregisters stale entries then (re)registers every active capability.
     */
    private void refreshDfRegistration() throws FIPAException {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        boolean hasServices = false;

        if (chatEnabled) {
            ServiceDescription chat = new ServiceDescription();
            chat.setType(CHAT_SERVICE_TYPE);
            chat.setName(getLocalName());
            dfd.addServices(chat);
            hasServices = true;
        }

        if (morseServiceEnabled) {
            ServiceDescription morse = new ServiceDescription();
            morse.setType(MORSE_SERVICE_TYPE);
            morse.setName(getLocalName() + "-morse");
            morse.addOntologies(MORSE_SERVICE_ONTOLOGY);
            morse.addProtocols("REQUEST-REPLY");
            jade.domain.FIPAAgentManagement.Property flag = new jade.domain.FIPAAgentManagement.Property("demo", "morse-translator");
            morse.addProperties(flag);
            dfd.addServices(morse);
            hasServices = true;
        }

        if (!hasServices) {
            return; // With no services enabled we simply leave DF empty.
        }

        DFService.register(this, dfd);
    }

    /**
     * Query DF for all registered services across all agents, group them by service type,
     * and log a structured summary. This helps visualize which agents provide which capabilities.
     */
    private void listDfServices() {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    // Template with no restrictions returns ALL services (we'll set maxResults = -1)
                    DFAgentDescription template = new DFAgentDescription();
                    jade.domain.FIPAAgentManagement.SearchConstraints sc = new jade.domain.FIPAAgentManagement.SearchConstraints();
                    sc.setMaxResults(-1L); // no limit
                    DFAgentDescription[] results = DFService.search(myAgent, template, sc);

                    if (results == null || results.length == 0) {
                        logColoredMessage("DF: No services registered.", Color.GRAY);
                        return;
                    }

                    // Group by service type
                    java.util.Map<String, java.util.List<ServiceDescription>> byType = new java.util.HashMap<>();
                    java.util.Map<ServiceDescription, AID> serviceOwners = new java.util.HashMap<>();

                    for (DFAgentDescription dfd : results) {
                        AID owner = dfd.getName();
                        @SuppressWarnings("unchecked")
                        java.util.Iterator<ServiceDescription> it = dfd.getAllServices();
                        while (it != null && it.hasNext()) {
                            ServiceDescription sd = it.next();
                            String type = sd.getType();
                            if (type == null) type = "<no-type>";
                            byType.computeIfAbsent(type, k -> new java.util.ArrayList<>()).add(sd);
                            serviceOwners.put(sd, owner);
                        }
                    }

                    // Log grouped report
                    logColoredMessage("=== DF Service Directory ===", new Color(50,50,120));
                    for (String type : byType.keySet()) {
                        java.util.List<ServiceDescription> list = byType.get(type);
                        logColoredMessage("Type: " + type + " (" + list.size() + " service(s))", new Color(80,80,160));
                        for (ServiceDescription sd : list) {
                            AID owner = serviceOwners.get(sd);
                            StringBuilder sb = new StringBuilder();
                            sb.append("  - Agent: ").append(owner.getLocalName());
                            sb.append(" | Name: ").append(sd.getName());

                            // List properties if any
                            @SuppressWarnings("unchecked") java.util.Iterator<jade.domain.FIPAAgentManagement.Property> pit = sd.getAllProperties();
                            java.util.List<String> props = new java.util.ArrayList<>();
                            while (pit != null && pit.hasNext()) {
                                jade.domain.FIPAAgentManagement.Property p = pit.next();
                                props.add(p.getName() + "=" + String.valueOf(p.getValue()));
                            }
                            if (!props.isEmpty()) {
                                sb.append(" | Properties: ").append(String.join(", ", props));
                            }
                            // Protocols / ontologies / languages can also be iterated if used
                            logColoredMessage(sb.toString(), Color.DARK_GRAY);
                        }
                    }
                } catch (FIPAException fe) {
                    logColoredMessage("DF list error: " + fe.getMessage(), Color.RED);
                } catch (Exception e) {
                    logColoredMessage("DF list unexpected error: " + e.getMessage(), Color.RED);
                }
            }
        });
    }

    /**
     * Locate a registered Morse provider via the DF. Prefers remote providers so the teacher can demonstrate inter-agent calls,
     * but gracefully falls back to this agent when we happen to be the only provider.
     */
    private AID findMorseProvider(String preferredLocalName) throws FIPAException {

        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(MORSE_SERVICE_TYPE);
        template.addServices(sd);

        jade.domain.FIPAAgentManagement.SearchConstraints sc = new jade.domain.FIPAAgentManagement.SearchConstraints();
        sc.setMaxResults(10L);//constrained about max results to avoid error idk why
        DFAgentDescription[] results = DFService.search(this, template, sc);
        if (results == null || results.length == 0) {
            return null;
        }
        //Fall backs in case of errors just in case, sometimes its agent itself sometimes other agents
        AID fallbackRemote = null;
        AID fallbackSelf = null;
        String preferred = preferredLocalName == null ? "" : preferredLocalName.trim();

        for (DFAgentDescription dfd : results) {
            AID candidate = dfd.getName();
            if (!preferred.isEmpty()) {
                if (candidate.getLocalName().equalsIgnoreCase(preferred)) {
                    return candidate;
                }
                continue;
            }
            if (!candidate.equals(getAID()) && fallbackRemote == null) {
                fallbackRemote = candidate;
            } else if (candidate.equals(getAID())) {
                fallbackSelf = candidate;
            }
        }

        if (!preferred.isEmpty()) {
            // Preferred agent not advertising the service
            return null;
        }

        return fallbackRemote != null ? fallbackRemote : fallbackSelf;
    }

    /**
     * Convert readable text to Morse code
     * Unsupported characters are replaced with "?" psq hkda why not
     */
    private String translateToMorse(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean justAddedGap = false;
        for (char raw : text.toCharArray()) {
            char ch = Character.toUpperCase(raw);
            if (Character.isWhitespace(ch)) {
                if (!justAddedGap && sb.length() > 0) {
                    sb.append(" / ");
                    justAddedGap = true;
                }
                continue;
            }
            String morse = MORSE_MAP.get(ch);
            if (morse == null) {
                morse = "?";
            }
            if (sb.length() > 0 && !justAddedGap) {
                sb.append(' ');
            }
            sb.append(morse);
            justAddedGap = false;
        }
        return sb.toString();
    }

    /**
     * Cyclic behaviour that answers incoming Morse translation requests
     * ;UST BE TOGGLE ON
     */
    private class MorseProviderBehaviour extends CyclicBehaviour {
        private final MessageTemplate template = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
            MessageTemplate.MatchOntology(MORSE_SERVICE_ONTOLOGY)
        );

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(template);//???
            if (msg == null) {
                block();
                return;
            }

            ACLMessage reply = msg.createReply();
            reply.setOntology(MORSE_SERVICE_ONTOLOGY);
            String content = msg.getContent();
            if (content == null || content.trim().isEmpty()) {
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setContent("No text supplied for Morse translation.");
                send(reply);
                return;
            }

            String morse = translateToMorse(content);
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(morse);
            send(reply);

            logColoredMessage(
                "Provided Morse translation to " + msg.getSender().getLocalName() + ": " + morse,
                new Color(30, 144, 255)
            );
        }
    }

    private static java.util.Map<Character, String> buildMorseMap() {
        java.util.Map<Character, String> map = new java.util.LinkedHashMap<>();
        map.put('A', ".-");
        map.put('B', "-...");
        map.put('C', "-.-.");
        map.put('D', "-..");
        map.put('E', ".");
        map.put('F', "..-.");
        map.put('G', "--.");
        map.put('H', "....");
        map.put('I', "..");
        map.put('J', ".---");
        map.put('K', "-.-");
        map.put('L', ".-..");
        map.put('M', "--");
        map.put('N', "-.");
        map.put('O', "---");
        map.put('P', ".--.");
        map.put('Q', "--.-");
        map.put('R', ".-.");
        map.put('S', "...");
        map.put('T', "-");
        map.put('U', "..-");
        map.put('V', "...-");
        map.put('W', ".--");
        map.put('X', "-..-");
        map.put('Y', "-.--");
        map.put('Z', "--..");
        map.put('0', "-----");
        map.put('1', ".----");
        map.put('2', "..---");
        map.put('3', "...--");
        map.put('4', "....-");
        map.put('5', ".....");
        map.put('6', "-....");
        map.put('7', "--...");
        map.put('8', "---..");
        map.put('9', "----.");
        map.put('.', ".-.-.-");
        map.put(',', "--..--");
        map.put('?', "..--..");
        map.put('!', "-.-.--");
        map.put('-', "-....-");
        map.put('/', "-..-.");
        map.put('@', ".--.-.");
        map.put('(', "-.--.");
        map.put(')', "-.--.-");
        map.put('&', ".-...");
        return java.util.Collections.unmodifiableMap(map);
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
