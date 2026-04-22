package fr.enderclem.massif.api;

/**
 * A single Voronoi seed in world coordinates.
 *
 * <p>{@code id} is stable across the lifetime of a world and unique across
 * the entire seed space — cheap for cell-level equality checks without
 * comparing positions. {@code kind} is a {@link ZoneTypeRegistry} id.
 * {@code wx}/{@code wz} are the seed's current position, which for the
 * default (jittered) seed pool is its initial placement and for Lloyd-
 * relaxed pools moves toward each iteration's cell centroid.
 */
public record ZoneSeed(double wx, double wz, int kind, int id) {}
