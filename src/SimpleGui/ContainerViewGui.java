package SimpleGui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class ContainerViewGui extends JFrame {
    private SimpleAgent.SimpleAgentClass agent;
    private JList<String> agentList;
    private DefaultListModel<String> listModel;
    private JLabel containerNameLabel;
    private String containerName; // Store the container we are viewing
    
    public ContainerViewGui(SimpleAgent.SimpleAgentClass agent, String containerName) {
        this.agent = agent;
        this.containerName = containerName;
        
        setTitle("Container View - " + containerName);
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Set modern look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Main background color
        Color bgColor = new Color(240, 240, 245);
        getContentPane().setBackground(bgColor);
        
        // Top panel - Container name
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(bgColor);
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        
        containerNameLabel = new JLabel("Container: " + containerName);
        containerNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        containerNameLabel.setForeground(new Color(50, 50, 100));
        topPanel.add(containerNameLabel, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Center panel - Agent list
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBackground(bgColor);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 10, 15));
        
        JLabel listTitle = new JLabel("Agents in this Container:");
        listTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        listTitle.setForeground(new Color(50, 50, 100));
        listTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        
        listModel = new DefaultListModel<>();
        agentList = new JList<>(listModel);
        agentList.setFont(new Font("Consolas", Font.PLAIN, 13));
        agentList.setBackground(new Color(250, 250, 255));
        agentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        agentList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        JScrollPane scrollPane = new JScrollPane(agentList);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 150), 2),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        
        centerPanel.add(listTitle, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel - Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.setBackground(bgColor);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));
        
        JButton btnRefresh = createStyledButton("Refresh", new Color(70, 130, 180));
        JButton btnCreateAgent = createStyledButton("Create Agent", new Color(60, 179, 113));
        
        btnRefresh.addActionListener(e -> agent.sendCommand("REFRESH_CONTAINER:" + containerName)); // Command 7: Refresh container view
        btnCreateAgent.addActionListener(e -> agent.sendCommand("CREATE_AGENT_IN:" + containerName)); // Command 8: Create agent in container
        
        bottomPanel.add(btnRefresh);
        bottomPanel.add(btnCreateAgent);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Center the window on screen
        setLocationRelativeTo(null);
        
        setVisible(true);
    }
    
    // Helper method to create styled buttons
    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(140, 40));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Add hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });
        
        return button;
    }
    
    // Method to update agent list
    public void updateAgentList(String[] agents) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            if (agents != null && agents.length > 0) {
                for (String agentName : agents) {
                    listModel.addElement(agentName);
                }
            } else {
                listModel.addElement("(No agents in this container)");
            }
        });
    }
    
    // Method to ask for agent creation details
    public void askForAgentCreation() {
        SwingUtilities.invokeLater(() -> {
            String agentName = JOptionPane.showInputDialog(this, "Enter new agent name:");
            if (agentName != null && !agentName.trim().isEmpty()) {
                String className = JOptionPane.showInputDialog(this, 
                    "Enter agent class name:\n(Leave empty for SimpleAgent.SimpleAgentClass)");
                
                // If empty, use default
                if (className == null || className.trim().isEmpty()) {
                    className = "SimpleAgent.SimpleAgentClass";
                }
                
                // Send command with container name included
                agent.sendCommand("CREATE_AGENT:" + containerName + "|" + agentName.trim() + "|" + className.trim());
            }
        });
    }
}
