package fr.enderclem.massif.dag;

import fr.enderclem.massif.primitives.RegionCoord;

/**
 * A {@link Layer} whose output is derived via a kernel whose footprint extends
 * beyond a single region's boundary. Declares the kernel radius so the pipeline
 * can arrange bounded-depth-1 cross-region reads per scope.md invariant 2.
 *
 * <p>Border-aware layers must provide a {@link #computeBorderStrip} method that
 * produces the layer's aggregate output restricted to a specified strip,
 * bit-identical to the corresponding slice of a full-region compute. This is
 * what a neighbouring region's distance transform will read.
 *
 * <p>Callees of {@code computeBorderStrip} are forbidden from triggering
 * further cross-region reads. The {@link CrossRegionDepth} guard enforces this
 * at runtime.
 */
public interface BorderAwareLayer extends Layer {

    /**
     * Border-strip half-width in cells. Neighbouring regions' aggregate data
     * must be provided within this distance of any shared edge in order for
     * this layer's derived field to be seamless.
     *
     * <p>For a ridge layer using {@code h = amp · exp(-d/σ)} falloff, a radius
     * of ~3σ is typical — beyond that the exponential is negligible.
     */
    int borderStripRadius();

    /**
     * Compute the aggregate output for an arbitrary region in border-strip mode.
     * Callers (the pipeline's neighbour-cache machinery) use this to populate
     * cross-region data.
     *
     * <p>Well-behaved layers do <em>not</em> use {@code cache} from within this
     * method — any cross-region read here would be at depth &gt; 1 and
     * {@link CrossRegionDepth} will throw. The parameter exists so that tests
     * can construct contrived layers that verify the depth guard fires.
     *
     * @param seed   world seed
     * @param coord  the region to compute for (typically a neighbour of the
     *               region currently being generated)
     * @param strip  portion of the region's grid the caller needs; cells
     *               outside the strip may be returned as zero
     * @param cache  the same cache that triggered this call — provided for
     *               symmetry and testing, but should not be used by properly
     *               implemented layers
     * @return a {@code size × size} byte mask where cells inside {@code strip}
     *         are bit-identical to the corresponding cells of a full-region
     *         compute, and cells outside are zero
     */
    byte[][] computeBorderStrip(long seed, RegionCoord coord, StripSpec strip, NeighbourCache cache);
}
