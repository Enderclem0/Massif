package fr.enderclem.massif.layers.dla;

import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.dag.BorderAwareLayer;
import fr.enderclem.massif.dag.LayerContext;
import fr.enderclem.massif.dag.NeighbourCache;
import fr.enderclem.massif.dag.StripSpec;
import fr.enderclem.massif.layers.Features;
import fr.enderclem.massif.layers.voronoi.HandshakeGraph;
import fr.enderclem.massif.layers.voronoi.HandshakeGraph.Edge;
import fr.enderclem.massif.layers.voronoi.HandshakeGraph.HandshakeNode;
import fr.enderclem.massif.layers.voronoi.HandshakeLayerImpl;
import fr.enderclem.massif.layers.voronoi.VoronoiClassifier;
import fr.enderclem.massif.layers.voronoi.ZoneKind;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Scope.md Stage 3. Grows a fractal ridge skeleton inside a region's mountain
 * cells via Diffusion-Limited Aggregation, then turns it into a height
 * contribution. Cross-region continuity is guaranteed by the
 * {@link BorderAwareLayer} contract: this layer publishes a border-strip
 * aggregate that neighbouring regions pull from via the pipeline's neighbour
 * cache, and its own distance transform includes those same strips as seeds.
 *
 * <h2>Pyramid DLA</h2>
 * Classical single-resolution DLA at 256×256 is ruinously slow — walkers
 * spawned on the image boundary take tens of thousands of random steps to
 * find a sparse central aggregate. Instead we run DLA at progressively finer
 * resolutions ({@link #PYRAMID_SIZES} = {@code 32→64→128→256}). Coarse levels
 * establish the branching topology cheaply (a 32×32 grid has 1024 cells, so
 * walkers find the aggregate in dozens of steps, not tens of thousands).
 * Between levels we upscale by drawing "which pixel stuck to which" parent
 * edges as jiggled two-segment lines, preserving connectivity while adding
 * geometric irregularity. Fine levels then grow sub-branches on top of the
 * upscaled skeleton.
 *
 * <h2>Walker algorithm</h2>
 * Each level runs a fixed walker budget. Walkers spawn on a square ring a
 * few cells <em>outside</em> the image and random-walk into the grid. Spawning
 * outside is what keeps walkers from pinning against the grid edge: a walker
 * that spawns at {@code x=0} has its first stick-check evaluated there and
 * tends to glue to the boundary; spawned at {@code x=-spawnMargin} it has to
 * traverse into the region before it can stick. Motion is unclamped, so
 * walkers may drift out the far side and come back, which keeps in-region
 * aggregate density uniform. Termination: stick (an orthogonal neighbour is
 * frozen), step-count, or drift past the kill radius from the grid centre.
 * On stick, the walker records its parent (the frozen neighbour that caught
 * it); these parent pointers drive the line-connection upscale between levels.
 *
 * <h2>Mountain-aware seeding</h2>
 * Mountain cells in the region are flood-filled into connected components;
 * every component gets its own seed placed at the cell nearest that
 * component's centroid. This is what makes regions with several disconnected
 * mountain areas get DLA growing in every area rather than only the one
 * closest to the region centre. Seeds are scaled down to the first pyramid
 * level; when multiple clusters quantise onto the same 8-grid cell they
 * collapse into one starting cell, which is fine — successive upscales
 * re-separate them via the line-connection draw. Regions with no mountain
 * cells short-circuit to an empty mask.
 *
 * <h2>Border-strip mode (cross-region continuity)</h2>
 * {@link #computeBorderStrip} currently recomputes the full-region aggregate
 * and filters to the strip — trivially bit-identical to the full-mode result
 * within the strip (aggregate identity test). Walker culling for strip mode
 * is a future optimisation; it needs a safe bound on which walkers can
 * possibly affect the strip, which is non-obvious with boundary spawning.
 */
public final class RidgeDlaLayer implements BorderAwareLayer {

    /**
     * Pyramid resolutions from coarsest to finest. The last element must equal
     * {@link Features#REGION_SIZE}. Each step doubles in size; the upscaler
     * handles arbitrary integer ratios but 2× is what the line-drawing is
     * tuned for.
     */
    private static final int[] PYRAMID_SIZES = { 8, 16, 32, 64, 128, 256 };
    /** Walkers per level = {@code size² / WALKER_DENSITY_DIVISOR}. */
    private static final int WALKER_DENSITY_DIVISOR = 4;
    private static final int MAX_STEPS_PER_WALKER = 600;
    /**
     * Outer ring of the final 256×256 mask that is forced to zero
     * (exemptions at handshake seeds). Prevents the "solid aggregate line
     * along the grid edge" artifact caused by seeds quantising onto the
     * boundary at low pyramid levels and propagating there through every
     * upscale, and by walkers entering at the grid edge and sticking on the
     * first interior cell. This output-time clip is the single mechanism for
     * border cleanup — a min-walk-steps filter inside {@link #runDla} was
     * tried but rejected: it also killed legitimate walkers sticking near
     * cluster seeds that happened to quantise near the grid edge, leaving
     * whole mountain clusters with no DLA growth.
     */
    private static final int BORDER_CLEAR = 4;

    // Height bump tuning.
    private static final double RIDGE_AMPLITUDE = 0.55;
    private static final double RIDGE_FALLOFF_CELLS = 8.0;
    private static final int RIDGE_BLUR_PASSES = 2;

    // Chamfer-3-4 weights: orthogonal = 3, diagonal = 4 ≈ 3·euclidean.
    private static final int CHAMFER_ORTH = 3;
    private static final int CHAMFER_DIAG = 4;

    /** Border-strip half-width in cells. 3·σ so exp(-d/σ) contributions beyond are negligible. */
    private static final int BORDER_STRIP_RADIUS = (int) Math.ceil(3.0 * RIDGE_FALLOFF_CELLS);

    private static final int[] DX = { 1, -1, 0, 0 };
    private static final int[] DZ = { 0, 0, 1, -1 };

    @Override
    public String name() {
        return "terrain.ridge_dla";
    }

    @Override
    public int borderStripRadius() {
        return BORDER_STRIP_RADIUS;
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(Features.ZONES, Features.HANDSHAKE);
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(Features.RIDGE_MASK, Features.RIDGE_HEIGHT);
    }

    @Override
    public void compute(LayerContext ctx) {
        int size = Features.REGION_SIZE;
        // Validate inputs (and establish DAG dependencies).
        ctx.read(Features.ZONES);
        ctx.read(Features.HANDSHAKE);

        RegionCoord coord = ctx.coord();
        long seed = ctx.seed();

        // Own full-region aggregate.
        byte[][] ownMask = computeMask(seed, coord, StripSpec.full(size));

        // Pull neighbour border strips via the cache. Each cache entry is the
        // corresponding neighbour's byte[size][size] mask with only strip
        // cells populated, produced by this same layer in border-strip mode.
        byte[][][] neighbourMasks = fetchNeighbours(ctx, coord, size);

        int w = BORDER_STRIP_RADIUS;
        int[][] distExt = extendedDistanceTransformFull(ownMask, neighbourMasks, size, w);

        // Compute ridge-height on the extended grid so the subsequent blur can
        // see across region boundaries — otherwise the blur clamps at the edge
        // and reintroduces a seam that cancels the DT's seamlessness.
        int[][] zonesExt = extendedZones(seed, coord, size, w);
        float[][] ridgeExt = heightBumpExt(zonesExt, distExt, size, w);
        for (int i = 0; i < RIDGE_BLUR_PASSES; i++) {
            ridgeExt = boxBlur3Ext(ridgeExt);
        }

        // Extract central window.
        float[][] ridge = new float[size][size];
        for (int z = 0; z < size; z++) {
            System.arraycopy(ridgeExt[z + w], w, ridge[z], 0, size);
        }

        ctx.write(Features.RIDGE_MASK, ownMask);
        ctx.write(Features.RIDGE_HEIGHT, ridge);
    }

    /**
     * Border-strip entry point called by the neighbour cache. Recomputes the
     * prerequisite zone grid and handshake graph from pure functions — these
     * are both deterministic world-coord-only computations — so no blackboard
     * access is needed. Cross-region recursion is forbidden here (enforced by
     * the depth guard wrapping the cache lookup).
     */
    @Override
    public byte[][] computeBorderStrip(long seed, RegionCoord coord, StripSpec strip, NeighbourCache cache) {
        // cache intentionally unused — see BorderAwareLayer javadoc.
        return computeMask(seed, coord, strip);
    }

    // -------------------------------------------------------------------------
    // Pyramid DLA core.
    // -------------------------------------------------------------------------

    /**
     * Produce the aggregate mask for {@code coord}.
     *
     * <p>Runs the full-region DLA regardless of {@code strip} and masks the
     * output: cells outside the strip are zeroed. This keeps the full-mode
     * and strip-mode outputs bit-identical inside the strip (aggregate
     * identity guarantee).
     *
     * <p>Mountain-aware: the initial seed is placed at the mountain cell
     * closest to the region centre, and regions with no mountain cells
     * return an empty mask without running DLA (ridge height is zone-gated
     * anyway, so anything grown outside mountains is invisible).
     *
     * <p>Return format: {@code byte[256][256]} indexed {@code [z][x]}, values
     * in {0, 1}.
     */
    private static byte[][] computeMask(long seed, RegionCoord coord, StripSpec strip) {
        int regionSize = Features.REGION_SIZE;
        long chunkSeed = seed ^ (coord.rx() * 341873128712L) ^ ((long) coord.rz() * 132897987541L);

        int[][] zones = VoronoiClassifier.classify(seed, coord, regionSize);
        List<int[]> seeds256 = findClusterSeeds(zones, regionSize);

        // Cross-region continuity (scope.md §2, Layer A). The handshake graph
        // is a pure function of (seed, coord) that both adjacent regions
        // compute identically for their shared edge — so seeding DLA at
        // handshake nodes whose own-side is mountain makes both regions
        // grow ridges toward the exact same boundary points. When the
        // aggregates meet at the edge, ridges cross seamlessly.
        HandshakeGraph handshakes = HandshakeLayerImpl.compute(seed, coord, regionSize);
        List<int[]> handshakeSeeds = mountainHandshakeSeeds(handshakes, regionSize);
        seeds256.addAll(handshakeSeeds);

        if (seeds256.isEmpty()) return new byte[regionSize][regionSize];

        Random random = new Random(chunkSeed);
        int firstSize = PYRAMID_SIZES[0];
        boolean[][] frozen = new boolean[firstSize][firstSize];
        int[][] parent = new int[firstSize][firstSize];
        for (int[] row : parent) Arrays.fill(row, -1);
        for (int[] s : seeds256) {
            int sx = (s[0] * firstSize) / regionSize;
            int sz = (s[1] * firstSize) / regionSize;
            frozen[sz][sx] = true; // duplicate seeds collapse harmlessly
        }

        for (int level = 0; level < PYRAMID_SIZES.length; level++) {
            int size = PYRAMID_SIZES[level];

            if (level > 0) {
                int prevSize = PYRAMID_SIZES[level - 1];
                boolean[][] newFrozen = new boolean[size][size];
                int[][] newParent = new int[size][size];
                for (int[] row : newParent) Arrays.fill(row, -1);
                upscale(frozen, parent, prevSize, newFrozen, newParent, size, random);
                frozen = newFrozen;
                parent = newParent;
            }

            // Anchor = grid centre, since multiple clusters may be anywhere.
            // Kill radius of 2×size covers the full grid plus margin, so all
            // clusters are reachable from any walker spawn.
            int anchorX = size / 2, anchorZ = size / 2;
            int walkers = (size * size) / WALKER_DENSITY_DIVISOR;
            // Walkers spawn outside the grid and need to random-walk in to
            // find the aggregate. Expected steps to traverse D cells is O(D²).
            // Scaling linearly with size gives a budget that lets walkers
            // cover a gap of ~sqrt(size·k) cells — comfortably more than the
            // spawn-to-aggregate gap at every level in practice.
            int maxSteps = Math.max(MAX_STEPS_PER_WALKER, size * 8);
            runDla(frozen, parent, size, walkers, maxSteps, anchorX, anchorZ, random);
        }

        // frozen is now at the final pyramid size == regionSize. The outer
        // BORDER_CLEAR ring is forced to zero EXCEPT within a small radius
        // around handshake mountain seed positions. The clear kills the
        // boundary-hug artifact (seeds quantising to x=0, walkers entering
        // and sticking on the first interior cell). The per-handshake
        // preserve patches keep the exact cells where scope §2 Layer A
        // sanctions a cross-region ridge crossing — without them we'd also
        // erase the very aggregate both sides agreed on.
        boolean[][] preserve = preserveMaskAroundHandshakes(handshakeSeeds, regionSize);
        boolean fullMode = strip.x0() == 0 && strip.z0() == 0
                        && strip.width() == regionSize && strip.height() == regionSize;
        byte[][] mask = new byte[regionSize][regionSize];
        for (int z = 0; z < regionSize; z++) {
            for (int x = 0; x < regionSize; x++) {
                if (!frozen[z][x]) continue;
                boolean inBorderRing = x < BORDER_CLEAR || x >= regionSize - BORDER_CLEAR
                                    || z < BORDER_CLEAR || z >= regionSize - BORDER_CLEAR;
                if (inBorderRing && !preserve[z][x]) continue;
                if (fullMode || strip.contains(x, z)) {
                    mask[z][x] = 1;
                }
            }
        }
        return mask;
    }

    /**
     * Radius (in cells) around a handshake seed that stays unclipped by the
     * border-clear pass. Picked to exceed {@link #BORDER_CLEAR} so the
     * preserved patch actually extends through the ring.
     */
    private static final int HANDSHAKE_PRESERVE_RADIUS = 6;

    /**
     * Handshake nodes whose own-region side is a mountain zone become extra
     * DLA seeds placed on the region edge. Since both neighbouring regions
     * compute identical handshake nodes from shared inputs (scope §2, Layer
     * A), the two sides seed at the same world coordinate and grow ridges
     * that meet at the boundary.
     */
    private static List<int[]> mountainHandshakeSeeds(HandshakeGraph graph, int size) {
        int mountainKind = ZoneKind.MOUNTAINS.ordinal();
        List<int[]> out = new ArrayList<>();
        for (HandshakeNode n : graph.nodes()) {
            // "Own" side depends on which edge this node is on. In
            // HandshakeLayerImpl, upper is +normal and lower is -normal; for
            // the NORTH (z=0) and WEST (x=0) edges the own region lives on
            // the +normal side, for SOUTH/EAST on the -normal side.
            int ownKind = switch (n.edge()) {
                case NORTH, WEST -> n.kindUpper();
                case SOUTH, EAST -> n.kindLower();
            };
            if (ownKind != mountainKind) continue;

            int along = Math.min(size - 1, Math.max(0, (int) (n.t() * size)));
            int x, z;
            switch (n.edge()) {
                case NORTH -> { x = along;      z = 0;          }
                case SOUTH -> { x = along;      z = size - 1;   }
                case WEST  -> { x = 0;          z = along;      }
                case EAST  -> { x = size - 1;   z = along;      }
                default    -> { continue; }
            }
            out.add(new int[] { x, z });
        }
        return out;
    }

    /**
     * Build a boolean mask marking cells within {@link #HANDSHAKE_PRESERVE_RADIUS}
     * (Chebyshev) of any handshake seed. Used to exempt those cells from
     * the outer-ring border clear — otherwise we'd zero the very aggregate
     * both sides of the boundary agreed on.
     */
    private static boolean[][] preserveMaskAroundHandshakes(List<int[]> handshakeSeeds, int size) {
        boolean[][] preserve = new boolean[size][size];
        int r = HANDSHAKE_PRESERVE_RADIUS;
        for (int[] s : handshakeSeeds) {
            int hx = s[0], hz = s[1];
            int z0 = Math.max(0, hz - r), z1 = Math.min(size - 1, hz + r);
            int x0 = Math.max(0, hx - r), x1 = Math.min(size - 1, hx + r);
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    preserve[z][x] = true;
                }
            }
        }
        return preserve;
    }

    /**
     * Flood-fill the mountain cells into connected components; return one
     * seed per component. Each seed is the mountain cell closest to that
     * component's centroid (so a U- or C-shaped cluster still seeds inside
     * itself rather than outside). Returns an empty list if the region has
     * no mountain cells.
     */
    private static List<int[]> findClusterSeeds(int[][] zones, int size) {
        int mountainKind = ZoneKind.MOUNTAINS.ordinal();
        boolean[][] visited = new boolean[size][size];
        int[] queueX = new int[size * size];
        int[] queueZ = new int[size * size];
        List<int[]> seeds = new ArrayList<>();

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                if (zones[z][x] != mountainKind || visited[z][x]) continue;
                int qTail = 0, head = 0;
                queueX[qTail] = x;
                queueZ[qTail] = z;
                qTail++;
                visited[z][x] = true;
                long cxSum = 0, czSum = 0;
                while (head < qTail) {
                    int cxc = queueX[head], czc = queueZ[head];
                    head++;
                    cxSum += cxc;
                    czSum += czc;
                    for (int d = 0; d < 4; d++) {
                        int nx = cxc + DX[d], nz = czc + DZ[d];
                        if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                        if (zones[nz][nx] != mountainKind || visited[nz][nx]) continue;
                        visited[nz][nx] = true;
                        queueX[qTail] = nx;
                        queueZ[qTail] = nz;
                        qTail++;
                    }
                }
                int cxAvg = (int) (cxSum / qTail);
                int czAvg = (int) (czSum / qTail);
                int bestX = queueX[0], bestZ = queueZ[0];
                int bestD = Integer.MAX_VALUE;
                for (int i = 0; i < qTail; i++) {
                    int dx = queueX[i] - cxAvg, dz = queueZ[i] - czAvg;
                    int d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        bestX = queueX[i];
                        bestZ = queueZ[i];
                    }
                }
                seeds.add(new int[] { bestX, bestZ });
            }
        }
        return seeds;
    }

    /**
     * Walker pass. Walkers spawn on a square ring OUTSIDE the grid (offset by
     * {@code spawnMargin} beyond the image boundary) and random-walk in.
     * Walking is unclamped — walkers may cross the image edge freely in both
     * directions, so they can leave and drift back in. Any interior stick is
     * accepted; boundary cluttering is handled at output time by
     * {@link #BORDER_CLEAR} rather than by filtering walkers mid-pass (a
     * per-walker filter would also discard legitimate sticks near seeds that
     * quantised close to the grid edge, leaving entire clusters un-grown).
     * Termination: stick, max-steps, or drift past the kill radius from the
     * anchor.
     */
    private static void runDla(boolean[][] frozen, int[][] parent, int size,
                               int walkerCount, int maxSteps,
                               int anchorX, int anchorZ, Random rng) {
        long killR = (long) size * 2L;
        long killRSq = killR * killR;
        int spawnMargin = Math.max(2, size / 16);
        int spawnSpan = size + 2 * spawnMargin;

        for (int i = 0; i < walkerCount; i++) {
            int side = rng.nextInt(4);
            int pos = rng.nextInt(spawnSpan) - spawnMargin;
            int px, pz;
            switch (side) {
                case 0:  px = -spawnMargin;           pz = pos;                    break;
                case 1:  px = size + spawnMargin - 1; pz = pos;                    break;
                case 2:  px = pos;                    pz = -spawnMargin;           break;
                default: px = pos;                    pz = size + spawnMargin - 1; break;
            }

            for (int step = 0; step < maxSteps; step++) {
                if (px >= 0 && px < size && pz >= 0 && pz < size) {
                    if (frozen[pz][px]) break; // walked onto aggregate — nothing to add
                    int parentIdx = firstFrozenNeighborIndex(px, pz, frozen, size);
                    if (parentIdx >= 0) {
                        frozen[pz][px] = true;
                        parent[pz][px] = parentIdx;
                        break;
                    }
                }
                long ddx = px - anchorX, ddz = pz - anchorZ;
                if (ddx * ddx + ddz * ddz > killRSq) break;
                int dir = rng.nextInt(4);
                px += DX[dir];
                pz += DZ[dir];
            }
        }
    }

    /**
     * Returns the flat index ({@code z*size + x}) of the first 4-neighbour of
     * (x, z) that is frozen, or -1 if none are frozen. Scan order is
     * deterministic (east, west, south, north).
     */
    private static int firstFrozenNeighborIndex(int x, int z, boolean[][] frozen, int size) {
        if (x > 0        && frozen[z][x - 1]) return z * size + (x - 1);
        if (x < size - 1 && frozen[z][x + 1]) return z * size + (x + 1);
        if (z > 0        && frozen[z - 1][x]) return (z - 1) * size + x;
        if (z < size - 1 && frozen[z + 1][x]) return (z + 1) * size + x;
        return -1;
    }

    // -------------------------------------------------------------------------
    // Line-connection upscale (the PDF's "custom" upscaler).
    // -------------------------------------------------------------------------

    /**
     * Upscale the {@code srcSize × srcSize} aggregate into {@code tgtSize ×
     * tgtSize} by (1) transferring each source frozen cell to its mapped
     * target position, then (2) drawing a two-segment line from every
     * source child to its source parent, with the intermediate midpoint
     * jiggled by one cell. Jiggling prevents the upscaled lines from
     * forming rigid, axis-aligned geometry — which was the PDF's specific
     * complaint against nearest-neighbour and linear interpolation.
     *
     * <p>Target parent pointers along each drawn line form a chain back
     * toward the source parent's mapped position, preserving connectivity
     * for the next upscale.
     */
    private static void upscale(boolean[][] srcFrozen, int[][] srcParent, int srcSize,
                                boolean[][] tgtFrozen, int[][] tgtParent, int tgtSize,
                                Random rng) {
        // Stamp source frozen cells into the target grid at their mapped positions.
        for (int z = 0; z < srcSize; z++) {
            for (int x = 0; x < srcSize; x++) {
                if (!srcFrozen[z][x]) continue;
                int tx = (x * tgtSize) / srcSize;
                int tz = (z * tgtSize) / srcSize;
                tgtFrozen[tz][tx] = true;
            }
        }
        // Draw a jiggled two-segment line for each source parent edge.
        for (int z = 0; z < srcSize; z++) {
            for (int x = 0; x < srcSize; x++) {
                if (!srcFrozen[z][x]) continue;
                int p = srcParent[z][x];
                if (p < 0) continue; // root cell: no incoming edge to draw
                int sx = p % srcSize;
                int sz = p / srcSize;

                int tcx = (x  * tgtSize) / srcSize;
                int tcz = (z  * tgtSize) / srcSize;
                int tpx = (sx * tgtSize) / srcSize;
                int tpz = (sz * tgtSize) / srcSize;

                int mx = (tcx + tpx) / 2 + rng.nextInt(3) - 1;
                int mz = (tcz + tpz) / 2 + rng.nextInt(3) - 1;

                drawLine(tgtFrozen, tgtParent, tgtSize, tcx, tcz, mx,  mz);
                drawLine(tgtFrozen, tgtParent, tgtSize, mx,  mz,  tpx, tpz);
            }
        }
    }

    /**
     * Bresenham line from (x0, z0) to (x1, z1). Marks every in-bounds cell
     * along the line as frozen, and sets each cell's parent pointer to the
     * next cell on the line toward the endpoint — so the endpoint (x1, z1)
     * keeps whatever parent pointer it already had, and cells along the
     * way chain forward to it.
     */
    private static void drawLine(boolean[][] frozen, int[][] parent, int size,
                                 int x0, int z0, int x1, int z1) {
        int cx = x0, cz = z0;
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int prevX = Integer.MIN_VALUE, prevZ = 0;

        while (true) {
            boolean inBounds = cx >= 0 && cx < size && cz >= 0 && cz < size;
            if (inBounds) {
                frozen[cz][cx] = true;
                if (prevX != Integer.MIN_VALUE) {
                    parent[prevZ][prevX] = cz * size + cx;
                }
                prevX = cx;
                prevZ = cz;
            } else {
                // Don't link a chain across an out-of-bounds gap.
                prevX = Integer.MIN_VALUE;
            }
            if (cx == x1 && cz == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; cx += sx; }
            if (e2 <  dx) { err += dx; cz += sz; }
        }
    }



    // -------------------------------------------------------------------------
    // Neighbour fetching.
    // -------------------------------------------------------------------------

    /**
     * Fetch border-strip masks for all 8 neighbour regions via the pipeline's
     * neighbour cache. Each is a size×size byte[][] with only the strip cells
     * populated. Returns {@code null} for neighbours that would be identical
     * to the "no-op" empty mask (not relevant here — every neighbour has a
     * strip, even if entirely zero).
     *
     * <p>Layout of the returned array (row-major, centred on 0,0):
     * <pre>
     *   [0] = (-1,-1) NW corner     [1] = (0,-1) N       [2] = (+1,-1) NE corner
     *   [3] = (-1, 0) W              [4] = (0, 0) skip    [5] = (+1, 0) E
     *   [6] = (-1,+1) SW corner     [7] = (0,+1) S       [8] = (+1,+1) SE corner
     * </pre>
     * Index 4 (the centre) is unused; keeping the 3×3 layout simplifies the
     * DT extension below.
     */
    private byte[][][] fetchNeighbours(LayerContext ctx, RegionCoord coord, int size) {
        int w = BORDER_STRIP_RADIUS;
        byte[][][] out = new byte[9][][];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int idx = (dz + 1) * 3 + (dx + 1);
                if (dx == 0 && dz == 0) continue; // skip self

                StripSpec strip = stripFor(dx, dz, size, w);
                out[idx] = ctx.neighbourStrip(
                    RegionCoord.of(coord.rx() + dx, coord.rz() + dz), this, strip);
            }
        }
        return out;
    }

    /**
     * The portion of a neighbour's grid we need to see, expressed in that
     * neighbour's local coordinates. A region at offset {@code (dx, dz)} from
     * us contributes its strip on the side facing us. For corner neighbours
     * the strip is a {@code w × w} square; for edge neighbours it's a full
     * row or column of width {@code w}.
     */
    private static StripSpec stripFor(int dx, int dz, int size, int w) {
        int x0, z0, width, height;
        // Along x: if the neighbour is to our east (dx=+1), we need its WEST
        // strip (x ∈ [0, w)); if to our west (dx=-1), its EAST strip.
        if      (dx == +1) { x0 = 0;        width = w; }
        else if (dx == -1) { x0 = size - w; width = w; }
        else               { x0 = 0;        width = size; }
        if      (dz == +1) { z0 = 0;        height = w; }
        else if (dz == -1) { z0 = size - w; height = w; }
        else               { z0 = 0;        height = size; }
        return new StripSpec(x0, z0, width, height);
    }

    // -------------------------------------------------------------------------
    // Extended distance transform.
    // -------------------------------------------------------------------------

    /**
     * Chamfer-3-4 distance transform seeded from own mask plus neighbour strip
     * masks projected into an extended (size + 2w) × (size + 2w) grid. Returns
     * the full extended grid so downstream passes (height-bump and blur) can
     * also operate across region boundaries, preserving continuity.
     */
    private static int[][] extendedDistanceTransformFull(byte[][] ownMask,
                                                         byte[][][] neighbourMasks,
                                                         int size, int w) {
        int ext = size + 2 * w;
        int large = ext * CHAMFER_DIAG * 4;
        int[][] dist = new int[ext][ext];
        for (int z = 0; z < ext; z++) {
            for (int x = 0; x < ext; x++) {
                dist[z][x] = large;
            }
        }
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                if (ownMask[z][x] != 0) dist[z + w][x + w] = 0;
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                int idx = (dz + 1) * 3 + (dx + 1);
                byte[][] nm = neighbourMasks[idx];
                if (nm == null) continue;
                int nxBase = w + dx * size;
                int nzBase = w + dz * size;
                for (int lz = 0; lz < size; lz++) {
                    int gz = nzBase + lz;
                    if (gz < 0 || gz >= ext) continue;
                    for (int lx = 0; lx < size; lx++) {
                        int gx = nxBase + lx;
                        if (gx < 0 || gx >= ext) continue;
                        if (nm[lz][lx] != 0) dist[gz][gx] = 0;
                    }
                }
            }
        }
        // Forward pass.
        for (int z = 0; z < ext; z++) {
            for (int x = 0; x < ext; x++) {
                int v = dist[z][x];
                if (x > 0)           v = Math.min(v, dist[z][x - 1]     + CHAMFER_ORTH);
                if (z > 0) {
                    if (x > 0)       v = Math.min(v, dist[z - 1][x - 1] + CHAMFER_DIAG);
                    v = Math.min(v, dist[z - 1][x] + CHAMFER_ORTH);
                    if (x < ext - 1) v = Math.min(v, dist[z - 1][x + 1] + CHAMFER_DIAG);
                }
                dist[z][x] = v;
            }
        }
        // Backward pass.
        for (int z = ext - 1; z >= 0; z--) {
            for (int x = ext - 1; x >= 0; x--) {
                int v = dist[z][x];
                if (x < ext - 1)     v = Math.min(v, dist[z][x + 1]     + CHAMFER_ORTH);
                if (z < ext - 1) {
                    if (x < ext - 1) v = Math.min(v, dist[z + 1][x + 1] + CHAMFER_DIAG);
                    v = Math.min(v, dist[z + 1][x] + CHAMFER_ORTH);
                    if (x > 0)       v = Math.min(v, dist[z + 1][x - 1] + CHAMFER_DIAG);
                }
                dist[z][x] = v;
            }
        }
        return dist;
    }

    /**
     * Zone classification over the extended 3×3 block — one classification per
     * extended-grid cell. Each of the 9 regions classifies its own size×size
     * window via {@link VoronoiClassifier}. Used as a gate for the height
     * bump: only mountain cells get non-zero contribution.
     */
    private static int[][] extendedZones(long seed, RegionCoord centre, int size, int w) {
        int ext = size + 2 * w;
        int[][] zonesExt = new int[ext][ext];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                RegionCoord n = RegionCoord.of(centre.rx() + dx, centre.rz() + dz);
                int[][] z = VoronoiClassifier.classify(seed, n, size);
                int baseX = w + dx * size;
                int baseZ = w + dz * size;
                for (int lz = 0; lz < size; lz++) {
                    int gz = baseZ + lz;
                    if (gz < 0 || gz >= ext) continue;
                    for (int lx = 0; lx < size; lx++) {
                        int gx = baseX + lx;
                        if (gx < 0 || gx >= ext) continue;
                        zonesExt[gz][gx] = z[lz][lx];
                    }
                }
            }
        }
        return zonesExt;
    }

    /** Exponential height bump applied over the full extended grid. */
    private static float[][] heightBumpExt(int[][] zonesExt, int[][] distExt, int size, int w) {
        int ext = size + 2 * w;
        float[][] ridge = new float[ext][ext];
        int mountainKind = ZoneKind.MOUNTAINS.ordinal();
        double invSigma = 1.0 / (RIDGE_FALLOFF_CELLS * CHAMFER_ORTH);
        for (int z = 0; z < ext; z++) {
            for (int x = 0; x < ext; x++) {
                if (zonesExt[z][x] != mountainKind) continue;
                ridge[z][x] = (float) (RIDGE_AMPLITUDE * StrictMath.exp(-distExt[z][x] * invSigma));
            }
        }
        return ridge;
    }

    /** 3×3 separable box blur over the extended grid, clamping at extended boundaries. */
    private static float[][] boxBlur3Ext(float[][] src) {
        int ext = src.length;
        float[][] tmp = new float[ext][ext];
        float[][] out = new float[ext][ext];
        for (int z = 0; z < ext; z++) {
            for (int x = 0; x < ext; x++) {
                int xm = Math.max(0, x - 1);
                int xp = Math.min(ext - 1, x + 1);
                tmp[z][x] = (src[z][xm] + src[z][x] + src[z][xp]) * (1.0f / 3.0f);
            }
        }
        for (int z = 0; z < ext; z++) {
            int zm = Math.max(0, z - 1);
            int zp = Math.min(ext - 1, z + 1);
            for (int x = 0; x < ext; x++) {
                out[z][x] = (tmp[zm][x] + tmp[z][x] + tmp[zp][x]) * (1.0f / 3.0f);
            }
        }
        return out;
    }
}
