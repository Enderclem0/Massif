package fr.enderclem.massif.api;

import fr.enderclem.massif.stages.DemoHeightmapProducer;
import fr.enderclem.massif.stages.zones.BorderFieldProducer;
import fr.enderclem.massif.stages.zones.ZoneFieldProducer;
import fr.enderclem.massif.stages.zones.ZoneGraphProducer;
import fr.enderclem.massif.stages.zones.ZoneRegistryProducer;

/**
 * Public factory for consumers that just want a ready-to-run framework.
 * Consumers (e.g. the visualizer) call {@link #defaultFramework} and never
 * touch the internal {@code pipeline} / {@code stages} packages directly.
 */
public final class Massif {

    private Massif() {}

    /**
     * A framework wired with every producer the current rebuild phase has
     * implemented. Uses {@link ZoneTypeRegistry#defaultRegistry()} for zone
     * types; call {@link #framework(ZoneTypeRegistry)} to supply a custom
     * registry.
     */
    public static MassifFramework defaultFramework() {
        return framework(ZoneTypeRegistry.defaultRegistry());
    }

    /**
     * Framework with the given {@code registry}. Wires the Phase 3 producers:
     * registry publication, Voronoi zone field, and the Phase 2 demo
     * heightmap (kept until Layer 4 composition lands).
     */
    public static MassifFramework framework(ZoneTypeRegistry registry) {
        return MassifFramework.of(
            new ZoneRegistryProducer(registry),
            new ZoneFieldProducer(),
            new BorderFieldProducer(),
            new ZoneGraphProducer(),
            new DemoHeightmapProducer()
        );
    }
}
