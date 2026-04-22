package fr.enderclem.massif.stages.features;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.MountainCluster;
import fr.enderclem.massif.api.MountainClusters;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flood-fills the zone graph over the "mountain" zone type to produce a
 * {@link MountainCluster} per connected component. Each cluster carries a
 * polyline spine — the graph-diameter path between the two farthest-apart
 * cells — recovered via the standard two-BFS diameter heuristic so L-shape
 * or curved clusters get a bent spine instead of a straight PCA axis. The
 * representative point is the middle cell of the spine, guaranteed inside
 * the cluster even for C / U / ring shapes.
 *
 * <p>The zone-type name looked up is {@value #MOUNTAIN_ZONE_NAME}; worlds
 * using a custom registry without it produce an empty
 * {@link MountainClusters}.
 */
public final class MountainClusterProducer implements Producer {

    private static final String MOUNTAIN_ZONE_NAME = "mountain";
    private static final int CELLS_PER_PEAK = 2;

    private final String defaultTechnique;

    public MountainClusterProducer() {
        this("ridge_graph");
    }

    public MountainClusterProducer(String defaultTechnique) {
        this.defaultTechnique = defaultTechnique;
    }

    @Override
    public String name() {
        return "features.mountain_clusters";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.MOUNTAIN_CLUSTERS);
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(MassifKeys.ZONE_GRAPH, MassifKeys.ZONE_REGISTRY);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneGraph graph = ctx.read(MassifKeys.ZONE_GRAPH);
        ZoneTypeRegistry registry = ctx.read(MassifKeys.ZONE_REGISTRY);

        int mountainType = resolveMountainType(registry);
        if (mountainType < 0) {
            ctx.write(MassifKeys.MOUNTAIN_CLUSTERS, new MountainClusters(List.of()));
            return;
        }

        Map<Integer, ZoneCell> byId = graph.byId();
        Set<Integer> visited = new HashSet<>();
        List<MountainCluster> clusters = new ArrayList<>();

        for (ZoneCell cell : graph.cells()) {
            if (cell.type() != mountainType || visited.contains(cell.id())) continue;
            List<Integer> componentIds = floodFillComponent(byId, cell.id(), mountainType, visited);
            clusters.add(buildCluster(byId, componentIds));
        }

        ctx.write(MassifKeys.MOUNTAIN_CLUSTERS, new MountainClusters(clusters));
    }

    private static int resolveMountainType(ZoneTypeRegistry registry) {
        for (int i = 0; i < registry.size(); i++) {
            if (MOUNTAIN_ZONE_NAME.equals(registry.get(i).name())) return i;
        }
        return -1;
    }

    private static List<Integer> floodFillComponent(Map<Integer, ZoneCell> byId,
                                                    int rootId, int type,
                                                    Set<Integer> visited) {
        List<Integer> componentIds = new ArrayList<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(rootId);
        visited.add(rootId);
        while (!queue.isEmpty()) {
            int cid = queue.pollFirst();
            componentIds.add(cid);
            ZoneCell c = byId.get(cid);
            if (c == null) continue;
            for (int nid : c.neighbourIds()) {
                if (visited.contains(nid)) continue;
                ZoneCell n = byId.get(nid);
                if (n != null && n.type() == type) {
                    visited.add(nid);
                    queue.addLast(nid);
                }
            }
        }
        return componentIds;
    }

    private MountainCluster buildCluster(Map<Integer, ZoneCell> byId, List<Integer> componentIds) {
        Set<Integer> members = new HashSet<>(componentIds);
        int minId = Integer.MAX_VALUE;
        for (int cid : componentIds) if (cid < minId) minId = cid;

        // Two-BFS diameter: pick any cell, BFS to the farthest, BFS from
        // that one to get the true diameter endpoints. The path between
        // them (via parent pointers from the second BFS) is the spine.
        int anchor = componentIds.get(0);
        int end1 = bfsFarthest(byId, members, anchor).farthest();
        BfsResult fromEnd1 = bfsFarthest(byId, members, end1);
        int end2 = fromEnd1.farthest();
        List<Integer> spine = reconstructPath(fromEnd1.parents(), end1, end2);

        int midIdx = spine.size() / 2;
        ZoneCell midCell = byId.get(spine.get(midIdx));
        int peakHint = Math.max(1, componentIds.size() / CELLS_PER_PEAK);

        return new MountainCluster(
            minId, componentIds, spine,
            midCell.seedX(), midCell.seedZ(),
            peakHint, defaultTechnique);
    }

    private record BfsResult(int farthest, Map<Integer, Integer> parents) {}

    private static BfsResult bfsFarthest(Map<Integer, ZoneCell> byId,
                                         Set<Integer> members,
                                         int source) {
        Map<Integer, Integer> parent = new HashMap<>();
        parent.put(source, -1);
        Map<Integer, Integer> dist = new HashMap<>();
        dist.put(source, 0);
        Deque<Integer> q = new ArrayDeque<>();
        q.add(source);
        int farthest = source;
        int maxDist = 0;
        while (!q.isEmpty()) {
            int cur = q.pollFirst();
            int d = dist.get(cur);
            if (d > maxDist) { maxDist = d; farthest = cur; }
            ZoneCell c = byId.get(cur);
            if (c == null) continue;
            for (int nid : c.neighbourIds()) {
                if (!members.contains(nid) || parent.containsKey(nid)) continue;
                parent.put(nid, cur);
                dist.put(nid, d + 1);
                q.addLast(nid);
            }
        }
        return new BfsResult(farthest, parent);
    }

    private static List<Integer> reconstructPath(Map<Integer, Integer> parent, int from, int to) {
        List<Integer> path = new ArrayList<>();
        int cur = to;
        while (cur != -1) {
            path.add(cur);
            Integer p = parent.get(cur);
            if (p == null) break; // defensive — shouldn't happen for well-formed graph
            cur = p;
        }
        // Path is currently to → from; the spine ordering is irrelevant to
        // correctness but reversing it so it runs from → to is conventional.
        Collections.reverse(path);
        return path;
    }
}
