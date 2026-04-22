package fr.enderclem.massif.layers.voronoi;

import fr.enderclem.massif.primitives.RegionCoord;

/**
 * Pure-function Voronoi cell classifier. Zone kinds are opaque integer ids in
 * {@code [0, kindCount)}; the rebuild's zone-type registry will supply that id
 * space when Phase 3 lands.
 */
public final class VoronoiClassifier {

    private VoronoiClassifier() {}

    public static int[][] classify(long seed, RegionCoord coord, int size, int kindCount) {
        ZoneSeed[] pool = ZoneSeeds.neighbourhood(seed, coord.rx(), coord.rz(), size, kindCount);
        int[][] zones = new int[size][size];
        double originX = (double) coord.rx() * size;
        double originZ = (double) coord.rz() * size;
        for (int z = 0; z < size; z++) {
            double wz = originZ + z + 0.5;
            for (int x = 0; x < size; x++) {
                double wx = originX + x + 0.5;
                zones[z][x] = nearestKind(wx, wz, pool);
            }
        }
        return zones;
    }

    static int nearestKind(double wx, double wz, ZoneSeed[] pool) {
        int bestKind = 0;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (ZoneSeed s : pool) {
            double dx = s.wx() - wx;
            double dz = s.wz() - wz;
            double d = dx * dx + dz * dz;
            if (d < bestDistSq) {
                bestDistSq = d;
                bestKind = s.kind();
            }
        }
        return bestKind;
    }
}
