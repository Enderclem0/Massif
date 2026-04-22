package fr.enderclem.massif;

import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.Catalog;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.MassifFramework;
import fr.enderclem.massif.pipeline.Producer;
import java.util.Set;

/**
 * Phase 1 smoke demo: two stub producers wired into the pipeline so the
 * schedule compilation, execution, and blackboard printout all exercise
 * end-to-end. Real producers land in later phases (zones, structural plan,
 * hydrology, techniques, composition).
 */
public final class Main {

    private static final FeatureKey<Long> DEMO_SEED = FeatureKey.of("core:demo_seed", Long.class);
    private static final FeatureKey<String> DEMO_GREETING = FeatureKey.of("core:demo_greeting", String.class);

    private Main() {}

    public static void main(String[] args) {
        Producer seedProducer = new Producer() {
            @Override public String name() { return "demo.seed"; }
            @Override public Set<FeatureKey<?>> writes() { return Set.of(DEMO_SEED); }
            @Override public void compute(ExecutionContext ctx) { ctx.write(DEMO_SEED, ctx.seed()); }
        };
        Producer greetingProducer = new Producer() {
            @Override public String name() { return "demo.greeting"; }
            @Override public Set<FeatureKey<?>> writes() { return Set.of(DEMO_GREETING); }
            @Override public Set<FeatureKey<?>> reads() { return Set.of(DEMO_SEED); }
            @Override public void compute(ExecutionContext ctx) {
                ctx.write(DEMO_GREETING, "Massif phase 1, seed=" + ctx.read(DEMO_SEED));
            }
        };

        MassifFramework fw = MassifFramework.of(greetingProducer, seedProducer);
        System.out.print(Catalog.scheduleListing(fw.schedule()));

        Blackboard.Sealed board = fw.generate(1234L);
        System.out.print(Catalog.blackboardListing(board));
        System.out.println("greeting: " + board.get(DEMO_GREETING));
    }
}
