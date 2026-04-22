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
 * <p>{@code spineCellIds} is an ordered polyline through the cluster: the
 * graph-diameter path — the path through cluster cells connecting the two
 * cells that are farthest apart in hops, recovered via the standard
 * two-BFS diameter heuristic. A straight PCA major axis worked badly for
 * L-shaped or curved clusters; a polyline spine follows the shape.
 *
 * <p>{@code representativeX/Z} is the position of the middle cell of
 * {@code spineCellIds}, so it's always inside the cluster even when the
 * geometric centroid of cell positions wouldn't be (C-shapes, rings,
 * U-shapes). Consumers that want "a representative point to anchor a
 * label or marker" should use this.
 *
 * <p>{@code technique} is an opaque string naming the generation strategy
 * the structural plan selected — a placeholder for now; Phase 6 wires it
 * to actual mountain-technique implementations.
 */
public record MountainCluster(
    int id,
    List<Integer> cellIds,
    List<Integer> spineCellIds,
    double representativeX,
    double representativeZ,
    int peakCountHint,
    String technique
) {
    public MountainCluster {
        cellIds = List.copyOf(cellIds);
        spineCellIds = List.copyOf(spineCellIds);
        if (spineCellIds.isEmpty()) {
            throw new IllegalArgumentException("spineCellIds must be non-empty");
        }
    }
}
