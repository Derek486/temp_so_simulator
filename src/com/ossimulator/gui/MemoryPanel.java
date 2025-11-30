package com.ossimulator.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import com.ossimulator.memory.MemoryManager;
import com.ossimulator.process.Proceso;

public class MemoryPanel extends JPanel {
    private MemoryManager memoryManager;
    private JTable frameTable;
    private DefaultTableModel tableModel;
    private JLabel pageFaultsLabel;
    private JLabel replacementsLabel;

    public MemoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(240, 240, 240));

        JLabel titleLabel = new JLabel("Virtual Memory Management");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tableModel = new DefaultTableModel(new String[]{"Frame", "Process"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        frameTable = new JTable(tableModel);
        frameTable.setFillsViewportHeight(true);
        frameTable.setRowHeight(25);

        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        pageFaultsLabel = new JLabel("Page Faults: 0");
        replacementsLabel = new JLabel("Replacements: 0");
        statsPanel.add(pageFaultsLabel);
        statsPanel.add(replacementsLabel);

        add(titleLabel, BorderLayout.NORTH);
        add(new JScrollPane(frameTable), BorderLayout.CENTER);
        add(statsPanel, BorderLayout.SOUTH);
    }

    public void updateData(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;

        if (memoryManager != null) {
            tableModel.setRowCount(0);

            Map<Integer, Proceso> frames = memoryManager.getFrameStatus();
            for (int i = 0; i < 10; i++) {
                Proceso p = frames.get(i);
                Object[] row = {i, p != null ? p.getPid() : "Free"};
                tableModel.addRow(row);
            }

            pageFaultsLabel.setText("Page Faults: " + memoryManager.getTotalPageFaults());
            replacementsLabel.setText("Replacements: " + memoryManager.getTotalReplacements());
        }
    }
}
