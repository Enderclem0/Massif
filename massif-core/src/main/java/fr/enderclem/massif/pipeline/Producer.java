package fr.enderclem.massif.pipeline;

import fr.enderclem.massif.blackboard.FeatureKey;
import java.util.Collections;
import java.util.Set;

/**
 * A unit of generation. Declares the blackboard keys it reads and writes, and
 * populates every declared write when run. Producers are the nodes of the
 * pipeline's key-dependency DAG: {@link Schedule#compile(java.util.List)}
 * topologically sorts them so every read is populated before the reader runs.
 *
 * <p>{@code reads()}/{@code writes()} are commitments. Writing an undeclared
 * key or reading one fails loudly at runtime — the schedule relies on the
 * declarations being accurate to determine execution order.
 */
public interface Producer {

    /** Diagnostic name, not a stable identifier. Usually the class name or a short label. */
    String name();

    /** Keys this producer populates on the blackboard. */
    Set<FeatureKey<?>> writes();

    /** Keys this producer reads from the blackboard. Empty by default. */
    default Set<FeatureKey<?>> reads() {
        return Collections.emptySet();
    }

    /** Populate an entry for every key in {@link #writes()}. */
    void compute(ExecutionContext ctx);
}
