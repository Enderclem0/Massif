package fr.enderclem.massif.layers.voronoi;

import fr.enderclem.massif.layers.voronoi.HandshakeGraph.Edge;
import fr.enderclem.massif.layers.voronoi.HandshakeGraph.HandshakeNode;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-function handshake-graph builder. Extracted from {@link HandshakeLayer}
 * so border-aware layers can produce the handshake for a neighbour region on
 * demand, without a blackboard. The result is a deterministic function of
 * {@code (seed, coord)}.
 */
public final class HandshakeLayerImpl {

    private static final double EPSILON = 0.25;

    private HandshakeLayerImpl() {}

    public static HandshakeGraph compute(long seed, RegionCoord coord, int size) {
        ZoneSeed[] pool = ZoneSeeds.neighbourhood(seed, coord.rx(), coord.rz(), size);

        double x0 = (double) coord.rx() * size;
        double z0 = (double) coord.rz() * size;
        double x1 = x0 + size;
        double z1 = z0 + size;

        List<HandshakeNode> nodes = new ArrayList<>();
        nodes.addAll(scanEdge(Edge.NORTH, true,  x0, z0, x1, z0, pool, size));
        nodes.addAll(scanEdge(Edge.SOUTH, true,  x0, z1, x1, z1, pool, size));
        nodes.addAll(scanEdge(Edge.WEST,  false, x0, z0, x0, z1, pool, size));
        nodes.addAll(scanEdge(Edge.EAST,  false, x1, z0, x1, z1, pool, size));
        return new HandshakeGraph(List.copyOf(nodes));
    }

    private static List<HandshakeNode> scanEdge(Edge edge, boolean horizontal,
                                                double ax, double az,
                                                double bx, double bz,
                                                ZoneSeed[] pool,
                                                int samples) {
        double nx = horizontal ? 0.0 : 1.0;
        double nz = horizontal ? 1.0 : 0.0;

        int[] lower = new int[samples];
        int[] upper = new int[samples];
        for (int i = 0; i < samples; i++) {
            double t = (i + 0.5) / samples;
            double px = ax + (bx - ax) * t;
            double pz = az + (bz - az) * t;
            lower[i] = VoronoiClassifier.nearestKind(px - nx * EPSILON, pz - nz * EPSILON, pool);
            upper[i] = VoronoiClassifier.nearestKind(px + nx * EPSILON, pz + nz * EPSILON, pool);
        }

        List<HandshakeNode> out = new ArrayList<>();
        int runStart = 0;
        for (int i = 1; i <= samples; i++) {
            boolean endOfRun =
                (i == samples) ||
                (lower[i] != lower[runStart]) ||
                (upper[i] != upper[runStart]);
            if (endOfRun) {
                int mid = (runStart + i - 1) / 2;
                double t = (mid + 0.5) / samples;
                double px = ax + (bx - ax) * t;
                double pz = az + (bz - az) * t;
                out.add(new HandshakeNode(edge, t, px, pz, lower[runStart], upper[runStart]));
                runStart = i;
            }
        }
        return out;
    }
}
