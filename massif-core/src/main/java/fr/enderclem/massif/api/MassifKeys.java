package fr.enderclem.massif.api;

import fr.enderclem.massif.blackboard.FeatureKey;

/**
 * Public catalogue of {@code core:*} blackboard keys. Consumers look keys up
 * here by constant reference rather than re-declaring them; the constants
 * are the single source of truth for names and value types.
 *
 * <p>New keys are added as each rebuild phase lands; every addition is
 * backwards-compatible (new constants only) and existing entries follow
 * additive-schema-evolution rules.
 */
public final class MassifKeys {

    private MassifKeys() {}

    /**
     * Default world-window side length in blocks. Still referenced by
     * {@link WorldWindow#defaultWindow()} and the no-arg producer
     * constructors; the visualiser threads an explicit {@link WorldWindow}
     * through the framework when the user zooms or pans.
     */
    public static final int VIEW_SIZE = 512;

    /**
     * Surface-elevation grid covering the current walking-skeleton viewport,
     * indexed {@code [z][x]}. Values roughly in {@code [-1, 1]}. Phase 2
     * placeholder — replaced by a world-coordinate-queryable {@code
     * core:height} when the composition stage lands.
     */
    public static final FeatureKey<float[][]> HEIGHTMAP =
        FeatureKey.of("core:heightmap", float[][].class);

    /** Ordered, immutable registry of all zone types used by the current world. */
    public static final FeatureKey<ZoneTypeRegistry> ZONE_REGISTRY =
        FeatureKey.of("core:zone_registry", ZoneTypeRegistry.class);

    /**
     * Voronoi seed source consulted by the zone field, border field, and
     * graph producers. Publishing a single pool means the Lloyd-vs-jittered
     * decision is made in one place instead of repeated across every
     * downstream zone producer.
     */
    public static final FeatureKey<ZoneSeedPool> ZONE_SEED_POOL =
        FeatureKey.of("core:zone_seed_pool", ZoneSeedPool.class);

    /**
     * Queryable zone classification: at any world coordinate returns the
     * integer id of the zone type covering it. Ids are defined by
     * {@link #ZONE_REGISTRY}.
     */
    public static final FeatureKey<ZoneField> ZONE_FIELD =
        FeatureKey.of("core:zone_field", ZoneField.class);

    /**
     * Voronoi cells within (and one-region halo around) the current render
     * window, with adjacency lists. Use this for graph traversals like
     * "colour neighbouring cells" or "enumerate all mountain cells within
     * view". For unbounded point queries use {@link #ZONE_FIELD} instead.
     */
    public static final FeatureKey<ZoneGraph> ZONE_GRAPH =
        FeatureKey.of("core:zone_graph", ZoneGraph.class);

    /**
     * Queryable Voronoi border geometry: distance / normal / zone-type pair
     * at any world coordinate. Design doc §Transitions lists these as three
     * separate keys; the current implementation packs them in one
     * {@link BorderSample} per call because a shared two-nearest-seeds
     * lookup produces all three.
     */
    public static final FeatureKey<BorderField> BORDER_FIELD =
        FeatureKey.of("core:border_field", BorderField.class);

    /**
     * Connected components of mountain-type cells in the zone graph, each
     * with structural-plan metadata (centroid, orientation, semi-major
     * extent, peak-count hint, selected generation technique). The first
     * {@link MountainCluster}-level output — Layer 2 of the design doc.
     */
    public static final FeatureKey<MountainClusters> MOUNTAIN_CLUSTERS =
        FeatureKey.of("core:mountain_clusters", MountainClusters.class);

    /**
     * Per-zone-cell raw surface elevation, assigned from zone type + a
     * deterministic per-cell jitter. Upstream of the hydrology pass.
     */
    public static final FeatureKey<CellElevation> CELL_ELEVATION =
        FeatureKey.of("core:cell_elevation", CellElevation.class);

    /**
     * Priority-flood drainage: downhill pointer per cell + water level
     * (raised inside closed basins → lakes) + list of terminals (ocean
     * cells plus endorheic-basin bottoms). Consumes {@link #CELL_ELEVATION}
     * and the zone graph.
     */
    public static final FeatureKey<DrainageGraph> DRAINAGE_GRAPH =
        FeatureKey.of("core:drainage_graph", DrainageGraph.class);

    /**
     * Drainage basins — one per terminal in {@link #DRAINAGE_GRAPH}. Every
     * non-terminal cell belongs to exactly one basin: whichever terminal
     * its downhill chain reaches.
     */
    public static final FeatureKey<DrainageBasins> DRAINAGE_BASINS =
        FeatureKey.of("core:drainage_basins", DrainageBasins.class);
}
