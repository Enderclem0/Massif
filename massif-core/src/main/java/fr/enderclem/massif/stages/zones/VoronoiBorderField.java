package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.BorderField;
import fr.enderclem.massif.api.BorderSample;

/**
 * Voronoi-backed {@link BorderField}. Finds the two nearest seeds to each
 * sample point and derives border distance, normal, and zone-type pairing
 * from them. Pure function of world seed + coordinate, like
 * {@link VoronoiZoneField}.
 */
public final class VoronoiBorderField implements BorderField {

    private final long seed;
    private final int kindCount;
    private final int cellSize;

    public VoronoiBorderField(long seed, int kindCount, int cellSize) {
        if (kindCount <= 0) throw new IllegalArgumentException("kindCount must be positive");
        if (cellSize <= 0) throw new IllegalArgumentException("cellSize must be positive");
        this.seed = seed;
        this.kindCount = kindCount;
        this.cellSize = cellSize;
    }

    @Override
    public BorderSample sampleAt(double x, double z) {
        int rx = Math.floorDiv((int) Math.floor(x), cellSize);
        int rz = Math.floorDiv((int) Math.floor(z), cellSize);
        ZoneSeed[] pool = ZoneSeeds.neighbourhood(seed, rx, rz, cellSize, kindCount);

        ZoneSeed near = null, other = null;
        double nearDistSq = Double.POSITIVE_INFINITY;
        double otherDistSq = Double.POSITIVE_INFINITY;
        for (ZoneSeed s : pool) {
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
        // Pool is always ≥ 2 seeds (3×3 neighbourhood of non-empty regions),
        // so `other` is non-null here.

        double sx = other.wx() - near.wx();
        double sz = other.wz() - near.wz();
        double sep = Math.sqrt(sx * sx + sz * sz);
        // Exact signed distance to the bisector between `near` and `other`,
        // positive on `near`'s side (always the case here because near is
        // the closer seed by construction). Reduces to
        // (otherDistSq - nearDistSq) / (2 · sep).
        double distance = (otherDistSq - nearDistSq) / (2.0 * sep);

        // Normal from `other` toward `near`: points into near's cell.
        double nx = -sx / sep;
        double nz = -sz / sep;

        return new BorderSample(distance, nx, nz, near.kind(), other.kind());
    }
}
