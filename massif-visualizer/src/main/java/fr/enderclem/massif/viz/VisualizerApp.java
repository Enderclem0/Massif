package fr.enderclem.massif.viz;

import fr.enderclem.massif.api.BorderField;
import fr.enderclem.massif.api.BorderSample;
import fr.enderclem.massif.api.Massif;
import fr.enderclem.massif.api.MassifFramework;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneField;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
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
 * framework needs to publish it. This is the architectural discipline that
 * keeps the blackboard the real primary interface.
 *
 * <p>Phase 2 capabilities are minimal: an editable seed combo (typed or
 * picked from saved entries), a randomize button, a save button, a grayscale
 * render of {@link MassifKeys#HEIGHTMAP}, and a dump of every key currently
 * on the sealed blackboard. Layer toggles, point inspection, parameter
 * tuning, and alternative renderers land as later phases publish more keys.
 */
public final class VisualizerApp extends JFrame {

    private static final int RENDER_SIZE = MassifKeys.VIEW_SIZE;
    private static final Path SEEDS_FILE = Paths.get(
        System.getProperty("user.home"), ".massif", "seeds.tsv");

    private final MassifFramework framework = Massif.defaultFramework();

    /**
     * Editable: user types a seed, or picks a saved entry from the dropdown.
     * The dropdown list renders items as {@code "label (seed)"}; the editor
     * always shows just the seed number ({@link SavedSeed#toString}).
     */
    private final JComboBox<SavedSeed> seedCombo = new JComboBox<>();
    private final JComboBox<View> viewCombo = new JComboBox<>(View.values());
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea keyListing = new JTextArea();
    private final RenderPanel canvas = new RenderPanel();

    /** Last blackboard we generated; cached so switching view doesn't regenerate. */
    private Blackboard.Sealed lastBoard;

    /** Suppresses combobox actionPerformed during programmatic reloads. */
    private boolean loadingCombo = false;

    private VisualizerApp() {
        super("Massif — Phase 2 walking skeleton");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUi();
        pack();
        setLocationRelativeTo(null);
        regenerate();
    }

    private void buildUi() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Seed:"));

        seedCombo.setEditable(true);
        // Prototype sets combo width to fit the widest label reasonably expected.
        seedCombo.setPrototypeDisplayValue(new SavedSeed("xxxxxxxxxxxxxxxxxxxx", 0L));
        // Editor text field is visible when the combo is closed; keep it a
        // reasonable width so short seeds don't leave the box cramped.
        if (seedCombo.getEditor().getEditorComponent() instanceof JTextField jtf) {
            jtf.setColumns(18);
        }
        // Dropdown list rendering: "label  (seed)"; the editor still shows
        // just the seed (because SavedSeed.toString returns the seed number).
        seedCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                String display;
                if (value instanceof SavedSeed s) {
                    display = s.label() + "  (" + s.seed() + ")";
                } else {
                    display = value == null ? "" : value.toString();
                }
                return super.getListCellRendererComponent(
                    list, display, index, isSelected, cellHasFocus);
            }
        });
        seedCombo.addActionListener(e -> {
            if (loadingCombo) return;
            regenerate();
        });
        setSeedText("1234");
        controls.add(seedCombo);

        JButton randomBtn = new JButton("Randomize");
        randomBtn.setToolTipText("Fill the seed with a random long and regenerate");
        randomBtn.addActionListener(e -> {
            setSeedText(Long.toString(ThreadLocalRandom.current().nextLong()));
            regenerate();
        });
        controls.add(randomBtn);

        JButton saveBtn = new JButton("Save");
        saveBtn.setToolTipText("Save the current seed to " + SEEDS_FILE);
        saveBtn.addActionListener(e -> promptAndSave());
        controls.add(saveBtn);

        JButton go = new JButton("Generate");
        go.addActionListener(e -> regenerate());
        controls.add(go);

        controls.add(new JLabel("View:"));
        viewCombo.addActionListener(e -> renderLast());
        controls.add(viewCombo);

        keyListing.setEditable(false);
        keyListing.setRows(6);
        keyListing.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));

        reloadSavedCombo();

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(new JScrollPane(keyListing), BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(controls, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
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

    private void regenerate() {
        long seed;
        try {
            seed = Long.parseLong(currentSeedText());
        } catch (NumberFormatException ex) {
            statusLabel.setText("Seed must be a long integer");
            return;
        }
        long t0 = System.nanoTime();
        Blackboard.Sealed board = framework.generate(seed);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        lastBoard = board;
        renderLast();
        statusLabel.setText(String.format(
            "seed=%d  generated in %d ms  |  %d keys on blackboard",
            seed, ms, board.keys().size()));
        keyListing.setText(formatKeyList(board));
        keyListing.setCaretPosition(0);
    }

    /** Paint {@link #lastBoard} using the currently-selected view. */
    private void renderLast() {
        if (lastBoard == null) return;
        View view = (View) viewCombo.getSelectedItem();
        if (view == null) view = View.HEIGHTMAP;
        canvas.setImage(view.render(lastBoard));
    }

    /** Which blackboard key the canvas is rendering. */
    private enum View {
        HEIGHTMAP("Heightmap") {
            @Override
            BufferedImage render(Blackboard.Sealed board) {
                return heightmapImage(board.get(MassifKeys.HEIGHTMAP));
            }
        },
        ZONES("Zones") {
            @Override
            BufferedImage render(Blackboard.Sealed board) {
                return zonesImage(
                    board.get(MassifKeys.ZONE_FIELD),
                    board.get(MassifKeys.ZONE_REGISTRY));
            }
        },
        BORDERS("Border distance") {
            @Override
            BufferedImage render(Blackboard.Sealed board) {
                return borderDistanceImage(board.get(MassifKeys.BORDER_FIELD));
            }
        },
        GRAPH("Zone graph") {
            @Override
            BufferedImage render(Blackboard.Sealed board) {
                return zoneGraphImage(
                    board.get(MassifKeys.ZONE_FIELD),
                    board.get(MassifKeys.ZONE_REGISTRY),
                    board.get(MassifKeys.ZONE_GRAPH));
            }
        };

        private final String label;

        View(String label) { this.label = label; }

        @Override public String toString() { return label; }

        abstract BufferedImage render(Blackboard.Sealed board);
    }

    private static BufferedImage heightmapImage(float[][] field) {
        int size = field.length;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (float[] row : field) {
            for (float v : row) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
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

    private static BufferedImage zonesImage(ZoneField field, ZoneTypeRegistry registry) {
        int size = RENDER_SIZE;
        int x0 = -size / 2;
        int z0 = -size / 2;
        int[][] grid = field.sampleGrid(x0, z0, size, size);
        int[] palette = new int[registry.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = registry.get(i).displayColour();
        }
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                img.setRGB(x, z, palette[grid[z][x]]);
            }
        }
        return img;
    }

    /** Distance to nearest Voronoi border, mapped to grayscale (black = at border). */
    private static BufferedImage borderDistanceImage(BorderField field) {
        int size = RENDER_SIZE;
        int x0 = -size / 2;
        int z0 = -size / 2;
        double[][] dist = new double[size][size];
        double max = 0.0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                double d = field.sampleAt(x0 + x + 0.5, z0 + z + 0.5).distance();
                dist[z][x] = d;
                if (d > max) max = d;
            }
        }
        if (max <= 0.0) max = 1.0; // avoid div-by-zero on degenerate boards
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

    /**
     * Zone fill + seed dots + adjacency polyline overlay. Seeds outside the
     * window are drawn too (they live in the halo ring of the graph) so
     * edges that would otherwise dead-end at the window boundary visibly
     * continue into the surrounding cells.
     */
    private static BufferedImage zoneGraphImage(ZoneField field,
                                                ZoneTypeRegistry registry,
                                                ZoneGraph graph) {
        BufferedImage img = zonesImage(field, registry);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = RENDER_SIZE;
        int half = size / 2;

        var byId = graph.byId();
        // Edges first so seed dots sit on top.
        g2.setColor(new Color(0, 0, 0, 160));
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        for (ZoneCell c : graph.cells()) {
            int cx = (int) Math.round(c.seedX()) + half;
            int cz = (int) Math.round(c.seedZ()) + half;
            for (int nid : c.neighbourIds()) {
                if (nid <= c.id()) continue; // draw each edge once
                ZoneCell n = byId.get(nid);
                if (n == null) continue;
                int nx = (int) Math.round(n.seedX()) + half;
                int nz = (int) Math.round(n.seedZ()) + half;
                g2.drawLine(cx, cz, nx, nz);
            }
        }
        // Seed dots.
        g2.setColor(Color.WHITE);
        for (ZoneCell c : graph.cells()) {
            int cx = (int) Math.round(c.seedX()) + half;
            int cz = (int) Math.round(c.seedZ()) + half;
            g2.fillOval(cx - 3, cz - 3, 6, 6);
        }
        g2.setColor(Color.BLACK);
        for (ZoneCell c : graph.cells()) {
            int cx = (int) Math.round(c.seedX()) + half;
            int cz = (int) Math.round(c.seedZ()) + half;
            g2.drawOval(cx - 3, cz - 3, 6, 6);
        }
        g2.dispose();
        return img;
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

    // ------------------------------------------------------------------
    //  Saved-seed persistence
    // ------------------------------------------------------------------

    /**
     * One entry in {@link #SEEDS_FILE}. {@link #toString} returns just the
     * seed number so that when the user picks an entry from the combo the
     * editor ends up containing a plain long — the dropdown-list renderer
     * shows {@code "label  (seed)"} separately.
     */
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
        if (input == null) return; // cancelled
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
        // Tabs and newlines in the label would break the file format; strip them.
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
            seedCombo.setSelectedIndex(-1); // nothing "selected"; editor keeps its text
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
                } catch (NumberFormatException ignored) {
                    // skip malformed line
                }
            }
        } catch (IOException ignored) {
            // unreadable file → empty list; status line surfaces nothing, not fatal
        }
        return out;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VisualizerApp().setVisible(true));
    }

    /**
     * Centred, aspect-preserving scaler for whichever image the current view
     * produced. Nearest-neighbour so the actual pixel grid is visible when the
     * window is larger than the source.
     */
    private static final class RenderPanel extends JPanel {

        private BufferedImage image;

        RenderPanel() {
            setPreferredSize(new Dimension(RENDER_SIZE, RENDER_SIZE));
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
