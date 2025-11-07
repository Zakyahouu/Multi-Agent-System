package SimpleGui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;

public class SimpleGuiClass extends JFrame {
    private SimpleAgent.SimpleAgentClass agent;
    private JTextPane logPane; // Changed from JTextArea to JTextPane for colored text
    private StyledDocument logDoc;
    private CirclePanel colorIndicator; // Panel to show agent's unique color as a circle
    
    public SimpleGuiClass(SimpleAgent.SimpleAgentClass agent) {
        this.agent = agent;
        
        setTitle("Agent Control Panel - " + agent.getLocalName());
        setSize(900, 650);
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
        
        // Top panel - Agent color indicator
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(bgColor);
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));
        
        JLabel agentNameLabel = new JLabel("Agent: " + agent.getLocalName());
        agentNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        agentNameLabel.setForeground(new Color(50, 50, 100));
        
        colorIndicator = new CirclePanel();
        colorIndicator.setPreferredSize(new Dimension(40, 40));
        
        JPanel topLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        topLeftPanel.setBackground(bgColor);
        topLeftPanel.add(colorIndicator);
        topLeftPanel.add(agentNameLabel);
        
        topPanel.add(topLeftPanel, BorderLayout.WEST);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Left panel with buttons
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(15, 15, 15, 10),
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 150), 2),
                "Actions",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14),
                new Color(50, 50, 100)
            )
        ));
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.setBackground(bgColor);
        
        // Create styled buttons
        JButton btnAgentName = createStyledButton("Agent Info", new Color(70, 130, 180));
        JButton btnContainer = createStyledButton("Show Containers", new Color(100, 149, 237));
        JButton btnCreateContainer = createStyledButton("Create Container", new Color(60, 179, 113));
        JButton btnContainerView = createStyledButton("Container View", new Color(65, 105, 225));
        JButton btnTransfer = createStyledButton("Transfer Agent", new Color(255, 165, 0));
        JButton btnClone = createStyledButton("Clone Agent", new Color(147, 112, 219));
        JButton btnKill = createStyledButton("Kill Agent", new Color(220, 20, 60));
        
        // Add action listeners - just send command numbers
        btnAgentName.addActionListener(e -> agent.sendCommand("1"));
        btnContainer.addActionListener(e -> agent.sendCommand("2"));
        btnCreateContainer.addActionListener(e -> agent.sendCommand("3"));
        btnContainerView.addActionListener(e -> agent.sendCommand("9")); // New command for container view
        btnTransfer.addActionListener(e -> agent.sendCommand("4"));
        btnClone.addActionListener(e -> agent.sendCommand("5"));
        btnKill.addActionListener(e -> agent.sendCommand("6"));
        
        // Add buttons with spacing
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(btnAgentName);
        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(btnContainer);
        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(btnCreateContainer);
        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(btnContainerView);
        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(btnTransfer);
        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(btnClone);
        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(btnKill);
        leftPanel.add(Box.createVerticalGlue());
        
        add(leftPanel, BorderLayout.WEST);
        
        // Right panel with log area and clear button
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 15));
        rightPanel.setBackground(bgColor);
        
        // Title for log area
        JLabel logTitle = new JLabel("Activity Log");
        logTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        logTitle.setForeground(new Color(50, 50, 100));
        logTitle.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 0));
        
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(new Font("Consolas", Font.PLAIN, 13));
        logPane.setBackground(new Color(250, 250, 255));
        logPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        logDoc = logPane.getStyledDocument();
        
        JScrollPane scrollPane = new JScrollPane(logPane);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 150), 2),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        
        JButton btnClear = createStyledButton("Clear Log", new Color(128, 128, 128));
        btnClear.setPreferredSize(new Dimension(0, 40));
        btnClear.addActionListener(e -> {
            try {
                logDoc.remove(0, logDoc.getLength());
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            }
        });
        
        // Messaging panel - bottom of right panel
        JPanel messagingPanel = new JPanel(new GridBagLayout());
        messagingPanel.setBackground(bgColor);
        messagingPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 0, 0, 0),
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 150), 2),
                "Send Messages",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13),
                new Color(50, 50, 100)
            )
        ));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Agent Name label and field
        JLabel lblAgentName = new JLabel("Agent Name:");
        lblAgentName.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        messagingPanel.add(lblAgentName, gbc);
        
        JTextField txtAgentName = new JTextField();
        txtAgentName.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        messagingPanel.add(txtAgentName, gbc);
        
        // Message label and field
        JLabel lblMessage = new JLabel("Message:");
        lblMessage.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        messagingPanel.add(lblMessage, gbc);
        
        JTextField txtMessage = new JTextField();
        txtMessage.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        messagingPanel.add(txtMessage, gbc);
        
        // Buttons panel
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        btnPanel.setBackground(bgColor);
        
        JButton btnSendMessage = createStyledButton("Send Message", new Color(46, 139, 87));
        JButton btnBroadcast = createStyledButton("Broadcast", new Color(255, 140, 0));
        
        btnSendMessage.setPreferredSize(new Dimension(130, 35));
        btnBroadcast.setPreferredSize(new Dimension(130, 35));
        
        btnSendMessage.addActionListener(e -> {
            String targetAgent = txtAgentName.getText().trim();
            String message = txtMessage.getText().trim();
            if (!message.isEmpty()) {
                if (!targetAgent.isEmpty()) {
                    agent.sendCommand("SEND_MESSAGE:" + targetAgent + "|" + message);
                    txtMessage.setText("");
                } else {
                    JOptionPane.showMessageDialog(this, "Please enter agent name!", "Error", JOptionPane.WARNING_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please enter a message!", "Error", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        btnBroadcast.addActionListener(e -> {
            String message = txtMessage.getText().trim();
            if (!message.isEmpty()) {
                agent.sendCommand("BROADCAST:" + message);
                txtMessage.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "Please enter a message!", "Error", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        btnPanel.add(btnSendMessage);
        btnPanel.add(btnBroadcast);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        messagingPanel.add(btnPanel, gbc);
        
        // Add components to right panel
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBackground(bgColor);
        bottomPanel.add(btnClear, BorderLayout.NORTH);
        bottomPanel.add(messagingPanel, BorderLayout.CENTER);
        
        rightPanel.add(logTitle, BorderLayout.NORTH);
        rightPanel.add(scrollPane, BorderLayout.CENTER);
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(rightPanel, BorderLayout.CENTER);
        
        // Add window listener to kill agent when GUI is closed
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                // Kill the agent when window is closed
                agent.doDelete();
            }
        });
        
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
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
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
    
    // Method for agent to log messages (plain text)
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, new Color(40, 40, 40));
                logDoc.insertString(logDoc.getLength(), message + "\n", attrs);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }
    
    // Method for agent to log colored messages
    public void logColored(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, color);
                StyleConstants.setBold(attrs, true);
                logDoc.insertString(logDoc.getLength(), message + "\n", attrs);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }
    
    // Method to ask for input and send back to agent
    public void askForInput(String prompt, String commandPrefix) {
        SwingUtilities.invokeLater(() -> {
            String input = JOptionPane.showInputDialog(this, prompt);
            if (input != null && !input.trim().isEmpty()) {
                // Send The command line to Agent
                agent.sendCommand(commandPrefix + input.trim());
            }
        });
    }
    
    // Method to ask for clone information
    public void askForClone() {
        SwingUtilities.invokeLater(() -> {
            String containerName = JOptionPane.showInputDialog(this, "Enter destination container name:");
            if (containerName != null && !containerName.trim().isEmpty()) {
                String newName = JOptionPane.showInputDialog(this, "Enter new agent name:");
                if (newName != null && !newName.trim().isEmpty()) {
                    // Send the command line to Agent
                    agent.sendCommand("CLONE:" + containerName.trim() + "|" + newName.trim());
                }
            }
        });
    }
    
    // Method to set the agent's color indicator
    public void setAgentColor(Color color) {
        SwingUtilities.invokeLater(() -> {
            if (colorIndicator != null) {
                colorIndicator.setBackground(color);
                colorIndicator.repaint();
            }
        });
    }
    
    // Inner class for circular color indicator
    private class CirclePanel extends JPanel {
        public CirclePanel() {
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int diameter = Math.min(getWidth(), getHeight()) - 4;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            
            // Draw filled circle with agent's color
            g2d.setColor(getBackground());
            g2d.fillOval(x, y, diameter, diameter);
            
            // Draw border
            g2d.setColor(new Color(100, 100, 150));
            g2d.setStroke(new BasicStroke(2));
            g2d.drawOval(x, y, diameter, diameter);
        }
    }
}
