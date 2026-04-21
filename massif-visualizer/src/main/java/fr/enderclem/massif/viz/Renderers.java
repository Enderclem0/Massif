package fr.enderclem.massif.viz;

import fr.enderclem.massif.api.RegionPlan;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.layers.Features;
import fr.enderclem.massif.layers.voronoi.ZoneKind;
import fr.enderclem.massif.viz.FeatureRenderer.LegendEntry;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Renderers for the stub features. Keep in this module so core stays rendering-free. */
public final class Renderers {

    private Renderers() {}

    public static FeatureRenderer heightmap() {
        return new FloatGridRenderer(Features.HEIGHTMAP, "Heightmap (composed)");
    }

    public static FeatureRenderer baseHeight() {
        return new FloatGridRenderer(Features.BASE_HEIGHT, "Base height (noise only)");
    }

    public static FeatureRenderer ridgeHeight() {
        return new FloatGridRenderer(Features.RIDGE_HEIGHT, "Ridge height (DLA)");
    }

    public static FeatureRenderer ridgeMask() {
        return new ByteMaskRenderer(Features.RIDGE_MASK, "Ridge mask (DLA aggregate)");
    }

    public static FeatureRenderer gradient() {
        return new FloatGridRenderer(Features.GRADIENT_MAG, "Gradient magnitude");
    }

    public static FeatureRenderer zones() {
        return new IntGridRenderer(Features.ZONES, "Zones");
    }

    /** Shared min/max across every tile in the current view. */
    record FloatRange(float min, float max) {
        float normalize(float v) {
            float span = Math.max(1e-9f, max - min);
            return (v - min) / span;
        }
    }

    static final class FloatGridRenderer implements FeatureRenderer {
        private final FeatureKey<float[][]> key;
        private final String label;

        FloatGridRenderer(FeatureKey<float[][]> key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override public FeatureKey<?> key() { return key; }
        @Override public String label() { return label; }

        @Override
        public Object computeScale(List<RegionPlan> plans) {
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (RegionPlan plan : plans) {
                float[][] g = plan.get(key);
                for (float[] row : g) {
                    for (float v : row) {
                        if (v < min) min = v;
                        if (v > max) max = v;
                    }
                }
            }
            if (!Float.isFinite(min) || !Float.isFinite(max)) {
                min = 0f;
                max = 1f;
            }
            return new FloatRange(min, max);
        }

        @Override
        public BufferedImage render(RegionPlan plan, Object scale) {
            FloatRange range = (scale instanceof FloatRange r) ? r : (FloatRange) computeScale(List.of(plan));
            float[][] g = plan.get(key);
            int size = g.length;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    float t = range.normalize(g[z][x]);
                    int v = Math.round(Math.min(1f, Math.max(0f, t)) * 255);
                    img.setRGB(x, z, (v << 16) | (v << 8) | v);
                }
            }
            return img;
        }
    }

    /** Renders a {@code byte[][]} 0/1 mask as black background, white aggregate. */
    static final class ByteMaskRenderer implements FeatureRenderer {
        private final FeatureKey<byte[][]> key;
        private final String label;

        ByteMaskRenderer(FeatureKey<byte[][]> key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override public FeatureKey<?> key() { return key; }
        @Override public String label() { return label; }

        @Override
        public BufferedImage render(RegionPlan plan, Object scale) {
            byte[][] g = plan.get(key);
            int size = g.length;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int v = g[z][x] != 0 ? 0xFFFFFF : 0x000000;
                    img.setRGB(x, z, v);
                }
            }
            return img;
        }
    }

    static final class IntGridRenderer implements FeatureRenderer {
        private final FeatureKey<int[][]> key;
        private final String label;

        IntGridRenderer(FeatureKey<int[][]> key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override public FeatureKey<?> key() { return key; }
        @Override public String label() { return label; }

        @Override
        public BufferedImage render(RegionPlan plan, Object scale) {
            int[][] g = plan.get(key);
            int size = g.length;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    img.setRGB(x, z, colorFor(g[z][x]).getRGB());
                }
            }
            return img;
        }

        @Override
        public List<LegendEntry> legend(RegionPlan plan) {
            int[][] g = plan.get(key);
            TreeSet<Integer> ids = new TreeSet<>();
            for (int[] row : g) {
                for (int v : row) {
                    ids.add(v);
                }
            }
            List<LegendEntry> entries = new ArrayList<>(ids.size());
            for (int id : ids) {
                String label = key == Features.ZONES ? ZoneKind.byId(id).displayName() : ("id " + id);
                entries.add(new LegendEntry(label, colorFor(id)));
            }
            return entries;
        }

        /** Palette lookup: ZoneKind colours for the zones grid, hash-colour fallback otherwise. */
        private Color colorFor(int id) {
            if (key == Features.ZONES) {
                return new Color(ZoneKind.byId(id).rgb());
            }
            int h = Integer.rotateLeft(id * 0x9E3779B1, 13) ^ 0x85EBCA6B;
            return new Color((h >>> 16) & 0xFF, (h >>> 8) & 0xFF, h & 0xFF);
        }
    }
}
