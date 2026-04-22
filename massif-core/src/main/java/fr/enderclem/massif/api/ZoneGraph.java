package fr.enderclem.massif.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * List of Voronoi {@link ZoneCell}s covering (and one ring beyond) a
 * world-scoped rendering window. Published under
 * {@link MassifKeys#ZONE_GRAPH}.
 *
 * <p>The world itself has no finite Voronoi graph — seeds extend to infinity.
 * This graph is the enumerable snapshot covering the viewable area plus a
 * halo so border adjacencies at the window's edge are complete. Consumers
 * wanting cells outside the graph should sample {@link ZoneField} instead.
 */
public record ZoneGraph(List<ZoneCell> cells) {

    public ZoneGraph {
        cells = List.copyOf(cells);
    }

    public Optional<ZoneCell> findById(int id) {
        for (ZoneCell c : cells) if (c.id() == id) return Optional.of(c);
        return Optional.empty();
    }

    /** Index of {@code cell id → cell} for O(1) lookup. Computed lazily per caller. */
    public Map<Integer, ZoneCell> byId() {
        Map<Integer, ZoneCell> m = new HashMap<>(cells.size() * 2);
        for (ZoneCell c : cells) m.put(c.id(), c);
        return m;
    }
}
