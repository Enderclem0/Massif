package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.ZoneSeed;
import fr.enderclem.massif.api.ZoneSeedPool;
import fr.enderclem.massif.api.ZoneTypeRegistry;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Single point of variation for the Voronoi seed source. Writes
 * {@link MassifKeys#ZONE_SEED_POOL} so every downstream zone producer
 * (field, border, graph) consults the same pool without duplicating the
 * Lloyd decision.
 *
 * <p>With {@code relaxIterations == 0} the producer publishes a
 * {@link JitteredZoneSeedPool} — on-demand, unbounded, the original Phase 3
 * behaviour. With {@code relaxIterations > 0} it computes Lloyd-relaxed
 * positions over the window {@code [-windowSize/2, windowSize/2]²} plus a
 * one-cell halo and wraps them in a {@link RelaxedZoneSeedPool} with the
 * jittered pool as the fallback for out-of-window queries.
 */
public final class ZoneSeedPoolProducer implements Producer {

    private final int cellSize;
    private final int relaxIterations;
    private final int windowSize;

    public ZoneSeedPoolProducer() {
        this(ZoneFieldProducer.DEFAULT_CELL_SIZE, 0, MassifKeys.VIEW_SIZE);
    }

    public ZoneSeedPoolProducer(int cellSize, int relaxIterations, int windowSize) {
        if (cellSize <= 0) throw new IllegalArgumentException("cellSize must be positive");
        if (relaxIterations < 0) throw new IllegalArgumentException("relaxIterations must be ≥ 0");
        if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be positive");
        this.cellSize = cellSize;
        this.relaxIterations = relaxIterations;
        this.windowSize = windowSize;
    }

    @Override
    public String name() {
        return "zones.seed_pool";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.ZONE_SEED_POOL);
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(MassifKeys.ZONE_REGISTRY);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        ZoneTypeRegistry registry = ctx.read(MassifKeys.ZONE_REGISTRY);
        int kindCount = registry.size();
        JitteredZoneSeedPool jittered = new JitteredZoneSeedPool(ctx.seed(), cellSize, kindCount);

        if (relaxIterations == 0) {
            ctx.write(MassifKeys.ZONE_SEED_POOL, jittered);
            return;
        }

        // Collect jittered seeds over window + 1-cell halo. The halo's seeds
        // don't appear in the rendered viewport but they exist so the seeds
        // on the window edge relax against real neighbours, not against a
        // vacuum that would bias them inward.
        int windowHalf = windowSize / 2;
        int x0 = -windowHalf - cellSize;
        int z0 = -windowHalf - cellSize;
        int x1 = windowHalf + cellSize;
        int z1 = windowHalf + cellSize;
        int rxMin = Math.floorDiv(x0, cellSize);
        int rxMax = Math.floorDiv(x1 - 1, cellSize);
        int rzMin = Math.floorDiv(z0, cellSize);
        int rzMax = Math.floorDiv(z1 - 1, cellSize);

        List<ZoneSeed> initial = new ArrayList<>();
        for (int rz = rzMin; rz <= rzMax; rz++) {
            for (int rx = rxMin; rx <= rxMax; rx++) {
                ZoneSeed[] region = ZoneSeeds.of(ctx.seed(), rx, rz, cellSize, kindCount);
                Collections.addAll(initial, region);
            }
        }

        List<ZoneSeed> relaxed = LloydRelaxation.relax(initial, x0, z0, x1, z1, relaxIterations);
        ZoneSeedPool pool = new RelaxedZoneSeedPool(
            relaxed.toArray(new ZoneSeed[0]),
            -windowHalf, -windowHalf, windowHalf, windowHalf,
            jittered);
        ctx.write(MassifKeys.ZONE_SEED_POOL, pool);
    }
}
