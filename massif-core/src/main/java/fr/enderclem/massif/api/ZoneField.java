package fr.enderclem.massif.api;

/**
 * Queryable zone classification over the world. Given any world coordinate,
 * returns the integer id of the zone type that covers it. IDs are defined
 * by the {@link ZoneTypeRegistry} also published on the blackboard.
 *
 * <p>The interface is intentionally minimal: one point query plus a bulk
 * grid sampler for renderers. Implementations are free to be lazy (compute
 * on demand as the backing Voronoi seed pool does) or precompute a grid.
 */
public interface ZoneField {

    /** Zone type id at world coordinate {@code (x, z)}. */
    int typeAt(double x, double z);

    /**
     * Sample a {@code width × height} grid of zone ids starting at world
     * coordinate {@code (x0, z0)}, one cell per block. Default uses {@code
     * typeAt} per point; implementations are encouraged to override with a
     * cached / vectorised version for hot rendering paths.
     */
    default int[][] sampleGrid(int x0, int z0, int width, int height) {
        int[][] out = new int[height][width];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                out[z][x] = typeAt(x0 + x + 0.5, z0 + z + 0.5);
            }
        }
        return out;
    }
}
