package fr.enderclem.massif.dag;

import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.primitives.RegionCoord;
import fr.enderclem.massif.primitives.RegionRng;
import java.util.Set;

/**
 * Per-execution façade for a single {@link Layer}. Enforces the layer's declared
 * reads/writes so that the DAG schedule remains honest — a layer that reads a
 * key it did not declare will throw, surfacing a scheduling bug immediately
 * rather than causing non-deterministic ordering.
 */
public final class LayerContext {

    private final Blackboard board;
    private final Set<FeatureKey<?>> declaredReads;
    private final Set<FeatureKey<?>> declaredWrites;
    private final long seed;
    private final RegionCoord coord;
    private final String layerName;
    private final NeighbourCache neighbourCache;

    public LayerContext(Blackboard board,
                        Set<FeatureKey<?>> declaredReads,
                        Set<FeatureKey<?>> declaredWrites,
                        long seed,
                        RegionCoord coord,
                        String layerName,
                        NeighbourCache neighbourCache) {
        this.board = board;
        this.declaredReads = declaredReads;
        this.declaredWrites = declaredWrites;
        this.seed = seed;
        this.coord = coord;
        this.layerName = layerName;
        this.neighbourCache = neighbourCache;
    }

    public long seed() {
        return seed;
    }

    public RegionCoord coord() {
        return coord;
    }

    /** Salted RNG unique to this (seed, coord, layerName, salt) tuple. */
    public RegionRng rng(long salt) {
        long layerSalt = layerName.hashCode() * 0x9E3779B97F4A7C15L ^ salt;
        return new RegionRng(seed, coord.rx(), coord.rz(), layerSalt);
    }

    /**
     * Salted RNG that depends only on the world seed + layer + salt — NOT on
     * the region coordinate. Use this when a layer must sample from a
     * world-global field (e.g. a noise lattice) so adjacent regions read
     * from the same infinite source and tile seamlessly.
     */
    public RegionRng worldRng(long salt) {
        long layerSalt = layerName.hashCode() * 0x9E3779B97F4A7C15L ^ salt;
        return new RegionRng(seed, 0, 0, layerSalt);
    }

    public <T> T read(FeatureKey<T> key) {
        if (!declaredReads.contains(key)) {
            throw new IllegalStateException(
                "Layer '" + layerName + "' read undeclared feature: " + key.name());
        }
        return board.get(key);
    }

    public <T> void write(FeatureKey<T> key, T value) {
        if (!declaredWrites.contains(key)) {
            throw new IllegalStateException(
                "Layer '" + layerName + "' wrote undeclared feature: " + key.name());
        }
        board.put(key, value);
    }

    /**
     * Fetch a border-strip mask for the given neighbour region and layer. On a
     * cache miss, the neighbour's {@link BorderAwareLayer#computeBorderStrip}
     * is invoked under the depth-1 cross-region guard.
     */
    public byte[][] neighbourStrip(RegionCoord neighbour, BorderAwareLayer layer, StripSpec strip) {
        return neighbourCache.getOrCompute(seed, neighbour, layer, strip);
    }
}
