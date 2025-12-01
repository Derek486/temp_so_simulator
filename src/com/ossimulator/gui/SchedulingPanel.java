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

public class SchedulingPanel extends JPanel {
    private OSSimulator simulator;
    private static final int LEFT_LABEL_WIDTH = 80;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_SPACING = 8;
    private static final int TICK_WIDTH = 12; // pixels per time unit; ajusta si quieres zoom

    public SchedulingPanel() {
        setPreferredSize(new Dimension(900, 300));
    }

    public void updateData(OSSimulator sim) {
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
        int n = processes.size();
        if (n == 0) {
            g.drawString("No processes loaded", 10, 20);
            return;
        }

        int cpuAreaTop = 10;
        int cpuAreaHeight = (ROW_HEIGHT + ROW_SPACING) * n;
        int ioAreaTop = cpuAreaTop + cpuAreaHeight + 40;
        int ioAreaHeight = cpuAreaHeight;

        // titulos
        g.setColor(Color.BLACK);
        g.drawString("CPU Gantt", LEFT_LABEL_WIDTH, cpuAreaTop - 4);
        g.drawString("I/O Gantt", LEFT_LABEL_WIDTH, ioAreaTop - 4);

        // dibujar grid de ticks horizontales
        int currentTime = simulator.getCurrentTime();
        int widthNeeded = LEFT_LABEL_WIDTH + (currentTime + 10) * TICK_WIDTH; // algo de margen
        if (widthNeeded > getWidth()) {
            setPreferredSize(new Dimension(widthNeeded, Math.max(getHeight(), ioAreaTop + ioAreaHeight + 20)));
            revalidate();
        }

        // marcar ticks y líneas verticales
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

        // dibujar filas de procesos
        for (int i = 0; i < n; i++) {
            Proceso p = processes.get(i);
            int rowY = cpuAreaTop + i * (ROW_HEIGHT + ROW_SPACING);

            // dibujar etiqueta PID
            g.setColor(Color.BLACK);
            g.drawString(p.getPid(), 8, rowY + ROW_HEIGHT - 4);

            // dibujar intervalos CPU
            for (Interval itv : p.getCpuIntervals()) {
                int x1 = LEFT_LABEL_WIDTH + itv.start * TICK_WIDTH;
                int x2 = LEFT_LABEL_WIDTH + itv.end * TICK_WIDTH;
                int w = Math.max(1, x2 - x1);
                g.setColor(new Color(70, 130, 180)); // steel blue
                g.fillRect(x1, rowY, w, ROW_HEIGHT);
            }

            // si hay un intervalo CPU abierto (en ejecución) dibujarlo hasta currentTime
            if (p.getCpuIntervals().isEmpty() == false) {
                // ya dibujados
            }
            // dibujar I/O en la zona inferior
            int ioRowY = ioAreaTop + i * (ROW_HEIGHT + ROW_SPACING);
            g.setColor(Color.BLACK);
            g.drawString(p.getPid(), 8, ioRowY + ROW_HEIGHT - 4);

            for (Interval itv : p.getIoIntervals()) {
                int x1 = LEFT_LABEL_WIDTH + itv.start * TICK_WIDTH;
                int x2 = LEFT_LABEL_WIDTH + itv.end * TICK_WIDTH;
                int w = Math.max(1, x2 - x1);
                g.setColor(new Color(220, 100, 80)); // rojo suave
                g.fillRect(x1, ioRowY, w, ROW_HEIGHT);
            }

            // dibujar separador (línea fina)
            g.setColor(new Color(180, 180, 180));
            int sepY = rowY + ROW_HEIGHT + ROW_SPACING / 2;
            g.drawLine(LEFT_LABEL_WIDTH, sepY, getWidth() - 10, sepY);
        }

        // Leyenda
        int legendY = ioAreaTop + ioAreaHeight + 24;
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
