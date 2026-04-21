package fr.enderclem.massif.layers.voronoi;

/**
 * Placeholder zone types with per-kind noise profile parameters.
 *
 * <p>{@code amplitude} scales the zone's contribution to the base noise field;
 * {@code offset} adds a constant to shift the zone up or down. Output height
 * at a cell = baseNoise × blendedAmplitude + blendedOffset, blended smoothly
 * across zone boundaries by the noise layer.
 *
 * <p>Values are hand-tuned to a [-1, 1] base-noise range; they are intentionally
 * crude — Stage 2+ will replace this with richer per-zone behaviour (frequency,
 * warp, secondary octaves). The key invariant is that the enum's ordinal matches
 * what {@link ZoneSeeds} emits as the integer kind.
 */
public enum ZoneKind {
    OCEAN     ("Ocean",     0.05, -0.60, 0x1A4F7A),
    SWAMP     ("Swamp",     0.08, -0.15, 0x3E5A2D),
    PLAINS    ("Plains",    0.10,  0.00, 0x7BA05B),
    DESERT    ("Desert",    0.14,  0.05, 0xD4B06A),
    HILLS     ("Hills",     0.28,  0.20, 0x6B8247),
    MOUNTAINS ("Mountains", 0.60,  0.45, 0x8A8276);

    private final String displayName;
    private final double amplitude;
    private final double offset;
    private final int rgb;

    ZoneKind(String displayName, double amplitude, double offset, int rgb) {
        this.displayName = displayName;
        this.amplitude = amplitude;
        this.offset = offset;
        this.rgb = rgb;
    }

    public String displayName() { return displayName; }
    public double amplitude() { return amplitude; }
    public double offset() { return offset; }

    /** Packed 0xRRGGBB terrain-palette colour. Kept as int so core stays AWT-free. */
    public int rgb() { return rgb; }

    public static ZoneKind byId(int id) {
        ZoneKind[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    public static int count() {
        return values().length;
    }
}
