package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.WorldWindow;
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
 * Enumerates the Voronoi cells whose seeds fall inside the configured
 * {@link WorldWindow} (plus a one-region halo) and detects Voronoi
 * adjacencies by grid-sampling: any two adjacent sample pixels whose
 * nearest seeds differ contribute an edge between those cells. Catches
 * every adjacency longer than one pixel — sufficient for the walking-
 * skeleton viewport; a rigorous Delaunay pass can replace the detector
 * without changing the graph schema.
 *
 * <p>Enumeration prefers the pool's {@link ZoneSeedPool#allSeeds} so
 * graph cell positions match the field producers'. When the pool is
 * unbounded (jittered default) the producer walks the region grid around
 * the window on its own.
 */
public final class ZoneGraphProducer implements Producer {

    private final int cellSize;
    private final WorldWindow window;

    public ZoneGraphProducer() {
        this(ZoneFieldProducer.DEFAULT_CELL_SIZE, WorldWindow.defaultWindow());
    }

    public ZoneGraphProducer(int cellSize, WorldWindow window) {
        this.cellSize = cellSize;
        this.window = window;
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

        List<ZoneSeed> seeds = gatherSeeds(pool, ctx.seed(), kindCount);

        int sampleSize = window.size();
        int sampleX0 = window.x0();
        int sampleZ0 = window.z0();
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

    private List<ZoneSeed> gatherSeeds(ZoneSeedPool pool, long worldSeed, int kindCount) {
        ZoneSeed[] poolSeeds = pool.allSeeds();
        if (poolSeeds.length > 0) {
            List<ZoneSeed> out = new ArrayList<>(poolSeeds.length);
            Collections.addAll(out, poolSeeds);
            return out;
        }
        // Unbounded pool (jittered default): walk the region grid ourselves
        // over the window plus one-cell halo.
        int rxMin = Math.floorDiv(window.x0() - cellSize, cellSize);
        int rxMax = Math.floorDiv(window.x1() + cellSize, cellSize);
        int rzMin = Math.floorDiv(window.z0() - cellSize, cellSize);
        int rzMax = Math.floorDiv(window.z1() + cellSize, cellSize);
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
