package fr.enderclem.massif.api;

import java.util.List;

/**
 * Connected component of mountain-type Voronoi cells in the zone graph.
 * Carries the structural metadata downstream mountain-generation
 * techniques need to pick an algorithm (how many peaks, along what axis,
 * which strategy) without re-inspecting the zone graph.
 *
 * <p>{@code id} is the lowest {@link ZoneCell#id()} in the component —
 * stable across regenerations of the same (seed, window) config but
 * subject to shift when the window changes (a new "lowest" cell enters).
 * Fully-stable ids will arrive when Phase 4b computes clusters from a
 * globally-enumerable zone graph.
 *
 * <p>{@code orientationAngle} is the major-axis direction in radians,
 * derived from 2D PCA on cell centroids, in {@code [-π/2, π/2)} since the
 * axis is direction-symmetric. {@code semiMajorAxisLength} is the farthest
 * cell-centroid projection along that axis (0 for single-cell clusters).
 *
 * <p>{@code technique} is an opaque string naming the generation strategy
 * the structural plan selected — a placeholder for now; Phase 6 wires it
 * to actual mountain-technique implementations.
 */
public record MountainCluster(
    int id,
    List<Integer> cellIds,
    double centroidX,
    double centroidZ,
    double orientationAngle,
    double semiMajorAxisLength,
    int peakCountHint,
    String technique
) {
    public MountainCluster {
        cellIds = List.copyOf(cellIds);
    }
}
