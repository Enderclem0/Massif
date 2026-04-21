package fr.enderclem.massif.tests;

import fr.enderclem.massif.api.TerrainFramework;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.layers.BaseNoiseLayer;
import fr.enderclem.massif.layers.GradientLayer;
import fr.enderclem.massif.layers.TerrainCompositionLayer;
import fr.enderclem.massif.layers.dla.RidgeDlaLayer;
import fr.enderclem.massif.layers.voronoi.HandshakeLayer;
import fr.enderclem.massif.layers.voronoi.VoronoiZonesLayer;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.Arrays;
import java.util.List;

/** Diagnostic: measure generate() wall time over a block of regions. Not part of the required suite. */
public final class PerfCheck {

    public static void main(String[] args) {
        List<Layer> layers = List.of(
            new VoronoiZonesLayer(), new HandshakeLayer(),
            new BaseNoiseLayer(), new RidgeDlaLayer(),
            new TerrainCompositionLayer(), new GradientLayer());
        TerrainFramework fw = new TerrainFramework(layers);

        long seed = 7777L;
        int grid = 7;
        int half = grid / 2;

        // Warm up JIT.
        fw.generate(seed, RegionCoord.of(-100, -100));
        fw.generate(seed, RegionCoord.of(-101, -100));

        long[] times = new long[grid * grid];
        int idx = 0;
        for (int dz = -half; dz <= half; dz++) {
            for (int dx = -half; dx <= half; dx++) {
                long t0 = System.nanoTime();
                fw.generate(seed, RegionCoord.of(dx, dz));
                times[idx++] = (System.nanoTime() - t0) / 1_000_000;
            }
        }
        Arrays.sort(times);
        long median = times[times.length / 2];
        long p95 = times[(int) (times.length * 0.95)];
        long max = times[times.length - 1];
        long sum = 0;
        for (long t : times) sum += t;
        long mean = sum / times.length;

        System.out.printf("PerfCheck %dx%d grid: mean=%dms median=%dms p95=%dms max=%dms (first=%dms)%n",
            grid, grid, mean, median, p95, max, times[0]);
    }
}
