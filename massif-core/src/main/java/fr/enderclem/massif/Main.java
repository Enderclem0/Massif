package fr.enderclem.massif;

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
import fr.enderclem.massif.primitives.RegionCoord;
import java.util.List;

public final class Main {

    public static void main(String[] args) {
        List<Layer> layers = List.of(
            new VoronoiZonesLayer(),
            new HandshakeLayer(),
            new BaseNoiseLayer(),
            new RidgeDlaLayer(),
            new TerrainCompositionLayer(),
            new GradientLayer());

        TerrainFramework fw = new TerrainFramework(layers);

        System.out.println("Compiled DAG order:");
        for (int i = 0; i < fw.schedule().order().size(); i++) {
            Layer l = fw.schedule().order().get(i);
            System.out.printf("  %d. %-20s reads=%s writes=%s%n",
                i + 1, l.name(), keyNames(l.reads()), keyNames(l.writes()));
        }

        long seed = 1234L;
        RegionCoord coord = RegionCoord.of(0, 0);

        long t0 = System.nanoTime();
        RegionPlan plan = fw.generate(seed, coord);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("%nGenerated region %s at seed=%d in %d ms%n",
            coord, seed, ms);

        float[][] h = plan.get(Features.HEIGHTMAP);
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY, sum = 0f;
        int n = h.length * h[0].length;
        for (float[] row : h) {
            for (float v : row) {
                if (v < min) min = v;
                if (v > max) max = v;
                sum += v;
            }
        }
        System.out.printf("HEIGHTMAP  range=[%.3f, %.3f]  mean=%.3f%n",
            min, max, sum / n);
    }

    private static String keyNames(java.util.Set<?> keys) {
        if (keys.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object k : keys) {
            if (!first) sb.append(", ");
            sb.append(((fr.enderclem.massif.blackboard.FeatureKey<?>) k).name());
            first = false;
        }
        return sb.append("]").toString();
    }
}
