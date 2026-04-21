package fr.enderclem.massif.tests;

import fr.enderclem.massif.api.RegionPlan;
import fr.enderclem.massif.api.TerrainFramework;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.layers.BaseNoiseLayer;
import fr.enderclem.massif.layers.Features;
import fr.enderclem.massif.layers.GradientLayer;
import fr.enderclem.massif.layers.TerrainCompositionLayer;
import fr.enderclem.massif.layers.dla.RidgeDlaLayer;
import fr.enderclem.massif.layers.voronoi.HandshakeLayer;
import fr.enderclem.massif.layers.voronoi.VoronoiZonesLayer;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Required test 5 — parallel execution determinism.
 *
 * <p>Sequential and heavily-parallel region generations must produce
 * byte-identical output, on a per-region basis. The refactored walker
 * algorithm achieves this by making each walker's RNG sub-stream independent
 * of every other walker's and by taking a mask snapshot at the start of each
 * generation so stick-detection is deterministic within a generation
 * regardless of scheduling order.
 *
 * <p>The test generates a 3×3 block of regions sequentially and in parallel
 * (on a fixed-size thread pool) and verifies every byte of every grid output
 * matches.
 */
public final class ParallelDeterminismTest {

    public static int run() {
        try {
            long seed = 777L;
            int grid = 3, half = grid / 2;

            TerrainFramework sequential = newFramework();
            TerrainFramework parallel = newFramework();

            List<RegionPlan> seq = new ArrayList<>();
            for (int dz = -half; dz <= half; dz++) {
                for (int dx = -half; dx <= half; dx++) {
                    seq.add(sequential.generate(seed, RegionCoord.of(dx, dz)));
                }
            }

            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                List<Future<RegionPlan>> futures = new ArrayList<>();
                for (int dz = -half; dz <= half; dz++) {
                    for (int dx = -half; dx <= half; dx++) {
                        final int fx = dx, fz = dz;
                        futures.add(pool.submit(() ->
                            parallel.generate(seed, RegionCoord.of(fx, fz))));
                    }
                }
                for (int i = 0; i < seq.size(); i++) {
                    RegionPlan s = seq.get(i);
                    RegionPlan p = futures.get(i).get();
                    assertEqualPlans(s, p);
                }
            } finally {
                pool.shutdownNow();
            }

            System.out.println("  ParallelDeterminismTest: 9 regions byte-identical across schedules");
            return 0;
        } catch (Throwable t) {
            System.err.println("  ParallelDeterminismTest FAILED: " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    private static TerrainFramework newFramework() {
        List<Layer> layers = List.of(
            new VoronoiZonesLayer(), new HandshakeLayer(),
            new BaseNoiseLayer(), new RidgeDlaLayer(),
            new TerrainCompositionLayer(), new GradientLayer());
        return new TerrainFramework(layers);
    }

    private static void assertEqualPlans(RegionPlan a, RegionPlan b) {
        byte[][] maskA = a.get(Features.RIDGE_MASK);
        byte[][] maskB = b.get(Features.RIDGE_MASK);
        for (int z = 0; z < maskA.length; z++) {
            for (int x = 0; x < maskA[z].length; x++) {
                if (maskA[z][x] != maskB[z][x]) {
                    throw new AssertionError("RIDGE_MASK differs at (" + x + "," + z
                        + ") for region " + a.coord() + ": " + maskA[z][x] + " vs " + maskB[z][x]);
                }
            }
        }
        float[][] hA = a.get(Features.HEIGHTMAP);
        float[][] hB = b.get(Features.HEIGHTMAP);
        for (int z = 0; z < hA.length; z++) {
            for (int x = 0; x < hA[z].length; x++) {
                // Byte-identical: use Float.floatToRawIntBits for strict comparison.
                int rA = Float.floatToRawIntBits(hA[z][x]);
                int rB = Float.floatToRawIntBits(hB[z][x]);
                if (rA != rB) {
                    throw new AssertionError("HEIGHTMAP differs at (" + x + "," + z
                        + ") for region " + a.coord() + ": " + hA[z][x] + " vs " + hB[z][x]);
                }
            }
        }
    }
}
