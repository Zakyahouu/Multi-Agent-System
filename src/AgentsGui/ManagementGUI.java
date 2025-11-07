package AgentsGui;

import Agents.ManagementAgent;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ManagementGUI extends JFrame {

    private ManagementAgent agent;
    private JComboBox<String> containerCombo;
    private JTable agentTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;

    public ManagementGUI(ManagementAgent agent) {
        super("JADE Platform Management");
        this.agent = agent;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());

        // Top: Containers
        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(new JLabel("Containers:"));
        containerCombo = new JComboBox<>();
        topPanel.add(containerCombo);
        JButton refreshContainersBtn = new JButton("Refresh Containers");
        refreshContainersBtn.addActionListener(e -> agent.queryContainers());
        topPanel.add(refreshContainersBtn);
        JButton createContainerBtn = new JButton("Create Container");
        createContainerBtn.addActionListener(e -> showCreateContainerDialog());
        topPanel.add(createContainerBtn);
        add(topPanel, BorderLayout.NORTH);

        // Center: Agents table
        String[] columns = {"Name", "Container", "GUI Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };
        agentTable = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(agentTable);
        add(tableScroll, BorderLayout.CENTER);

        // Bottom: Controls
        JPanel bottomPanel = new JPanel(new GridLayout(2, 1));

        // Open GUI Panel
        JPanel openPanel = new JPanel(new FlowLayout());
        JButton createAgentBtn = new JButton("Create Agent");
        createAgentBtn.addActionListener(e -> showCreateAgentDialog());
        openPanel.add(createAgentBtn);
        JButton openGUIBtn = new JButton("Open GUI for Selected Agent");
        openGUIBtn.addActionListener(this::handleOpenGUI);
        openPanel.add(openGUIBtn);
        JButton refreshAgentsBtn = new JButton("Refresh Agents");
        refreshAgentsBtn.addActionListener(e -> agent.queryAgents());
        openPanel.add(refreshAgentsBtn);
        bottomPanel.add(openPanel);

        // Log
        logArea = new JTextArea(5, 50);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        bottomPanel.add(logScroll);

        add(bottomPanel, BorderLayout.SOUTH);

        // Initial refresh
        agent.queryContainers();
        agent.queryAgents();

        setVisible(true);
    }

    private void showCreateAgentDialog() {
        JDialog dialog = new JDialog(this, "Create New Agent", true);
        dialog.setLayout(new GridLayout(5, 2));
        dialog.setSize(300, 200);

        dialog.add(new JLabel("Agent Class:"));
        JComboBox<String> classCombo = new JComboBox<>();
        loadAgentClasses(classCombo);
        dialog.add(classCombo);

        dialog.add(new JLabel("Agent Name:"));
        JTextField nameField = new JTextField();
        dialog.add(nameField);

        dialog.add(new JLabel("Container:"));
        JComboBox<String> containerCombo = new JComboBox<>();
        containerCombo.setModel(this.containerCombo.getModel());
        dialog.add(containerCombo);

        dialog.add(new JLabel("Auto-open GUI:"));
        JCheckBox autoOpenCheck = new JCheckBox();
        dialog.add(autoOpenCheck);

        JButton createBtn = new JButton("Create");
        createBtn.addActionListener(e -> {
            String className = (String) classCombo.getSelectedItem();
            String agentName = nameField.getText().trim();
            String containerName = (String) containerCombo.getSelectedItem();
            boolean autoOpen = autoOpenCheck.isSelected();

            if (className == null || agentName.isEmpty() || containerName == null) {
                JOptionPane.showMessageDialog(dialog, "Please fill all fields.");
                return;
            }

            String fullClassName = "Agents." + className;
            agent.createAgent(fullClassName, agentName, containerName, autoOpen);
            dialog.dispose();
        });
        dialog.add(createBtn);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.add(cancelBtn);

        dialog.setVisible(true);
    }

    private void showCreateContainerDialog() {
        JDialog dialog = new JDialog(this, "Create New Container", true);
        dialog.setLayout(new GridLayout(3, 2));
        dialog.setSize(300, 150);

        dialog.add(new JLabel("Container Name:"));
        JTextField nameField = new JTextField();
        dialog.add(nameField);

        JButton createBtn = new JButton("Create");
        createBtn.addActionListener(e -> {
            String containerName = nameField.getText().trim();
            if (containerName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter a container name.");
                return;
            }
            agent.createContainer(containerName);
            dialog.dispose();
        });
        dialog.add(createBtn);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.add(cancelBtn);

        dialog.setVisible(true);
    }

    private void loadAgentClasses(JComboBox<String> combo) {
        // Scan bin/Agents/ for .class files
        File agentsDir = new File("bin/Agents");
        if (agentsDir.exists() && agentsDir.isDirectory()) {
            File[] files = agentsDir.listFiles((dir, name) -> name.endsWith(".class"));
            if (files != null) {
                for (File file : files) {
                    String className = file.getName().replace(".class", "");
                    combo.addItem(className);
                }
            }
        }
        if (combo.getItemCount() == 0) {
            combo.addItem("FirstAgent"); // Fallback
        }
    }

    public void updateContainers(String[] containers) {
        SwingUtilities.invokeLater(() -> {
            containerCombo.removeAllItems();
            for (String c : containers) {
                containerCombo.addItem(c);
            }
            log("Containers updated: " + containers.length + " found.");
        });
    }

    public void updateAgents(String[][] agents) {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (String[] a : agents) {
                tableModel.addRow(a);
            }
            log("Agents updated: " + agents.length + " found.");
        });
    }

    private void handleOpenGUI(ActionEvent e) {
        int row = agentTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select an agent from the table.");
            return;
        }
        String agentName = (String) tableModel.getValueAt(row, 0);
        openAgentGUI(agentName);
    }

    public void openAgentGUI(String agentName) {
        // Get the agent instance and create GUI
        Agents.FirstAgent agent = Agents.FirstAgent.getAgent(agentName);
        if (agent != null) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                new AgentInterface(agent);
            });
            log("GUI opened for agent: " + agentName);
            Agents.ManagementAgent.setGUIStatus(agentName, true);
        } else {
            log("Agent " + agentName + " not found.");
        }
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}