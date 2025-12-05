package com.ossimulator.gui;

import com.ossimulator.memory.MemoryManager;
import com.ossimulator.memory.MemoryManager.AccessEvent;
import com.ossimulator.process.Proceso;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * MemoryVisualizerPanel
 *
 * Panel compuesto por:
 * - Un canvas tipo Gantt en la parte superior que muestra, por cada frame
 * (fila),
 * los AccessEvent como pequeñas barras alineadas por secuencia
 * (AccessEvent.seq).
 * - Una tabla en el centro que presenta un snapshot simple: Frame | Process |
 * Page.
 * - Un panel de estadísticas en la parte inferior con contadores (page faults y
 * replacements).
 *
 * El método {@link #updateData(MemoryManager)} aplica un snapshot leido desde
 * MemoryManager y debe invocarse desde el hilo de la UI (EDT). Internamente
 * maneja
 * InterruptedException que pueda lanzar el MemoryManager al tomar snapshots.
 */
public class MemoryVisualizerPanel extends JPanel {
  private static final long serialVersionUID = 1L;

  private final GanttCanvas canvas;
  private final JTable frameTable;
  private final DefaultTableModel tableModel;
  private final JLabel pageFaultsLabel;
  private final JLabel replacementsLabel;

  /**
   * Construye el panel, crea componentes y dispone el layout (canvas arriba,
   * tabla centro, stats abajo).
   */
  public MemoryVisualizerPanel() {
    setLayout(new BorderLayout());
    setBackground(new Color(250, 250, 250));

    canvas = new GanttCanvas();
    JScrollPane canvasScroll = new JScrollPane(canvas);
    canvasScroll.setPreferredSize(new Dimension(1000, 260));
    canvasScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

    tableModel = new DefaultTableModel(new String[] { "Frame", "Process", "Page" }, 0) {
      private static final long serialVersionUID = 1L;

      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
    frameTable = new JTable(tableModel);
    frameTable.setRowHeight(22);

    JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT));
    pageFaultsLabel = new JLabel("Page Faults: 0");
    replacementsLabel = new JLabel("Replacements: 0");
    stats.add(pageFaultsLabel);
    stats.add(Box.createHorizontalStrut(20));
    stats.add(replacementsLabel);

    add(canvasScroll, BorderLayout.NORTH);
    add(new JScrollPane(frameTable), BorderLayout.CENTER);
    add(stats, BorderLayout.SOUTH);
  }

  /**
   * Actualiza la vista con un snapshot tomado del MemoryManager.
   *
   * <p>
   * Advertencia: este método debe invocarse desde el hilo de la interfaz (EDT).
   * Si se llama desde otro hilo, envolver con
   * {@code SwingUtilities.invokeLater(...)}.
   * </p>
   *
   * @param memoryManager instancia de MemoryManager o {@code null} para limpiar
   *                      la vista
   */
  public void updateData(MemoryManager memoryManager) {
    if (memoryManager == null) {
      tableModel.setRowCount(0);
      pageFaultsLabel.setText("Page Faults: 0");
      replacementsLabel.setText("Replacements: 0");
      canvas.setSnapshot(null, 0L, 0);
      return;
    }

    try {
      Map<Integer, Proceso> frames = memoryManager.getFrameStatus();
      Map<Integer, Integer> framePages = memoryManager.getFrameToPageMap();
      Map<Integer, List<AccessEvent>> history = memoryManager.getFrameAccessHistorySnapshot();
      int totalFrames = memoryManager.getTotalFrames();
      int currentTime = memoryManager.getCurrentTime();
      long maxSeq = memoryManager.getMaxAccessSequence();

      tableModel.setRowCount(0);
      for (int i = 0; i < totalFrames; i++) {
        Proceso p = frames.get(i);
        Integer pg = framePages.get(i);
        String pname = (p != null) ? p.getPid() : "Free";
        String pstr = (pg == null || pg < 0) ? "-" : String.valueOf(pg);
        tableModel.addRow(new Object[] { i, pname, pstr });
      }

      pageFaultsLabel.setText("Page Faults: " + memoryManager.getTotalPageFaults());
      replacementsLabel.setText("Replacements: " + memoryManager.getTotalReplacements());

      canvas.setSnapshot(history, maxSeq, currentTime);
      canvas.revalidate();
      canvas.repaint();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Canvas interno que pinta una fila por frame y una columna por índice de
   * secuencia (seq).
   */
  private static class GanttCanvas extends JPanel {
    private static final long serialVersionUID = 1L;

    private Map<Integer, List<AccessEvent>> history = Collections.emptyMap();
    private long maxSeq = 0L;
    private int currentTime = 0;

    private static final int ROW_HEIGHT = 26;
    private static final int ROW_SPACING = 6;
    private static final int LEFT_COL = 56;
    private static final int TICK_W = 18;
    private static final int TOP_PADDING = 14;
    private static final int RIGHT_PADDING = 40;

    /**
     * Construye el canvas con tamaño preferido inicial y fondo blanco.
     */
    public GanttCanvas() {
      setPreferredSize(new Dimension(1200, 300));
      setBackground(Color.WHITE);
    }

    /**
     * Establece el snapshot que se pintará y recalcula el tamaño preferido del
     * canvas.
     *
     * @param history     mapa frame -> lista de AccessEvent (puede ser
     *                    {@code null})
     * @param maxSeq      valor máximo de secuencia observado
     * @param currentTime tiempo actual de simulación (solo informativo)
     */
    public void setSnapshot(Map<Integer, List<AccessEvent>> history, long maxSeq, int currentTime) {
      this.history = (history == null) ? Collections.emptyMap() : history;
      this.maxSeq = Math.max(0L, maxSeq);
      this.currentTime = currentTime;

      int rows = Math.max(1, this.history.size());
      int height = TOP_PADDING + rows * (ROW_HEIGHT + ROW_SPACING) + 80;
      int width = LEFT_COL + (int) ((this.maxSeq + 6) * TICK_W) + RIGHT_PADDING;
      setPreferredSize(new Dimension(Math.max(width, 600), Math.max(height, 180)));
    }

    @Override
    protected void paintComponent(Graphics g0) {
      super.paintComponent(g0);
      Graphics2D g = (Graphics2D) g0.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g.setColor(new Color(250, 250, 250));
      g.fillRect(0, 0, getWidth(), getHeight());

      g.setColor(new Color(230, 230, 230));
      long columns = Math.max(1L, Math.max(6L, maxSeq + 2));
      for (long s = 0; s <= columns; s++) {
        int x = LEFT_COL + (int) (s * TICK_W);
        g.drawLine(x, TOP_PADDING - 6, x, getHeight() - 40);
      }

      g.setColor(Color.DARK_GRAY);
      int labelStep = (maxSeq <= 40) ? 1 : Math.max(5, (int) Math.ceil(maxSeq / 40.0) + 4);
      for (long s = 0; s <= maxSeq + 1; s += labelStep) {
        int x = LEFT_COL + (int) (s * TICK_W);
        g.drawString(String.valueOf(s == 0 ? 0 : s), x + 2, TOP_PADDING - 2);
      }

      if (history == null || history.isEmpty()) {
        g.setColor(Color.DARK_GRAY);
        g.drawString("No memory events yet", LEFT_COL + 10, TOP_PADDING + 20);
        g.dispose();
        return;
      }

      List<Integer> frames = history.keySet().stream().sorted().collect(Collectors.toList());
      if (frames.isEmpty()) {
        g.setColor(Color.DARK_GRAY);
        g.drawString("No memory events yet", LEFT_COL + 10, TOP_PADDING + 20);
        g.dispose();
        return;
      }

      int rowIndex = 0;
      for (Integer frame : frames) {
        int y = TOP_PADDING + rowIndex * (ROW_HEIGHT + ROW_SPACING);

        g.setColor(new Color(245, 245, 245));
        g.fillRect(0, y, LEFT_COL - 8, ROW_HEIGHT);
        g.setColor(Color.BLACK);
        g.drawString("F" + frame, 8, y + ROW_HEIGHT - 10);

        g.setColor(new Color(235, 235, 235));
        g.fillRect(LEFT_COL - 2, y, (int) ((maxSeq + 6) * TICK_W) + 4, ROW_HEIGHT);

        List<AccessEvent> evs = history.get(frame);
        if (evs != null) {
          for (AccessEvent ev : evs) {
            long seqIndex = Math.max(1L, ev.seq);
            int x = LEFT_COL + (int) ((seqIndex - 1) * TICK_W);
            int w = Math.max(2, TICK_W - 2);

            if (ev.hit) {
              g.setColor(new Color(80, 180, 80));
            } else {
              if ("evict".equals(ev.note) || "force-evict".equals(ev.note)) {
                g.setColor(new Color(200, 80, 80));
              } else {
                g.setColor(new Color(240, 160, 40));
              }
            }

            g.fillRoundRect(x + 1, y + 3, w, ROW_HEIGHT - 6, 6, 6);

            g.setColor(Color.BLACK);
            String label = (ev.page >= 0) ? String.valueOf(ev.page) : "X";
            FontMetrics fm = g.getFontMetrics();
            int strW = fm.stringWidth(label);
            int tx = x + Math.max(2, (w - strW) / 2);
            int ty = y + (ROW_HEIGHT + fm.getAscent()) / 2 - 4;
            g.drawString(label, tx, ty);

            if ("evict".equals(ev.note) || "force-evict".equals(ev.note)) {
              g.setColor(new Color(120, 20, 20));
              g.drawString("E", x + 1, y + 12);
            }
          }
        }

        rowIndex++;
      }

      int legendY = getHeight() - 28;
      g.setColor(new Color(80, 180, 80));
      g.fillRect(LEFT_COL, legendY, 14, 10);
      g.setColor(Color.BLACK);
      g.drawString("Hit", LEFT_COL + 18, legendY + 10);

      g.setColor(new Color(240, 160, 40));
      g.fillRect(LEFT_COL + 80, legendY, 14, 10);
      g.setColor(Color.BLACK);
      g.drawString("Load / Miss / Alloc", LEFT_COL + 100, legendY + 10);

      g.setColor(new Color(200, 80, 80));
      g.fillRect(LEFT_COL + 260, legendY, 14, 10);
      g.setColor(Color.BLACK);
      g.drawString("Eviction", LEFT_COL + 278, legendY + 10);

      g.setColor(Color.DARK_GRAY);
      g.drawString("sim time: " + currentTime + "   maxSeq: " + maxSeq, getWidth() - 220, legendY + 10);

      g.dispose();
    }
  }
}
