package fr.enderclem.massif.primitives;

public record RegionCoord(int rx, int rz) {
    public static RegionCoord of(int rx, int rz) {
        return new RegionCoord(rx, rz);
    }
}
