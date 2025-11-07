package Agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.ContainerID;
import jade.core.Location;
import jade.core.Runtime;
import jade.wrapper.ContainerController;
import jade.wrapper.AgentController;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.domain.JADEAgentManagement.CreateAgent;
import jade.domain.JADEAgentManagement.QueryPlatformLocationsAction;
import jade.domain.JADEAgentManagement.QueryAgentsOnLocation;
import jade.content.lang.sl.SLCodec;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.util.leap.List;
import jade.util.leap.ArrayList;
import jade.core.behaviours.OneShotBehaviour;
import java.util.Map;
import java.util.HashMap;
import AgentsGui.ManagementGUI;

public class ManagementAgent extends Agent {

    // Reference to the GUI for callbacks
    private ManagementGUI gui;

    // Map to track GUI status: agentName -> guiOpen
    private static Map<String, Boolean> guiStatus = new HashMap<>();

    public static void setGUIStatus(String agentName, boolean open) {
        guiStatus.put(agentName, open);
    }

    public void setGUI(ManagementGUI gui) {
        this.gui = gui;
    }

    @Override
    protected void setup() {
        // Register content manager for AMS communication
        try {
            getContentManager().registerLanguage(new SLCodec());
            getContentManager().registerOntology(JADEManagementOntology.getInstance());
            System.out.println("ManagementAgent setup complete.");
            
            // Create the GUI
            gui = new ManagementGUI(this);
        } catch (Exception e) {
            System.err.println("Error setting up ManagementAgent: " + e.getMessage());
        }
    }

