package fr.enderclem.massif.pipeline;

import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Introspection helpers for a {@link Schedule} and the blackboards it
 * produces. Minimal for Phase 1 — richer tooling (dependency-graph
 * visualisation, generation trace, point inspector) will layer on top
 * as the key inventory and consumer surface grow.
 */
public final class Catalog {

    private Catalog() {}

    /**
     * Render the topologically-sorted producer list with the keys each one
     * reads and writes. Suitable for dumping to stdout or including in a
     * test failure message.
     */
    public static String scheduleListing(Schedule schedule) {
        StringBuilder sb = new StringBuilder();
        sb.append("Schedule (topological order):\n");
        List<Producer> order = schedule.order();
        int width = Integer.toString(order.size()).length();
        for (int i = 0; i < order.size(); i++) {
            Producer p = order.get(i);
            sb.append(String.format("  %" + width + "d. %s%n", i + 1, p.name()));
            for (FeatureKey<?> k : sortedByName(p.reads())) {
                sb.append("       reads  ").append(k.name()).append('\n');
            }
            for (FeatureKey<?> k : sortedByName(p.writes())) {
                sb.append("       writes ").append(k.name()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Render every key currently on the sealed blackboard, with the type of
     * the stored value. Useful for verifying a pipeline populated what it
     * claimed to populate.
     */
    public static String blackboardListing(Blackboard.Sealed board) {
        StringBuilder sb = new StringBuilder();
        sb.append("Blackboard contents:\n");
        for (FeatureKey<?> k : sortedByName(board.keys())) {
            sb.append("  ").append(k.name())
              .append("  :  ").append(k.type().getSimpleName())
              .append('\n');
        }
        return sb.toString();
    }

    private static List<FeatureKey<?>> sortedByName(Iterable<FeatureKey<?>> keys) {
        List<FeatureKey<?>> out = new ArrayList<>();
        for (FeatureKey<?> k : keys) out.add(k);
        out.sort(Comparator.comparing(FeatureKey::name));
        return out;
    }
}
