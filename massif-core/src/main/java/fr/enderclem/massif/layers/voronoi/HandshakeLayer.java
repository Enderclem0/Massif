package fr.enderclem.massif.layers.voronoi;

import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.dag.LayerContext;
import fr.enderclem.massif.layers.Features;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.Set;

/**
 * Computes the handshake graph for this region by walking each edge, classifying
 * both sides against the 3x3 neighbourhood seed pool, segmenting into runs of
 * identical zone-pairs, and emitting one node at the midpoint of each run.
 *
 * Both regions sharing an edge produce identical nodes because the pool and
 * the sampling scheme are deterministic functions of (worldSeed, rx, rz).
 */
public final class HandshakeLayer implements Layer {

    @Override
    public String name() {
        return "voronoi.handshake";
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        // Reading ZONE_SEEDS establishes the DAG dep on VoronoiZonesLayer. The
        // neighbour pool is recomputed inline (pure function), not read from
        // a neighbour's blackboard.
        return Set.of(Features.ZONE_SEEDS);
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(Features.HANDSHAKE);
    }

    @Override
    public void compute(LayerContext ctx) {
        ctx.read(Features.ZONE_SEEDS); // declare the dep; value not used here

        long seed = ctx.seed();
        RegionCoord coord = ctx.coord();
        int size = Features.REGION_SIZE;

        ctx.write(Features.HANDSHAKE, HandshakeLayerImpl.compute(seed, coord, size));
    }
}
