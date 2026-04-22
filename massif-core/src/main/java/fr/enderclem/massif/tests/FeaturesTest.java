package fr.enderclem.massif.tests;

import fr.enderclem.massif.api.Massif;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.MountainCluster;
import fr.enderclem.massif.api.MountainClusters;
import fr.enderclem.massif.api.SpineEdge;
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
            spineEdgesAreGraphAdjacencies();
            spineIsSpanningTree();
            representativeCellIsInCluster();
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

    private static void spineEdgesAreGraphAdjacencies() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(5L);
        MountainClusters mc = board.get(MassifKeys.MOUNTAIN_CLUSTERS);
        Map<Integer, ZoneCell> byId = board.get(MassifKeys.ZONE_GRAPH).byId();
        for (MountainCluster c : mc.clusters()) {
            Set<Integer> members = new HashSet<>(c.cellIds());
            for (SpineEdge e : c.spineEdges()) {
                TestAssert.assertTrue(members.contains(e.fromCellId()) && members.contains(e.toCellId()),
                    "spine edge references non-member cell in cluster " + c.id());
                TestAssert.assertTrue(
                    byId.get(e.fromCellId()).neighbourIds().contains(e.toCellId()),
                    "spine edge " + e.fromCellId() + "→" + e.toCellId()
                        + " is not a graph adjacency in cluster " + c.id());
            }
        }
    }

    private static void spineIsSpanningTree() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(5L);
        MountainClusters mc = board.get(MassifKeys.MOUNTAIN_CLUSTERS);
        for (MountainCluster c : mc.clusters()) {
            // n cells → tree has n-1 edges.
            int expectedEdges = c.cellIds().size() - 1;
            TestAssert.assertEquals(expectedEdges, c.spineEdges().size(),
                "spine-edge count must equal cellCount - 1 for cluster " + c.id());

            // Every cell must be reachable via spine edges from any start.
            Map<Integer, Set<Integer>> adj = new java.util.HashMap<>();
            for (int cid : c.cellIds()) adj.put(cid, new HashSet<>());
            for (SpineEdge e : c.spineEdges()) {
                adj.get(e.fromCellId()).add(e.toCellId());
                adj.get(e.toCellId()).add(e.fromCellId());
            }
            Set<Integer> reached = new HashSet<>();
            java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
            int start = c.cellIds().get(0);
            reached.add(start);
            q.add(start);
            while (!q.isEmpty()) {
                int cur = q.poll();
                for (int nid : adj.get(cur)) if (reached.add(nid)) q.add(nid);
            }
            TestAssert.assertEquals(c.cellIds().size(), reached.size(),
                "spine tree disconnected in cluster " + c.id());
        }
    }

    private static void representativeCellIsInCluster() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(5L);
        MountainClusters mc = board.get(MassifKeys.MOUNTAIN_CLUSTERS);
        Map<Integer, ZoneCell> byId = board.get(MassifKeys.ZONE_GRAPH).byId();
        for (MountainCluster c : mc.clusters()) {
            TestAssert.assertTrue(c.cellIds().contains(c.representativeCellId()),
                "representative cell " + c.representativeCellId()
                    + " not in cluster " + c.id());
            ZoneCell cell = byId.get(c.representativeCellId());
            TestAssert.assertTrue(
                cell.seedX() == c.representativeX() && cell.seedZ() == c.representativeZ(),
                "representativeX/Z must match the representative cell's seed position");
        }
    }
}
