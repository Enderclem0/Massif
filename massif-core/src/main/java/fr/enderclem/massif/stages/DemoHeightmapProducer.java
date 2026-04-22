package fr.enderclem.massif.stages;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.noise.ValueNoise2D;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.Set;

/**
 * Phase 2 walking-skeleton producer. Populates {@link MassifKeys#HEIGHTMAP}
 * with fBM value noise centred on world (0, 0) so the visualizer has real
 * data to render and the end-to-end pipeline (producer → blackboard →
 * visualizer) exercises. Replaced in later phases by the proper composition
 * stage that folds zone passes, mountain techniques, rivers, and set pieces
 * into {@code core:height}.
 */
public final class DemoHeightmapProducer implements Producer {

    @Override
    public String name() {
        return "demo.heightmap";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.HEIGHTMAP);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        int size = MassifKeys.DEMO_SIZE;
        long seed = ctx.seed();
        float[][] map = new float[size][size];
        double freq = 1.0 / 64.0;  // one fBM period spans ~64 cells
        int octaves = 5;
        double lacunarity = 2.0;
        double gain = 0.5;
        double half = size / 2.0;
        for (int z = 0; z < size; z++) {
            double wz = z - half;
            for (int x = 0; x < size; x++) {
                double wx = x - half;
                map[z][x] = (float) ValueNoise2D.fbm(wx, wz, seed, octaves, freq, lacunarity, gain);
            }
        }
        ctx.write(MassifKeys.HEIGHTMAP, map);
    }
}
