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
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flood-fills the zone graph over the "mountain" zone type to produce a
 * {@link MountainCluster} per connected component. Derives centroid,
 * major-axis orientation via 2D PCA, the semi-major extent, and a
 * peak-count hint from each component's cell list.
 *
 * <p>The zone-type name looked up is {@value #MOUNTAIN_ZONE_NAME}; worlds
 * using a custom registry that doesn't declare a mountain type produce an
 * empty {@link MountainClusters} (this isn't an error — it just means the
 * world has no mountain Voronoi cells).
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
        int n = componentIds.size();
        double cx = 0.0;
        double cz = 0.0;
        int minId = Integer.MAX_VALUE;
        for (int cid : componentIds) {
            ZoneCell c = byId.get(cid);
            cx += c.seedX();
            cz += c.seedZ();
            if (cid < minId) minId = cid;
        }
        cx /= n;
        cz /= n;

        // 2D covariance on centred cell positions. Not divided by n — atan2
        // below is scale-invariant, so the division is wasted work.
        double mxx = 0.0;
        double mzz = 0.0;
        double mxz = 0.0;
        for (int cid : componentIds) {
            ZoneCell c = byId.get(cid);
            double dx = c.seedX() - cx;
            double dz = c.seedZ() - cz;
            mxx += dx * dx;
            mzz += dz * dz;
            mxz += dx * dz;
        }

        double angle = (mxx == 0.0 && mzz == 0.0 && mxz == 0.0)
            ? 0.0
            : 0.5 * Math.atan2(2.0 * mxz, mxx - mzz);

        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);
        double semiMajor = 0.0;
        for (int cid : componentIds) {
            ZoneCell c = byId.get(cid);
            double dx = c.seedX() - cx;
            double dz = c.seedZ() - cz;
            double proj = Math.abs(dx * cosA + dz * sinA);
            if (proj > semiMajor) semiMajor = proj;
        }

        int peakHint = Math.max(1, n / CELLS_PER_PEAK);
        return new MountainCluster(
            minId, componentIds, cx, cz, angle, semiMajor, peakHint, defaultTechnique);
    }
}
