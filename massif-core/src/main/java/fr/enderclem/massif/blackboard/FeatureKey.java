package fr.enderclem.massif.blackboard;

import java.util.Objects;

/**
 * Typed, named handle for a blackboard entry.
 *
 * <p>Key names follow Minecraft-style namespacing: {@code namespace:local_name}.
 * The framework's own keys live under {@code core:}; third-party extensions use
 * their own namespaces. Identity is by full name (namespace and local combined);
 * {@code type} is metadata for runtime checks.
 *
 * <p>Key names and their semantic meanings are stability commitments. Once
 * published, a key's name and contract should not change; schema evolution
 * should be additive where possible and versioned (via a new key) where not.
 */
public final class FeatureKey<T> {

    private final String name;
    private final String namespace;
    private final String localName;
    private final Class<T> type;

    private FeatureKey(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        int colon = name.indexOf(':');
        if (colon <= 0 || colon >= name.length() - 1 || name.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException(
                "FeatureKey name must be 'namespace:local_name' with exactly one colon, got '"
                    + name + "'");
        }
        this.name = name;
        this.namespace = name.substring(0, colon);
        this.localName = name.substring(colon + 1);
        this.type = type;
    }

    public static <T> FeatureKey<T> of(String name, Class<T> type) {
        return new FeatureKey<>(name, type);
    }

    public String name() { return name; }
    public String namespace() { return namespace; }
    public String localName() { return localName; }
    public Class<T> type() { return type; }

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
