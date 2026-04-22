package fr.enderclem.massif.tests;

import fr.enderclem.massif.api.Massif;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.MountainCluster;
import fr.enderclem.massif.api.MountainClusters;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.blackboard.Blackboard;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Covers Phase-4 structural-plan producers. Currently just mountain-cluster
 * flood-fill correctness; expands as drainage / basins / confluence points
 * land.
 */
public final class FeaturesTest {

    private FeaturesTest() {}

    public static int run() {
        try {
            mountainClustersPublished();
            clusterCellsAreMountainType();
            clusterCellsAreGraphCells();
            componentsArePartition();
            componentsAreConnected();
            orientationInValidRange();
            System.out.println("  FeaturesTest: OK");
            return 0;
        } catch (Throwable t) {
            System.err.println("  FeaturesTest FAILED: " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    private static void mountainClustersPublished() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(1234L);
        TestAssert.assertTrue(board.has(MassifKeys.MOUNTAIN_CLUSTERS),
            "core:mountain_clusters present on default framework");
    }

    private static void clusterCellsAreMountainType() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(42L);
        MountainClusters mc = board.get(MassifKeys.MOUNTAIN_CLUSTERS);
        Map<Integer, ZoneCell> byId = board.get(MassifKeys.ZONE_GRAPH).byId();
        int mountainTypeId = board.get(MassifKeys.ZONE_REGISTRY).get("mountain").id();
        for (MountainCluster c : mc.clusters()) {
            for (int cid : c.cellIds()) {
                ZoneCell cell = byId.get(cid);
                TestAssert.assertTrue(cell != null, "cell " + cid + " referenced but not in graph");
                TestAssert.assertEquals(mountainTypeId, cell.type(),
                    "cell " + cid + " in mountain cluster but type " + cell.type());
            }
        }
    }

    private static void clusterCellsAreGraphCells() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(7L);
        MountainClusters mc = board.get(MassifKeys.MOUNTAIN_CLUSTERS);
        Set<Integer> graphIds = board.get(MassifKeys.ZONE_GRAPH).byId().keySet();
        for (MountainCluster c : mc.clusters()) {
            for (int cid : c.cellIds()) {
                TestAssert.assertTrue(graphIds.contains(cid),
                    "cluster cell " + cid + " not in graph");
            }
        }
    }

    private static void componentsArePartition() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(99L);
        MountainClusters mc = board.get(MassifKeys.MOUNTAIN_CLUSTERS);
        Set<Integer> seen = new HashSet<>();
        for (MountainCluster c : mc.clusters()) {
            for (int cid : c.cellIds()) {
                TestAssert.assertTrue(seen.add(cid),
                    "cell " + cid + " appears in multiple clusters");
            }
        }
    }

    private static void componentsAreConnected() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(1001L);
        MountainClusters mc = board.get(MassifKeys.MOUNTAIN_CLUSTERS);
        ZoneGraph graph = board.get(MassifKeys.ZONE_GRAPH);
        Map<Integer, ZoneCell> byId = graph.byId();
        for (MountainCluster c : mc.clusters()) {
            Set<Integer> member = new HashSet<>(c.cellIds());
            // BFS inside the cluster over mountain-type neighbours; result must
            // match the declared membership.
            Set<Integer> reached = new HashSet<>();
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            queue.add(c.cellIds().get(0));
            reached.add(c.cellIds().get(0));
            while (!queue.isEmpty()) {
                int cid = queue.poll();
                for (int nid : byId.get(cid).neighbourIds()) {
                    if (member.contains(nid) && reached.add(nid)) queue.add(nid);
                }
            }
            TestAssert.assertEquals(member.size(), reached.size(),
                "cluster " + c.id() + " disconnected — only " + reached.size()
                    + "/" + member.size() + " reachable from root");
        }
    }

    private static void orientationInValidRange() {
        MountainClusters mc = Massif.defaultFramework().generate(5L).get(MassifKeys.MOUNTAIN_CLUSTERS);
        for (MountainCluster c : mc.clusters()) {
            double a = c.orientationAngle();
            TestAssert.assertTrue(a >= -Math.PI / 2 - 1e-6 && a <= Math.PI / 2 + 1e-6,
                "orientation " + a + " outside [-π/2, π/2]");
            TestAssert.assertTrue(c.semiMajorAxisLength() >= 0.0,
                "semiMajorAxisLength negative: " + c.semiMajorAxisLength());
        }
    }
}
