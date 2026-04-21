package fr.enderclem.massif.dag;

import fr.enderclem.massif.blackboard.FeatureKey;
import java.util.Set;

/**
 * A POCO pipeline unit. Declares which features it reads and writes; the DAG
 * scheduler derives execution order from those declarations.
 *
 * Implementations must be stateless: {@link #compute} is a pure function of
 * {@link LayerContext} (which in turn is a pure function of seed and coord).
 */
public interface Layer {

    /** Stable identifier, used for logging and UI. */
    String name();

    /** Features this layer will read from the blackboard. */
    Set<FeatureKey<?>> reads();

    /** Features this layer will write to the blackboard. Each write must occur exactly once. */
    Set<FeatureKey<?>> writes();

    void compute(LayerContext ctx);
}
