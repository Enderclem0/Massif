package fr.enderclem.massif.dag;

/**
 * Rectangular region within a region's local coordinate grid, used by border-aware
 * layers to describe which slice of a neighbour's output they need.
 *
 * <p>Coordinates are in the neighbour's own 0-based cell space. A {@code width}
 * or {@code height} equal to the full region size covers an entire side; a
 * {@code W × W} patch in one corner is a diagonal neighbour's corner strip.
 */
public record StripSpec(int x0, int z0, int width, int height) {

    public int x1() { return x0 + width; }
    public int z1() { return z0 + height; }

    public boolean contains(int x, int z) {
        return x >= x0 && x < x1() && z >= z0 && z < z1();
    }

    /** The full {@code size × size} region — used when border-strip mode is unneeded. */
    public static StripSpec full(int size) {
        return new StripSpec(0, 0, size, size);
    }
}
