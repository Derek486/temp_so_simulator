package com.ossimulator.gui;

import com.ossimulator.simulator.SystemMetrics;
import javax.swing.*;
import java.awt.*;

public class MetricsPanel extends JPanel {
    private JLabel avgWaitLabel;
    private JLabel avgTurnaroundLabel;
    private JLabel cpuUtilLabel;
    private JLabel contextSwitchesLabel;

    public MetricsPanel() {
        setLayout(new GridLayout(4, 1, 10, 10));
        setBackground(new Color(240, 240, 240));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Performance Metrics");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));

        avgWaitLabel = new JLabel("Average Waiting Time: 0.00");
        avgWaitLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        avgTurnaroundLabel = new JLabel("Average Turnaround Time: 0.00");
        avgTurnaroundLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        cpuUtilLabel = new JLabel("CPU Utilization: 0.00%");
        cpuUtilLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        contextSwitchesLabel = new JLabel("Context Switches: 0");
        contextSwitchesLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        add(titleLabel);
        add(avgWaitLabel);
        add(avgTurnaroundLabel);
        add(cpuUtilLabel);
        add(contextSwitchesLabel);
    }

    public void updateMetrics(SystemMetrics metrics) {
        if (metrics != null) {
            avgWaitLabel.setText(String.format("Average Waiting Time: %.2f", metrics.getAverageWaitingTime()));
            avgTurnaroundLabel.setText(String.format("Average Turnaround Time: %.2f", metrics.getAverageTurnaroundTime()));
            cpuUtilLabel.setText(String.format("CPU Utilization: %.2f%%", metrics.getCPUUtilization()));
            contextSwitchesLabel.setText("Context Switches: " + metrics.getContextSwitches());
        }
    }
}
