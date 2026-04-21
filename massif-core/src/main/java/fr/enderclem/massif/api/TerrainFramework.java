package fr.enderclem.massif.api;

import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.dag.DagScheduler;
import fr.enderclem.massif.dag.DagScheduler.Schedule;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.dag.LayerContext;
import fr.enderclem.massif.dag.NeighbourCache;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.List;

/**
 * Top-level entry point. Construct with a list of layers; the DAG is compiled
 * once and reused for every region. {@link #generate} is the hot path and is
 * safe to call concurrently for different coordinates.
 *
 * <p>Carries a process-scoped {@link NeighbourCache} so that border-aware
 * layers can fetch neighbouring regions' border-strip outputs without
 * recomputing them on every region that happens to border the same neighbour.
 */
public final class TerrainFramework {

    private final Schedule schedule;
    private final NeighbourCache neighbourCache = new NeighbourCache();

    public TerrainFramework(List<Layer> layers) {
        this.schedule = DagScheduler.compile(layers);
    }

    public Schedule schedule() {
        return schedule;
    }

    public NeighbourCache neighbourCache() {
        return neighbourCache;
    }

    public RegionPlan generate(long seed, RegionCoord coord) {
        Blackboard board = new Blackboard();
        for (Layer layer : schedule.order()) {
            LayerContext ctx = new LayerContext(
                board, layer.reads(), layer.writes(), seed, coord, layer.name(), neighbourCache);
            layer.compute(ctx);
        }
        return new RegionPlan(seed, coord, board.seal());
    }
}
