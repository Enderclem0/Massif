package fr.enderclem.massif.api;

/**
 * Axis-aligned square world-space window used by window-bounded producers
 * (seed-pool Lloyd relaxation, zone graph enumeration, demo heightmap).
 *
 * <p>Centred on {@code (centerX, centerZ)} with side length {@code size}
 * blocks; the half-open interval {@code [x0, x1) × [z0, z1)} is what each
 * producer iterates. Point-query producers ({@link ZoneField},
 * {@link BorderField}) remain unbounded — they pay no attention to this
 * window because they're pure functions of world coordinates.
 *
 * <p>Used today by the visualiser's zoom/pan controls to re-scope the
 * framework on each user action; the config model in Phase 5+ will grow
 * into a fuller "world spec" (seed, window, registry, lloyd count, …).
 */
public record WorldWindow(int centerX, int centerZ, int size) {

    public WorldWindow {
        if (size <= 0) throw new IllegalArgumentException("size must be positive, got " + size);
    }

    public int x0() { return centerX - size / 2; }
    public int z0() { return centerZ - size / 2; }
    public int x1() { return centerX + size / 2; }
    public int z1() { return centerZ + size / 2; }

    /** Default: 512-block window centred on the world origin. */
    public static WorldWindow defaultWindow() {
        return new WorldWindow(0, 0, 512);
    }
}
