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
     * Framework with the default {@link ZoneTypeRegistry}, the default
     * {@link WorldWindow}, and {@link #DEFAULT_LLOYD_ITERATIONS} iterations
     * of Lloyd relaxation.
     */
    public static MassifFramework defaultFramework() {
        return framework(
            ZoneTypeRegistry.defaultRegistry(),
            DEFAULT_LLOYD_ITERATIONS,
            WorldWindow.defaultWindow());
    }

    /**
     * Framework with the given registry; uses the default world window
     * and zero Lloyd iterations.
     */
    public static MassifFramework framework(ZoneTypeRegistry registry) {
        return framework(registry, 0, WorldWindow.defaultWindow());
    }

    /**
     * Framework with the given registry and Lloyd iteration count; uses
     * the default world window.
     */
    public static MassifFramework framework(ZoneTypeRegistry registry, int lloydIterations) {
        return framework(registry, lloydIterations, WorldWindow.defaultWindow());
    }

    /**
     * Framework wired with every Phase-4 producer at the specified world
     * window. The visualiser rebuilds the framework with a new window each
     * time the user zooms or pans.
     */
    public static MassifFramework framework(ZoneTypeRegistry registry,
                                            int lloydIterations,
                                            WorldWindow window) {
        return MassifFramework.of(
            new ZoneRegistryProducer(registry),
            new ZoneSeedPoolProducer(ZoneFieldProducer.DEFAULT_CELL_SIZE, lloydIterations, window),
            new ZoneFieldProducer(),
            new BorderFieldProducer(),
            new ZoneGraphProducer(ZoneFieldProducer.DEFAULT_CELL_SIZE, window),
            new MountainClusterProducer(),
            new DemoHeightmapProducer(window)
        );
    }
}
