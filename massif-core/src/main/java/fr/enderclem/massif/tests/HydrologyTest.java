package fr.enderclem.massif.tests;

import fr.enderclem.massif.api.CellElevation;
import fr.enderclem.massif.api.DrainageBasin;
import fr.enderclem.massif.api.DrainageBasins;
import fr.enderclem.massif.api.DrainageGraph;
import fr.enderclem.massif.api.DrainageGraph.CellDrainage;
import fr.enderclem.massif.api.Massif;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.blackboard.Blackboard;
import java.util.HashSet;
import java.util.Set;

/**
 * Covers the Phase-5 coarse-hydrology producers. Verifies elevation
 * assignment, the priority-flood invariants (every cell has a downhill
 * path, water flows downhill, water level ≥ raw elevation), and that
 * basins partition the cell set with each basin containing its outlet.
 */
public final class HydrologyTest {

    private HydrologyTest() {}

    public static int run() {
        try {
            elevationPublishedForEveryCell();
            drainagePublishedForEveryCell();
            downhillChainReachesTerminal();
            waterFlowsDownhill();
            waterLevelAtLeastRaw();
            basinsPartitionCells();
            basinsContainOutlets();
            basinCountMatchesTerminals();
            System.out.println("  HydrologyTest: OK");
            return 0;
        } catch (Throwable t) {
            System.err.println("  HydrologyTest FAILED: " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    private static void elevationPublishedForEveryCell() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(42L);
        CellElevation elev = board.get(MassifKeys.CELL_ELEVATION);
        ZoneGraph graph = board.get(MassifKeys.ZONE_GRAPH);
        for (ZoneCell c : graph.cells()) {
            TestAssert.assertTrue(elev.has(c.id()),
                "elevation missing for cell " + c.id());
        }
    }

    private static void drainagePublishedForEveryCell() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(42L);
        DrainageGraph drainage = board.get(MassifKeys.DRAINAGE_GRAPH);
        ZoneGraph graph = board.get(MassifKeys.ZONE_GRAPH);
        for (ZoneCell c : graph.cells()) {
            TestAssert.assertTrue(drainage.byCellId().containsKey(c.id()),
                "drainage missing for cell " + c.id());
        }
    }

    private static void downhillChainReachesTerminal() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(7L);
        DrainageGraph drainage = board.get(MassifKeys.DRAINAGE_GRAPH);
        Set<Integer> terminals = new HashSet<>(drainage.terminals());
        for (CellDrainage entry : drainage.byCellId().values()) {
            int cur = entry.cellId();
            Set<Integer> visited = new HashSet<>();
            while (true) {
                if (!visited.add(cur)) {
                    throw new AssertionError("cycle in drainage graph at " + cur);
                }
                CellDrainage here = drainage.of(cur);
                if (here.downhillCellId() < 0) {
                    TestAssert.assertTrue(terminals.contains(here.cellId()),
                        "terminal " + here.cellId() + " not in drainage.terminals()");
                    break;
                }
                cur = here.downhillCellId();
            }
        }
    }

    private static void waterFlowsDownhill() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(99L);
        DrainageGraph drainage = board.get(MassifKeys.DRAINAGE_GRAPH);
        for (CellDrainage entry : drainage.byCellId().values()) {
            if (entry.downhillCellId() < 0) continue;
            CellDrainage dh = drainage.of(entry.downhillCellId());
            TestAssert.assertTrue(entry.waterLevel() >= dh.waterLevel() - 1e-9,
                "cell " + entry.cellId() + " at " + entry.waterLevel()
                    + " flows to " + dh.cellId() + " at higher " + dh.waterLevel());
        }
    }

    private static void waterLevelAtLeastRaw() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(1L);
        DrainageGraph drainage = board.get(MassifKeys.DRAINAGE_GRAPH);
        for (CellDrainage entry : drainage.byCellId().values()) {
            TestAssert.assertTrue(entry.waterLevel() >= entry.rawElevation() - 1e-9,
                "cell " + entry.cellId() + " water " + entry.waterLevel()
                    + " below raw " + entry.rawElevation());
        }
    }

    private static void basinsPartitionCells() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(1234L);
        DrainageBasins basins = board.get(MassifKeys.DRAINAGE_BASINS);
        DrainageGraph drainage = board.get(MassifKeys.DRAINAGE_GRAPH);
        Set<Integer> seen = new HashSet<>();
        for (DrainageBasin b : basins.basins()) {
            for (int cid : b.memberCellIds()) {
                TestAssert.assertTrue(seen.add(cid),
                    "cell " + cid + " appears in multiple basins");
            }
        }
        TestAssert.assertEquals(drainage.byCellId().size(), seen.size(),
            "basin membership doesn't cover every cell");
    }

    private static void basinsContainOutlets() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(321L);
        DrainageBasins basins = board.get(MassifKeys.DRAINAGE_BASINS);
        for (DrainageBasin b : basins.basins()) {
            TestAssert.assertTrue(b.memberCellIds().contains(b.outletCellId()),
                "basin for outlet " + b.outletCellId() + " does not contain the outlet");
        }
    }

    private static void basinCountMatchesTerminals() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(77L);
        DrainageGraph drainage = board.get(MassifKeys.DRAINAGE_GRAPH);
        DrainageBasins basins = board.get(MassifKeys.DRAINAGE_BASINS);
        TestAssert.assertEquals(drainage.terminals().size(), basins.basins().size(),
            "one basin per terminal");
    }
}
