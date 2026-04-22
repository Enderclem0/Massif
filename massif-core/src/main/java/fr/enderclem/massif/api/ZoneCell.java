package fr.enderclem.massif.api;

import java.util.List;

/**
 * One cell of a {@link ZoneGraph}: a Voronoi seed plus its type and the
 * cells it's Voronoi-adjacent to.
 *
 * <p>{@code id} is globally unique across the world, derived from the seed
 * generator. {@code seedX}/{@code seedZ} are world coordinates. {@code
 * type} is a {@link ZoneTypeRegistry} id. {@code neighbourIds} lists the
 * cell ids this cell shares a border with — in the same graph.
 */
public record ZoneCell(
    int id,
    double seedX,
    double seedZ,
    int type,
    List<Integer> neighbourIds
) {
    public ZoneCell {
        neighbourIds = List.copyOf(neighbourIds);
    }
}
