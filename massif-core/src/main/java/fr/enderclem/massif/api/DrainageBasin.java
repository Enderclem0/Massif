package fr.enderclem.massif.api;

import java.util.List;

/**
 * A single drainage basin: all cells whose downhill path terminates at
 * the same outlet cell. Ocean-outlet basins are the conventional case;
 * {@code endorheic} basins drain to a closed-basin bottom inside the
 * graph rather than to the sea.
 *
 * <p>{@code outletCellId} matches the terminal's {@link
 * DrainageGraph.CellDrainage#cellId()}. {@code memberCellIds} includes
 * the outlet itself and is in breadth-first order from the outlet — so
 * consuming "upstream" just means walking the list backwards.
 */
public record DrainageBasin(
    int outletCellId,
    boolean endorheic,
    List<Integer> memberCellIds
) {
    public DrainageBasin {
        memberCellIds = List.copyOf(memberCellIds);
    }
}
