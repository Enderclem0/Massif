package fr.enderclem.massif.tests;

import fr.enderclem.massif.api.BorderField;
import fr.enderclem.massif.api.BorderSample;
import fr.enderclem.massif.api.Massif;
import fr.enderclem.massif.api.MassifFramework;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneField;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.api.ZoneType;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.Blackboard;
import java.util.List;

/**
 * Covers the Phase 3 zone public API: registry validation, zone-field
 * output range and determinism, and the end-to-end pipeline populating
 * {@link MassifKeys#ZONE_REGISTRY} / {@link MassifKeys#ZONE_FIELD}.
 */
public final class ZonesTest {

    private ZonesTest() {}

    public static int run() {
        try {
            registryRejectsMisordered();
            registryRejectsDuplicateName();
            defaultRegistryPopulated();
            fieldReturnsValidIds();
            fieldIsDeterministic();
            pipelinePublishesZoneKeys();
            borderDistancesAreNonNegative();
            borderNormalIsUnit();
            borderPairingMatchesField();
            zoneGraphCellsNonEmpty();
            zoneGraphAdjacencyIsSymmetric();
            System.out.println("  ZonesTest: OK");
            return 0;
        } catch (Throwable t) {
            System.err.println("  ZonesTest FAILED: " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    private static void registryRejectsMisordered() {
        TestAssert.assertThrows(IllegalArgumentException.class, () -> new ZoneTypeRegistry(List.of(
            new ZoneType(0, "a", 0),
            new ZoneType(2, "b", 0) // id should be 1
        )), "registry must reject id != slot");
    }

    private static void registryRejectsDuplicateName() {
        TestAssert.assertThrows(IllegalArgumentException.class, () -> new ZoneTypeRegistry(List.of(
            new ZoneType(0, "dup", 0),
            new ZoneType(1, "dup", 0)
        )), "registry must reject duplicate names");
    }

    private static void defaultRegistryPopulated() {
        ZoneTypeRegistry r = ZoneTypeRegistry.defaultRegistry();
        TestAssert.assertTrue(r.size() >= 2, "default registry has multiple zone types");
        for (int i = 0; i < r.size(); i++) {
            TestAssert.assertEquals(i, r.get(i).id(), "default registry slot " + i);
        }
    }

    private static void fieldReturnsValidIds() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(1234L);
        ZoneField f = board.get(MassifKeys.ZONE_FIELD);
        ZoneTypeRegistry r = board.get(MassifKeys.ZONE_REGISTRY);
        for (int i = 0; i < 50; i++) {
            int id = f.typeAt(i * 7.31, i * -3.17);
            TestAssert.assertTrue(id >= 0 && id < r.size(),
                "zone id " + id + " outside [0, " + r.size() + ")");
        }
    }

    private static void fieldIsDeterministic() {
        MassifFramework fw = Massif.defaultFramework();
        ZoneField f1 = fw.generate(42L).get(MassifKeys.ZONE_FIELD);
        ZoneField f2 = fw.generate(42L).get(MassifKeys.ZONE_FIELD);
        for (int i = 0; i < 100; i++) {
            double x = i * 11.5;
            double z = i * -4.3;
            TestAssert.assertEquals(f1.typeAt(x, z), f2.typeAt(x, z),
                "same seed -> same zone at same coord");
        }
    }

    private static void pipelinePublishesZoneKeys() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(1L);
        TestAssert.assertTrue(board.has(MassifKeys.ZONE_REGISTRY), "registry key present");
        TestAssert.assertTrue(board.has(MassifKeys.ZONE_FIELD), "field key present");
        TestAssert.assertTrue(board.has(MassifKeys.ZONE_GRAPH), "graph key present");
        TestAssert.assertTrue(board.has(MassifKeys.BORDER_FIELD), "border key present");
        TestAssert.assertTrue(board.has(MassifKeys.HEIGHTMAP), "heightmap still present (legacy)");
    }

    private static void borderDistancesAreNonNegative() {
        BorderField b = Massif.defaultFramework().generate(1234L).get(MassifKeys.BORDER_FIELD);
        for (int i = 0; i < 200; i++) {
            double x = (i - 100) * 5.7;
            double z = (i - 100) * -3.3;
            BorderSample s = b.sampleAt(x, z);
            TestAssert.assertTrue(s.distance() >= -1e-6,
                "distance must be non-negative, got " + s.distance() + " at (" + x + "," + z + ")");
        }
    }

    private static void borderNormalIsUnit() {
        BorderField b = Massif.defaultFramework().generate(42L).get(MassifKeys.BORDER_FIELD);
        for (int i = 0; i < 50; i++) {
            double x = i * 13.1;
            double z = i * -7.9;
            BorderSample s = b.sampleAt(x, z);
            double len = Math.sqrt(s.normalX() * s.normalX() + s.normalZ() * s.normalZ());
            TestAssert.assertEquals(1.0, len, 1e-9,
                "normal must have unit length at (" + x + "," + z + ")");
        }
    }

    private static void borderPairingMatchesField() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(7L);
        BorderField b = board.get(MassifKeys.BORDER_FIELD);
        ZoneField z = board.get(MassifKeys.ZONE_FIELD);
        for (int i = 0; i < 50; i++) {
            double x = i * 9.7;
            double zz = i * 2.3;
            BorderSample s = b.sampleAt(x, zz);
            int fromField = z.typeAt(x, zz);
            TestAssert.assertEquals(fromField, s.nearType(),
                "BorderField.nearType must agree with ZoneField.typeAt at (" + x + "," + zz + ")");
        }
    }

    private static void zoneGraphCellsNonEmpty() {
        Blackboard.Sealed board = Massif.defaultFramework().generate(321L);
        ZoneGraph g = board.get(MassifKeys.ZONE_GRAPH);
        TestAssert.assertTrue(g.cells().size() > 0, "graph must have at least one cell");
        ZoneTypeRegistry r = board.get(MassifKeys.ZONE_REGISTRY);
        for (ZoneCell c : g.cells()) {
            TestAssert.assertTrue(c.type() >= 0 && c.type() < r.size(),
                "cell type " + c.type() + " out of registry range");
        }
    }

    private static void zoneGraphAdjacencyIsSymmetric() {
        ZoneGraph g = Massif.defaultFramework().generate(77L).get(MassifKeys.ZONE_GRAPH);
        var byId = g.byId();
        for (ZoneCell c : g.cells()) {
            for (int nid : c.neighbourIds()) {
                ZoneCell n = byId.get(nid);
                TestAssert.assertTrue(n != null, "neighbour " + nid + " of cell " + c.id() + " missing");
                TestAssert.assertTrue(n.neighbourIds().contains(c.id()),
                    "adjacency not symmetric: cell " + c.id() + " → " + nid);
            }
        }
    }
}
