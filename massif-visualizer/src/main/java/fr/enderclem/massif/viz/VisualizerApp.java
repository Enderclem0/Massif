package fr.enderclem.massif.viz;

import fr.enderclem.massif.api.Massif;
import fr.enderclem.massif.api.MassifFramework;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
 * <p>Phase 2 capabilities are minimal: one seed input, a generate button,
 * a grayscale render of {@link MassifKeys#HEIGHTMAP}, and a dump of every
 * key currently on the sealed blackboard. Layer toggles, point inspection,
 * parameter tuning, and alternative renderers land as later phases publish
 * more keys.
 */
public final class VisualizerApp extends JFrame {

    private static final int RENDER_SIZE = MassifKeys.DEMO_SIZE;

    private final MassifFramework framework = Massif.defaultFramework();

    private final JTextField seedField = new JTextField("1234", 16);
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea keyListing = new JTextArea();
    private final RenderPanel canvas = new RenderPanel();

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
        controls.add(seedField);
        JButton go = new JButton("Generate");
        go.addActionListener(e -> regenerate());
        controls.add(go);
        seedField.addActionListener(e -> regenerate());

        keyListing.setEditable(false);
        keyListing.setRows(6);
        keyListing.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(new JScrollPane(keyListing), BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(controls, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void regenerate() {
        long seed;
        try {
            seed = Long.parseLong(seedField.getText().trim());
        } catch (NumberFormatException ex) {
            statusLabel.setText("Seed must be a long integer");
            return;
        }
        long t0 = System.nanoTime();
        Blackboard.Sealed board = framework.generate(seed);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        float[][] map = board.get(MassifKeys.HEIGHTMAP);
        canvas.setField(map);
        statusLabel.setText(String.format(
            "seed=%d  generated in %d ms  |  %d keys on blackboard",
            seed, ms, board.keys().size()));
        keyListing.setText(formatKeyList(board));
        keyListing.setCaretPosition(0);
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VisualizerApp().setVisible(true));
    }

    /** Scales a {@code float[][]} heightmap into the rendered image on repaint. */
    private static final class RenderPanel extends JPanel {

        private BufferedImage image;

        RenderPanel() {
            setPreferredSize(new Dimension(RENDER_SIZE, RENDER_SIZE));
            setBackground(Color.DARK_GRAY);
        }

        void setField(float[][] field) {
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
            this.image = img;
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
