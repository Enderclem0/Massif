package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.BorderField;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.Set;

/**
 * Publishes a Voronoi-backed {@link BorderField} under
 * {@link MassifKeys#BORDER_FIELD}. Cell size matches {@link ZoneFieldProducer}
 * so both fields resolve against the same underlying seed lattice.
 */
public final class BorderFieldProducer implements Producer {

    private final int cellSize;

    public BorderFieldProducer() {
        this(ZoneFieldProducer.DEFAULT_CELL_SIZE);
    }

    public BorderFieldProducer(int cellSize) {
        this.cellSize = cellSize;
    }

    @Override
    public String name() {
        return "zones.border_field";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.BORDER_FIELD);
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(MassifKeys.ZONE_REGISTRY);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneTypeRegistry registry = ctx.read(MassifKeys.ZONE_REGISTRY);
        BorderField field = new VoronoiBorderField(ctx.seed(), registry.size(), cellSize);
        ctx.write(MassifKeys.BORDER_FIELD, field);
    }
}
