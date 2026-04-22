package fr.enderclem.massif.api;

import java.util.Map;
import java.util.Set;

/**
 * Per-cell "raw" elevation — the unflooded surface height assigned to each
 * {@link ZoneCell} based on its zone type plus per-cell jitter. Published
 * before drainage: the hydrology pass in {@link MassifKeys#DRAINAGE_GRAPH}
 * consumes this to run priority-flood, producing the lake-adjusted water
 * level separately (so consumers that want raw topology keep the original
 * here).
 *
 * <p>Values are unitless in the walking skeleton — rough mapping: ocean
 * below 0, plains around 0.1, mountains around 0.7. Real-world block
 * heights come from the composition stage (Phase 7+) that folds zone
 * passes and feature contributions into {@code core:height}.
 */
public record CellElevation(Map<Integer, Double> byCellId) {

    public CellElevation {
        byCellId = Map.copyOf(byCellId);
    }

    public double at(int cellId) {
        Double v = byCellId.get(cellId);
        if (v == null) {
            throw new IllegalArgumentException("no elevation for cell " + cellId);
        }
        return v;
    }

    public boolean has(int cellId) {
        return byCellId.containsKey(cellId);
    }

    public Set<Integer> cellIds() {
        return byCellId.keySet();
    }
}
