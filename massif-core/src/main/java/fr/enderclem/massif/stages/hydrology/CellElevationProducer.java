package fr.enderclem.massif.stages.hydrology;

import fr.enderclem.massif.api.CellElevation;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneCell;
import fr.enderclem.massif.api.ZoneGraph;
import fr.enderclem.massif.api.ZoneType;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Assigns a raw surface elevation to every cell in the zone graph.
 *
 * <p>Elevation is the zone-type's nominal height plus a per-cell jitter
 * derived from {@code (worldSeed ^ cellId)} so adjacent cells of the
 * same type don't tie (priority-flood drainage needs distinct elevations
 * to pick a downhill direction). The nominal values cover a
 * walking-skeleton range; real-world block heights arrive when the
 * composition stage produces {@code core:height}.
 */
public final class CellElevationProducer implements Producer {

    /** Default nominal elevation used when a zone type isn't in the table. */
    private static final double DEFAULT_ELEVATION = 0.2;

    /** Amplitude of per-cell jitter added to the nominal value. */
    private static final double JITTER_AMPLITUDE = 0.08;

    /** Nominal elevations by zone-type {@link ZoneType#name()}. */
    private static final Map<String, Double> BASE_ELEVATION = Map.of(
        "ocean",    -0.60,
        "plains",    0.10,
        "desert",    0.20,
        "tundra",    0.30,
        "mountain",  0.75
    );

    @Override
    public String name() {
        return "hydrology.cell_elevation";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.CELL_ELEVATION);
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(MassifKeys.ZONE_GRAPH, MassifKeys.ZONE_REGISTRY);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneGraph graph = ctx.read(MassifKeys.ZONE_GRAPH);
        ZoneTypeRegistry registry = ctx.read(MassifKeys.ZONE_REGISTRY);
        long seed = ctx.seed();

        Map<Integer, Double> byCellId = new HashMap<>();
        for (ZoneCell cell : graph.cells()) {
            double base = BASE_ELEVATION.getOrDefault(
                registry.get(cell.type()).name(), DEFAULT_ELEVATION);
            // Mixing the cell id into the seed keeps this a pure function
            // of (worldSeed, cellId) — same cell always gets the same jitter.
            double jitter = (new Random(seed ^ (long) cell.id() * 0x9E37_79B9_7F4A_7C15L)
                .nextDouble() - 0.5) * 2.0 * JITTER_AMPLITUDE;
            byCellId.put(cell.id(), base + jitter);
        }
        ctx.write(MassifKeys.CELL_ELEVATION, new CellElevation(byCellId));
    }
}
