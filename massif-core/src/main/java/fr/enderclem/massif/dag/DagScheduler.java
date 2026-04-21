package fr.enderclem.massif.dag;

import fr.enderclem.massif.blackboard.FeatureKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a set of {@link Layer}s into a deterministic, executable order.
 *
 * The schedule's edges are: writer-of-key → reader-of-key, for every
 * (reader.reads ∩ writer.writes) pair. Kahn's algorithm produces a topological
 * order; ties are broken by layer name so the compiled schedule is stable.
 *
 * Fails fast on:
 *   - duplicate writers for a feature,
 *   - cycles in the derived graph,
 *   - reads of features no layer writes.
 */
public final class DagScheduler {

    private DagScheduler() {}

    public static Schedule compile(List<Layer> layers) {
        Map<String, Layer> byName = new LinkedHashMap<>();
        for (Layer l : layers) {
            if (byName.putIfAbsent(l.name(), l) != null) {
                throw new IllegalArgumentException("Duplicate layer name: " + l.name());
            }
        }

        Map<FeatureKey<?>, Layer> producer = new HashMap<>();
        for (Layer l : layers) {
            for (FeatureKey<?> w : l.writes()) {
                Layer prev = producer.putIfAbsent(w, l);
                if (prev != null) {
                    throw new IllegalArgumentException(
                        "Feature '" + w.name() + "' has two writers: "
                            + prev.name() + " and " + l.name());
                }
            }
        }

        Map<Layer, Set<Layer>> edges = new HashMap<>();
        Map<Layer, Integer> indeg = new HashMap<>();
        for (Layer l : layers) {
            edges.put(l, new HashSet<>());
            indeg.put(l, 0);
        }
        for (Layer reader : layers) {
            for (FeatureKey<?> r : reader.reads()) {
                Layer writer = producer.get(r);
                if (writer == null) {
                    throw new IllegalArgumentException(
                        "Layer '" + reader.name() + "' reads unproduced feature: "
                            + r.name());
                }
                if (writer == reader) {
                    throw new IllegalArgumentException(
                        "Layer '" + reader.name() + "' reads its own write: " + r.name());
                }
                if (edges.get(writer).add(reader)) {
                    indeg.merge(reader, 1, Integer::sum);
                }
            }
        }

        // Kahn with name-based tie-break for stability.
        Deque<Layer> ready = new ArrayDeque<>();
        layers.stream()
            .filter(l -> indeg.get(l) == 0)
            .sorted((a, b) -> a.name().compareTo(b.name()))
            .forEach(ready::add);

        List<Layer> order = new ArrayList<>(layers.size());
        while (!ready.isEmpty()) {
            Layer l = ready.pollFirst();
            order.add(l);
            List<Layer> unlocked = new ArrayList<>();
            for (Layer downstream : edges.get(l)) {
                int next = indeg.merge(downstream, -1, Integer::sum);
                if (next == 0) {
                    unlocked.add(downstream);
                }
            }
            unlocked.sort((a, b) -> a.name().compareTo(b.name()));
            for (Layer u : unlocked) {
                ready.addLast(u);
            }
        }

        if (order.size() != layers.size()) {
            List<String> stuck = new ArrayList<>();
            for (Layer l : layers) {
                if (!order.contains(l)) {
                    stuck.add(l.name());
                }
            }
            throw new IllegalArgumentException("Cycle detected among layers: " + stuck);
        }

        return new Schedule(List.copyOf(order), Map.copyOf(producer));
    }

    /** Compiled, immutable execution schedule. */
    public static final class Schedule {
        private final List<Layer> order;
        private final Map<FeatureKey<?>, Layer> producer;

        Schedule(List<Layer> order, Map<FeatureKey<?>, Layer> producer) {
            this.order = order;
            this.producer = producer;
        }

        public List<Layer> order() {
            return order;
        }

        public Set<FeatureKey<?>> allFeatures() {
            return producer.keySet();
        }

        public Layer producerOf(FeatureKey<?> key) {
            return producer.get(key);
        }
    }
}
