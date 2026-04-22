package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.api.ZoneTypeRegistry;
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
 * Enumerates the Voronoi cells whose seeds fall inside a fixed world
 * window (plus a one-region halo so cells on the window edge still list
 * their across-the-boundary neighbours). Adjacencies are detected by
 * grid-sampling: any two adjacent sample pixels whose nearest seeds differ
 * contribute an edge between those two cells. This catches every
 * adjacency longer than one pixel, which is enough resolution for the
 * walking-skeleton viewport.
 *
 * <p>A more rigorous adjacency algorithm (true Delaunay triangulation,
 * or Gabriel-edge testing with extension) can replace this without
 * changing the {@link ZoneGraph} schema.
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
        return Set.of(MassifKeys.ZONE_REGISTRY);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneTypeRegistry registry = ctx.read(MassifKeys.ZONE_REGISTRY);
        int kindCount = registry.size();

        // Gather all seeds in window + one-region halo on each side.
        int windowHalf = windowSize / 2;
        int rxMin = Math.floorDiv(-windowHalf - cellSize, cellSize);
        int rxMax = Math.floorDiv(windowHalf + cellSize, cellSize);
        int rzMin = Math.floorDiv(-windowHalf - cellSize, cellSize);
        int rzMax = Math.floorDiv(windowHalf + cellSize, cellSize);

        List<ZoneSeed> seeds = new ArrayList<>();
        for (int rz = rzMin; rz <= rzMax; rz++) {
            for (int rx = rxMin; rx <= rxMax; rx++) {
                ZoneSeed[] r = ZoneSeeds.of(ctx.seed(), rx, rz, cellSize, kindCount);
                Collections.addAll(seeds, r);
            }
        }

        // Grid-sample the visible window to determine adjacencies. For each
        // pixel, find the nearest seed (by index into the `seeds` list).
        int sampleSize = windowSize;
        int sampleX0 = -windowHalf;
        int sampleZ0 = -windowHalf;
        int[][] nearest = new int[sampleSize][sampleSize];
        for (int z = 0; z < sampleSize; z++) {
            double wz = sampleZ0 + z + 0.5;
            for (int x = 0; x < sampleSize; x++) {
                double wx = sampleX0 + x + 0.5;
                int best = 0;
                double bestDistSq = Double.POSITIVE_INFINITY;
                for (int si = 0; si < seeds.size(); si++) {
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

        // Walk the grid; every orthogonally-adjacent pixel pair with
        // different nearest-seed indices contributes an edge.
        Set<Long> edgeKeys = new HashSet<>();
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (int z = 0; z < sampleSize; z++) {
            for (int x = 0; x < sampleSize; x++) {
                int a = nearest[z][x];
                if (x + 1 < sampleSize) addEdgeIfNew(a, nearest[z][x + 1], edgeKeys, adj);
                if (z + 1 < sampleSize) addEdgeIfNew(a, nearest[z + 1][x], edgeKeys, adj);
            }
        }

        // Build the final cell list.
        List<ZoneCell> cells = new ArrayList<>(seeds.size());
        for (int si = 0; si < seeds.size(); si++) {
            ZoneSeed s = seeds.get(si);
            Set<Integer> neighbourIndices = adj.getOrDefault(si, Collections.emptySet());
            List<Integer> neighbourIds = new ArrayList<>(neighbourIndices.size());
            for (int ni : neighbourIndices) {
                neighbourIds.add(seeds.get(ni).id());
            }
            Collections.sort(neighbourIds);
            cells.add(new ZoneCell(s.id(), s.wx(), s.wz(), s.kind(), neighbourIds));
        }

        ctx.write(MassifKeys.ZONE_GRAPH, new ZoneGraph(cells));
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
