package com.ossimulator.gui;

import com.ossimulator.memory.FIFO;
import com.ossimulator.memory.LRU;
import com.ossimulator.memory.MemoryManager;
import com.ossimulator.memory.Optimal;
import com.ossimulator.memory.PageReplacementAlgorithm;
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
import javax.swing.*;

/**
 * MainWindow
 *
 * Ventana principal del simulador. Contiene:
 * - Panel de configuración y control (carga, inicio, parada).
 * - Panel visual: Gantt (scheduling), Memory visualizer y métricas.
 *
 * La clase administra la creación del OSSimulator y MemoryManager y expone
 * métodos para iniciar/detener la simulación. El diseño separa UI de lógica:
 * todo acceso a datos del simulador ocurre a través de sus getters y listeners.
 */
public class MainWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    private SchedulingPanel schedulingPanel;
    private MemoryVisualizerPanel memoryPanel;
    private MetricsPanel metricsPanel;
    private OSSimulator simulator;
    private MemoryManager memoryManager;
    private List<Proceso> loadedProcesses = new ArrayList<>();

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

    /**
     * Inicializa componentes UI y controles.
     */
    private void initComponents() {
        schedulingPanel = new SchedulingPanel();
        memoryPanel = new MemoryVisualizerPanel();
        metricsPanel = new MetricsPanel();

        schedulerCombo = new JComboBox<>(new String[] { "FCFS", "SJF", "Round Robin", "Priority" });
        pageReplaceCombo = new JComboBox<>(new String[] { "FIFO", "LRU", "Optimal" });

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

    /**
     * Construye el layout principal con panel de control y panel visual.
     */
    private void layoutComponents() {
        JPanel controlPanel = createControlPanel();
        JPanel visualPanel = createVisualPanel();

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlPanel, visualPanel);
        mainSplit.setDividerLocation(300);
        add(mainSplit);
    }

    /**
     * Crea el panel lateral de control con configuraciones y log de eventos.
     *
     * @return JPanel listo para colocarse en la ventana principal
     */
    private JPanel createControlPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 240));

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

        JPanel logPanel = new JPanel(new BorderLayout());
        JLabel logTitle = new JLabel("Event Log");
        logTitle.setFont(new Font("Arial", Font.BOLD, 14));
        logPanel.add(logTitle, BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(logArea);
        logPanel.add(logScroll, BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(logPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    /**
     * Crea el panel visual que contiene el Gantt, el visualizador de memoria y las
     * métricas.
     *
     * @return JPanel con la vista principal del simulador
     */
    private JPanel createVisualPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new java.awt.GridLayout(3, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JScrollPane ganttScroll = new JScrollPane(schedulingPanel);
        panel.add(ganttScroll);
        panel.add(memoryPanel);
        panel.add(metricsPanel);

        return panel;
    }

    /**
     * Abre un selector de archivos para cargar procesos desde un archivo de
     * configuración.
     */
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

    /**
     * Inicia la simulación creando OSSimulator y MemoryManager según la
     * configuración seleccionada.
     * Registra listeners para actualizar la UI en el hilo EDT.
     */
    private void startSimulation() {
        if (loadedProcesses == null || loadedProcesses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No processes loaded. Please load processes first.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (this.memoryManager != null) {
            this.memoryManager.setUpdateListener(null);
        }

        SchedulingAlgorithm scheduler = createScheduler();
        PageReplacementAlgorithm pageAlgorithm = createPageAlgorithm();
        int memoryFrames = (int) memoryFramesSpinner.getValue();
        int quantum = (int) quantumSpinner.getValue();

        final MemoryManager currentMemoryManager = new MemoryManager(memoryFrames, pageAlgorithm);

        this.memoryManager = currentMemoryManager;

        try {
            currentMemoryManager.setPreserveFramesOnProcessTermination(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        currentMemoryManager
                .setUpdateListener(
                        () -> javax.swing.SwingUtilities.invokeLater(() -> memoryPanel.updateData(this.memoryManager)));

        simulator = new OSSimulator(this.loadedProcesses, scheduler, this.memoryManager, quantum);

        simulator.setUpdateListener(new OSSimulator.SimulationUpdateListener() {
            @Override
            public void onUpdate() {
                javax.swing.SwingUtilities.invokeLater(() -> updateUI());
            }

            @Override
            public void onComplete() {
                javax.swing.SwingUtilities.invokeLater(() -> simulationComplete());
            }
        });

        simulator.getEventLogger()
                .addListener(event -> javax.swing.SwingUtilities.invokeLater(() -> logArea.append(event + "\n")));

        memoryPanel.updateData(currentMemoryManager);

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        simulator.start();
    }

    /**
     * Detiene la simulación actual y actualiza el estado de la UI.
     */
    private void stopSimulation() {
        if (simulator != null) {
            simulator.stop();
            simulator = null;
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    /**
     * Actualiza los paneles visibles con el estado actual del simulador y memoria.
     */
    private void updateUI() {
        if (simulator != null) {
            schedulingPanel.updateData(simulator);
            metricsPanel.updateMetrics(simulator.getMetrics());
        }
        if (this.memoryManager != null) {
            memoryPanel.updateData(this.memoryManager);
        }
    }

    /**
     * Muestra diálogo de finalización y restaura controles.
     */
    private void simulationComplete() {
        JOptionPane.showMessageDialog(this, "Simulation complete!",
                "Info", JOptionPane.INFORMATION_MESSAGE);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    /**
     * Crea el algoritmo de planificación seleccionado por el usuario.
     *
     * @return SchedulingAlgorithm instancia del algoritmo
     */
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

    /**
     * Crea el algoritmo de reemplazo de páginas seleccionado por el usuario.
     *
     * @return PageReplacementAlgorithm instancia del algoritmo
     */
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
