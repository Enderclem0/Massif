package fr.enderclem.massif.api;

import java.util.List;
import java.util.Map;

/**
 * Directed drainage graph over the zone-cell grid.
 *
 * <p>Each cell carries its raw surface elevation, its "water level" (the
 * raw elevation, raised to the spill-point in closed depressions — so a
 * cell with {@code waterLevel > rawElevation} is a lake), and the id of
 * its downhill neighbour. Terminals (cells with {@code downhillCellId
 * < 0}) are drainage outlets: ocean cells, and — for endorheic basins —
 * the lowest cell of each closed depression. The {@link #terminals} list
 * collects them for quick iteration.
 *
 * <p>Produced by a priority-flood watershed pass. Every non-terminal
 * cell's downhill pointer forms a path reaching a terminal in finite
 * steps (the graph is a forest).
 */
public record DrainageGraph(
    Map<Integer, CellDrainage> byCellId,
    List<Integer> terminals
) {

    public DrainageGraph {
        byCellId = Map.copyOf(byCellId);
        terminals = List.copyOf(terminals);
    }

    public CellDrainage of(int cellId) {
        CellDrainage d = byCellId.get(cellId);
        if (d == null) {
            throw new IllegalArgumentException("no drainage entry for cell " + cellId);
        }
        return d;
    }

    /**
     * @param cellId                 which cell this entry describes
     * @param rawElevation           raw surface elevation from {@link CellElevation}
     * @param waterLevel             elevation of the water surface;
     *                               {@code == rawElevation} on dry land,
     *                               strictly greater in a lake
     * @param downhillCellId         id of the next cell on the flow path,
     *                               or {@code -1} for a terminal
     * @param isLake                 {@code waterLevel > rawElevation}
     * @param isOcean                true for ocean-type cells
     * @param isEndorheicTerminal    true for non-ocean terminals (closed-basin bottoms)
     */
    public record CellDrainage(
        int cellId,
        double rawElevation,
        double waterLevel,
        int downhillCellId,
        boolean isLake,
        boolean isOcean,
        boolean isEndorheicTerminal
    ) {}
}
