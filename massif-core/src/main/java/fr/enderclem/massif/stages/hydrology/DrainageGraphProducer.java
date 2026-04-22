package fr.enderclem.massif.stages.hydrology;

import fr.enderclem.massif.api.CellElevation;
import fr.enderclem.massif.api.DrainageGraph;
import fr.enderclem.massif.api.DrainageGraph.CellDrainage;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Priority-flood watershed (Planchon-Darboux / Barnes style) over the zone
 * graph. Produces per-cell downhill pointers and a "water level" that
 * equals raw elevation on dry land and rises to the spill-point elevation
 * inside closed depressions (forming lakes).
 *
 * <p>Terminals: ocean cells seed the main flood. Any cell not reached from
 * the ocean pass sits in an endorheic basin — the algorithm then re-seeds
 * from the lowest unvisited cell and floods again. Repeats until every
 * cell has a terminal; each unique terminal becomes a drainage outlet.
 *
 * <p>Tie-breaks are deterministic: the priority queue orders primarily by
 * ascending water level, then by cell id. Two cells at the same water
 * level get the lower-id processed first so basin assignment doesn't
 * depend on hash-map iteration order.
 */
public final class DrainageGraphProducer implements Producer {

    private static final String OCEAN_ZONE_NAME = "ocean";

    @Override
    public String name() {
        return "hydrology.drainage_graph";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.DRAINAGE_GRAPH);
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(MassifKeys.ZONE_GRAPH, MassifKeys.ZONE_REGISTRY, MassifKeys.CELL_ELEVATION);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneGraph graph = ctx.read(MassifKeys.ZONE_GRAPH);
        ZoneTypeRegistry registry = ctx.read(MassifKeys.ZONE_REGISTRY);
        CellElevation elev = ctx.read(MassifKeys.CELL_ELEVATION);

        int oceanType = resolveOceanType(registry);

        Map<Integer, ZoneCell> byId = graph.byId();
        Map<Integer, Double> waterLevel = new HashMap<>();
        Map<Integer, Integer> downhill = new HashMap<>();

        PriorityQueue<QueueEntry> pq = new PriorityQueue<>(
            Comparator.<QueueEntry>comparingDouble(q -> q.waterLevel).thenComparingInt(q -> q.cellId));

        // Seed: ocean cells. Each is its own terminal at its raw elevation.
        List<Integer> terminals = new ArrayList<>();
        if (oceanType >= 0) {
            for (ZoneCell c : graph.cells()) {
                if (c.type() != oceanType) continue;
                double wl = elev.at(c.id());
                waterLevel.put(c.id(), wl);
                downhill.put(c.id(), -1);
                terminals.add(c.id());
                pq.add(new QueueEntry(c.id(), wl));
            }
        }

        // Main priority flood: propagate outward. Each neighbour inherits
        // max(neighbour's own raw elevation, current cell's water level),
        // which is the spill-point rule — water has to climb up to the
        // outgoing cell's lowest pour point before continuing.
        floodFrom(pq, byId, elev, waterLevel, downhill);

        // Endorheic: any cell still missing from waterLevel sits in a
        // closed basin. Seed from its lowest cell and flood again.
        while (true) {
            int lowestUnvisited = -1;
            double lowestElev = Double.POSITIVE_INFINITY;
            for (ZoneCell c : graph.cells()) {
                if (waterLevel.containsKey(c.id())) continue;
                double e = elev.at(c.id());
                if (e < lowestElev || (e == lowestElev && c.id() < lowestUnvisited)) {
                    lowestElev = e;
                    lowestUnvisited = c.id();
                }
            }
            if (lowestUnvisited < 0) break;
            waterLevel.put(lowestUnvisited, lowestElev);
            downhill.put(lowestUnvisited, -1);
            terminals.add(lowestUnvisited);
            pq.add(new QueueEntry(lowestUnvisited, lowestElev));
            floodFrom(pq, byId, elev, waterLevel, downhill);
        }

        // Build the immutable entries.
        Map<Integer, CellDrainage> entries = new HashMap<>();
        for (ZoneCell c : graph.cells()) {
            int id = c.id();
            double raw = elev.at(id);
            double wl = waterLevel.get(id);
            int dh = downhill.get(id);
            boolean isLake = wl > raw;
            boolean isOcean = oceanType >= 0 && c.type() == oceanType;
            boolean endorheicTerminal = dh < 0 && !isOcean;
            entries.put(id, new CellDrainage(id, raw, wl, dh, isLake, isOcean, endorheicTerminal));
        }

        ctx.write(MassifKeys.DRAINAGE_GRAPH, new DrainageGraph(entries, terminals));
    }

    private static void floodFrom(PriorityQueue<QueueEntry> pq,
                                  Map<Integer, ZoneCell> byId,
                                  CellElevation elev,
                                  Map<Integer, Double> waterLevel,
                                  Map<Integer, Integer> downhill) {
        while (!pq.isEmpty()) {
            QueueEntry cur = pq.poll();
            // Skip stale entries — the current water level for a cell may
            // have been lowered by a later entry that reached it first.
            double stored = waterLevel.get(cur.cellId);
            if (stored != cur.waterLevel) continue;

            ZoneCell cell = byId.get(cur.cellId);
            if (cell == null) continue;
            for (int nid : cell.neighbourIds()) {
                if (!byId.containsKey(nid)) continue;
                if (!elev.has(nid)) continue;
                double candidate = Math.max(elev.at(nid), cur.waterLevel);
                Double existing = waterLevel.get(nid);
                if (existing == null || candidate < existing) {
                    waterLevel.put(nid, candidate);
                    downhill.put(nid, cur.cellId);
                    pq.add(new QueueEntry(nid, candidate));
                }
            }
        }
    }

    private static int resolveOceanType(ZoneTypeRegistry registry) {
        for (int i = 0; i < registry.size(); i++) {
            if (OCEAN_ZONE_NAME.equals(registry.get(i).name())) return i;
        }
        return -1;
    }

    private record QueueEntry(int cellId, double waterLevel) {}
}
