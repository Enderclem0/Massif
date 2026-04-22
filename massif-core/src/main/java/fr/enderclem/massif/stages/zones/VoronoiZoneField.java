package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.ZoneField;
import fr.enderclem.massif.api.ZoneSeed;
import fr.enderclem.massif.api.ZoneSeedPool;

/**
 * Voronoi-backed {@link ZoneField}. Nearest-seed search is delegated to
 * whatever {@link ZoneSeedPool} is passed in — jittered (on-demand, 3×3
 * neighbourhood) or relaxed (precomputed, full-window). This class only
 * owns the linear search over whatever the pool hands it.
 */
public final class VoronoiZoneField implements ZoneField {

    private final ZoneSeedPool pool;

    public VoronoiZoneField(ZoneSeedPool pool) {
        this.pool = pool;
    }

    @Override
    public int typeAt(double x, double z) {
        ZoneSeed[] seeds = pool.seedsNear(x, z);
        int best = 0;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (ZoneSeed s : seeds) {
            double dx = s.wx() - x;
            double dz = s.wz() - z;
            double d = dx * dx + dz * dz;
            if (d < bestDistSq) {
                bestDistSq = d;
                best = s.kind();
            }
        }
        return best;
    }

    @Override
    public int[][] sampleGrid(int x0, int z0, int width, int height) {
        int[][] out = new int[height][width];
        // Lightweight caching: every time the pool's response changes we
        // recompute. For JitteredZoneSeedPool the response is keyed on
        // region (rx, rz) so consecutive same-region samples re-use the
        // array by identity; for RelaxedZoneSeedPool every call returns
        // the same array.
        ZoneSeed[] cached = null;
        for (int z = 0; z < height; z++) {
            double wz = z0 + z + 0.5;
            for (int x = 0; x < width; x++) {
                double wx = x0 + x + 0.5;
                ZoneSeed[] seeds = pool.seedsNear(wx, wz);
                if (seeds != cached) cached = seeds;
                int best = 0;
                double bestDistSq = Double.POSITIVE_INFINITY;
                for (ZoneSeed s : cached) {
                    double dx = s.wx() - wx;
                    double dz = s.wz() - wz;
                    double d = dx * dx + dz * dz;
                    if (d < bestDistSq) {
                        bestDistSq = d;
                        best = s.kind();
                    }
                }
                out[z][x] = best;
            }
        }
        return out;
    }
}
