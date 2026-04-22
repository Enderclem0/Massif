package fr.enderclem.massif.blackboard;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared, typed key/value store populated by producers during generation.
 * Write-once: a given {@link FeatureKey} may be written at most once per
 * blackboard instance. Sealed via {@link #seal()} once the pipeline has
 * finished, after which the returned {@link Sealed} view is immutable and
 * safe to hand to consumers.
 */
public final class Blackboard {

    private final Map<FeatureKey<?>, Object> store = new HashMap<>();
    private boolean sealed = false;

    public <T> void put(FeatureKey<T> key, T value) {
        if (sealed) {
            throw new IllegalStateException("Blackboard is sealed");
        }
        if (store.containsKey(key)) {
            throw new IllegalStateException("Already written: " + key.name());
        }
        store.put(key, value);
    }

    public <T> T get(FeatureKey<T> key) {
        Object value = store.get(key);
        if (value == null) {
            throw new IllegalStateException("Not present on blackboard: " + key.name());
        }
        return key.castValue(value);
    }

    public boolean has(FeatureKey<?> key) {
        return store.containsKey(key);
    }

    public Set<FeatureKey<?>> keys() {
        return Collections.unmodifiableSet(store.keySet());
    }

    public Sealed seal() {
        this.sealed = true;
        return new Sealed(Map.copyOf(store));
    }

    /** Immutable snapshot returned after {@link Blackboard#seal()}. */
    public static final class Sealed {
        private final Map<FeatureKey<?>, Object> store;

        Sealed(Map<FeatureKey<?>, Object> store) {
            this.store = store;
        }

        public <T> T get(FeatureKey<T> key) {
            Object value = store.get(key);
            if (value == null) {
                throw new IllegalStateException("Not present: " + key.name());
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
