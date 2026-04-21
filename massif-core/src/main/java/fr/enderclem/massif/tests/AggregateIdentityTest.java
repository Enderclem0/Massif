package fr.enderclem.massif.tests;

import fr.enderclem.massif.dag.NeighbourCache;
import fr.enderclem.massif.dag.StripSpec;
import fr.enderclem.massif.layers.Features;
import fr.enderclem.massif.layers.dla.RidgeDlaLayer;
import fr.enderclem.massif.primitives.RegionCoord;

/**
 * Required test 2 — aggregate identity.
 *
 * <p>Within the border strip, the mask produced by border-strip-mode DLA must
 * be bit-identical to the corresponding slice of the full-region mask. This is
 * the central guarantee that makes the seam fix correct: neighbouring regions
 * see the same aggregate in their shared overlap, so their distance fields
 * agree.
 *
 * <p>Culling in border-strip mode is provably safe under the refactored walker
 * algorithm (independent per-walker RNG + fixed anchor list); this test
 * catches any regression that accidentally reintroduces a dependency.
 */
public final class AggregateIdentityTest {

    public static int run() {
        try {
            int size = Features.REGION_SIZE;
            RidgeDlaLayer layer = new RidgeDlaLayer();
            int w = layer.borderStripRadius();
            RegionCoord coord = RegionCoord.of(0, 0);

            long[] seeds = { 42L, 7777L, 12345L };
            StripSpec[] strips = new StripSpec[] {
                new StripSpec(0,        0,        w,    size), // west
                new StripSpec(size - w, 0,        w,    size), // east
                new StripSpec(0,        0,        size, w),    // north
                new StripSpec(0,        size - w, size, w),    // south
            };
            String[] names = { "west", "east", "north", "south" };

            for (long seed : seeds) {
                NeighbourCache cache = new NeighbourCache();
                byte[][] full = layer.computeBorderStrip(seed, coord, StripSpec.full(size), cache);

                for (int s = 0; s < strips.length; s++) {
                    StripSpec strip = strips[s];
                    byte[][] stripMask = layer.computeBorderStrip(seed, coord, strip, cache);
                    int mismatches = 0;
                    int stripPopulation = 0, fullStripPopulation = 0;
                    for (int z = strip.z0(); z < strip.z1(); z++) {
                        for (int x = strip.x0(); x < strip.x1(); x++) {
                            if (full[z][x] != stripMask[z][x]) mismatches++;
                            if (stripMask[z][x] != 0) stripPopulation++;
                            if (full[z][x] != 0) fullStripPopulation++;
                        }
                    }
                    TestAssert.assertEquals(0, mismatches,
                        String.format("seed=%d %s strip: mismatched cells between full and strip-mode",
                            seed, names[s]));
                    System.out.printf("  AggregateIdentityTest seed=%d [%s]: strip pop %d / full pop %d%n",
                        seed, names[s], stripPopulation, fullStripPopulation);
                }
            }
            return 0;
        } catch (Throwable t) {
            System.err.println("  AggregateIdentityTest FAILED: " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }
}
