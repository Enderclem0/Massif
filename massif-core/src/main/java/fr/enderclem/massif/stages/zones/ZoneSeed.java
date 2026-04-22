package fr.enderclem.massif.stages.zones;

/**
 * Voronoi seed point in world coordinates.
 * {@code kind} is a placeholder for the eventual biome/zone-type enum.
 * {@code id} is a globally-unique integer derived from (rx, rz, localIndex)
 *   so two seeds in two regions cannot accidentally collide on the wire.
 */
public record ZoneSeed(double wx, double wz, int kind, int id) {}
