package fr.enderclem.massif.api;

/**
 * Per-point snapshot of zone-border geometry used by {@link BorderField}.
 *
 * <p>{@code distance} is the (non-negative) distance to the nearest Voronoi
 * cell border. It's computed as the perpendicular distance from the sample
 * point to the bisector of the two nearest seeds — i.e.
 * {@code (d_other² - d_near²) / (2 · |S_near - S_other|)}, which is exact
 * when the sample's closest border point lies on the bisector itself and a
 * very close approximation everywhere else.
 *
 * <p>{@code normalX}/{@code normalZ} is the unit vector from the "other"
 * (second-nearest) cell toward the "near" (closest) cell — i.e. points
 * <em>into</em> the cell the sample is currently inside.
 *
 * <p>{@code nearType}/{@code otherType} identify which two zone types meet
 * at the nearest border. Near three-way corners this records only the two
 * closest; higher-order intersections are ignored in Phase 3.
 */
public record BorderSample(
    double distance,
    double normalX,
    double normalZ,
    int nearType,
    int otherType
) {}
