package fr.enderclem.massif.blackboard;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-region typed key/value store written by layers during pipeline execution.
 * Write-once: a given {@link FeatureKey} may only be written once per blackboard.
 * Sealed via {@link #seal()} once the pipeline has finished, after which the
 * returned view is immutable and safe to publish.
 */
public final class Blackboard {

    private final Map<FeatureKey<?>, Object> store = new HashMap<>();
    private boolean sealed = false;

    public <T> void put(FeatureKey<T> key, T value) {
        if (sealed) {
            throw new IllegalStateException("Blackboard is sealed");
        }
        if (store.containsKey(key)) {
            throw new IllegalStateException(
                "Feature already written: " + key.name());
        }
        store.put(key, value);
    }

    public <T> T get(FeatureKey<T> key) {
        Object value = store.get(key);
        if (value == null) {
            throw new IllegalStateException(
                "Feature not present on blackboard: " + key.name());
        }
        return key.castValue(value);
    }

    public boolean has(FeatureKey<?> key) {
        return store.containsKey(key);
    }

    public Set<FeatureKey<?>> keys() {
        return Collections.unmodifiableSet(store.keySet());
    }

    public SealedBlackboard seal() {
        this.sealed = true;
        return new SealedBlackboard(Map.copyOf(store));
    }

    /** Immutable snapshot returned after {@link Blackboard#seal()}. */
    public static final class SealedBlackboard {
        private final Map<FeatureKey<?>, Object> store;

        SealedBlackboard(Map<FeatureKey<?>, Object> store) {
            this.store = store;
        }

        public <T> T get(FeatureKey<T> key) {
            Object value = store.get(key);
            if (value == null) {
                throw new IllegalStateException(
                    "Feature not present: " + key.name());
            }
            return key.castValue(value);
        }

        public boolean has(FeatureKey<?> key) {
            return store.containsKey(key);
        }

        public Set<FeatureKey<?>> keys() {
            return store.keySet();
        }
    }
}
