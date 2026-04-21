package fr.enderclem.massif.primitives;

/**
 * Deterministic RNG seeded from (seed, rx, rz, salt).
 * SplitMix64 mixing on construction, then a stateful SplitMix64 sequence.
 * Invariant: identical inputs produce identical output streams across threads,
 * runs, and processes. Never use ThreadLocalRandom or Math.random in the core.
 */
public final class RegionRng {

    private long state;

    public RegionRng(long seed, int rx, int rz, long salt) {
        long s = mix(seed);
        s = mix(s ^ ((long) rx * 0x9E3779B97F4A7C15L));
        s = mix(s ^ ((long) rz * 0xBF58476D1CE4E5B9L));
        s = mix(s ^ (salt * 0x94D049BB133111EBL));
        this.state = s == 0 ? 0x9E3779B97F4A7C15L : s;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public int nextInt() {
        return (int) (nextLong() >>> 32);
    }

    public int nextInt(int boundExclusive) {
        if (boundExclusive <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return Math.floorMod(nextInt(), boundExclusive);
    }

    /** Uniform double in [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Uniform double in [min, max). */
    public double nextDouble(double min, double max) {
        return min + (max - min) * nextDouble();
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
