package fr.enderclem.massif;

import fr.enderclem.massif.api.Massif;
import fr.enderclem.massif.api.MassifFramework;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.pipeline.Catalog;

/**
 * CLI smoke entry point. Shows the current schedule and generates one
 * blackboard so the walking-skeleton pipeline runs end to end without
 * spinning up the visualizer.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1234L;

        MassifFramework fw = Massif.defaultFramework();
        System.out.print(Catalog.scheduleListing(fw.schedule()));

        long t0 = System.nanoTime();
        Blackboard.Sealed board = fw.generate(seed);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("Generated blackboard for seed=%d in %d ms%n", seed, ms);
        System.out.print(Catalog.blackboardListing(board));

        float[][] map = board.get(MassifKeys.HEIGHTMAP);
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY, sum = 0f;
        int n = map.length * map[0].length;
        for (float[] row : map) {
            for (float v : row) {
                if (v < min) min = v;
                if (v > max) max = v;
                sum += v;
            }
        }
        System.out.printf("core:heightmap range=[%.3f, %.3f]  mean=%.3f%n",
            min, max, sum / n);
    }
}
