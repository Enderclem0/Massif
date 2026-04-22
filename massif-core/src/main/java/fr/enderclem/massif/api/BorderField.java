package fr.enderclem.massif.api;

/**
 * Queryable Voronoi-border primitives at any world coordinate. A single
 * {@link BorderSample} call returns distance, normal, and the pair of zone
 * types meeting at the nearest border — packaged together because a
 * straightforward implementation computes all of them from the same two
 * nearest seeds.
 *
 * <p>Published on the blackboard under {@link MassifKeys#BORDER_FIELD}.
 */
public interface BorderField {

    /**
     * Sample border geometry at world coordinate {@code (x, z)}. Thread-safe:
     * implementations compute from pure functions of world seed and the
     * coordinate alone.
     */
    BorderSample sampleAt(double x, double z);
}
