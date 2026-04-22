package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.BorderField;
import fr.enderclem.massif.api.BorderSample;
import fr.enderclem.massif.api.ZoneSeed;
import fr.enderclem.massif.api.ZoneSeedPool;

/**
 * Voronoi-backed {@link BorderField}. Finds the two nearest seeds in the
 * configured {@link ZoneSeedPool} and derives distance, normal, and
 * zone-type pairing from them.
 */
public final class VoronoiBorderField implements BorderField {

    private final ZoneSeedPool pool;

    public VoronoiBorderField(ZoneSeedPool pool) {
        this.pool = pool;
    }

    @Override
    public BorderSample sampleAt(double x, double z) {
        ZoneSeed[] seeds = pool.seedsNear(x, z);

        ZoneSeed near = null, other = null;
        double nearDistSq = Double.POSITIVE_INFINITY;
        double otherDistSq = Double.POSITIVE_INFINITY;
        for (ZoneSeed s : seeds) {
            double dx = s.wx() - x;
            double dz = s.wz() - z;
            double d = dx * dx + dz * dz;
            if (d < nearDistSq) {
                otherDistSq = nearDistSq;
                other = near;
                nearDistSq = d;
                near = s;
            } else if (d < otherDistSq) {
                otherDistSq = d;
                other = s;
            }
        }
        // Pool is always ≥ 2 seeds in practice (jittered: 3×3 × 4 = 36;
        // relaxed: all window seeds). If it ever weren't, BorderSample
        // below would dereference null — callers won't observe this.

        double sx = other.wx() - near.wx();
        double sz = other.wz() - near.wz();
        double sep = Math.sqrt(sx * sx + sz * sz);
        double distance = (otherDistSq - nearDistSq) / (2.0 * sep);
        double nx = -sx / sep;
        double nz = -sz / sep;

        return new BorderSample(distance, nx, nz, near.kind(), other.kind());
    }
}
