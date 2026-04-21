package fr.enderclem.massif.dag;

import fr.enderclem.massif.primitives.RegionCoord;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pipeline-owned cache of border-aware layers' per-region outputs. Populated
 * lazily on neighbour lookup; guarded by {@link CrossRegionDepth} so a
 * neighbour-side compute cannot re-enter another cross-region read.
 *
 * <p>Key is {@code (seed, coord, stageName)} so different stages don't collide
 * and the same region across different world seeds isn't accidentally shared.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap#computeIfAbsent}. Concurrent
 * {@code get} calls for distinct keys run without blocking; concurrent calls
 * for the same key de-duplicate. A neighbour-side compute triggered inside
 * {@code computeIfAbsent} targets a different key (a different region), so no
 * recursive-lock deadlock occurs in practice.
 */
public final class NeighbourCache {

    /**
     * Cache key. Includes {@link StripSpec} so that two different requesters
     * asking for different slices of the same neighbour's output don't collide
     * and receive each other's data — without this, parallel schedules
     * produced subtly divergent masks depending on which strip requester
     * happened to populate the cache first.
     */
    public record Key(long seed, int rx, int rz, String stageName, StripSpec strip) {
        public static Key of(long seed, RegionCoord coord, String stageName, StripSpec strip) {
            return new Key(seed, coord.rx(), coord.rz(), stageName, strip);
        }
    }

    private final ConcurrentHashMap<Key, byte[][]> store = new ConcurrentHashMap<>();

    /**
     * Fetch the cached border-strip mask for {@code (seed, coord, layer.name(), strip)},
     * computing it via {@link BorderAwareLayer#computeBorderStrip} on a miss.
     *
     * <p>The depth guard ensures the computation cannot recursively trigger
     * further cross-region reads. If a miss causes a compute at depth &gt; 1,
     * {@link CrossRegionRecursionException} propagates.
     */
    public byte[][] getOrCompute(long seed,
                                 RegionCoord coord,
                                 BorderAwareLayer layer,
                                 StripSpec strip) {
        Key key = Key.of(seed, coord, layer.name(), strip);
        return store.computeIfAbsent(key, k -> {
            CrossRegionDepth.enter();
            try {
                return layer.computeBorderStrip(k.seed(), RegionCoord.of(k.rx(), k.rz()), strip, this);
            } finally {
                CrossRegionDepth.exit();
            }
        });
    }

    /** Visible for tests: drop cached entries. */
    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