    // Query AMS for all containers
    public void queryContainers() {
        addBehaviour(new OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    // Re-register if needed
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
                    System.out.println("ManagementAgent: Sent container query to AMS.");

                    MessageTemplate mt = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage response = blockingReceive(mt, 5000);

                    if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                        processContainerResponse(response);
                    } else {
                        System.out.println("ManagementAgent: AMS container query failed.");
                        // Fallback: add current container
                        if (gui != null) {
                            gui.updateContainers(new String[]{here().getName()});
                        }
                    }
                } catch (Exception e) {
                    System.err.println("ManagementAgent: Error querying containers: " + e.getMessage());
                    if (gui != null) {
                        gui.updateContainers(new String[]{here().getName()});
                    }
                }
            }
        });
    }

    private void processContainerResponse(ACLMessage response) {
        try {
            Object content = getContentManager().extractContent(response);
            if (content instanceof Result) {
                Result result = (Result) content;
                List locations = (List) result.getValue();
                java.util.List<String> containerNames = new java.util.ArrayList<>();
                for (int i = 0; i < locations.size(); i++) {
                    Location loc = (Location) locations.get(i);
                    containerNames.add(loc.getName());
                }
                if (gui != null) {
                    gui.updateContainers(containerNames.toArray(new String[0]));
                }
            }
        } catch (Exception e) {
            System.err.println("ManagementAgent: Error processing container response: " + e.getMessage());
        }
    }

    // Query AMS for agents on all locations
    public void queryAgents() {
        addBehaviour(new OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    getContentManager().registerLanguage(new SLCodec());
                    getContentManager().registerOntology(JADEManagementOntology.getInstance());

                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(new AID("ams", AID.ISLOCALNAME));
                    request.setLanguage("fipa-sl");
                    request.setOntology("JADE-agent-management");

                    // Query agents on current location (local container)
                    QueryAgentsOnLocation query = new QueryAgentsOnLocation();
                    query.setLocation(here());
                    Action action = new Action(getAID(), query);
                    getContentManager().fillContent(request, action);

                    send(request);
                    System.out.println("ManagementAgent: Sent agent query to AMS.");

                    MessageTemplate mt = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage response = blockingReceive(mt, 5000);

                    if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                        processAgentResponse(response);
                    } else {
                        System.out.println("ManagementAgent: AMS agent query failed.");
                        // Fallback: empty list
                        if (gui != null) {
                            gui.updateAgents(new String[0][0]);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("ManagementAgent: Error querying agents: " + e.getMessage());
                    if (gui != null) {
                        gui.updateAgents(new String[0][0]);
                    }
                }
            }
        });
    }

    private void processAgentResponse(ACLMessage response) {
        try {
            Object content = getContentManager().extractContent(response);
            if (content instanceof Result) {
                Result result = (Result) content;
                List agents = (List) result.getValue();
                java.util.List<String[]> agentData = new java.util.ArrayList<>();
                for (int i = 0; i < agents.size(); i++) {
                    AID aid = (AID) agents.get(i);
                    String name = aid.getLocalName();
                    String container = here().getName(); // Assuming current, but AMS might provide location
                    boolean guiOpen = guiStatus.getOrDefault(name, false);
                    agentData.add(new String[]{name, container, guiOpen ? "Open" : "Closed"});
                }
                if (gui != null) {
                    gui.updateAgents(agentData.toArray(new String[0][]));
                }
            }
        } catch (Exception e) {
            System.err.println("ManagementAgent: Error processing agent response: " + e.getMessage());
        }
    }

    // Create a new agent via AMS
    public void createAgent(String className, String agentName, String containerName, boolean autoOpenGUI) {
        addBehaviour(new OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    getContentManager().registerLanguage(new SLCodec());
                    getContentManager().registerOntology(JADEManagementOntology.getInstance());

                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(new AID("ams", AID.ISLOCALNAME));
                    request.setLanguage("fipa-sl");
                    request.setOntology("JADE-agent-management");

                    CreateAgent create = new CreateAgent();
                    create.setAgentName(agentName);
                    create.setClassName(className);
                    create.setContainer(new ContainerID(containerName, null));
                    // Optional: set arguments if needed

                    Action action = new Action(getAID(), create);
                    getContentManager().fillContent(request, action);

                    send(request);
                    System.out.println("ManagementAgent: Sent create agent request to AMS for " + agentName);

                    MessageTemplate mt = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                    ACLMessage response = blockingReceive(mt, 5000);

                    if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                        System.out.println("ManagementAgent: Agent " + agentName + " created successfully.");
                        guiStatus.put(agentName, autoOpenGUI);
                        if (gui != null) {
                            gui.log("Agent " + agentName + " created in " + containerName);
                            if (autoOpenGUI) {
                                gui.openAgentGUI(agentName);
                            }
                            // Refresh lists
                            queryAgents();
                        }
                    } else {
                        System.err.println("ManagementAgent: Failed to create agent " + agentName);
                        if (gui != null) {
                            gui.log("Failed to create agent " + agentName);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("ManagementAgent: Error creating agent: " + e.getMessage());
                    if (gui != null) {
                        gui.log("Error creating agent: " + e.getMessage());
                    }
                }
            }
        });
    }

    // Update GUI status when GUI is opened/closed
    // Already defined above

    // Create a new peripheral container
    public void createContainer(String containerName) {
        addBehaviour(new OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    Profile p = new ProfileImpl();
                    p.setParameter(Profile.MAIN_HOST, "127.0.0.1");
                    p.setParameter(Profile.MAIN_PORT, "1099");
                    p.setParameter(Profile.CONTAINER_NAME, containerName);

                    jade.core.Runtime rt = jade.core.Runtime.instance();
                    ContainerController cc = rt.createAgentContainer(p);
                    System.out.println("Peripheral container " + containerName + " created");

                    // Create and start ManagementAgent in the new container
                    String agentName = "ManagementAgent-" + containerName;
                    AgentController agentCtrl = cc.createNewAgent(agentName, ManagementAgent.class.getName(), null);
                    agentCtrl.start();

                    if (gui != null) {
                        gui.log("Container " + containerName + " created");
                        queryContainers(); // Refresh container list
                    }
                } catch (Exception e) {
                    System.err.println("Error creating container: " + e.getMessage());
                    if (gui != null) {
                        gui.log("Error creating container: " + e.getMessage());
                    }
                }
            }
        });
    }
}