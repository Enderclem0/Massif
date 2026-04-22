package fr.enderclem.massif.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordered, immutable registry of {@link ZoneType}s. The slot order is the
 * source of truth for the integer {@code id} space used by the rest of the
 * framework: {@code registry.get(id)} must return a type with {@code id ==
 * id}. Worlds configure a registry once (via {@link Massif#framework}) and
 * publish it on the blackboard under {@link MassifKeys#ZONE_REGISTRY} so
 * every consumer agrees on what integer ids mean.
 */
public final class ZoneTypeRegistry {

    private final List<ZoneType> types;
    private final Map<String, ZoneType> byName;

    public ZoneTypeRegistry(List<ZoneType> types) {
        this.types = List.copyOf(types);
        this.byName = new HashMap<>();
        for (int i = 0; i < this.types.size(); i++) {
            ZoneType t = this.types.get(i);
            if (t.id() != i) {
                throw new IllegalArgumentException(
                    "ZoneType at slot " + i + " has id " + t.id() + "; id must match slot");
            }
            if (byName.put(t.name(), t) != null) {
                throw new IllegalArgumentException("Duplicate zone-type name: " + t.name());
            }
        }
    }

    public int size() {
        return types.size();
    }

    public ZoneType get(int id) {
        return types.get(id);
    }

    public ZoneType get(String name) {
        ZoneType t = byName.get(name);
        if (t == null) throw new IllegalArgumentException("Unknown zone-type name: " + name);
        return t;
    }

    public List<ZoneType> all() {
        return types;
    }

    /**
     * Reasonable starting registry: ocean, plains, mountain, desert, tundra.
     * Colours are chosen to give adjacent types enough contrast that the
     * visualiser's default zone view is readable without extra config.
     */
    public static ZoneTypeRegistry defaultRegistry() {
        return new ZoneTypeRegistry(List.of(
            new ZoneType(0, "ocean",    0x2060A0),
            new ZoneType(1, "plains",   0x8FBC8F),
            new ZoneType(2, "mountain", 0x8B8974),
            new ZoneType(3, "desert",   0xEDC9AF),
            new ZoneType(4, "tundra",   0xE0E8F0)
        ));
    }
}
