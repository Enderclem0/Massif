package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.ZoneField;

/**
 * Voronoi-backed {@link ZoneField}. Seeds are jittered-grid points placed
 * by {@link ZoneSeeds} across the world (pure function of {@code (seed, rx,
 * rz)}), so any two queries at the same world coordinate — in any thread,
 * in any visualiser instance, in any neighbour region's pipeline — see the
 * same zone id. There is no shared state, no caching between calls of
 * {@link #typeAt}, and no notion of "finite world": the grid extends
 * infinitely.
 *
 * <p>{@link #sampleGrid} overrides the default one-seed-pool-per-point
 * implementation with a simple "last pool wins" cache. Iterating a 2D grid
 * row-by-row, consecutive samples almost always fall in the same region,
 * so rebuilding the 3×3 neighbourhood per point is wasted work.
 */
public final class VoronoiZoneField implements ZoneField {

    private final long seed;
    private final int kindCount;
    private final int cellSize;

    public VoronoiZoneField(long seed, int kindCount, int cellSize) {
        if (kindCount <= 0) throw new IllegalArgumentException("kindCount must be positive");
        if (cellSize <= 0) throw new IllegalArgumentException("cellSize must be positive");
        this.seed = seed;
        this.kindCount = kindCount;
        this.cellSize = cellSize;
    }

    @Override
    public int typeAt(double x, double z) {
        int rx = Math.floorDiv((int) Math.floor(x), cellSize);
        int rz = Math.floorDiv((int) Math.floor(z), cellSize);
        ZoneSeed[] pool = ZoneSeeds.neighbourhood(seed, rx, rz, cellSize, kindCount);
        return VoronoiClassifier.nearestKind(x, z, pool);
    }

    @Override
    public int[][] sampleGrid(int x0, int z0, int width, int height) {
        int[][] out = new int[height][width];
        int cachedRx = Integer.MIN_VALUE;
        int cachedRz = Integer.MIN_VALUE;
        ZoneSeed[] pool = null;

        for (int z = 0; z < height; z++) {
            int wz = z0 + z;
            for (int x = 0; x < width; x++) {
                int wx = x0 + x;
                int rx = Math.floorDiv(wx, cellSize);
                int rz = Math.floorDiv(wz, cellSize);
                if (pool == null || rx != cachedRx || rz != cachedRz) {
                    pool = ZoneSeeds.neighbourhood(seed, rx, rz, cellSize, kindCount);
                    cachedRx = rx;
                    cachedRz = rz;
                }
                out[z][x] = VoronoiClassifier.nearestKind(wx + 0.5, wz + 0.5, pool);
            }
        }
        return out;
    }
}
