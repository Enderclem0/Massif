package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.api.ZoneSeed;
import fr.enderclem.massif.api.ZoneSeedPool;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the {@link ZoneGraph} by enumerating all seeds covering (and one
 * region beyond) the render window and detecting Voronoi adjacencies via
 * grid sampling: every orthogonally-adjacent pair of sample pixels with
 * different nearest seeds contributes an edge.
 *
 * <p>Seed enumeration prefers the pool's {@link ZoneSeedPool#allSeeds}
 * (populated by the Lloyd-relaxed pool) so the graph's cell positions
 * match the zone/border fields'. When the pool is unbounded (jittered
 * default) the producer falls back to walking the region grid itself.
 */
public final class ZoneGraphProducer implements Producer {

    private final int cellSize;
    private final int windowSize;

    public ZoneGraphProducer() {
        this(ZoneFieldProducer.DEFAULT_CELL_SIZE, MassifKeys.VIEW_SIZE);
    }

    public ZoneGraphProducer(int cellSize, int windowSize) {
        this.cellSize = cellSize;
        this.windowSize = windowSize;
    }

    @Override
    public String name() {
        return "zones.graph";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.ZONE_GRAPH);
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(MassifKeys.ZONE_REGISTRY, MassifKeys.ZONE_SEED_POOL);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneSeedPool pool = ctx.read(MassifKeys.ZONE_SEED_POOL);
        int kindCount = ctx.read(MassifKeys.ZONE_REGISTRY).size();

        int windowHalf = windowSize / 2;
        List<ZoneSeed> seeds = gatherSeeds(pool, ctx.seed(), kindCount, windowHalf);

        // Grid-sample the visible window to determine adjacencies.
        int sampleSize = windowSize;
        int sampleX0 = -windowHalf;
        int sampleZ0 = -windowHalf;
        int[][] nearest = new int[sampleSize][sampleSize];
        int n = seeds.size();
        for (int z = 0; z < sampleSize; z++) {
            double wz = sampleZ0 + z + 0.5;
            for (int x = 0; x < sampleSize; x++) {
                double wx = sampleX0 + x + 0.5;
                int best = 0;
                double bestDistSq = Double.POSITIVE_INFINITY;
                for (int si = 0; si < n; si++) {
                    ZoneSeed s = seeds.get(si);
                    double dx = s.wx() - wx;
                    double dz = s.wz() - wz;
                    double d = dx * dx + dz * dz;
                    if (d < bestDistSq) {
                        bestDistSq = d;
                        best = si;
                    }
                }
                nearest[z][x] = best;
            }
        }

        Set<Long> edgeKeys = new HashSet<>();
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (int z = 0; z < sampleSize; z++) {
            for (int x = 0; x < sampleSize; x++) {
                int a = nearest[z][x];
                if (x + 1 < sampleSize) addEdgeIfNew(a, nearest[z][x + 1], edgeKeys, adj);
                if (z + 1 < sampleSize) addEdgeIfNew(a, nearest[z + 1][x], edgeKeys, adj);
            }
        }

        List<ZoneCell> cells = new ArrayList<>(n);
        for (int si = 0; si < n; si++) {
            ZoneSeed s = seeds.get(si);
            Set<Integer> neighbourIndices = adj.getOrDefault(si, Collections.emptySet());
            List<Integer> neighbourIds = new ArrayList<>(neighbourIndices.size());
            for (int ni : neighbourIndices) neighbourIds.add(seeds.get(ni).id());
            Collections.sort(neighbourIds);
            cells.add(new ZoneCell(s.id(), s.wx(), s.wz(), s.kind(), neighbourIds));
        }

        ctx.write(MassifKeys.ZONE_GRAPH, new ZoneGraph(cells));
    }

    private List<ZoneSeed> gatherSeeds(ZoneSeedPool pool, long worldSeed, int kindCount, int windowHalf) {
        ZoneSeed[] poolSeeds = pool.allSeeds();
        if (poolSeeds.length > 0) {
            List<ZoneSeed> out = new ArrayList<>(poolSeeds.length);
            Collections.addAll(out, poolSeeds);
            return out;
        }
        // Unbounded pool (e.g. jittered default) — walk the region grid over
        // the window plus a one-cell halo and ask ZoneSeeds directly.
        int rxMin = Math.floorDiv(-windowHalf - cellSize, cellSize);
        int rxMax = Math.floorDiv(windowHalf + cellSize, cellSize);
        int rzMin = Math.floorDiv(-windowHalf - cellSize, cellSize);
        int rzMax = Math.floorDiv(windowHalf + cellSize, cellSize);
        List<ZoneSeed> out = new ArrayList<>();
        for (int rz = rzMin; rz <= rzMax; rz++) {
            for (int rx = rxMin; rx <= rxMax; rx++) {
                Collections.addAll(out, ZoneSeeds.of(worldSeed, rx, rz, cellSize, kindCount));
            }
        }
        return out;
    }

    private static void addEdgeIfNew(int a, int b,
                                     Set<Long> edgeKeys,
                                     Map<Integer, Set<Integer>> adj) {
        if (a == b) return;
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        long key = (((long) lo) << 32) | (hi & 0xFFFFFFFFL);
        if (!edgeKeys.add(key)) return;
        adj.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        adj.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }
}
