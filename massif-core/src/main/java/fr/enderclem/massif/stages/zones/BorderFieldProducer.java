package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.BorderField;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneSeedPool;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.Set;

/**
 * Wraps {@link MassifKeys#ZONE_SEED_POOL} in a queryable {@link BorderField}.
 */
public final class BorderFieldProducer implements Producer {

    public BorderFieldProducer() {}

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
        return Set.of(MassifKeys.ZONE_SEED_POOL);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneSeedPool pool = ctx.read(MassifKeys.ZONE_SEED_POOL);
        BorderField field = new VoronoiBorderField(pool);
        ctx.write(MassifKeys.BORDER_FIELD, field);
    }
}
