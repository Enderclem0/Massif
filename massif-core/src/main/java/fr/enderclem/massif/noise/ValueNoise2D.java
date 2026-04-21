package fr.enderclem.massif.noise;

import fr.enderclem.massif.primitives.RegionRng;

/**
 * Bilinearly-interpolated integer-lattice value noise with quintic smoothing,
 * plus an fBM convenience. Pure function of (x, z, seed) — two points in any
 * two regions that query the same (x, z) receive identical values, which is
 * what makes the world noise field seamless across regions.
 */
public final class ValueNoise2D {

    private ValueNoise2D() {}

    /** Single-octave value noise in roughly [-1, 1]. */
    public static double sample(double x, double z, long seed) {
        int xi = fastFloor(x);
        int zi = fastFloor(z);
        double tx = smooth(x - xi);
        double tz = smooth(z - zi);

        double v00 = lattice(xi,     zi,     seed);
        double v10 = lattice(xi + 1, zi,     seed);
        double v01 = lattice(xi,     zi + 1, seed);
        double v11 = lattice(xi + 1, zi + 1, seed);

        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }

    /**
     * Standard fractal Brownian motion: sum of {@code octaves} detuned copies
     * of {@link #sample}, normalised so output stays roughly in [-1, 1].
     */
    public static double fbm(double x, double z, long seed,
                             int octaves, double baseFrequency,
                             double lacunarity, double gain) {
        double amp = 1.0, freq = baseFrequency, sum = 0.0, norm = 0.0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * sample(x * freq, z * freq, seed + o);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    private static double lattice(int x, int z, long seed) {
        RegionRng rng = new RegionRng(seed, x, z, 0xCAFEBABEL);
        return rng.nextDouble() * 2.0 - 1.0;
    }

    private static double smooth(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
