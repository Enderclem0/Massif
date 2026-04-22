package fr.enderclem.massif.api;

/**
 * A single edge in a {@link MountainCluster}'s spine tree. Connects two
 * zone-cell ids that are Voronoi-adjacent in the cluster. The pair is not
 * semantically ordered — the spine is an unrooted tree for rendering and
 * traversal purposes — but the producer writes them in BFS child→parent
 * order as a convenience for consumers that want to trace toward the root
 * without recomputing the tree.
 */
public record SpineEdge(int fromCellId, int toCellId) {}
