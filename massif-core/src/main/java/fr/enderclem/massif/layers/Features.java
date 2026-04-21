package fr.enderclem.massif.layers;

import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.layers.voronoi.HandshakeGraph;
import fr.enderclem.massif.layers.voronoi.ZoneSeed;

/**
 * Well-known feature keys shared by the stub layers. Third-party layers (Phase 2)
 * will register their own keys; this class is not a closed enum.
 *
 * Grid resolution for the skeleton is {@link #REGION_SIZE} cells per side.
 * Values are {@code float[size][size]} unless noted.
 */
public final class Features {

    private Features() {}

    public static final int REGION_SIZE = 256;

    public static final FeatureKey<int[][]> ZONES =
        FeatureKey.of("massif:zones", int[][].class);

    public static final FeatureKey<ZoneSeed[]> ZONE_SEEDS =
        FeatureKey.of("massif:zone_seeds", ZoneSeed[].class);

    public static final FeatureKey<HandshakeGraph> HANDSHAKE =
        FeatureKey.of("massif:handshake", HandshakeGraph.class);

    /** Zone-aware base noise height contribution. */
    public static final FeatureKey<float[][]> BASE_HEIGHT =
        FeatureKey.of("massif:base_height", float[][].class);

    /** 0/1 grid marking cells that are part of the DLA ridge aggregate. */
    public static final FeatureKey<byte[][]> RIDGE_MASK =
        FeatureKey.of("massif:ridge_mask", byte[][].class);

    /** Ridge-derived height contribution (height bump around the DLA aggregate). */
    public static final FeatureKey<float[][]> RIDGE_HEIGHT =
        FeatureKey.of("massif:ridge_height", float[][].class);

    /** Final composed heightmap: sum of {@link #BASE_HEIGHT} and {@link #RIDGE_HEIGHT}. */
    public static final FeatureKey<float[][]> HEIGHTMAP =
        FeatureKey.of("massif:heightmap", float[][].class);

    public static final FeatureKey<float[][]> GRADIENT_MAG =
        FeatureKey.of("massif:gradient_mag", float[][].class);
}
