package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.ZoneSeed;
import fr.enderclem.massif.api.ZoneSeedPool;

/**
 * Holds the Lloyd-relaxed seed positions over a bounded window (plus a
 * halo of un-visualised cells that exist so edge seeds inside the window
 * relax against real neighbours rather than against emptiness).
 *
 * <p>Relaxation breaks the 3×3 locality guarantee that the jittered pool
 * relies on — a relaxed seed can migrate into any region within the pool —
 * so {@link #seedsNear} returns the entire relaxed array for queries
 * anywhere inside the window. Outside the window the fallback pool takes
 * over, which means a point just outside the window will see jittered
 * (un-relaxed) seeds. Consumers should keep their queries inside the
 * window when using relaxation.
 */
public final class RelaxedZoneSeedPool implements ZoneSeedPool {

    private final ZoneSeed[] relaxedSeeds;
    private final double windowX0;
    private final double windowZ0;
    private final double windowX1;
    private final double windowZ1;
    private final ZoneSeedPool fallback;

    public RelaxedZoneSeedPool(ZoneSeed[] relaxedSeeds,
                               int windowX0, int windowZ0,
                               int windowX1, int windowZ1,
                               ZoneSeedPool fallback) {
        this.relaxedSeeds = relaxedSeeds.clone();
        this.windowX0 = windowX0;
        this.windowZ0 = windowZ0;
        this.windowX1 = windowX1;
        this.windowZ1 = windowZ1;
        this.fallback = fallback;
    }

    @Override
    public ZoneSeed[] seedsNear(double x, double z) {
        if (x >= windowX0 && x < windowX1 && z >= windowZ0 && z < windowZ1) {
            return relaxedSeeds;
        }
        return fallback.seedsNear(x, z);
    }

    @Override
    public ZoneSeed[] allSeeds() {
        return relaxedSeeds.clone();
    }
}
