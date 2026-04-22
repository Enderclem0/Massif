package fr.enderclem.massif.api;

import java.util.List;

/**
 * List wrapper over the {@link DrainageBasin}s computed from the
 * {@link DrainageGraph}. Published under
 * {@link MassifKeys#DRAINAGE_BASINS}.
 */
public record DrainageBasins(List<DrainageBasin> basins) {

    public DrainageBasins {
        basins = List.copyOf(basins);
    }
}
