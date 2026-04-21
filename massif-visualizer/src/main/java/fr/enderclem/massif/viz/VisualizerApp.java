package fr.enderclem.massif.viz;

import fr.enderclem.massif.api.RegionPlan;
import fr.enderclem.massif.api.TerrainFramework;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.layers.BaseNoiseLayer;
import fr.enderclem.massif.layers.Features;
import fr.enderclem.massif.layers.GradientLayer;
import fr.enderclem.massif.layers.TerrainCompositionLayer;
import fr.enderclem.massif.layers.dla.RidgeDlaLayer;
import fr.enderclem.massif.layers.voronoi.HandshakeGraph;
import fr.enderclem.massif.layers.voronoi.HandshakeGraph.HandshakeNode;
import fr.enderclem.massif.layers.voronoi.HandshakeLayer;
import fr.enderclem.massif.layers.voronoi.VoronoiZonesLayer;
import fr.enderclem.massif.layers.voronoi.ZoneKind;
import fr.enderclem.massif.primitives.RegionCoord;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/** Entry point for the Swing visualizer. Depends only on massif-core and the JDK. */
public final class VisualizerApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(VisualizerApp::launch);
    }

    private static void launch() {
        List<Layer> layers = List.of(
            new VoronoiZonesLayer(),
            new HandshakeLayer(),
            new BaseNoiseLayer(),
            new RidgeDlaLayer(),
            new TerrainCompositionLayer(),
            new GradientLayer());
        TerrainFramework fw = new TerrainFramework(layers);

        List<FeatureRenderer> renderers = List.of(
            Renderers.heightmap(),
            Renderers.baseHeight(),
            Renderers.ridgeHeight(),
            Renderers.ridgeMask(),
            Renderers.gradient(),
            Renderers.zones());

        new VisualizerFrame(fw, renderers).setVisible(true);
    }

    private static final class VisualizerFrame extends JFrame {

        private final TerrainFramework fw;

        private final JTextField seedField = new JTextField("1234", 10);
        private final JSpinner rxSpinner = new JSpinner(new SpinnerNumberModel(0, -1024, 1024, 1));
        private final JSpinner rzSpinner = new JSpinner(new SpinnerNumberModel(0, -1024, 1024, 1));
        private final JSpinner gridSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 7, 2));
        private final JCheckBox handshakeToggle = new JCheckBox("Handshake", true);
        private final JCheckBox tintToggle = new JCheckBox("Zone tint", false);
        private final JCheckBox hillshadeToggle = new JCheckBox("Hillshade", false);
        private final JComboBox<FeatureRenderer> featureCombo = new JComboBox<>();
        private final JLabel statusLabel = new JLabel(" ");
        private final JTextArea dagArea = new JTextArea();
        private final ImagePanel imagePanel = new ImagePanel();
        private final JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        VisualizerFrame(TerrainFramework fw, List<FeatureRenderer> renderers) {
            super("Massif Visualizer");
            this.fw = fw;

            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout());

            DefaultComboBoxModel<FeatureRenderer> model = new DefaultComboBoxModel<>();
            for (FeatureRenderer r : renderers) {
                model.addElement(r);
            }
            featureCombo.setModel(model);
            featureCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(
                        javax.swing.JList<?> list, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {
                    String text = value instanceof FeatureRenderer r ? r.label() : "";
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
                }
            });

            add(buildControlPanel(), BorderLayout.NORTH);

            dagArea.setEditable(false);
            dagArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
            dagArea.setText(dagSummary());

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                imagePanel,
                new JScrollPane(dagArea));
            split.setResizeWeight(0.75);
            add(split, BorderLayout.CENTER);

            legendPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

            JPanel southStack = new JPanel(new BorderLayout());
            southStack.add(legendPanel, BorderLayout.NORTH);
            southStack.add(statusLabel, BorderLayout.SOUTH);
            add(southStack, BorderLayout.SOUTH);
            statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            setSize(1200, 820);
            setLocationRelativeTo(null);

            regenerate();
        }

        private JPanel buildControlPanel() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 4, 2, 4);
            c.anchor = GridBagConstraints.WEST;

            int col = 0;
            c.gridx = col++; p.add(new JLabel("Seed:"), c);
            c.gridx = col++; p.add(seedField, c);
            c.gridx = col++; p.add(new JLabel("rx:"), c);
            c.gridx = col++; p.add(rxSpinner, c);
            c.gridx = col++; p.add(new JLabel("rz:"), c);
            c.gridx = col++; p.add(rzSpinner, c);
            c.gridx = col++; p.add(new JLabel("Grid:"), c);
            c.gridx = col++; p.add(gridSpinner, c);
            c.gridx = col++; p.add(new JLabel("Feature:"), c);
            c.gridx = col++; p.add(featureCombo, c);
            c.gridx = col++; p.add(handshakeToggle, c);
            c.gridx = col++; p.add(tintToggle, c);
            c.gridx = col++; p.add(hillshadeToggle, c);

            JButton regen = new JButton("Regenerate (new seed)");
            regen.addActionListener(e -> {
                seedField.setText(Long.toString(new java.util.Random().nextLong()));
                regenerate();
            });
            c.gridx = col++; p.add(regen, c);

            seedField.addActionListener(e -> regenerate());
            featureCombo.addActionListener(e -> regenerate());
            gridSpinner.addChangeListener(e -> regenerate());
            rxSpinner.addChangeListener(e -> regenerate());
            rzSpinner.addChangeListener(e -> regenerate());
            handshakeToggle.addActionListener(e -> regenerate());
            tintToggle.addActionListener(e -> regenerate());
            hillshadeToggle.addActionListener(e -> regenerate());
            return p;
        }

        private String dagSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Compiled DAG order\n");
            sb.append("------------------\n");
            List<Layer> order = fw.schedule().order();
            for (int i = 0; i < order.size(); i++) {
                Layer l = order.get(i);
                sb.append(String.format("%d. %s%n", i + 1, l.name()));
                sb.append("     reads : ");
                if (l.reads().isEmpty()) {
                    sb.append("(none)\n");
                } else {
                    boolean first = true;
                    for (var k : l.reads()) {
                        if (!first) sb.append(", ");
                        sb.append(k.name());
                        first = false;
                    }
                    sb.append('\n');
                }
                sb.append("     writes: ");
                boolean first = true;
                for (var k : l.writes()) {
                    if (!first) sb.append(", ");
                    sb.append(k.name());
                    first = false;
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        private void regenerate() {
            long seed;
            try {
                seed = Long.parseLong(seedField.getText().trim());
            } catch (NumberFormatException ex) {
                statusLabel.setText("Invalid seed (must be an integer)");
                return;
            }
            int cRx = (Integer) rxSpinner.getValue();
            int cRz = (Integer) rzSpinner.getValue();
            int grid = (Integer) gridSpinner.getValue();
            if ((grid & 1) == 0) {
                grid -= 1; // keep odd so centre aligns
            }
            int half = grid / 2;

            FeatureRenderer renderer = (FeatureRenderer) featureCombo.getSelectedItem();
            if (renderer == null) {
                return;
            }

            int regionSize = Features.REGION_SIZE;
            int canvasSize = regionSize * grid;
            BufferedImage composite = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = composite.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            RegionPlan centrePlan = null;
            long totalGenNs = 0, totalRenderNs = 0;

            // Phase 1: generate every region plan. Collect into a flat list so
            // the renderer can compute a single colour scale across all tiles.
            int tilesPerSide = grid;
            RegionPlan[] plans = new RegionPlan[tilesPerSide * tilesPerSide];

            try {
                int idx = 0;
                for (int gz = -half; gz <= half; gz++) {
                    for (int gx = -half; gx <= half; gx++) {
                        int rx = cRx + gx, rz = cRz + gz;
                        long t0 = System.nanoTime();
                        RegionPlan plan = fw.generate(seed, RegionCoord.of(rx, rz));
                        totalGenNs += System.nanoTime() - t0;
                        plans[idx++] = plan;
                        if (rx == cRx && rz == cRz) {
                            centrePlan = plan;
                        }
                    }
                }

                // Phase 2: compute a shared colour scale, then render each tile.
                Object scale = renderer.computeScale(java.util.Arrays.asList(plans));
                boolean tint = tintToggle.isSelected() && renderer.key() != Features.ZONES;
                boolean hillshade = hillshadeToggle.isSelected();
                idx = 0;
                for (int gz = -half; gz <= half; gz++) {
                    for (int gx = -half; gx <= half; gx++) {
                        RegionPlan plan = plans[idx++];
                        long t1 = System.nanoTime();
                        BufferedImage tile = renderer.render(plan, scale);
                        if (tint) {
                            tile = tintByZone(tile, plan.get(Features.ZONES));
                        }
                        if (hillshade) {
                            tile = applyHillshade(tile, plan.get(Features.HEIGHTMAP));
                        }
                        totalRenderNs += System.nanoTime() - t1;
                        int px = (gx + half) * regionSize;
                        int py = (gz + half) * regionSize;
                        g2.drawImage(tile, px, py, null);
                    }
                }

                if (handshakeToggle.isSelected()) {
                    drawHandshakeOverlay(g2, seed, cRx, cRz, half, regionSize);
                }
            } finally {
                g2.dispose();
            }

            imagePanel.setImage(composite);
            updateLegend(centrePlan == null ? List.of() : renderer.legend(centrePlan));
            int regions = grid * grid;
            statusLabel.setText(String.format(
                "seed=%d  centre=(%d,%d)  grid=%dx%d (%d regions)  gen=%dms  render=%dms  feature=%s",
                seed, cRx, cRz, grid, grid, regions,
                totalGenNs / 1_000_000, totalRenderNs / 1_000_000,
                renderer.label()));
        }

        /**
         * For the centre region and each of its neighbours, draw every handshake node
         * as a small dot on the composite. Nodes on a shared edge are produced by both
         * adjacent regions; we draw them once, so visual overlap along the seam is a
         * visual confirmation that both sides agree.
         */
        private void drawHandshakeOverlay(Graphics2D g2, long seed,
                                          int cRx, int cRz, int half, int regionSize) {
            Graphics2D og = (Graphics2D) g2.create();
            try {
                og.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                og.setStroke(new BasicStroke(1.0f));
                double originWx = (double) (cRx - half) * regionSize;
                double originWz = (double) (cRz - half) * regionSize;

                for (int gz = -half; gz <= half; gz++) {
                    for (int gx = -half; gx <= half; gx++) {
                        int rx = cRx + gx, rz = cRz + gz;
                        RegionPlan plan = fw.generate(seed, RegionCoord.of(rx, rz));
                        HandshakeGraph graph = plan.get(Features.HANDSHAKE);
                        for (HandshakeNode n : graph.nodes()) {
                            int px = (int) Math.round(n.wx() - originWx);
                            int py = (int) Math.round(n.wz() - originWz);
                            Color fill = pairColor(n.kindLower(), n.kindUpper());
                            og.setColor(fill);
                            og.fillOval(px - 4, py - 4, 9, 9);
                            og.setColor(Color.BLACK);
                            og.drawOval(px - 4, py - 4, 9, 9);
                        }
                    }
                }
            } finally {
                og.dispose();
            }
        }

        // Sun direction (unit vector). NW azimuth, 45° elevation — standard cartographic convention.
        private static final double SUN_AZ = Math.toRadians(315);
        private static final double SUN_EL = Math.toRadians(45);
        private static final double SUN_X = Math.cos(SUN_EL) * Math.cos(SUN_AZ);
        private static final double SUN_Z = Math.cos(SUN_EL) * Math.sin(SUN_AZ);
        private static final double SUN_Y = Math.sin(SUN_EL);
        // Vertical exaggeration: heightmap is in ~[-0.6, 1.2]; multiply so slopes are legible.
        private static final double HEIGHT_SCALE = 80.0;

        /**
         * Multiply each pixel's brightness by a Lambertian shade computed from the
         * heightmap's local gradient. Cheap pseudo-3D: peaks and ridgelines show
         * bright faces toward the sun and dark faces away.
         */
        private static BufferedImage applyHillshade(BufferedImage base, float[][] heightmap) {
            int w = base.getWidth(), h = base.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int z = 0; z < h; z++) {
                int zm = Math.max(0, z - 1);
                int zp = Math.min(h - 1, z + 1);
                for (int x = 0; x < w; x++) {
                    int xm = Math.max(0, x - 1);
                    int xp = Math.min(w - 1, x + 1);
                    double dx = (heightmap[z][xp] - heightmap[z][xm]) * 0.5 * HEIGHT_SCALE;
                    double dz = (heightmap[zp][x] - heightmap[zm][x]) * 0.5 * HEIGHT_SCALE;
                    // Surface normal = (-dx, 1, -dz) in (x, up, z) convention.
                    double nx = -dx, ny = 1.0, nz = -dz;
                    double invLen = 1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz);
                    double dot = (nx * SUN_X + ny * SUN_Y + nz * SUN_Z) * invLen;
                    double shade = 0.25 + 0.75 * Math.max(0, dot); // ambient + diffuse

                    int rgb = base.getRGB(x, z);
                    int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * shade);
                    int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * shade);
                    int b = (int) Math.min(255, (rgb & 0xFF) * shade);
                    out.setRGB(x, z, (r << 16) | (g << 8) | b);
                }
            }
            return out;
        }

        /**
         * Colourise a grayscale feature tile using per-pixel zone hue/saturation,
         * preserving the feature's brightness. A bright heightmap peak in a
         * mountains cell comes out light-grey-brown; a low point in an ocean
         * cell comes out dark blue. Avoids the "tint everything to midtones"
         * washout of alpha blending.
         */
        private static BufferedImage tintByZone(BufferedImage base, int[][] zones) {
            int w = base.getWidth(), h = base.getHeight();
            ZoneKind[] kinds = ZoneKind.values();
            float[] hue = new float[kinds.length];
            float[] sat = new float[kinds.length];
            for (int i = 0; i < kinds.length; i++) {
                int rgb = kinds[i].rgb();
                float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
                hue[i] = hsb[0];
                sat[i] = hsb[1];
            }
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int z = 0; z < h; z++) {
                for (int x = 0; x < w; x++) {
                    int baseRgb = base.getRGB(x, z);
                    // Feature tiles are grayscale → any channel is fine.
                    float brightness = (baseRgb & 0xFF) / 255f;
                    int k = Math.floorMod(zones[z][x], kinds.length);
                    out.setRGB(x, z, Color.HSBtoRGB(hue[k], sat[k], brightness));
                }
            }
            return out;
        }

        private static Color pairColor(int a, int b) {
            int lo = Math.min(a, b), hi = Math.max(a, b);
            int h = Integer.rotateLeft(lo * 0x9E3779B1 ^ hi * 0x85EBCA6B, 9) ^ 0xDEADBEEF;
            return new Color((h >>> 16) & 0xFF, (h >>> 8) & 0xFF, h & 0xFF);
        }

        private void updateLegend(List<FeatureRenderer.LegendEntry> entries) {
            legendPanel.removeAll();
            if (entries.isEmpty()) {
                legendPanel.revalidate();
                legendPanel.repaint();
                return;
            }
            for (FeatureRenderer.LegendEntry entry : entries) {
                JPanel swatch = new JPanel();
                swatch.setBackground(entry.color());
                swatch.setPreferredSize(new Dimension(14, 14));
                swatch.setBorder(BorderFactory.createLineBorder(Color.BLACK));

                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                row.add(swatch);
                row.add(new JLabel(entry.label()));
                legendPanel.add(row);
            }
            legendPanel.revalidate();
            legendPanel.repaint();
        }
    }

    private static final class ImagePanel extends JPanel {
        private static final double MIN_ZOOM = 0.05;
        private static final double MAX_ZOOM = 64.0;
        private static final double ZOOM_STEP = 1.15;

        private BufferedImage image;
        private double zoom = 1.0;
        private double offsetX = 0.0, offsetY = 0.0;
        private boolean viewInitialised = false;
        private int lastImageW = -1, lastImageH = -1;
        private int dragAnchorX, dragAnchorY;

        ImagePanel() {
            setBackground(Color.DARK_GRAY);
            setPreferredSize(new Dimension(800, 800));

            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    // First real layout pass — refit if fit was computed against 0x0.
                    if (image != null && !viewInitialised) {
                        fitToView();
                        viewInitialised = true;
                        repaint();
                    }
                }
            });

            addMouseWheelListener(e -> {
                if (image == null) return;
                double factor = Math.pow(ZOOM_STEP, -e.getPreciseWheelRotation());
                double newZoom = clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
                if (newZoom == zoom) return;
                // Keep the image pixel under the cursor fixed during the zoom.
                double cx = e.getX(), cy = e.getY();
                double imgX = (cx - offsetX) / zoom;
                double imgY = (cy - offsetY) / zoom;
                zoom = newZoom;
                offsetX = cx - imgX * zoom;
                offsetY = cy - imgY * zoom;
                repaint();
            });

            java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    dragAnchorX = e.getX();
                    dragAnchorY = e.getY();
                }

                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    offsetX += e.getX() - dragAnchorX;
                    offsetY += e.getY() - dragAnchorY;
                    dragAnchorX = e.getX();
                    dragAnchorY = e.getY();
                    repaint();
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        void setImage(BufferedImage image) {
            boolean dimsChanged = this.image == null
                || image.getWidth() != lastImageW
                || image.getHeight() != lastImageH;
            this.image = image;
            this.lastImageW = image.getWidth();
            this.lastImageH = image.getHeight();
            if (dimsChanged) {
                viewInitialised = false; // force re-fit for new image size
            }
            if (!viewInitialised) {
                fitToView();
                // Mark initialised only if we had real panel dimensions; otherwise
                // the componentResized listener will refit once the layout pass runs.
                if (getWidth() > 1 && getHeight() > 1) {
                    viewInitialised = true;
                }
            }
            revalidate();
            repaint();
        }

        /** Reset zoom/offset so the image fits the current panel (or preferred size). */
        void fitToView() {
            if (image == null) return;
            int w = getWidth(), h = getHeight();
            if (w <= 1 || h <= 1) {
                Dimension pref = getPreferredSize();
                w = pref.width;
                h = pref.height;
            }
            double sx = (double) w / image.getWidth();
            double sy = (double) h / image.getHeight();
            zoom = Math.min(sx, sy);
            if (zoom <= 0 || !Double.isFinite(zoom)) zoom = 1.0;
            offsetX = (w - image.getWidth() * zoom) / 2.0;
            offsetY = (h - image.getHeight() * zoom) / 2.0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int w = (int) Math.round(image.getWidth() * zoom);
                int h = (int) Math.round(image.getHeight() * zoom);
                g2.drawImage(image, (int) Math.round(offsetX), (int) Math.round(offsetY),
                    w, h, null);
            } finally {
                g2.dispose();
            }
        }

        private static double clamp(double v, double lo, double hi) {
            return v < lo ? lo : Math.min(v, hi);
        }
    }
}
