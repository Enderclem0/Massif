package fr.enderclem.massif.api;

import java.util.List;
import java.util.Optional;

/**
 * List wrapper over the {@link MountainCluster}s the structural-plan stage
 * extracted from the current zone graph. Published under
 * {@link MassifKeys#MOUNTAIN_CLUSTERS}. Empty when the world has no
 * mountain zone type registered, or when no mountain cell fell inside the
 * zone graph's window.
 */
public record MountainClusters(List<MountainCluster> clusters) {

    public MountainClusters {
        clusters = List.copyOf(clusters);
    }

    public Optional<MountainCluster> findById(int id) {
        for (MountainCluster c : clusters) if (c.id() == id) return Optional.of(c);
        return Optional.empty();
    }
}
