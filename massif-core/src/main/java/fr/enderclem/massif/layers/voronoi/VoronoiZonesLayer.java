package fr.enderclem.massif.layers.voronoi;

import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.dag.LayerContext;
import fr.enderclem.massif.layers.Features;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.Set;

/**
 * Scope.md stage 1 (zones half). Writes this region's Voronoi seeds, then
 * classifies every cell against the 3x3 neighbourhood seed pool.
 *
 * Cross-region continuity is achieved by {@link ZoneSeeds} being a pure
 * function of (worldSeed, rx, rz): the layer running in region (rx+1, rz)
 * classifies border cells against the exact same seeds that region (rx, rz)
 * already saw, so their shared border is pixel-identical. No blackboard
 * reads from neighbours are required.
 */
public final class VoronoiZonesLayer implements Layer {

    @Override
    public String name() {
        return "voronoi.zones";
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of();
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(Features.ZONE_SEEDS, Features.ZONES);
    }

    @Override
    public void compute(LayerContext ctx) {
        long seed = ctx.seed();
        RegionCoord coord = ctx.coord();
        int size = Features.REGION_SIZE;

        ctx.write(Features.ZONE_SEEDS, ZoneSeeds.of(seed, coord.rx(), coord.rz(), size));
        ctx.write(Features.ZONES, VoronoiClassifier.classify(seed, coord, size));
    }
}
