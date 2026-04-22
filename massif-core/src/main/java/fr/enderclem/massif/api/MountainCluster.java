package fr.enderclem.massif.api;

import java.util.List;

/**
 * Connected component of mountain-type Voronoi cells in the zone graph.
 *
 * <p>{@code id} is stable across regenerations of the same {@code (seed,
 * window)} — the lowest {@link ZoneCell#id()} in the component. It may
 * shift when the window changes (a new lowest cell enters); fully-stable
 * ids arrive when Phase 4b computes clusters over a globally-enumerable
 * zone graph.
 *
 * <p>{@code spineEdges} form a spanning tree over the cluster's cells:
 * every cell is touched and there are exactly {@code cellIds.size() - 1}
 * edges (0 for a single-cell cluster). Using a tree instead of a single
 * polyline path means Y-shaped or branching clusters are fully
 * represented — all three arms of a Y radiate from the junction as
 * separate edges rather than two arms being collapsed into a single
 * diameter and the third disappearing.
 *
 * <p>{@code representativeCellId} is the tree centre — the cell with
 * minimum eccentricity within the cluster — and
 * {@code representativeX/Z} its world position. For Y clusters this is
 * the junction; for linear clusters it's the middle cell; for curved
 * clusters it's the bend. Always inside the cluster, which fixes the
 * "centroid outside the cluster" quirk that point-cloud centroids had on
 * C / U / ring shapes.
 *
 * <p>{@code technique} is an opaque string naming the generation strategy
 * the structural plan selected — a placeholder for now; Phase 6 wires it
 * to actual mountain-technique implementations.
 */
public record MountainCluster(
    int id,
    List<Integer> cellIds,
    List<SpineEdge> spineEdges,
    int representativeCellId,
    double representativeX,
    double representativeZ,
    int peakCountHint,
    String technique
) {
    public MountainCluster {
        cellIds = List.copyOf(cellIds);
        spineEdges = List.copyOf(spineEdges);
        if (cellIds.isEmpty()) {
            throw new IllegalArgumentException("cellIds must be non-empty");
        }
    }
}
