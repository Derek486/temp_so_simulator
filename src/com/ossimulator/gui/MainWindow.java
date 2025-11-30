package com.ossimulator.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import com.ossimulator.memory.FIFO;
import com.ossimulator.memory.LRU;
import com.ossimulator.memory.MemoryManager;
import com.ossimulator.memory.Optimal;
import com.ossimulator.memory.PageReplacementAlgorithm;
import com.ossimulator.process.Burst;
import com.ossimulator.process.BurstType;
import com.ossimulator.scheduling.FCFS;
import com.ossimulator.scheduling.PriorityScheduling;
import com.ossimulator.scheduling.RoundRobin;
import com.ossimulator.scheduling.SJF;
import com.ossimulator.scheduling.SchedulingAlgorithm;
import com.ossimulator.simulator.OSSimulator;
import com.ossimulator.util.ConfigParser;

public class MainWindow extends JFrame {
    private SchedulingPanel schedulingPanel;
    private MemoryPanel memoryPanel;
    private MetricsPanel metricsPanel;
    private OSSimulator simulator;
    private boolean simulationRunning = false;

    private JComboBox<String> schedulerCombo;
    private JComboBox<String> pageReplaceCombo;
    private JSpinner quantumSpinner;
    private JSpinner memoryFramesSpinner;
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private JButton loadButton;

    public MainWindow() {
        setTitle("Operating Systems Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        initComponents();
        layoutComponents();
    }

    private void initComponents() {
        schedulingPanel = new SchedulingPanel();
        memoryPanel = new MemoryPanel();
        metricsPanel = new MetricsPanel();

        schedulerCombo = new JComboBox<>(new String[]{
                "FCFS", "SJF", "Round Robin", "Priority"
        });

        pageReplaceCombo = new JComboBox<>(new String[]{
                "FIFO", "LRU", "Optimal"
        });

        quantumSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 20, 1));
        memoryFramesSpinner = new JSpinner(new SpinnerNumberModel(10, 4, 50, 1));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

        startButton = new JButton("Start Simulation");
        startButton.addActionListener(e -> startSimulation());

        stopButton = new JButton("Stop Simulation");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopSimulation());

        loadButton = new JButton("Load Processes");
        loadButton.addActionListener(e -> loadProcesses());
    }

    private void layoutComponents() {
        JPanel controlPanel = createControlPanel();
        JPanel visualPanel = createVisualPanel();

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlPanel, visualPanel);
        mainSplit.setDividerLocation(300);

        add(mainSplit);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(240, 240, 240));

        JLabel configTitle = new JLabel("Configuration");
        configTitle.setFont(new Font("Arial", Font.BOLD, 14));

        panel.add(configTitle);
        panel.add(Box.createVerticalStrut(10));

        panel.add(new JLabel("Scheduling Algorithm:"));
        panel.add(schedulerCombo);
        panel.add(Box.createVerticalStrut(5));

        panel.add(new JLabel("Page Replacement:"));
        panel.add(pageReplaceCombo);
        panel.add(Box.createVerticalStrut(5));

        panel.add(new JLabel("Quantum (RR):"));
        panel.add(quantumSpinner);
        panel.add(Box.createVerticalStrut(5));

        panel.add(new JLabel("Memory Frames:"));
        panel.add(memoryFramesSpinner);
        panel.add(Box.createVerticalStrut(10));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(3, 1, 5, 5));
        buttonPanel.setBackground(new Color(240, 240, 240));
        buttonPanel.add(loadButton);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        panel.add(buttonPanel);

        panel.add(Box.createVerticalStrut(20));
        JLabel logTitle = new JLabel("Event Log");
        logTitle.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(logTitle);
        panel.add(new JScrollPane(logArea));

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel createVisualPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        panel.add(schedulingPanel);
        panel.add(memoryPanel);
        panel.add(metricsPanel);

        return panel;
    }

    private void loadProcesses() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                List<com.ossimulator.process.Proceso> processes = ConfigParser.parseProcessesFromFile(
                        fileChooser.getSelectedFile().getAbsolutePath()
                );
                JOptionPane.showMessageDialog(this,
                    "Loaded " + processes.size() + " processes",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Error loading file: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void startSimulation() {
        List<com.ossimulator.process.Proceso> processes = new ArrayList<>();
        processes.add(new com.ossimulator.process.Proceso("P1", 0,
            List.of(new Burst(BurstType.CPU, 4), new Burst(BurstType.IO, 3),
                    new Burst(BurstType.CPU, 5)), 1, 4));
        processes.add(new com.ossimulator.process.Proceso("P2", 2,
            List.of(new Burst(BurstType.CPU, 6), new Burst(BurstType.IO, 2),
                    new Burst(BurstType.CPU, 3)), 2, 5));
        processes.add(new com.ossimulator.process.Proceso("P3", 4,
            List.of(new Burst(BurstType.CPU, 8)), 3, 6));

        SchedulingAlgorithm scheduler = createScheduler();
        PageReplacementAlgorithm pageAlgorithm = createPageAlgorithm();
        int memoryFrames = (int) memoryFramesSpinner.getValue();
        int quantum = (int) quantumSpinner.getValue();

        MemoryManager memoryManager = new MemoryManager(memoryFrames, pageAlgorithm);
        simulator = new OSSimulator(processes, scheduler, memoryManager, quantum);

        simulator.setUpdateListener(new OSSimulator.SimulationUpdateListener() {
            @Override
            public void onUpdate() {
                SwingUtilities.invokeLater(() -> updateUI());
            }

            @Override
            public void onComplete() {
                SwingUtilities.invokeLater(() -> simulationComplete());
            }
        });

        simulator.getEventLogger().addListener(event ->
            SwingUtilities.invokeLater(() -> logArea.append(event + "\n"))
        );

        simulationRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        simulator.start();
    }

    private void stopSimulation() {
        if (simulator != null) {
            simulator.stop();
            simulationRunning = false;
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
    }

    private void updateUI() {
        if (simulator != null) {
            schedulingPanel.updateData(simulator);
            metricsPanel.updateMetrics(simulator.getMetrics());
        }
    }

    private void simulationComplete() {
        JOptionPane.showMessageDialog(this, "Simulation complete!",
            "Info", JOptionPane.INFORMATION_MESSAGE);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private SchedulingAlgorithm createScheduler() {
        String selected = (String) schedulerCombo.getSelectedItem();
        return switch (selected) {
            case "FCFS" -> new FCFS();
            case "SJF" -> new SJF();
            case "Round Robin" -> new RoundRobin((int) quantumSpinner.getValue());
            case "Priority" -> new PriorityScheduling();
            default -> new FCFS();
        };
    }

    private PageReplacementAlgorithm createPageAlgorithm() {
        String selected = (String) pageReplaceCombo.getSelectedItem();
        return switch (selected) {
            case "FIFO" -> new FIFO();
            case "LRU" -> new LRU();
            case "Optimal" -> new Optimal(new ArrayList<>());
            default -> new FIFO();
        };
    }
}
