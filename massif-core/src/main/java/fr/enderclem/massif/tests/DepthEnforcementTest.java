package fr.enderclem.massif.tests;

import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.dag.BorderAwareLayer;
import fr.enderclem.massif.dag.CrossRegionRecursionException;
import fr.enderclem.massif.dag.NeighbourCache;
import fr.enderclem.massif.dag.StripSpec;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.Set;

/**
 * Required test 3 — depth-1 enforcement.
 *
 * <p>Construct a contrived layer whose border-strip mode deliberately re-enters
 * the neighbour cache for a different region. That is the depth-2 call the
 * {@link fr.enderclem.massif.dag.CrossRegionDepth} guard must refuse, raising
 * {@link CrossRegionRecursionException}. The test triggers the outer-most
 * call via {@link NeighbourCache#getOrCompute} (depth 0 → 1) and expects the
 * second call inside to throw.
 */
public final class DepthEnforcementTest {

    public static int run() {
        try {
            NeighbourCache cache = new NeighbourCache();
            RecursiveLayer layer = new RecursiveLayer();

            // Outer call: depth 0 → 1 inside the cache lambda. Inside the
            // compute, the layer attempts another cache hit for a different
            // key, which takes the depth to 2 → must throw.
            TestAssert.assertThrows(CrossRegionRecursionException.class,
                () -> cache.getOrCompute(1234L, RegionCoord.of(0, 0), layer, StripSpec.full(8)),
                "recursive cross-region read must throw");

            System.out.println("  DepthEnforcementTest: CrossRegionRecursionException raised as expected");
            return 0;
        } catch (Throwable t) {
            System.err.println("  DepthEnforcementTest FAILED: " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    /** Contrived layer: its computeBorderStrip tries another cache hit. */
    private static final class RecursiveLayer implements BorderAwareLayer {
        @Override public String name() { return "test.recursive"; }
        @Override public int borderStripRadius() { return 1; }
        @Override public Set<FeatureKey<?>> reads() { return Set.of(); }
        @Override public Set<FeatureKey<?>> writes() { return Set.of(); }
        @Override public void compute(fr.enderclem.massif.dag.LayerContext ctx) { }

        @Override
        public byte[][] computeBorderStrip(long seed, RegionCoord coord, StripSpec strip, NeighbourCache cache) {
            // Depth 1 here — this next call should push depth to 2 and throw.
            cache.getOrCompute(seed, RegionCoord.of(coord.rx() + 1, coord.rz()), this, strip);
            // Unreachable if guard works.
            return new byte[strip.width()][strip.height()];
        }
    }
}
