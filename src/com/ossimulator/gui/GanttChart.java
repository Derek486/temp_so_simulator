package com.ossimulator.gui;

import com.ossimulator.process.Proceso;
import com.ossimulator.process.Proceso.Interval;
import com.ossimulator.simulator.OSSimulator;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.util.List;
import javax.swing.JPanel;

/**
 * GanttChart
 *
 * Componente Swing que pinta un diagrama tipo Gantt dividido en dos zonas:
 * - CPU Gantt: muestra las ráfagas de CPU por proceso.
 * - I/O Gantt: muestra las ráfagas de E/S por proceso.
 *
 * Esta clase mantiene solo la referencia al simulador (lectura),
 * calcula tamaño dinámico según el tiempo actual y dibuja las barras por
 * intervalos. No muta el simulador.
 */
public class GanttChart extends JPanel {
    private static final long serialVersionUID = 1L;

    private OSSimulator simulator;

    private static final int LEFT_LABEL_WIDTH = 80;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_SPACING = 8;
    private static final int TICK_WIDTH = 14;
    private static final int MARGIN_RIGHT = 20;

    public GanttChart() {
        setPreferredSize(new Dimension(900, 400));
    }

    /**
     * Actualiza la referencia al simulador y fuerza repintado.
     * Debe llamarse desde EDT (SwingUtilities.invokeLater) cuando provenga de hilos.
     *
     * @param sim instancia del simulador (puede ser null para limpiar la vista)
     */
    public void updateChart(OSSimulator sim) {
        this.simulator = sim;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));

        if (simulator == null) {
            g.drawString("No simulation", 10, 20);
            return;
        }

        List<Proceso> processes = simulator.getAllProcesses();
        if (processes == null || processes.isEmpty()) {
            g.drawString("No processes loaded", 10, 20);
            return;
        }

        final int n = processes.size();
        final int cpuAreaTop = 10;
        final int cpuAreaHeight = (ROW_HEIGHT + ROW_SPACING) * n;
        final int ioAreaTop = cpuAreaTop + cpuAreaHeight + 40;
        final int ioAreaHeight = cpuAreaHeight;

        final int currentTime = simulator.getCurrentTime();
        final int widthNeeded = LEFT_LABEL_WIDTH + (currentTime + 10) * TICK_WIDTH + MARGIN_RIGHT;
        if (widthNeeded > getWidth()) {
            setPreferredSize(new Dimension(widthNeeded, Math.max(getHeight(), ioAreaTop + ioAreaHeight + 40)));
            revalidate();
        }

        drawTicks(g, currentTime, cpuAreaTop, ioAreaTop, ioAreaHeight);
        drawProcessRows(g, processes, cpuAreaTop, ioAreaTop);
        drawLegend(g, ioAreaTop + ioAreaHeight + 24);
    }

    private void drawTicks(Graphics g, int currentTime, int cpuAreaTop, int ioAreaTop, int ioAreaHeight) {
        g.setColor(new Color(220, 220, 220));
        for (int t = 0; t <= currentTime + 5; t++) {
            int x = LEFT_LABEL_WIDTH + t * TICK_WIDTH;
            g.drawLine(x, cpuAreaTop - 6, x, ioAreaTop + ioAreaHeight + 6);
            if (t % 5 == 0) {
                g.setColor(Color.DARK_GRAY);
                g.drawString(String.valueOf(t), x + 2, cpuAreaTop - 10);
                g.setColor(new Color(220, 220, 220));
            }
        }
    }

    private void drawProcessRows(Graphics g, List<Proceso> processes, int cpuAreaTop, int ioAreaTop) {
        for (int i = 0; i < processes.size(); i++) {
            Proceso p = processes.get(i);
            int rowY = cpuAreaTop + i * (ROW_HEIGHT + ROW_SPACING);

            g.setColor(Color.BLACK);
            g.drawString(p.getPid(), 8, rowY + ROW_HEIGHT - 4);

            try {
                List<Proceso.Interval> cpuIntervals = p.getCpuIntervals();
                drawIntervals(g, cpuIntervals, rowY, new Color(70, 130, 180));

                int ioRowY = ioAreaTop + i * (ROW_HEIGHT + ROW_SPACING);
                g.setColor(Color.BLACK);
                g.drawString(p.getPid(), 8, ioRowY + ROW_HEIGHT - 4);

                List<Proceso.Interval> ioIntervals = p.getIoIntervals();
                drawIntervals(g, ioIntervals, ioRowY, new Color(220, 100, 80));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            g.setColor(new Color(180, 180, 180));
            int sepY = rowY + ROW_HEIGHT + ROW_SPACING / 2;
            g.drawLine(LEFT_LABEL_WIDTH, sepY, getWidth() - 10, sepY);
        }
    }

    private void drawIntervals(Graphics g, java.util.List<Interval> intervals, int y, Color fill) {
        if (intervals == null) return;
        for (Interval itv : intervals) {
            int startTick = itv.start;
            int endTick = itv.end;
            if (endTick <= startTick) continue;
            int x1 = LEFT_LABEL_WIDTH + startTick * TICK_WIDTH;
            int width = (endTick - startTick) * TICK_WIDTH;
            if (width > 1) width = width - 1;

            g.setColor(fill);
            g.fillRect(x1, y, width, ROW_HEIGHT);
            g.setColor(Color.BLACK);
            g.drawRect(x1, y, Math.max(0, width - 1), ROW_HEIGHT - 1);
        }
    }

    private void drawLegend(Graphics g, int legendY) {
        g.setColor(new Color(70, 130, 180));
        g.fillRect(LEFT_LABEL_WIDTH, legendY, 14, 10);
        g.setColor(Color.BLACK);
        g.drawString("CPU burst", LEFT_LABEL_WIDTH + 18, legendY + 10);

        g.setColor(new Color(220, 100, 80));
        g.fillRect(LEFT_LABEL_WIDTH + 120, legendY, 14, 10);
        g.setColor(Color.BLACK);
        g.drawString("I/O burst", LEFT_LABEL_WIDTH + 140, legendY + 10);
    }
}
