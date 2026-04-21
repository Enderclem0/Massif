package fr.enderclem.massif.layers;

import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.dag.LayerContext;
import java.util.Set;

/**
 * Derives gradient-magnitude from the heightmap via central differences.
 * Proves the read/write DAG wiring: writer-of-HEIGHTMAP → this reader, resolved
 * automatically by {@code DagScheduler} without any hardcoded ordering.
 */
public final class GradientLayer implements Layer {

    @Override
    public String name() {
        return "derive.gradient";
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(Features.HEIGHTMAP);
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(Features.GRADIENT_MAG);
    }

    @Override
    public void compute(LayerContext ctx) {
        float[][] h = ctx.read(Features.HEIGHTMAP);
        int size = h.length;
        float[][] g = new float[size][size];
        for (int z = 0; z < size; z++) {
            int zm = Math.max(0, z - 1);
            int zp = Math.min(size - 1, z + 1);
            for (int x = 0; x < size; x++) {
                int xm = Math.max(0, x - 1);
                int xp = Math.min(size - 1, x + 1);
                float dx = (h[z][xp] - h[z][xm]) * 0.5f;
                float dz = (h[zp][x] - h[zm][x]) * 0.5f;
                g[z][x] = (float) Math.sqrt(dx * dx + dz * dz);
            }
        }
        ctx.write(Features.GRADIENT_MAG, g);
    }
}
