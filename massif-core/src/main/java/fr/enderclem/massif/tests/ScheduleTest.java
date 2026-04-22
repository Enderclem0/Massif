package fr.enderclem.massif.tests;

import fr.enderclem.massif.api.MassifFramework;
import fr.enderclem.massif.blackboard.Blackboard;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import fr.enderclem.massif.pipeline.Schedule;
import java.util.List;
import java.util.Set;

/**
 * Covers pipeline scheduling and execution semantics: topological ordering,
 * cycle / missing-producer / double-writer detection, and the undeclared
 * read/write guards inside {@link ExecutionContext}.
 */
public final class ScheduleTest {

    private static final FeatureKey<Integer> A = FeatureKey.of("core:a", Integer.class);
    private static final FeatureKey<Integer> B = FeatureKey.of("core:b", Integer.class);
    private static final FeatureKey<Integer> C = FeatureKey.of("core:c", Integer.class);
    private static final FeatureKey<Integer> UNDECLARED = FeatureKey.of("core:undeclared", Integer.class);

    private ScheduleTest() {}

    public static int run() {
        try {
            topologicalOrderRespectsDependencies();
            duplicateWriterRejected();
            missingProducerRejected();
            cycleRejected();
            undeclaredReadRejected();
            undeclaredWriteRejected();
            producerMissingDeclaredWriteRejected();
            valuesPropagateThroughPipeline();
            System.out.println("  ScheduleTest: OK");
            return 0;
        } catch (Throwable t) {
            System.err.println("  ScheduleTest FAILED: " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    private static void topologicalOrderRespectsDependencies() {
        // C depends on B depends on A; declared in reverse order to exercise sorting.
        Producer pC = fixed("pC", Set.of(C), Set.of(B), ctx -> ctx.write(C, ctx.read(B) + 1));
        Producer pB = fixed("pB", Set.of(B), Set.of(A), ctx -> ctx.write(B, ctx.read(A) + 1));
        Producer pA = fixed("pA", Set.of(A), Set.of(), ctx -> ctx.write(A, 1));

        Schedule s = Schedule.compile(List.of(pC, pB, pA));
        List<Producer> order = s.order();
        TestAssert.assertTrue(order.indexOf(pA) < order.indexOf(pB), "A before B");
        TestAssert.assertTrue(order.indexOf(pB) < order.indexOf(pC), "B before C");
    }

    private static void duplicateWriterRejected() {
        Producer p1 = fixed("p1", Set.of(A), Set.of(), ctx -> ctx.write(A, 1));
        Producer p2 = fixed("p2", Set.of(A), Set.of(), ctx -> ctx.write(A, 2));
        TestAssert.assertThrows(IllegalStateException.class,
            () -> Schedule.compile(List.of(p1, p2)), "duplicate writer");
    }

    private static void missingProducerRejected() {
        Producer reader = fixed("reader", Set.of(B), Set.of(A), ctx -> ctx.write(B, ctx.read(A)));
        TestAssert.assertThrows(IllegalStateException.class,
            () -> Schedule.compile(List.of(reader)), "missing writer for A");
    }

    private static void cycleRejected() {
        Producer pA = fixed("pA", Set.of(A), Set.of(B), ctx -> {});
        Producer pB = fixed("pB", Set.of(B), Set.of(A), ctx -> {});
        TestAssert.assertThrows(IllegalStateException.class,
            () -> Schedule.compile(List.of(pA, pB)), "cycle");
    }

    private static void undeclaredReadRejected() {
        Producer pA = fixed("pA", Set.of(A), Set.of(), ctx -> ctx.write(A, 1));
        Producer pB = fixed("pB", Set.of(B), Set.of(A), ctx -> {
            // Reads UNDECLARED (not in reads set) — must throw.
            ctx.read(UNDECLARED);
            ctx.write(B, 0);
        });
        // Schedule compile requires UNDECLARED to have a writer or to be undeclared in reads;
        // since pB's reads() returns only {A}, compile succeeds. Failure is at runtime.
        Producer pU = fixed("pU", Set.of(UNDECLARED), Set.of(), ctx -> ctx.write(UNDECLARED, 0));
        MassifFramework fw = MassifFramework.of(List.of(pA, pU, pB));
        TestAssert.assertThrows(IllegalStateException.class,
            () -> fw.generate(0L), "undeclared read");
    }

    private static void undeclaredWriteRejected() {
        Producer bad = fixed("bad", Set.of(A), Set.of(), ctx -> {
            ctx.write(A, 1);
            ctx.write(UNDECLARED, 2); // not in writes()
        });
        Producer u = fixed("u", Set.of(UNDECLARED), Set.of(), ctx -> ctx.write(UNDECLARED, 0));
        MassifFramework fw = MassifFramework.of(List.of(bad, u));
        TestAssert.assertThrows(IllegalStateException.class,
            () -> fw.generate(0L), "undeclared write");
    }

    private static void producerMissingDeclaredWriteRejected() {
        Producer lazy = fixed("lazy", Set.of(A, B), Set.of(), ctx -> ctx.write(A, 1)); // forgets B
        MassifFramework fw = MassifFramework.of(List.of(lazy));
        TestAssert.assertThrows(IllegalStateException.class,
            () -> fw.generate(0L), "missing declared write");
    }

    private static void valuesPropagateThroughPipeline() {
        Producer pA = fixed("pA", Set.of(A), Set.of(), ctx -> ctx.write(A, 10));
        Producer pB = fixed("pB", Set.of(B), Set.of(A), ctx -> ctx.write(B, ctx.read(A) * 2));
        Producer pC = fixed("pC", Set.of(C), Set.of(A, B), ctx -> ctx.write(C, ctx.read(A) + ctx.read(B)));

        Blackboard.Sealed out = MassifFramework.of(List.of(pA, pB, pC)).generate(0L);
        TestAssert.assertEquals(10, out.get(A), "A");
        TestAssert.assertEquals(20, out.get(B), "B");
        TestAssert.assertEquals(30, out.get(C), "C");
    }

    // --- helpers -----------------------------------------------------------

    @FunctionalInterface
    private interface ComputeBody {
        void run(ExecutionContext ctx);
    }

    private static Producer fixed(String name,
                                  Set<FeatureKey<?>> writes,
                                  Set<FeatureKey<?>> reads,
                                  ComputeBody body) {
        return new Producer() {
            @Override public String name() { return name; }
            @Override public Set<FeatureKey<?>> writes() { return writes; }
            @Override public Set<FeatureKey<?>> reads() { return reads; }
            @Override public void compute(ExecutionContext ctx) { body.run(ctx); }
        };
    }
}
