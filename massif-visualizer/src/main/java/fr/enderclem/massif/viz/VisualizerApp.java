package fr.enderclem.massif.viz;

import fr.enderclem.massif.api.BorderField;
import fr.enderclem.massif.api.DrainageBasin;
import fr.enderclem.massif.api.DrainageBasins;
import fr.enderclem.massif.api.DrainageGraph;
import fr.enderclem.massif.api.Massif;
import fr.enderclem.massif.api.MassifFramework;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.MountainCluster;
import fr.enderclem.massif.api.MountainClusters;
import fr.enderclem.massif.api.SpineEdge;
import fr.enderclem.massif.api.WorldWindow;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneField;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Swing visualizer for the Massif pipeline.
 *
 * <p>Imports are intentionally restricted to {@code api} and {@code blackboard}.
 * Anything this app can display exists because the framework published it
 * through the blackboard — if the visualizer can't show something, the
 * framework needs to publish it.
 *
 * <p>Phase 4 capabilities: editable seed combo, saved-seed dropdown,
 * randomize, save, five rendering views (Heightmap / Zones / Border
 * distance / Zone graph / Mountain clusters), plus zoom / pan controls and
 * a live status line reporting the current world window. Each zoom or pan
 * rebuilds the framework with a new {@link WorldWindow}; panning outside
 * the default 512-block view widens the Lloyd-relaxed area automatically.
 */
public final class VisualizerApp extends JFrame {

    private static final int MIN_WINDOW_SIZE = 128;
    private static final int MAX_WINDOW_SIZE = 2048;
    private static final Path SEEDS_FILE = Paths.get(
        System.getProperty("user.home"), ".massif", "seeds.tsv");

    private WorldWindow currentWindow = WorldWindow.defaultWindow();

    private final JComboBox<SavedSeed> seedCombo = new JComboBox<>();
    private final JComboBox<View> viewCombo = new JComboBox<>(View.values());
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel windowLabel = new JLabel(" ");
    private final JTextArea keyListing = new JTextArea();
    private final RenderPanel canvas = new RenderPanel();

    private Blackboard.Sealed lastBoard;
    private WorldWindow lastWindow;
    private boolean loadingCombo = false;

    private VisualizerApp() {
        super("Massif — Phase 4 walking skeleton");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUi();
        pack();
        setLocationRelativeTo(null);
        regenerate();
    }

    // ------------------------------------------------------------------
    //  UI wiring
    // ------------------------------------------------------------------

