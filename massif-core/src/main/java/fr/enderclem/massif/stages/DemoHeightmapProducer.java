package fr.enderclem.massif.stages;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.WorldWindow;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.noise.ValueNoise2D;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.Set;

/**
 * Walking-skeleton placeholder producer. Populates {@link MassifKeys#HEIGHTMAP}
 * with fBM value noise over the configured {@link WorldWindow}, so the
 * visualiser has real data to render and the end-to-end pipeline exercises.
 * Replaced in later phases by the composition stage that folds zone passes,
 * mountain techniques, rivers, and set pieces into {@code core:height}.
 */
public final class DemoHeightmapProducer implements Producer {

    private final WorldWindow window;

    public DemoHeightmapProducer() {
        this(WorldWindow.defaultWindow());
    }

    public DemoHeightmapProducer(WorldWindow window) {
        this.window = window;
    }

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
        int size = window.size();
        int x0 = window.x0();
        int z0 = window.z0();
        long seed = ctx.seed();
        float[][] map = new float[size][size];
        double freq = 1.0 / 64.0;
        int octaves = 5;
        double lacunarity = 2.0;
        double gain = 0.5;
        for (int z = 0; z < size; z++) {
            double wz = z0 + z;
            for (int x = 0; x < size; x++) {
                double wx = x0 + x;
                map[z][x] = (float) ValueNoise2D.fbm(wx, wz, seed, octaves, freq, lacunarity, gain);
            }
        }
        ctx.write(MassifKeys.HEIGHTMAP, map);
    }
}
