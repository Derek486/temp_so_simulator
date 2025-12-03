package com.ossimulator.gui;

import com.ossimulator.memory.FIFO;
import com.ossimulator.memory.LRU;
import com.ossimulator.memory.MemoryManager;
import com.ossimulator.memory.Optimal;
import com.ossimulator.memory.PageReplacementAlgorithm;
import com.ossimulator.process.Burst;
import com.ossimulator.process.Proceso;
import com.ossimulator.scheduling.FCFS;
import com.ossimulator.scheduling.PriorityScheduling;
import com.ossimulator.scheduling.RoundRobin;
import com.ossimulator.scheduling.SJF;
import com.ossimulator.scheduling.SchedulingAlgorithm;
import com.ossimulator.simulator.OSSimulator;
import com.ossimulator.util.ConfigParser;
import java.awt.BorderLayout;
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

public class MainWindow extends JFrame {
    private SchedulingPanel schedulingPanel;
    private MemoryVisualizerPanel memoryPanel;
    private MetricsPanel metricsPanel;
    private OSSimulator simulator;
    private MemoryManager memoryManager; // <-- referencia mantenida para UI
    private List<Proceso> loadedProcesses = new ArrayList<>();
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
        memoryPanel = new MemoryVisualizerPanel();
        metricsPanel = new MetricsPanel();

        schedulerCombo = new JComboBox<>(new String[] {
                "FCFS", "SJF", "Round Robin", "Priority"
        });

        pageReplaceCombo = new JComboBox<>(new String[] {
                "FIFO", "LRU", "Optimal"
        });

        quantumSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 20, 1));
        memoryFramesSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 200, 1));

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
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 240));

        // --- PANEL SUPERIOR ---
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(new Color(240, 240, 240));

        JLabel configTitle = new JLabel("Configuration");
        configTitle.setFont(new Font("Arial", Font.BOLD, 14));

        topPanel.add(configTitle);
        topPanel.add(Box.createVerticalStrut(10));

        topPanel.add(new JLabel("Scheduling Algorithm:"));
        topPanel.add(schedulerCombo);
        topPanel.add(Box.createVerticalStrut(5));

        topPanel.add(new JLabel("Page Replacement:"));
        topPanel.add(pageReplaceCombo);
        topPanel.add(Box.createVerticalStrut(5));

        topPanel.add(new JLabel("Quantum (RR):"));
        topPanel.add(quantumSpinner);
        topPanel.add(Box.createVerticalStrut(5));

        topPanel.add(new JLabel("Memory Frames:"));
        topPanel.add(memoryFramesSpinner);
        topPanel.add(Box.createVerticalStrut(10));

        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        buttonPanel.setBackground(new Color(240, 240, 240));
        buttonPanel.add(loadButton);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        topPanel.add(buttonPanel);
        topPanel.add(Box.createVerticalStrut(10));

        // --- PANEL INFERIOR (EVENT LOG) ---
        JPanel logPanel = new JPanel(new BorderLayout());
        JLabel logTitle = new JLabel("Event Log");
        logTitle.setFont(new Font("Arial", Font.BOLD, 14));
        logPanel.add(logTitle, BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(logArea);
        logPanel.add(logScroll, BorderLayout.CENTER);

        // El top es auto, el log ocupa lo restante
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(logPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createVisualPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JScrollPane ganttScroll = new JScrollPane(schedulingPanel);
        panel.add(ganttScroll);
        panel.add(memoryPanel);
        panel.add(metricsPanel);

        return panel;
    }

    private void loadProcesses() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                loadedProcesses = ConfigParser.parseProcessesFromFile(
                        fileChooser.getSelectedFile().getAbsolutePath());
                JOptionPane.showMessageDialog(this,
                        "Loaded " + loadedProcesses.size() + " processes",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Error loading file: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private List<Proceso> cloneProcesses(List<Proceso> src) {
        List<Proceso> cloned = new ArrayList<>();
        for (Proceso p : src) {
            List<Burst> burstsClone = new ArrayList<>();
            for (Burst b : p.getBursts()) {
                burstsClone.add(new Burst(b.getType(), b.getDuration()));
            }
            Proceso cp = new Proceso(p.getPid(), p.getArrivalTime(), burstsClone, p.getPriority(), p.getPageCount());
            cloned.add(cp);
        }
        return cloned;
    }

    private void startSimulation() {
        if (loadedProcesses == null || loadedProcesses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No processes loaded. Please load processes first.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // clear previous memory state (moving it from stopSimulation to here)
        if (this.memoryManager != null) {
            this.memoryManager.setUpdateListener(null);
        }

        List<Proceso> processes = cloneProcesses(this.loadedProcesses);

        SchedulingAlgorithm scheduler = createScheduler();
        PageReplacementAlgorithm pageAlgorithm = createPageAlgorithm();
        int memoryFrames = (int) memoryFramesSpinner.getValue();
        int quantum = (int) quantumSpinner.getValue();

        // local variable to fix memory visualizer
        final MemoryManager currentMemoryManager = new MemoryManager(memoryFrames, pageAlgorithm);

        this.memoryManager = currentMemoryManager;

        // envolvemos en un bloque trycatch
        try {
            currentMemoryManager.setPreserveFramesOnProcessTermination(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        currentMemoryManager
                .setUpdateListener(() -> SwingUtilities.invokeLater(() -> memoryPanel.updateData(this.memoryManager)));

        simulator = new OSSimulator(processes, scheduler, this.memoryManager, quantum);

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

        simulator.getEventLogger().addListener(event -> SwingUtilities.invokeLater(() -> logArea.append(event + "\n")));

        // initial UI refresh so memory table shows initial state
        // memoryPanel.updateData(this.memoryManager);
        memoryPanel.updateData(currentMemoryManager);

        simulationRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        simulator.start();
    }

    private void stopSimulation() {
        if (simulator != null) {
            simulator.stop();
            simulator = null; // forzar nueva instancia en next start
        }
        // clear memory manager reference so next run will recreate it
        // this.memoryManager = null; <-- do not clear memory during stop
        simulationRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void updateUI() {
        if (simulator != null) {
            schedulingPanel.updateData(simulator);
            metricsPanel.updateMetrics(simulator.getMetrics());
        }
        // update memory panel from the current memoryManager reference (may be null)
        if (this.memoryManager != null) {
            memoryPanel.updateData(this.memoryManager);
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
