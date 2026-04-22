package fr.enderclem.massif.pipeline;

import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import java.util.Set;

/**
 * Scoped blackboard view handed to a producer during {@link Producer#compute}.
 * Enforces the producer's read/write declarations: reading a key not declared
 * in {@link Producer#reads} or writing one not declared in
 * {@link Producer#writes} throws immediately, so schedule correctness can
 * never drift from the declaration.
 */
public final class ExecutionContext {

    private final Blackboard blackboard;
    private final long seed;
    private final Set<FeatureKey<?>> declaredReads;
    private final Set<FeatureKey<?>> declaredWrites;
    private final String producerName;

    private ExecutionContext(Blackboard blackboard, long seed,
                             Set<FeatureKey<?>> reads, Set<FeatureKey<?>> writes,
                             String producerName) {
        this.blackboard = blackboard;
        this.seed = seed;
        this.declaredReads = reads;
        this.declaredWrites = writes;
        this.producerName = producerName;
    }

    /**
     * Factory used by {@code MassifFramework} (in the {@code api} package) to
     * hand a producer its scoped context. Public so the framework entry point
     * can live outside this package; not intended for direct use by producers.
     */
    public static ExecutionContext forProducer(Blackboard blackboard, long seed,
                                               Set<FeatureKey<?>> reads,
                                               Set<FeatureKey<?>> writes,
                                               String producerName) {
        return new ExecutionContext(blackboard, seed, reads, writes, producerName);
    }

    public long seed() {
        return seed;
    }

    public <T> T read(FeatureKey<T> key) {
        if (!declaredReads.contains(key)) {
            throw new IllegalStateException(
                "Producer '" + producerName + "' read undeclared key '" + key.name()
                    + "'. Add it to reads() or the schedule will not order it correctly.");
        }
        return blackboard.get(key);
    }

    public <T> void write(FeatureKey<T> key, T value) {
        if (!declaredWrites.contains(key)) {
            throw new IllegalStateException(
                "Producer '" + producerName + "' wrote undeclared key '" + key.name()
                    + "'. Add it to writes().");
        }
        blackboard.put(key, value);
    }
}
