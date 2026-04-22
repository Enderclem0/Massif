package fr.enderclem.massif.api;

import fr.enderclem.massif.blackboard.FeatureKey;

/**
 * Public catalogue of {@code core:*} blackboard keys. Consumers look keys up
 * here by constant reference rather than re-declaring them; the constants
 * are the single source of truth for names and value types.
 *
 * <p>The inventory is intentionally small during the Phase 2 walking
 * skeleton — it grows as each rebuild phase lands (zones, structural plan,
 * hydrology, techniques, composition). Every addition is backwards-compatible
 * (new constants only); existing entries follow additive-schema-evolution
 * rules.
 */
public final class MassifKeys {

    private MassifKeys() {}

    /** Region side length in cells for the current walking-skeleton outputs. */
    public static final int DEMO_SIZE = 256;

    /**
     * Surface-elevation grid for a single centred region, indexed {@code [z][x]}.
     * Values are roughly in {@code [-1, 1]}. Phase 2 placeholder — replaced by
     * {@code core:height} (sampled, world-coordinate-queryable) once the
     * composition stage lands.
     */
    public static final FeatureKey<float[][]> HEIGHTMAP =
        FeatureKey.of("core:heightmap", float[][].class);
}
