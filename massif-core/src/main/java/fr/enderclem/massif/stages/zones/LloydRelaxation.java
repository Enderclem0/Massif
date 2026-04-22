package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.ZoneSeed;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded centroidal-Voronoi relaxation (Lloyd's algorithm) over a window.
 *
 * <p>For each iteration, samples the window on a regular stride, finds the
 * nearest seed to every sample, and moves each seed to the mean of the
 * samples nearest to it. In the limit of stride → 1 this is equivalent to
 * analytic Lloyd on the window-clipped Voronoi diagram; at stride 4 it's
 * visually indistinguishable while being 16× faster.
 *
 * <p>Seeds with zero samples (cell entirely outside the window) keep their
 * original position for that iteration — this is the correct boundary
 * behaviour for a clipped relaxation, and the caller handles it by
 * providing a halo of seeds around the visible window.
 *
 * <p>{@code ZoneSeed.id} and {@code ZoneSeed.kind} are preserved across
 * iterations; only the position changes.
 */
public final class LloydRelaxation {

    private static final int SAMPLE_STRIDE = 4;

    private LloydRelaxation() {}

    /**
     * Relax the given seeds over the axis-aligned window
     * {@code [x0, x1) × [z0, z1)} for {@code iterations} steps. Returns a
     * new list; does not mutate the input.
     */
    public static List<ZoneSeed> relax(List<ZoneSeed> initial,
                                        int x0, int z0, int x1, int z1,
                                        int iterations) {
        if (iterations <= 0) return List.copyOf(initial);
        List<ZoneSeed> current = new ArrayList<>(initial);
        for (int i = 0; i < iterations; i++) {
            current = step(current, x0, z0, x1, z1);
        }
        return current;
    }

    private static List<ZoneSeed> step(List<ZoneSeed> seeds,
                                       int x0, int z0, int x1, int z1) {
        int n = seeds.size();
        double[] sumX = new double[n];
        double[] sumZ = new double[n];
        long[] counts = new long[n];

        ZoneSeed[] arr = seeds.toArray(new ZoneSeed[0]);
        for (int z = z0; z < z1; z += SAMPLE_STRIDE) {
            double sampleZ = z + 0.5;
            for (int x = x0; x < x1; x += SAMPLE_STRIDE) {
                double sampleX = x + 0.5;
                int nearest = 0;
                double bestDistSq = Double.POSITIVE_INFINITY;
                for (int si = 0; si < n; si++) {
                    ZoneSeed s = arr[si];
                    double dx = s.wx() - sampleX;
                    double dz = s.wz() - sampleZ;
                    double d = dx * dx + dz * dz;
                    if (d < bestDistSq) {
                        bestDistSq = d;
                        nearest = si;
                    }
                }
                sumX[nearest] += sampleX;
                sumZ[nearest] += sampleZ;
                counts[nearest]++;
            }
        }

        List<ZoneSeed> out = new ArrayList<>(n);
        for (int si = 0; si < n; si++) {
            ZoneSeed s = arr[si];
            if (counts[si] > 0) {
                double cx = sumX[si] / counts[si];
                double cz = sumZ[si] / counts[si];
                out.add(new ZoneSeed(cx, cz, s.kind(), s.id()));
            } else {
                out.add(s);
            }
        }
        return out;
    }
}
