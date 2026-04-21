package fr.enderclem.massif.tests;

import fr.enderclem.massif.api.RegionPlan;
import fr.enderclem.massif.api.TerrainFramework;
import fr.enderclem.massif.dag.Layer;
import fr.enderclem.massif.layers.BaseNoiseLayer;
import fr.enderclem.massif.layers.Features;
import fr.enderclem.massif.layers.GradientLayer;
import fr.enderclem.massif.layers.TerrainCompositionLayer;
import fr.enderclem.massif.layers.dla.RidgeDlaLayer;
import fr.enderclem.massif.layers.voronoi.HandshakeLayer;
import fr.enderclem.massif.layers.voronoi.VoronoiZonesLayer;
import fr.enderclem.massif.layers.voronoi.ZoneKind;
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.List;

/** Dump the ridge mask and the zones grid as ASCII for one region and one seed. */
public final class MaskDump {

    public static void main(String[] args) {
        long seed = Long.parseLong(args.length > 0 ? args[0] : "7777");
        int rx = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        int rz = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        int stride = args.length > 3 ? Integer.parseInt(args[3]) : 4;

        List<Layer> layers = List.of(
            new VoronoiZonesLayer(), new HandshakeLayer(),
            new BaseNoiseLayer(), new RidgeDlaLayer(),
            new TerrainCompositionLayer(), new GradientLayer());
        TerrainFramework fw = new TerrainFramework(layers);
        RegionPlan plan = fw.generate(seed, RegionCoord.of(rx, rz));
        byte[][] mask = plan.get(Features.RIDGE_MASK);
        int[][] zones = plan.get(Features.ZONES);
        int mountainKind = ZoneKind.MOUNTAINS.ordinal();
        int size = mask.length;

        int aggCount = 0;
        int mountainCount = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                if (mask[z][x] != 0) aggCount++;
                if (zones[z][x] == mountainKind) mountainCount++;
            }
        }
        // Connectivity sanity: for each aggregate cell, count how many of its
        // 4-neighbours are also aggregate. DLA aggregates should be connected;
        // lots of isolated cells indicate a walker-algorithm bug.
        int isolated = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                if (mask[z][x] == 0) continue;
                boolean hasNeighbour = false;
                if (x > 0 && mask[z][x - 1] != 0) hasNeighbour = true;
                if (x < size - 1 && mask[z][x + 1] != 0) hasNeighbour = true;
                if (z > 0 && mask[z - 1][x] != 0) hasNeighbour = true;
                if (z < size - 1 && mask[z + 1][x] != 0) hasNeighbour = true;
                if (!hasNeighbour) isolated++;
            }
        }
        System.out.printf("seed=%d region=(%d,%d) size=%d  aggregate=%d  isolated=%d (%.1f%%)  mountain cells=%d  (%.2f%% / %.2f%% of mountain)%n",
            seed, rx, rz, size, aggCount, isolated, 100.0 * isolated / Math.max(1, aggCount), mountainCount,
            100.0 * aggCount / (size * size),
            100.0 * aggCount / Math.max(1, mountainCount));

        System.out.println("Legend: # aggregate, M mountain non-agg, . other zone");
        for (int z = 0; z < size; z += stride) {
            StringBuilder row = new StringBuilder(size / stride + 2);
            for (int x = 0; x < size; x += stride) {
                char c = '.';
                if (zones[z][x] == mountainKind) c = 'M';
                if (mask[z][x] != 0) c = '#';
                row.append(c);
            }
            System.out.println(row);
        }
    }
}