    private void buildUi() {
        JPanel seedRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        seedRow.add(new JLabel("Seed:"));

        seedCombo.setEditable(true);
        seedCombo.setPrototypeDisplayValue(new SavedSeed("xxxxxxxxxxxxxxxxxxxx", 0L));
        if (seedCombo.getEditor().getEditorComponent() instanceof JTextField jtf) {
            jtf.setColumns(18);
        }
        seedCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                                                                   int index, boolean isSelected,
                                                                   boolean cellHasFocus) {
                String display = value instanceof SavedSeed s
                    ? s.label() + "  (" + s.seed() + ")"
                    : value == null ? "" : value.toString();
                return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
            }
        });
        seedCombo.addActionListener(e -> { if (!loadingCombo) regenerate(); });
        setSeedText("1234");
        seedRow.add(seedCombo);

        JButton randomBtn = new JButton("Randomize");
        randomBtn.addActionListener(e -> {
            setSeedText(Long.toString(ThreadLocalRandom.current().nextLong()));
            regenerate();
        });
        seedRow.add(randomBtn);

        JButton saveBtn = new JButton("Save");
        saveBtn.setToolTipText("Save the current seed to " + SEEDS_FILE);
        saveBtn.addActionListener(e -> promptAndSave());
        seedRow.add(saveBtn);

        JButton goBtn = new JButton("Generate");
        goBtn.addActionListener(e -> regenerate());
        seedRow.add(goBtn);

        seedRow.add(new JLabel("View:"));
        viewCombo.addActionListener(e -> renderLast());
        seedRow.add(viewCombo);

        JPanel viewRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        viewRow.add(new JLabel("Pan:"));
        viewRow.add(panButton("←", -1, 0));
        viewRow.add(panButton("→",  1, 0));
        viewRow.add(panButton("↑",  0, -1));
        viewRow.add(panButton("↓",  0,  1));
        viewRow.add(new JLabel("  Zoom:"));
        JButton zoomIn = new JButton("+");
        zoomIn.setToolTipText("Zoom in (halve the window size)");
        zoomIn.addActionListener(e -> zoom(0.5));
        viewRow.add(zoomIn);
        JButton zoomOut = new JButton("−");
        zoomOut.setToolTipText("Zoom out (double the window size)");
        zoomOut.addActionListener(e -> zoom(2.0));
        viewRow.add(zoomOut);
        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> {
            currentWindow = WorldWindow.defaultWindow();
            regenerate();
        });
        viewRow.add(resetBtn);
        viewRow.add(windowLabel);

        canvas.addMouseWheelListener(this::handleWheel);
        canvas.addMouseListener(new DragPanHandler());
        canvas.addMouseMotionListener(new DragPanHandler());

        keyListing.setEditable(false);
        keyListing.setRows(6);
        keyListing.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));

        reloadSavedCombo();

        JPanel controls = new JPanel(new BorderLayout());
        controls.add(seedRow, BorderLayout.NORTH);
        controls.add(viewRow, BorderLayout.SOUTH);

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(new JScrollPane(keyListing), BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(controls, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private JButton panButton(String label, int dirX, int dirZ) {
        JButton b = new JButton(label);
        b.addActionListener(e -> pan(dirX, dirZ));
        return b;
    }

    // ------------------------------------------------------------------
    //  Zoom / pan
    // ------------------------------------------------------------------

    private void zoom(double factor) {
        int newSize = (int) Math.round(currentWindow.size() * factor);
        newSize = Math.max(MIN_WINDOW_SIZE, Math.min(MAX_WINDOW_SIZE, newSize));
        if (newSize == currentWindow.size()) return;
        currentWindow = new WorldWindow(currentWindow.centerX(), currentWindow.centerZ(), newSize);
        regenerate();
    }

    private void pan(int dirX, int dirZ) {
        int step = Math.max(1, currentWindow.size() / 4);
        currentWindow = new WorldWindow(
            currentWindow.centerX() + dirX * step,
            currentWindow.centerZ() + dirZ * step,
            currentWindow.size());
        regenerate();
    }

    private void handleWheel(MouseWheelEvent e) {
        if (e.getWheelRotation() < 0) zoom(0.5);
        else if (e.getWheelRotation() > 0) zoom(2.0);
    }

    /**
     * Click-drag on the canvas pans the window. Updates are applied on
     * release (not during drag) so each drag produces one regenerate
     * instead of hundreds.
     */
    private final class DragPanHandler extends MouseAdapter {
        private int startX;
        private int startZ;
        private WorldWindow startWindow;

        @Override
        public void mousePressed(MouseEvent e) {
            startX = e.getX();
            startZ = e.getY();
            startWindow = currentWindow;
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (startWindow == null) return;
            int canvasSide = Math.min(canvas.getWidth(), canvas.getHeight());
            if (canvasSide <= 0) return;
            double scale = (double) startWindow.size() / canvasSide;
            int dx = (int) Math.round((startX - e.getX()) * scale);
            int dz = (int) Math.round((startZ - e.getY()) * scale);
            if (dx == 0 && dz == 0) return;
            currentWindow = new WorldWindow(
                startWindow.centerX() + dx,
                startWindow.centerZ() + dz,
                startWindow.size());
            startWindow = null;
            regenerate();
        }
    }

    // ------------------------------------------------------------------
    //  Pipeline trigger + rendering
    // ------------------------------------------------------------------

    private void regenerate() {
        long seed;
        try {
            seed = Long.parseLong(currentSeedText());
        } catch (NumberFormatException ex) {
            statusLabel.setText("Seed must be a long integer");
            return;
        }
        MassifFramework fw = Massif.framework(
            ZoneTypeRegistry.defaultRegistry(),
            Massif.DEFAULT_LLOYD_ITERATIONS,
            currentWindow);

        long t0 = System.nanoTime();
        Blackboard.Sealed board = fw.generate(seed);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        lastBoard = board;
        lastWindow = currentWindow;
        renderLast();
        statusLabel.setText(String.format(
            "seed=%d  generated in %d ms  |  %d keys on blackboard",
            seed, ms, board.keys().size()));
        windowLabel.setText(String.format(
            "  center=(%d, %d) size=%d  (x: %d..%d  z: %d..%d)",
            currentWindow.centerX(), currentWindow.centerZ(), currentWindow.size(),
            currentWindow.x0(), currentWindow.x1(),
            currentWindow.z0(), currentWindow.z1()));
        keyListing.setText(formatKeyList(board));
        keyListing.setCaretPosition(0);
    }

    private void renderLast() {
        if (lastBoard == null || lastWindow == null) return;
        View view = (View) viewCombo.getSelectedItem();
        if (view == null) view = View.HEIGHTMAP;
        canvas.setImage(view.render(lastBoard, lastWindow));
    }

    private enum View {
        HEIGHTMAP("Heightmap") {
            @Override BufferedImage render(Blackboard.Sealed board, WorldWindow w) {
                return heightmapImage(board.get(MassifKeys.HEIGHTMAP));
            }
        },
        ZONES("Zones") {
            @Override BufferedImage render(Blackboard.Sealed board, WorldWindow w) {
                return zonesImage(board.get(MassifKeys.ZONE_FIELD),
                                  board.get(MassifKeys.ZONE_REGISTRY), w);
            }
        },
        BORDERS("Border distance") {
            @Override BufferedImage render(Blackboard.Sealed board, WorldWindow w) {
                return borderDistanceImage(board.get(MassifKeys.BORDER_FIELD), w);
            }
        },
        GRAPH("Zone graph") {
            @Override BufferedImage render(Blackboard.Sealed board, WorldWindow w) {
                return zoneGraphImage(board.get(MassifKeys.ZONE_FIELD),
                                      board.get(MassifKeys.ZONE_REGISTRY),
                                      board.get(MassifKeys.ZONE_GRAPH), w);
            }
        },
        MOUNTAIN_CLUSTERS("Mountain clusters") {
            @Override BufferedImage render(Blackboard.Sealed board, WorldWindow w) {
                return mountainClustersImage(board.get(MassifKeys.ZONE_FIELD),
                                             board.get(MassifKeys.ZONE_REGISTRY),
                                             board.get(MassifKeys.ZONE_GRAPH),
                                             board.get(MassifKeys.MOUNTAIN_CLUSTERS), w);
            }
        },
        ELEVATION("Elevation") {
            @Override BufferedImage render(Blackboard.Sealed board, WorldWindow w) {
                return elevationImage(board.get(MassifKeys.ZONE_GRAPH),
                                      board.get(MassifKeys.DRAINAGE_GRAPH), w);
            }
        },
        BASINS("Drainage basins") {
            @Override BufferedImage render(Blackboard.Sealed board, WorldWindow w) {
                return basinsImage(board.get(MassifKeys.ZONE_GRAPH),
                                   board.get(MassifKeys.DRAINAGE_GRAPH),
                                   board.get(MassifKeys.DRAINAGE_BASINS), w);
            }
        };

        private final String label;
        View(String label) { this.label = label; }
        @Override public String toString() { return label; }
        abstract BufferedImage render(Blackboard.Sealed board, WorldWindow window);
    }

    private static BufferedImage heightmapImage(float[][] field) {
        int size = field.length;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (float[] row : field) for (float v : row) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        float range = Math.max(1e-6f, max - min);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                float t = (field[z][x] - min) / range;
                int g = Math.round(Math.max(0f, Math.min(1f, t)) * 255f);
                img.setRGB(x, z, (g << 16) | (g << 8) | g);
            }
        }
        return img;
    }

    private static BufferedImage zonesImage(ZoneField field, ZoneTypeRegistry registry, WorldWindow w) {
        int size = w.size();
        int[][] grid = field.sampleGrid(w.x0(), w.z0(), size, size);
        int[] palette = new int[registry.size()];
        for (int i = 0; i < palette.length; i++) palette[i] = registry.get(i).displayColour();
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                img.setRGB(x, z, palette[grid[z][x]]);
            }
        }
        return img;
    }

    private static BufferedImage borderDistanceImage(BorderField field, WorldWindow w) {
        int size = w.size();
        double[][] dist = new double[size][size];
        double max = 0.0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                double d = field.sampleAt(w.x0() + x + 0.5, w.z0() + z + 0.5).distance();
                dist[z][x] = d;
                if (d > max) max = d;
            }
        }
        if (max <= 0.0) max = 1.0;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int g = (int) Math.round(255.0 * (dist[z][x] / max));
                if (g < 0) g = 0; else if (g > 255) g = 255;
                img.setRGB(x, z, (g << 16) | (g << 8) | g);
            }
        }
        return img;
    }

    private static BufferedImage zoneGraphImage(ZoneField field, ZoneTypeRegistry registry,
                                                ZoneGraph graph, WorldWindow w) {
        BufferedImage img = zonesImage(field, registry, w);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(new Color(0, 0, 0, 160));
        Map<Integer, ZoneCell> byId = graph.byId();
        for (ZoneCell c : graph.cells()) {
            int cx = toPixel(c.seedX(), w.x0());
            int cz = toPixel(c.seedZ(), w.z0());
            for (int nid : c.neighbourIds()) {
                if (nid <= c.id()) continue;
                ZoneCell n = byId.get(nid);
                if (n == null) continue;
                g2.drawLine(cx, cz, toPixel(n.seedX(), w.x0()), toPixel(n.seedZ(), w.z0()));
            }
        }
        g2.setColor(Color.WHITE);
        for (ZoneCell c : graph.cells()) {
            int cx = toPixel(c.seedX(), w.x0());
            int cz = toPixel(c.seedZ(), w.z0());
            g2.fillOval(cx - 3, cz - 3, 6, 6);
        }
        g2.setColor(Color.BLACK);
        for (ZoneCell c : graph.cells()) {
            int cx = toPixel(c.seedX(), w.x0());
            int cz = toPixel(c.seedZ(), w.z0());
            g2.drawOval(cx - 3, cz - 3, 6, 6);
        }
        g2.dispose();
        return img;
    }

    private static BufferedImage mountainClustersImage(ZoneField field, ZoneTypeRegistry registry,
                                                       ZoneGraph graph, MountainClusters clusters,
                                                       WorldWindow w) {
        BufferedImage img = zonesImage(field, registry, w);
        int size = w.size();

        Map<Integer, Integer> cellToCluster = new HashMap<>();
        for (MountainCluster c : clusters.clusters()) {
            for (int cid : c.cellIds()) cellToCluster.put(cid, c.id());
        }

        ZoneCell[] cells = graph.cells().toArray(new ZoneCell[0]);
        for (int z = 0; z < size; z++) {
            double wz = w.z0() + z + 0.5;
            for (int x = 0; x < size; x++) {
                double wx = w.x0() + x + 0.5;
                ZoneCell nearest = null;
                double bestDistSq = Double.POSITIVE_INFINITY;
                for (ZoneCell c : cells) {
                    double dx = c.seedX() - wx;
                    double dz = c.seedZ() - wz;
                    double d = dx * dx + dz * dz;
                    if (d < bestDistSq) { bestDistSq = d; nearest = c; }
                }
                if (nearest == null) continue;
                Integer clusterId = cellToCluster.get(nearest.id());
                if (clusterId == null) continue;
                int base = img.getRGB(x, z);
                img.setRGB(x, z, blendRgb(base, colourForCluster(clusterId), 0.35f));
            }
        }

        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(2.0f));
        Map<Integer, ZoneCell> byId = graph.byId();
        for (MountainCluster c : clusters.clusters()) {
            int colour = colourForCluster(c.id());
            // Spine tree: one segment per tree edge. Y / branching clusters
            // get all their arms rendered from their junction outward.
            g2.setColor(new Color(colour));
            for (SpineEdge edge : c.spineEdges()) {
                ZoneCell a = byId.get(edge.fromCellId());
                ZoneCell b = byId.get(edge.toCellId());
                if (a == null || b == null) continue;
                g2.drawLine(
                    toPixel(a.seedX(), w.x0()), toPixel(a.seedZ(), w.z0()),
                    toPixel(b.seedX(), w.x0()), toPixel(b.seedZ(), w.z0()));
            }
            int cx = toPixel(c.representativeX(), w.x0());
            int cz = toPixel(c.representativeZ(), w.z0());
            g2.setColor(new Color(colour));
            g2.fillOval(cx - 5, cz - 5, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawOval(cx - 5, cz - 5, 10, 10);
        }
        g2.dispose();
        return img;
    }

    /** Convert a world coordinate to the pixel offset within the current window. */
    private static int toPixel(double worldCoord, int windowOrigin) {
        return (int) Math.round(worldCoord - windowOrigin);
    }

    /**
     * Each cell painted with its water-level elevation (water-level not
     * raw elevation — so lakes and oceans appear flat). Linear grayscale
     * ramp normalised over the window; extremes auto-scale.
     */
    private static BufferedImage elevationImage(ZoneGraph graph, DrainageGraph drainage, WorldWindow w) {
        int size = w.size();
        ZoneCell[] cells = graph.cells().toArray(new ZoneCell[0]);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (var d : drainage.byCellId().values()) {
            double v = d.waterLevel();
            if (v < min) min = v;
            if (v > max) max = v;
        }
        double range = Math.max(1e-6, max - min);

        for (int z = 0; z < size; z++) {
            double wz = w.z0() + z + 0.5;
            for (int x = 0; x < size; x++) {
                double wx = w.x0() + x + 0.5;
                ZoneCell nearest = cells[0];
                double bestDistSq = Double.POSITIVE_INFINITY;
                for (ZoneCell c : cells) {
                    double dx = c.seedX() - wx;
                    double dz = c.seedZ() - wz;
                    double d = dx * dx + dz * dz;
                    if (d < bestDistSq) { bestDistSq = d; nearest = c; }
                }
                var entry = drainage.byCellId().get(nearest.id());
                double t = entry == null ? 0.0 : (entry.waterLevel() - min) / range;
                int g = (int) Math.round(Math.max(0, Math.min(1, t)) * 255);
                img.setRGB(x, z, (g << 16) | (g << 8) | g);
            }
        }
        return img;
    }

    /**
     * Each cell tinted by its basin; lakes overlaid in a translucent blue;
     * flow-direction arrows drawn from each cell toward its downhill
     * neighbour; endorheic terminals get a distinct outline.
     */
    private static BufferedImage basinsImage(ZoneGraph graph, DrainageGraph drainage,
                                             DrainageBasins basins, WorldWindow w) {
        int size = w.size();
        ZoneCell[] cells = graph.cells().toArray(new ZoneCell[0]);
        Map<Integer, Integer> cellToBasinColour = new HashMap<>();
        for (DrainageBasin b : basins.basins()) {
            int colour = colourForBasin(b);
            for (int cid : b.memberCellIds()) cellToBasinColour.put(cid, colour);
        }

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            double wz = w.z0() + z + 0.5;
            for (int x = 0; x < size; x++) {
                double wx = w.x0() + x + 0.5;
                ZoneCell nearest = cells[0];
                double bestDistSq = Double.POSITIVE_INFINITY;
                for (ZoneCell c : cells) {
                    double dx = c.seedX() - wx;
                    double dz = c.seedZ() - wz;
                    double d = dx * dx + dz * dz;
                    if (d < bestDistSq) { bestDistSq = d; nearest = c; }
                }
                int colour = cellToBasinColour.getOrDefault(nearest.id(), 0x808080);
                var entry = drainage.byCellId().get(nearest.id());
                if (entry != null && entry.isLake()) {
                    colour = blendRgb(colour, 0x2060C0, 0.55f);
                }
                img.setRGB(x, z, colour);
            }
        }

        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Map<Integer, ZoneCell> byId = graph.byId();
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(new Color(0, 0, 0, 180));
        for (var entry : drainage.byCellId().values()) {
            if (entry.downhillCellId() < 0) continue;
            ZoneCell from = byId.get(entry.cellId());
            ZoneCell to = byId.get(entry.downhillCellId());
            if (from == null || to == null) continue;
            int fx = toPixel(from.seedX(), w.x0());
            int fz = toPixel(from.seedZ(), w.z0());
            int tx = toPixel(to.seedX(), w.x0());
            int tz = toPixel(to.seedZ(), w.z0());
            g2.drawLine(fx, fz, tx, tz);
            // Arrow head near the target.
            double len = Math.hypot(tx - fx, tz - fz);
            if (len > 4) {
                double ux = (tx - fx) / len;
                double uz = (tz - fz) / len;
                int hx = (int) Math.round(tx - ux * 5);
                int hz = (int) Math.round(tz - uz * 5);
                double perpX = -uz;
                double perpZ = ux;
                int ax = (int) Math.round(hx + perpX * 3);
                int az = (int) Math.round(hz + perpZ * 3);
                int bx = (int) Math.round(hx - perpX * 3);
                int bz = (int) Math.round(hz - perpZ * 3);
                g2.drawLine(tx, tz, ax, az);
                g2.drawLine(tx, tz, bx, bz);
            }
        }
        for (DrainageBasin b : basins.basins()) {
            ZoneCell outlet = byId.get(b.outletCellId());
            if (outlet == null) continue;
            int cx = toPixel(outlet.seedX(), w.x0());
            int cz = toPixel(outlet.seedZ(), w.z0());
            g2.setColor(b.endorheic() ? new Color(200, 60, 60) : new Color(30, 120, 220));
            g2.fillOval(cx - 5, cz - 5, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawOval(cx - 5, cz - 5, 10, 10);
        }
        g2.dispose();
        return img;
    }

    private static int colourForBasin(DrainageBasin b) {
        float h = ((b.outletCellId() * 0x9E37) & 0xFFFF) / 65535f;
        float saturation = b.endorheic() ? 0.45f : 0.6f;
        float value = b.endorheic() ? 0.75f : 0.9f;
        return Color.getHSBColor(h, saturation, value).getRGB() & 0xFFFFFF;
    }

    private static int colourForCluster(int id) {
        float h = ((id * 0x9E37) & 0xFFFF) / 65535f;
        return Color.getHSBColor(h, 0.75f, 0.95f).getRGB() & 0xFFFFFF;
    }

    private static int blendRgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static String formatKeyList(Blackboard.Sealed board) {
        List<FeatureKey<?>> sorted = board.keys().stream()
            .sorted((a, b) -> a.name().compareTo(b.name()))
            .toList();
        StringBuilder sb = new StringBuilder("Blackboard keys:\n");
        for (FeatureKey<?> k : sorted) {
            sb.append("  ").append(k.name())
              .append("  :  ").append(k.type().getSimpleName())
              .append('\n');
        }
        return sb.toString();
    }

    private void setSeedText(String text) {
        loadingCombo = true;
        try {
            seedCombo.getEditor().setItem(text);
        } finally {
            loadingCombo = false;
        }
    }

    private String currentSeedText() {
        Object item = seedCombo.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    // ------------------------------------------------------------------
    //  Saved-seed persistence
    // ------------------------------------------------------------------

    private record SavedSeed(String label, long seed) {
        @Override public String toString() { return Long.toString(seed); }
    }

    private void promptAndSave() {
        long seed;
        try {
            seed = Long.parseLong(currentSeedText());
        } catch (NumberFormatException ex) {
            statusLabel.setText("Seed must be a long integer");
            return;
        }
        String input = JOptionPane.showInputDialog(this,
            "Label for seed " + seed + ":", Long.toString(seed));
        if (input == null) return;
        String label = input.isBlank() ? Long.toString(seed) : input.trim();
        try {
            persistSeed(new SavedSeed(label, seed));
            reloadSavedCombo();
            statusLabel.setText("Saved '" + label + "' → " + SEEDS_FILE);
        } catch (IOException ex) {
            statusLabel.setText("Save failed: " + ex.getMessage());
        }
    }

    private static void persistSeed(SavedSeed entry) throws IOException {
        Files.createDirectories(SEEDS_FILE.getParent());
        String safeLabel = entry.label().replace('\t', ' ').replace('\n', ' ');
        String line = safeLabel + '\t' + entry.seed() + System.lineSeparator();
        Files.writeString(SEEDS_FILE, line, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void reloadSavedCombo() {
        List<SavedSeed> entries = readSeeds();
        DefaultComboBoxModel<SavedSeed> model = new DefaultComboBoxModel<>();
        for (SavedSeed e : entries) model.addElement(e);
        String preserved = currentSeedText();
        loadingCombo = true;
        try {
            seedCombo.setModel(model);
            seedCombo.setSelectedIndex(-1);
            seedCombo.getEditor().setItem(preserved);
        } finally {
            loadingCombo = false;
        }
    }

    private static List<SavedSeed> readSeeds() {
        List<SavedSeed> out = new ArrayList<>();
        if (!Files.isRegularFile(SEEDS_FILE)) return out;
        try {
            for (String line : Files.readAllLines(SEEDS_FILE, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0 || tab == line.length() - 1) continue;
                try {
                    long seed = Long.parseLong(line.substring(tab + 1).trim());
                    out.add(new SavedSeed(line.substring(0, tab), seed));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException ignored) {}
        return out;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VisualizerApp().setVisible(true));
    }

    /** Scaled-to-fit renderer for whichever image the current view produced. */
    private static final class RenderPanel extends JPanel {

        private BufferedImage image;

        RenderPanel() {
            setPreferredSize(new Dimension(640, 640));
            setBackground(Color.DARK_GRAY);
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int w = getWidth(), h = getHeight();
            int side = Math.min(w, h);
            int ox = (w - side) / 2;
            int oy = (h - side) / 2;
            g2.drawImage(image, ox, oy, side, side, null);
        }
    }
}
