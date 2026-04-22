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

    /** World-window side length in blocks for the walking-skeleton outputs. */
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
     * Queryable zone classification: at any world coordinate returns the
     * integer id of the zone type covering it. Ids are defined by
     * {@link #ZONE_REGISTRY}.
     */
    public static final FeatureKey<ZoneField> ZONE_FIELD =
        FeatureKey.of("core:zone_field", ZoneField.class);
}
