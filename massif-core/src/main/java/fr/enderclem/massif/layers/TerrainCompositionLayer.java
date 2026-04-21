package fr.enderclem.massif.layers;

import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.dag.LayerContext;
import java.util.Set;

/**
 * Sums the separate height contributions into the final {@link Features#HEIGHTMAP}.
 * Keeping composition in its own layer is the extension point: future stages
 * (rivers carving, gradient-trick detail noise, erosion) write their own
 * {@code *_HEIGHT} features and get added into the heightmap here.
 */
public final class TerrainCompositionLayer implements Layer {

    @Override
    public String name() {
        return "terrain.composition";
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(Features.BASE_HEIGHT, Features.RIDGE_HEIGHT);
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(Features.HEIGHTMAP);
    }

    @Override
    public void compute(LayerContext ctx) {
        float[][] base = ctx.read(Features.BASE_HEIGHT);
        float[][] ridge = ctx.read(Features.RIDGE_HEIGHT);
        int size = base.length;
        float[][] out = new float[size][size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                out[z][x] = base[z][x] + ridge[z][x];
            }
        }
        ctx.write(Features.HEIGHTMAP, out);
    }
}
