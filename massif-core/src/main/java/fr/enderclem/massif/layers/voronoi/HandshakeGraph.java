package fr.enderclem.massif.layers.voronoi;

import java.util.List;

/**
 * Per-edge crossing points produced by {@link HandshakeLayer}.
 *
 * Two adjacent regions compute an identical set of nodes on their shared edge
 * because both classify against the same 3x3 neighbourhood seed pool. Downstream
 * stages (rivers, ridges) use these nodes as the only sanctioned points where a
 * cross-region structure is allowed to pass.
 *
 * Invariant-3 note: node properties are locally computable (position, zone pair)
 * or additively/monotonically propagable. Adding new properties requires a proof.
 */
public record HandshakeGraph(List<HandshakeNode> nodes) {

    public enum Edge {
        /** Low-z border; shared with the region at (rx, rz - 1). */ NORTH,
        /** High-z border; shared with the region at (rx, rz + 1). */ SOUTH,
        /** Low-x border; shared with the region at (rx - 1, rz). */ WEST,
        /** High-x border; shared with the region at (rx + 1, rz). */ EAST
    }

    /**
     * A crossing candidate on one edge.
     *
     * @param edge      which of the four shared edges
     * @param t         position along the edge in [0, 1]
     * @param wx        world x of the node
     * @param wz        world z of the node
     * @param kindLower zone kind on the side of the edge with the lower world coordinate
     *                  (the edge's negative normal side: -x for EAST/WEST, -z for NORTH/SOUTH)
     * @param kindUpper zone kind on the opposite side
     */
    public record HandshakeNode(Edge edge, double t,
                                double wx, double wz,
                                int kindLower, int kindUpper) {}
}
