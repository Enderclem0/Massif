package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.ZoneSeed;
import fr.enderclem.massif.api.ZoneSeedPool;

/**
 * Default on-demand pool. For every {@link #seedsNear} call, computes the
 * 3×3 region neighbourhood around the query point from {@link ZoneSeeds}
 * and returns it. Pure function: no cached state, no window.
 *
 * <p>The 3×3 neighbourhood is sufficient for jittered seeds because each
 * seed is bounded to stay within 45% of its home region; the closest seed
 * to any point is therefore guaranteed to live in one of the 9 adjacent
 * regions. This guarantee does <em>not</em> survive Lloyd relaxation —
 * use {@link RelaxedZoneSeedPool} for that case.
 */
public final class JitteredZoneSeedPool implements ZoneSeedPool {

    private final long worldSeed;
    private final int cellSize;
    private final int kindCount;

    public JitteredZoneSeedPool(long worldSeed, int cellSize, int kindCount) {
        if (cellSize <= 0) throw new IllegalArgumentException("cellSize must be positive");
        if (kindCount <= 0) throw new IllegalArgumentException("kindCount must be positive");
        this.worldSeed = worldSeed;
        this.cellSize = cellSize;
        this.kindCount = kindCount;
    }

    @Override
    public ZoneSeed[] seedsNear(double x, double z) {
        int rx = Math.floorDiv((int) Math.floor(x), cellSize);
        int rz = Math.floorDiv((int) Math.floor(z), cellSize);
        return ZoneSeeds.neighbourhood(worldSeed, rx, rz, cellSize, kindCount);
    }
}
