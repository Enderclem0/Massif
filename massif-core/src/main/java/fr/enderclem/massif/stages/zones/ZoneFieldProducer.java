package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneField;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.Set;

/**
 * Builds the Voronoi {@link ZoneField} using the seed from the execution
 * context and the registry read from the blackboard. {@code cellSize} sets
 * the spatial scale of each Voronoi cell; at 256 blocks per cell, a
 * 512-block viewport contains a 2×2 grid of cells (~16 seeds with 2-per-
 * side jittering), which gives the walking-skeleton visualiser enough
 * variety to be interesting.
 */
public final class ZoneFieldProducer implements Producer {

    public static final int DEFAULT_CELL_SIZE = 256;

    private final int cellSize;

    public ZoneFieldProducer() {
        this(DEFAULT_CELL_SIZE);
    }

    public ZoneFieldProducer(int cellSize) {
        this.cellSize = cellSize;
    }

    @Override
    public String name() {
        return "zones.field";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.ZONE_FIELD);
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(MassifKeys.ZONE_REGISTRY);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneTypeRegistry registry = ctx.read(MassifKeys.ZONE_REGISTRY);
        ZoneField field = new VoronoiZoneField(ctx.seed(), registry.size(), cellSize);
        ctx.write(MassifKeys.ZONE_FIELD, field);
    }
}
