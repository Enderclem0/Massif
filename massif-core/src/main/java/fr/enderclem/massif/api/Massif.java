package fr.enderclem.massif.api;

import fr.enderclem.massif.stages.DemoHeightmapProducer;

/**
 * Public factory for consumers that just want a ready-to-run framework.
 * Consumers (e.g. the visualizer) call {@link #defaultFramework} and never
 * touch the internal {@code pipeline} / {@code stages} packages directly.
 */
public final class Massif {

    private Massif() {}

    /**
     * A framework wired with every producer the current rebuild phase has
     * implemented. Phase 2 ships a single demo heightmap producer; later
     * phases plug zone, structural-plan, hydrology, technique, and
     * composition producers into the same factory.
     */
    public static MassifFramework defaultFramework() {
        return MassifFramework.of(new DemoHeightmapProducer());
    }
}
