package fr.enderclem.massif.pipeline;

import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import java.util.List;

/**
 * Top-level framework entry point. Compiles a producer list into a
 * {@link Schedule} once, then runs that schedule against a freshly-built
 * {@link Blackboard} for each {@link #generate} call.
 *
 * <p>After every producer runs, the framework verifies the producer
 * populated every key it declared in {@link Producer#writes} — a silent
 * omission would cascade into a "not present on blackboard" failure in a
 * downstream reader, which is harder to debug than catching it at the
 * source.
 */
public final class MassifFramework {

    private final Schedule schedule;

    private MassifFramework(Schedule schedule) {
        this.schedule = schedule;
    }

    public static MassifFramework of(Producer... producers) {
        return new MassifFramework(Schedule.compile(List.of(producers)));
    }

    public static MassifFramework of(List<Producer> producers) {
        return new MassifFramework(Schedule.compile(producers));
    }

    public Schedule schedule() {
        return schedule;
    }

    public Blackboard.Sealed generate(long seed) {
        Blackboard board = new Blackboard();
        for (Producer p : schedule.order()) {
            ExecutionContext ctx = new ExecutionContext(
                board, seed, p.reads(), p.writes(), p.name());
            p.compute(ctx);
            for (FeatureKey<?> k : p.writes()) {
                if (!board.has(k)) {
                    throw new IllegalStateException(
                        "Producer '" + p.name() + "' did not write declared key '"
                            + k.name() + "'");
                }
            }
        }
        return board.seal();
    }
}
