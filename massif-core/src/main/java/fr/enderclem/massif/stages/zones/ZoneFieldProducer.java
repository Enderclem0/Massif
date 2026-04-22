package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneField;
import fr.enderclem.massif.api.ZoneSeedPool;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.Set;

/**
 * Wraps {@link MassifKeys#ZONE_SEED_POOL} in a queryable {@link ZoneField}.
 * All seed-source variation (jittered vs. Lloyd-relaxed) is handled
 * upstream by the pool producer.
 */
public final class ZoneFieldProducer implements Producer {

    public static final int DEFAULT_CELL_SIZE = 256;

    public ZoneFieldProducer() {}

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
        return Set.of(MassifKeys.ZONE_SEED_POOL);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneSeedPool pool = ctx.read(MassifKeys.ZONE_SEED_POOL);
        ctx.write(MassifKeys.ZONE_FIELD, new VoronoiZoneField(pool));
    }
}
