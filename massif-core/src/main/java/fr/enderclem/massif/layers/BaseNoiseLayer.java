package fr.enderclem.massif.layers;

import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.dag.LayerContext;
import fr.enderclem.massif.layers.voronoi.ZoneKind;
import fr.enderclem.massif.layers.voronoi.ZoneSeed;
import fr.enderclem.massif.layers.voronoi.ZoneSeeds;
import fr.enderclem.massif.noise.ValueNoise2D;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.Set;

/**
 * Zone-aware heightmap layer. Samples a world-global fBM noise field and
 * modulates it per-cell by a smooth blend of per-zone noise profiles.
 *
 * <p>Blend rule: for every cell, compute weights {@code w_i = 1/d_i⁴} over the
 * seeds in the 3x3 neighbourhood, normalise, and mix each seed's zone-kind
 * {@link ZoneKind#amplitude() amplitude} and {@link ZoneKind#offset() offset}.
 * The quartic falloff (vs. quadratic Shepard) keeps the blend essentially local:
 * at a zone centre the nearest seed contributes &gt;90% of the weight, so each
 * zone's profile dominates its own interior. At a boundary two equidistant
 * seeds blend symmetrically, giving C⁰ continuity with no visible Voronoi
 * edges.
 *
 * <p>Cross-region seamlessness is preserved because (a) the noise lattice seed
 * comes from {@code worldRng} and (b) the seed pool at the border of region A
 * is the same pool region B computes for its border — both use
 * {@link ZoneSeeds#neighbourhood}.
 */
public final class BaseNoiseLayer implements Layer {

    private static final int OCTAVES = 5;
    private static final double BASE_FREQUENCY = 1.0 / 64.0;
    private static final double LACUNARITY = 2.0;
    private static final double GAIN = 0.5;

    // Tiny epsilon guards against divide-by-zero when a cell lands exactly on a seed.
    private static final double INV_DIST_EPS = 1e-6;

    @Override
    public String name() {
        return "terrain.base_noise";
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        // Read ZONE_SEEDS so VoronoiZonesLayer runs first. The neighbour pool
        // used for blending is recomputed from ZoneSeeds (pure function).
        return Set.of(Features.ZONE_SEEDS);
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(Features.BASE_HEIGHT);
    }

    @Override
    public void compute(LayerContext ctx) {
        ctx.read(Features.ZONE_SEEDS); // declare dep; neighbour pool below is authoritative

        int size = Features.REGION_SIZE;
        RegionCoord c = ctx.coord();
        double originX = (double) c.rx() * size;
        double originZ = (double) c.rz() * size;

        long latticeSeed = ctx.worldRng(0xB_A_5_E).nextLong();
        ZoneSeed[] pool = ZoneSeeds.neighbourhood(ctx.seed(), c.rx(), c.rz(), size);

        double[] kindAmp = new double[ZoneKind.count()];
        double[] kindOff = new double[ZoneKind.count()];
        for (ZoneKind k : ZoneKind.values()) {
            kindAmp[k.ordinal()] = k.amplitude();
            kindOff[k.ordinal()] = k.offset();
        }

        float[][] h = new float[size][size];
        for (int z = 0; z < size; z++) {
            double wz = originZ + z + 0.5;
            for (int x = 0; x < size; x++) {
                double wx = originX + x + 0.5;

                double ampSum = 0.0, offSum = 0.0, wSum = 0.0;
                for (ZoneSeed s : pool) {
                    double dx = s.wx() - wx;
                    double dz = s.wz() - wz;
                    double invSq = 1.0 / (dx * dx + dz * dz + INV_DIST_EPS);
                    double w = invSq * invSq; // inverse quartic; see javadoc above
                    int k = s.kind();
                    ampSum += w * kindAmp[k];
                    offSum += w * kindOff[k];
                    wSum += w;
                }
                double amp = ampSum / wSum;
                double off = offSum / wSum;

                double n = ValueNoise2D.fbm(wx, wz, latticeSeed,
                    OCTAVES, BASE_FREQUENCY, LACUNARITY, GAIN);
                h[z][x] = (float) (n * amp + off);
            }
        }
        ctx.write(Features.BASE_HEIGHT, h);
    }
}
