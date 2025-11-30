package com.ossimulator.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import com.ossimulator.process.Proceso;

public class GanttChart extends JPanel {
    private List<Proceso> processes;
    private Map<String, List<Integer>> processTimelines;
    private int maxTime;

    public GanttChart() {
        this.processes = new ArrayList<>();
        this.processTimelines = new HashMap<>();
        this.maxTime = 0;
        setPreferredSize(new Dimension(800, 300));
        setBackground(new Color(245, 245, 245));
    }

    public void updateChart(List<Proceso> allProcesses, int currentTime) {
        this.processes = new ArrayList<>(allProcesses);
        this.maxTime = currentTime + 10;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int margin = 50;
        int chartWidth = getWidth() - 2 * margin;
        int chartHeight = getHeight() - 2 * margin;
        int rowHeight = 40;

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(margin, margin, chartWidth, chartHeight);

        g2.setFont(new Font("Arial", Font.BOLD, 12));

        for (int i = 0; i <= maxTime; i += 5) {
            int x = margin + (int) ((double) i / maxTime * chartWidth);
            g2.drawLine(x, margin + chartHeight, x, margin + chartHeight + 5);
            g2.drawString(String.valueOf(i), x - 10, margin + chartHeight + 20);
        }

        g2.setFont(new Font("Arial", Font.PLAIN, 11));
        int yPos = margin + 20;

        for (Proceso p : processes) {
            g2.drawString(p.getPid(), 10, yPos + 15);

            if (p.getStartTime() != -1 && p.getEndTime() != -1) {
                int startX = margin + (int) ((double) p.getStartTime() / maxTime * chartWidth);
                int endX = margin + (int) ((double) p.getEndTime() / maxTime * chartWidth);
                int width = Math.max(1, endX - startX);

                Color color;
                if (p.isComplete()) {
                    color = new Color(76, 175, 80);
                } else {
                    color = new Color(255, 152, 0);
                }

                g2.setColor(color);
                g2.fillRect(startX, yPos, width, rowHeight - 10);
                g2.setColor(Color.BLACK);
                g2.drawRect(startX, yPos, width, rowHeight - 10);

                g2.drawString(p.getPid(), startX + 5, yPos + 20);
            }

            yPos += rowHeight;
        }
    }
}
