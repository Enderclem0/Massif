package fr.enderclem.massif.pipeline;

import fr.enderclem.massif.blackboard.FeatureKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Topologically-sorted list of producers plus the key → producer map used
 * during compilation.
 *
 * <p>{@link #compile(List)} enforces three invariants:
 * <ol>
 *   <li>Every key is written by at most one producer.</li>
 *   <li>Every read has a corresponding writer.</li>
 *   <li>The read-writes graph is acyclic.</li>
 * </ol>
 * Violations throw {@link IllegalStateException} with a message identifying
 * the offending producers or keys. Ties in topological order are broken by
 * declaration order for deterministic output.
 */
public final class Schedule {

    private final List<Producer> order;
    private final Map<FeatureKey<?>, Producer> producerOf;

    private Schedule(List<Producer> order, Map<FeatureKey<?>, Producer> producerOf) {
        this.order = List.copyOf(order);
        this.producerOf = Map.copyOf(producerOf);
    }

    public List<Producer> order() {
        return order;
    }

    public Map<FeatureKey<?>, Producer> producerOf() {
        return producerOf;
    }

    public static Schedule compile(List<Producer> producers) {
        // 1. Build writer map, rejecting duplicate writers.
        Map<FeatureKey<?>, Producer> writerOf = new HashMap<>();
        for (Producer p : producers) {
            for (FeatureKey<?> k : p.writes()) {
                Producer prev = writerOf.put(k, p);
                if (prev != null && prev != p) {
                    throw new IllegalStateException(
                        "Key '" + k.name() + "' is written by multiple producers: '"
                            + prev.name() + "' and '" + p.name() + "'");
                }
            }
        }

        // 2. Reject reads with no producer.
        for (Producer p : producers) {
            for (FeatureKey<?> k : p.reads()) {
                if (!writerOf.containsKey(k)) {
                    throw new IllegalStateException(
                        "Producer '" + p.name() + "' reads '" + k.name()
                            + "' but no producer writes it");
                }
            }
        }

        // 3. Topological sort on "writer(key) → reader(key)" edges.
        Map<Producer, List<Producer>> outgoing = new IdentityHashMap<>();
        Map<Producer, Integer> indegree = new IdentityHashMap<>();
        for (Producer p : producers) {
            outgoing.put(p, new ArrayList<>());
            indegree.put(p, 0);
        }
        for (Producer p : producers) {
            for (FeatureKey<?> k : p.reads()) {
                Producer src = writerOf.get(k);
                if (src == p) continue; // self-read: odd but not a cycle
                outgoing.get(src).add(p);
                indegree.merge(p, 1, Integer::sum);
            }
        }

        // Deterministic Kahn's algorithm: ready queue keeps declaration order.
        Deque<Producer> ready = new ArrayDeque<>();
        for (Producer p : producers) {
            if (indegree.get(p) == 0) ready.addLast(p);
        }
        List<Producer> sorted = new ArrayList<>(producers.size());
        while (!ready.isEmpty()) {
            Producer p = ready.removeFirst();
            sorted.add(p);
            for (Producer q : outgoing.get(p)) {
                int d = indegree.get(q) - 1;
                indegree.put(q, d);
                if (d == 0) ready.addLast(q);
            }
        }

        if (sorted.size() != producers.size()) {
            List<String> stuck = new ArrayList<>();
            for (Producer p : producers) {
                if (indegree.get(p) > 0) stuck.add(p.name());
            }
            throw new IllegalStateException(
                "Cycle in producer dependencies involving: " + stuck);
        }

        return new Schedule(sorted, writerOf);
    }
}
