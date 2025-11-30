package com.ossimulator.gui;

import com.ossimulator.process.Proceso;
import com.ossimulator.simulator.OSSimulator;

import javax.swing.*;

import java.awt.*;
import java.util.*;

public class SchedulingPanel extends JPanel {
    private OSSimulator simulator;
    private GanttChart ganttChart;

    public SchedulingPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(240, 240, 240));

        JLabel titleLabel = new JLabel("CPU Scheduling");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        ganttChart = new GanttChart();

        add(titleLabel, BorderLayout.NORTH);
        add(new JScrollPane(ganttChart), BorderLayout.CENTER);
    }

    public void updateData(OSSimulator simulator) {
        this.simulator = simulator;
        if (simulator != null) {
            ganttChart.updateChart(simulator.getAllProcesses(), simulator.getCurrentTime());
        }
    }
}
