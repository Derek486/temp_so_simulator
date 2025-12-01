package com.ossimulator.gui;

import com.ossimulator.memory.MemoryManager;
import com.ossimulator.process.Proceso;
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

public class MemoryPanel extends JPanel {
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

        // Columnas: Frame, Process, Page
        tableModel = new DefaultTableModel(new String[] { "Frame", "Process", "Page" }, 0) {
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

    /**
     * Actualiza la tabla usando snapshot de memoryManager.
     * Debe llamarse desde hilo de la UI (Event Dispatch Thread) para evitar races
     * en Swing.
     */
    public void updateData(MemoryManager memoryManager) {
        if (memoryManager != null) {
            tableModel.setRowCount(0);

            // snapshot thread-safe
            Map<Integer, Proceso> frames = memoryManager.getFrameStatus();
            Map<Integer, Integer> frameToPage = memoryManager.getFrameToPageMap();

            int totalFrames = memoryManager.getTotalFrames();

            for (int i = 0; i < totalFrames; i++) {
                Proceso p = frames.get(i); // puede ser null => frame libre
                Integer page = frameToPage.get(i); // puede ser null
                String procName = (p != null) ? p.getPid() : "Free";
                String pageStr = (page != null) ? String.valueOf(page) : "-";
                Object[] row = { i, procName, pageStr };
                tableModel.addRow(row);
            }

            pageFaultsLabel.setText("Page Faults: " + memoryManager.getTotalPageFaults());
            replacementsLabel.setText("Replacements: " + memoryManager.getTotalReplacements());
        } else {
            tableModel.setRowCount(0);
            pageFaultsLabel.setText("Page Faults: 0");
            replacementsLabel.setText("Replacements: 0");
        }
    }
}
