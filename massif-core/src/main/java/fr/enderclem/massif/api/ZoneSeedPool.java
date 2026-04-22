package fr.enderclem.massif.api;

/**
 * The source of Voronoi seeds consulted by {@link ZoneField},
 * {@link BorderField}, and the zone-graph producer. Published on the
 * blackboard under {@link MassifKeys#ZONE_SEED_POOL}; having a single point
 * of variation lets one place decide between "jittered seeds computed on
 * demand" and "Lloyd-relaxed seeds precomputed over a bounded area",
 * without each downstream producer duplicating the Lloyd logic.
 *
 * <p>The split of responsibilities:
 * <ul>
 *   <li>{@link #seedsNear} — returns enough seeds that linear-searching the
 *       array is guaranteed to find the true nearest seed (and second
 *       nearest, etc.) to the query point.</li>
 *   <li>{@link #allSeeds} — every seed in a bounded pool, for producers
 *       that need to enumerate cells (graph-building). Returns an empty
 *       array when the pool is unbounded, in which case consumers must
 *       enumerate seeds themselves from their own coverage rectangle.</li>
 * </ul>
 */
public interface ZoneSeedPool {

    ZoneSeed[] seedsNear(double x, double z);

    default ZoneSeed[] allSeeds() {
        return new ZoneSeed[0];
    }
}
