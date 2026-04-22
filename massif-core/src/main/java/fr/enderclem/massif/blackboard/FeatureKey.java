package fr.enderclem.massif.blackboard;

import java.util.Objects;

/**
 * Typed, named handle for a blackboard entry. Identity is by {@code name};
 * {@code type} is metadata for runtime checks and UI. Subject to rework in
 * Phase 1 when the namespaced {@code "core:xxx"} key scheme and registry land.
 */
public final class FeatureKey<T> {

    private final String name;
    private final Class<T> type;

    private FeatureKey(String name, Class<T> type) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public static <T> FeatureKey<T> of(String name, Class<T> type) {
        return new FeatureKey<>(name, type);
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    @SuppressWarnings("unchecked")
    T castValue(Object value) {
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                "Feature '" + name + "' expected " + type.getName()
                    + " but got " + value.getClass().getName());
        }
        return (T) value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FeatureKey<?> other && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "FeatureKey[" + name + ":" + type.getSimpleName() + "]";
    }
}
