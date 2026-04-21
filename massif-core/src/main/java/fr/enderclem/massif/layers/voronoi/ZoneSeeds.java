package fr.enderclem.massif.layers.voronoi;

import fr.enderclem.massif.primitives.RegionRng;

/**
 * Pure function producing the {@link ZoneSeed}s for a single region.
 *
 * This function is intentionally stateless and has no blackboard coupling:
 * any layer, in any region, may call it to obtain the seeds for a neighbour.
 * Cross-region continuity works precisely because all sides compute identical
 * seed sets from identical inputs — no neighbour-blackboard reads required.
 */
public final class ZoneSeeds {

    private ZoneSeeds() {}

    /** Seeds per region, arranged as a jittered grid. 2x2 → zone scale ≈ region/2. */
    public static final int SEEDS_PER_SIDE = 2;
    public static final int KIND_COUNT = ZoneKind.count();

    private static final long SEEDS_SALT = 0x566F_726F_6E_6F_69L;

    public static ZoneSeed[] of(long worldSeed, int rx, int rz, int regionSize) {
        int n = SEEDS_PER_SIDE * SEEDS_PER_SIDE;
        ZoneSeed[] seeds = new ZoneSeed[n];
        RegionRng rng = new RegionRng(worldSeed, rx, rz, SEEDS_SALT);

        double cell = (double) regionSize / SEEDS_PER_SIDE;
        double jitter = cell * 0.45;
        double originX = (double) rx * regionSize;
        double originZ = (double) rz * regionSize;

        int idx = 0;
        for (int gz = 0; gz < SEEDS_PER_SIDE; gz++) {
            for (int gx = 0; gx < SEEDS_PER_SIDE; gx++) {
                double baseX = originX + (gx + 0.5) * cell;
                double baseZ = originZ + (gz + 0.5) * cell;
                double dx = rng.nextDouble(-jitter, jitter);
                double dz = rng.nextDouble(-jitter, jitter);
                int kind = rng.nextInt(KIND_COUNT);
                int id = packId(rx, rz, idx);
                seeds[idx++] = new ZoneSeed(baseX + dx, baseZ + dz, kind, id);
            }
        }
        return seeds;
    }

    /** Concatenated seeds for the 3x3 block centred on (rx, rz). Used by classification. */
    public static ZoneSeed[] neighbourhood(long worldSeed, int rx, int rz, int regionSize) {
        int per = SEEDS_PER_SIDE * SEEDS_PER_SIDE;
        ZoneSeed[] all = new ZoneSeed[per * 9];
        int i = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                ZoneSeed[] s = of(worldSeed, rx + dx, rz + dz, regionSize);
                System.arraycopy(s, 0, all, i, s.length);
                i += s.length;
            }
        }
        return all;
    }

    private static int packId(int rx, int rz, int localIdx) {
        // Deterministic per seed; not meant to be reversible. Mixes region coord and index.
        int h = rx * 0x9E3779B1 ^ Integer.rotateLeft(rz, 16) * 0x85EBCA6B;
        return (h ^ (localIdx * 0xC2B2AE35)) & 0x7FFF_FFFF;
    }
}
