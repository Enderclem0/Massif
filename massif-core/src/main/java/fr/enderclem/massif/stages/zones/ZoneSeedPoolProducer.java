package fr.enderclem.massif.stages.zones;

import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.api.WorldWindow;
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
 * positions over the {@link WorldWindow} plus a one-cell halo and wraps
 * them in a {@link RelaxedZoneSeedPool} with the jittered pool as the
 * fallback for out-of-window queries.
 */
public final class ZoneSeedPoolProducer implements Producer {

    private final int cellSize;
    private final int relaxIterations;
    private final WorldWindow window;

    public ZoneSeedPoolProducer() {
        this(ZoneFieldProducer.DEFAULT_CELL_SIZE, 0, WorldWindow.defaultWindow());
    }

    public ZoneSeedPoolProducer(int cellSize, int relaxIterations, WorldWindow window) {
        if (cellSize <= 0) throw new IllegalArgumentException("cellSize must be positive");
        if (relaxIterations < 0) throw new IllegalArgumentException("relaxIterations must be ≥ 0");
        this.cellSize = cellSize;
        this.relaxIterations = relaxIterations;
        this.window = window;
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

        // Window + one-cell halo. Halo seeds don't appear in the rendered
        // viewport but exist so the seeds on the window edge relax against
        // real neighbours instead of against a vacuum that biases them inward.
        int x0 = window.x0() - cellSize;
        int z0 = window.z0() - cellSize;
        int x1 = window.x1() + cellSize;
        int z1 = window.z1() + cellSize;
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
            window.x0(), window.z0(), window.x1(), window.z1(),
            jittered);
        ctx.write(MassifKeys.ZONE_SEED_POOL, pool);
    }
}
