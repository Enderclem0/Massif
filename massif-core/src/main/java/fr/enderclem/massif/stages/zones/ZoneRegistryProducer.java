package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.Set;

/**
 * Publishes the world's {@link ZoneTypeRegistry} under
 * {@link MassifKeys#ZONE_REGISTRY}. The registry is world configuration,
 * not seed-dependent, so every call writes the same value — routing it
 * through the blackboard keeps the "consumers read everything from the
 * blackboard" discipline intact.
 */
public final class ZoneRegistryProducer implements Producer {

    private final ZoneTypeRegistry registry;

    public ZoneRegistryProducer(ZoneTypeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "zones.registry";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.ZONE_REGISTRY);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ctx.write(MassifKeys.ZONE_REGISTRY, registry);
    }
}
