package fr.enderclem.massif.tests;

/**
 * Zero-dependency test runner for the required tests under scope.md §Required
 * Tests. Each test exits with 0 on pass and non-zero on fail; this runner
 * aggregates and exits non-zero if any failed.
 *
 * <p>Run with: {@code mvn -q -pl massif-core exec:java
 *   -Dexec.mainClass=fr.enderclem.massif.tests.TestRunner}.
 */
public final class TestRunner {

    public static void main(String[] args) {
        System.out.println("Required tests (massif core):");
        int fails = 0;
        fails += SeamEliminationTest.run();
        fails += AggregateIdentityTest.run();
        fails += DepthEnforcementTest.run();
        fails += DensityUniformityTest.run();
        fails += ParallelDeterminismTest.run();

        if (fails == 0) {
            System.out.println("All tests passed.");
            System.exit(0);
        } else {
            System.err.println(fails + " test(s) failed.");
            System.exit(1);
        }
    }
}
