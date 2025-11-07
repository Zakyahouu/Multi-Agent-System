package AgentsGui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Iterator;

// JADE Imports
import jade.core.AID;
import jade.core.Agent;
import jade.core.Location;
import jade.core.ContainerID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.domain.JADEAgentManagement.QueryPlatformLocationsAction;
import jade.content.lang.sl.SLCodec;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.util.leap.List;
import jade.core.behaviours.OneShotBehaviour;

public class AgentInterface extends JFrame {

    // Fields for GUI components
    private final Agent agent;
    private JTextField nameField;
    private JTextArea logArea;
    private JScrollPane scrollPane;
    private JButton clearButton, cloneButton, transferButton, killButton, refreshButton, reopenButton;
    private JComboBox<String> containerCombo;
    private JLabel containerLabel;

    // Constructor: Sets up the GUI for controlling the agent
    public AgentInterface(Agent agent) {
        super("Agent GUI - " + agent.getName());
        this.agent = agent;

        // Setup JADE Content Manager for AMS communication
        try {
            agent.getContentManager().registerLanguage(new SLCodec());
            agent.getContentManager().registerOntology(JADEManagementOntology.getInstance());
        } catch (Exception e) {
            log("Error setting up JADE Content Manager: " + e.getMessage());
        }

        // Window setup
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                Agents.ManagementAgent.setGUIStatus(agent.getLocalName(), false);
            }
        });
        setSize(550, 450);
        setLayout(new BorderLayout());

        // Top panel: Agent info
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        JPanel namePanel = new JPanel(new FlowLayout());
        nameField = new JTextField(agent.getLocalName(), 15);
        nameField.setEditable(false);
        namePanel.add(new JLabel("Agent Name:"));
        namePanel.add(nameField);
        containerLabel = new JLabel("Current Container: " + agent.here().getName());
        JPanel containerPanel = new JPanel(new FlowLayout());
        containerPanel.add(containerLabel);
        topPanel.add(namePanel);
        topPanel.add(containerPanel);
        add(topPanel, BorderLayout.NORTH);

        // Center: Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel: Controls
        JPanel bottomPanel = new JPanel(new FlowLayout());
        containerCombo = new JComboBox<>();
        refreshButton = new JButton("Refresh");
        transferButton = new JButton("Transfer");
        cloneButton = new JButton("Clone");
        killButton = new JButton("Kill/Delete");
        reopenButton = new JButton("Reopen GUI");
        clearButton = new JButton("Clear");
        JButton testConnectionButton = new JButton("Test Connection");

        bottomPanel.add(new JLabel("Destination:"));
        bottomPanel.add(containerCombo);
        bottomPanel.add(refreshButton);
        bottomPanel.add(cloneButton);
        bottomPanel.add(transferButton);
        bottomPanel.add(killButton);
        bottomPanel.add(reopenButton);
        bottomPanel.add(clearButton);
        bottomPanel.add(testConnectionButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Button actions
        clearButton.addActionListener(e -> logArea.setText(""));
        killButton.addActionListener(e -> { log("Agent will be deleted..."); agent.doDelete(); dispose(); });
        refreshButton.addActionListener(e -> { log("Requesting refresh of container list..."); populateContainerCombo(); });
        transferButton.addActionListener(this::handleMoveClick);
        cloneButton.addActionListener(this::handleCloneClick);
        testConnectionButton.addActionListener(e -> {
            log("Testing connection to agent...");
            if (agent instanceof Agents.FirstAgent) {
                ((Agents.FirstAgent) agent).logToGui("Connection test from GUI at " + System.currentTimeMillis());
            } else {
                log("Agent is not FirstAgent, cannot test connection.");
            }
        });

        // Initialize
        populateContainerCombo();
        // Notify management of GUI open
        Agents.ManagementAgent.setGUIStatus(agent.getLocalName(), true);

        setVisible(true);
        System.out.println("AgentInterface GUI created for agent " + agent.getName());
    }

    // Public utility methods
    public void refreshAllGuiElements() {
        SwingUtilities.invokeLater(() -> {
            containerLabel.setText("Current Container: " + agent.here().getName());
            populateContainerCombo();
        });
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void logFromAgent(String message) {
        log("[AGENT] " + message);
    }

    // Event handlers
    private void handleMoveClick(ActionEvent e) {
        String containerName = (String) containerCombo.getSelectedItem();
        if (containerName == null || containerName.trim().isEmpty() ||
            containerName.equals(agent.here().getName())) {
            JOptionPane.showMessageDialog(this, "Select a valid, different container.");
            return;
        }

        int ok = JOptionPane.showConfirmDialog(this,
                "Move agent to container: " + containerName + " ?",
                "Confirm Move", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        try {
            Location dest = new ContainerID(containerName, null);
            agent.doMove(dest);
            log("Move requested to container: " + containerName);
            System.out.println("Move requested to " + containerName);
        } catch (Exception ex) {
            ex.printStackTrace();
            log("ERROR Move failed: " + ex.getMessage());
        }
    }

    private void handleCloneClick(ActionEvent e) {
        String containerName = (String) containerCombo.getSelectedItem();
        String newName = JOptionPane.showInputDialog(this, "Enter clone name:");
        if (containerName == null || newName == null || newName.trim().isEmpty()) return;

        try {
            Location dest = new ContainerID(containerName, null);
            agent.doClone(dest, newName);
            log("Clone requested as " + newName + " in container " + containerName);
            System.out.println("Clone requested as " + newName + " in " + containerName);
        } catch (Exception ex) {
            ex.printStackTrace();
            log("ERROR Clone failed: " + ex.getMessage());
        }
    }

    // Container discovery: Queries AMS for available containers
    public void populateContainerCombo() {
        // Clear existing items and log start
        containerCombo.removeAllItems();
        log("Starting basic container discovery...");
        System.out.println("Starting populateContainerCombo for agent " + agent.getName());

        try {
            // Add a OneShotBehaviour to the agent to handle the query asynchronously
            // This prevents blocking the GUI thread while waiting for AMS response
            agent.addBehaviour(new OneShotBehaviour(agent) {
                @Override
                public void action() {
                    try {
                        // Re-register language and ontology in case agent moved to a new JVM
                        // This ensures the content manager can encode/decode messages properly
                        agent.getContentManager().registerLanguage(new SLCodec());
                        agent.getContentManager().registerOntology(JADEManagementOntology.getInstance());

                        // Create the ACL message to send to AMS
                        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                        // AMS is identified by local name "ams"
                        request.addReceiver(new AID("ams", AID.ISLOCALNAME));
                        // Set language and ontology for structured communication
                        request.setLanguage("fipa-sl");
                        request.setOntology("JADE-agent-management");

                        // Create the query action: Ask AMS for platform locations (containers)
                        QueryPlatformLocationsAction query = new QueryPlatformLocationsAction();
                        // Wrap in Action ontology element, specifying this agent as sender
                        Action action = new Action(agent.getAID(), query);
                        // Fill the message content with the encoded action
                        agent.getContentManager().fillContent(request, action);

                        // Send the request to AMS
                        agent.send(request);
                        log("Query sent to AMS. Waiting for reply...");

                        // Wait for reply from AMS with a 10-second timeout
                        // Use MessageTemplate to match replies from AMS
                        MessageTemplate mt = MessageTemplate.MatchSender(new AID("ams", AID.ISLOCALNAME));
                        ACLMessage response = agent.blockingReceive(mt, 10000);

                        // If response received and it's an INFORM (success), process it
                        if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                            processAMSResponse(response);
                        } else {
                            // Handle timeout or failure
                            log("⚠ AMS reply timed out or failed to receive.");
                            System.out.println("AMS reply timed out or failed for agent " + agent.getName());
                            // Fallback: Update GUI with current container only
                            SwingUtilities.invokeLater(() -> {
                                containerCombo.addItem(agent.here().getName());
                                log("Added current container due to AMS failure: " + agent.here().getName());
                            });
                        }

                    } catch (Exception e) {
                        // Handle any exceptions during query
                        log("ERROR Error during container discovery: " + e.getMessage());
                        System.out.println("Error in populateContainerCombo: " + e.getMessage());
                        e.printStackTrace();
                        // Fallback: Add current container
                        SwingUtilities.invokeLater(() -> {
                            containerCombo.addItem(agent.here().getName());
                            log("Added current container due to error: " + agent.here().getName());
                        });
                    }
                }
            });
        } catch (Exception e) {
            // Handle failure to add behavior
            log("ERROR Failed to add behaviour for container discovery: " + e.getMessage());
            System.out.println("Failed to add behaviour: " + e.getMessage());
            // Fallback: Add current container directly
            containerCombo.addItem(agent.here().getName());
            log("Fallback: Added current container: " + agent.here().getName());
        }
    }

    // Process the AMS response and update the GUI
    private void processAMSResponse(ACLMessage response) {
        try {
            // Extract the content from the ACL message using content manager
            // This decodes the structured data (ontology-based) into Java objects
            Object content = agent.getContentManager().extractContent(response);

            // Check if content is a Result (expected from AMS)
            if (content instanceof Result) {
                Result result = (Result) content;
                // AMS returns a List of Locations (containers)
                List locations = (List) result.getValue();

                // Update GUI on EDT (Event Dispatch Thread) for thread safety
                SwingUtilities.invokeLater(() -> {
                    // Clear combo and count containers
                    containerCombo.removeAllItems();
                    int count = 0;
                    // Iterate through the list of locations
                    Iterator<?> it = locations.iterator();
                    while (it.hasNext()) {
                        Object obj = it.next();
                        // Each item should be a Location (container)
                        if (obj instanceof Location) {
                            Location loc = (Location) obj;
                            // Add container name to combo box
                            containerCombo.addItem(loc.getName());
                            count++;
                        }
                    }
                    // Log success and count
                    log("✅ Container list updated (" + count + " found).");
                    System.out.println("AMS response processed, containers found: " + count);

                    // Ensure current container is always included (fallback)
                    String current = agent.here().getName();
                    boolean hasCurrent = false;
                    // Check if current container is already in combo
                    for (int i = 0; i < containerCombo.getItemCount(); i++) {
                        if (current.equals(containerCombo.getItemAt(i))) {
                            hasCurrent = true;
                            break;
                        }
                    }
                    // If not found, add it
                    if (!hasCurrent) {
                        containerCombo.addItem(current);
                        log("Added current container: " + current);
                        System.out.println("Added current container: " + current);
                    }
                });

            } else {
                // Unexpected content type
                log("Unexpected reply type from AMS: " + content.getClass().getName());
            }
        } catch (Exception e) {
            // Handle decoding or processing errors
            log("ERROR Error processing AMS reply: " + e.getMessage());
            e.printStackTrace();
        }
    }
}