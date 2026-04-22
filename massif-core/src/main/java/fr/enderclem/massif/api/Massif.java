package fr.enderclem.massif.api;

import fr.enderclem.massif.stages.DemoHeightmapProducer;
import fr.enderclem.massif.stages.features.MountainClusterProducer;
import fr.enderclem.massif.stages.zones.BorderFieldProducer;
import fr.enderclem.massif.stages.zones.ZoneFieldProducer;
import fr.enderclem.massif.stages.zones.ZoneGraphProducer;
import fr.enderclem.massif.stages.zones.ZoneRegistryProducer;
import fr.enderclem.massif.stages.zones.ZoneSeedPoolProducer;

/**
 * Public factory for consumers that just want a ready-to-run framework.
 * Consumers (e.g. the visualizer) call {@link #defaultFramework} and never
 * touch the internal {@code pipeline} / {@code stages} packages directly.
 */
public final class Massif {

    /** Lloyd iterations applied to the default framework's seed pool. */
    public static final int DEFAULT_LLOYD_ITERATIONS = 3;

    private Massif() {}

    /**
     * Framework with the default {@link ZoneTypeRegistry} and
     * {@link #DEFAULT_LLOYD_ITERATIONS} iterations of Lloyd relaxation on
     * the zone seed pool.
     */
    public static MassifFramework defaultFramework() {
        return framework(ZoneTypeRegistry.defaultRegistry(), DEFAULT_LLOYD_ITERATIONS);
    }

    /**
     * Framework with the given registry and zero Lloyd iterations (seed pool
     * is the jittered on-demand one — original Phase 3 behaviour).
     */
    public static MassifFramework framework(ZoneTypeRegistry registry) {
        return framework(registry, 0);
    }

    /**
     * Framework wired with every Phase-3 producer: registry publication,
     * the chosen seed pool (jittered if {@code lloydIterations == 0},
     * Lloyd-relaxed otherwise), zone field, border field, zone graph, and
     * the walking-skeleton demo heightmap.
     */
    public static MassifFramework framework(ZoneTypeRegistry registry, int lloydIterations) {
        return MassifFramework.of(
            new ZoneRegistryProducer(registry),
            new ZoneSeedPoolProducer(
                ZoneFieldProducer.DEFAULT_CELL_SIZE, lloydIterations, MassifKeys.VIEW_SIZE),
            new ZoneFieldProducer(),
            new BorderFieldProducer(),
            new ZoneGraphProducer(),
            new MountainClusterProducer(),
            new DemoHeightmapProducer()
        );
    }
}
