package fr.enderclem.massif.api;

import fr.enderclem.massif.blackboard.Blackboard.SealedBlackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.Set;

/**
 * Immutable output of running the pipeline for one region.
 * Produced by {@link TerrainFramework#generate}, safe to publish across threads.
 */
public final class RegionPlan {

    private final long seed;
    private final RegionCoord coord;
    private final SealedBlackboard board;

    RegionPlan(long seed, RegionCoord coord, SealedBlackboard board) {
        this.seed = seed;
        this.coord = coord;
        this.board = board;
    }

    public long seed() {
        return seed;
    }

    public RegionCoord coord() {
        return coord;
    }

    public <T> T get(FeatureKey<T> key) {
        return board.get(key);
    }

    public boolean has(FeatureKey<?> key) {
        return board.has(key);
    }

    public Set<FeatureKey<?>> keys() {
        return board.keys();
    }
}
