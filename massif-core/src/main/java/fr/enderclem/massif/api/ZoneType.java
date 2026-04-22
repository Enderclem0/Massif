package fr.enderclem.massif.api;

import java.util.Objects;

/**
 * One zone type in a {@link ZoneTypeRegistry}.
 *
 * <p>{@code id} is a stable integer used throughout the framework (in
 * {@link ZoneField}, in zone-weight distributions, in the zone graph) so hot
 * paths don't have to compare strings. {@code name} is the human-readable /
 * machine-identifier form used in keys and tooling (lowercase, no spaces
 * recommended). {@code displayColour} is a packed RGB value (0xRRGGBB) used
 * by default renderers; consumers that want their own palette can ignore it.
 *
 * <p>The old hard-coded {@code ZoneKind} enum was replaced by this registry
 * type so third parties can add their own zones without editing core code
 * (design doc §Zone system, "generalise zone types").
 */
public record ZoneType(int id, String name, int displayColour) {

    public ZoneType {
        Objects.requireNonNull(name, "name");
        if (id < 0) throw new IllegalArgumentException("id must be non-negative, got " + id);
        if (name.isBlank()) throw new IllegalArgumentException("name must be non-blank");
    }
}
