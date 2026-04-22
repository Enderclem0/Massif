package fr.enderclem.massif.tests;

/**
 * Zero-dependency test runner. Each test returns 0 on pass and non-zero on
 * fail; the runner aggregates and exits non-zero if any failed.
 *
 * <p>Run with: {@code mvn -q -pl massif-core exec:java
 *   -Dexec.mainClass=fr.enderclem.massif.tests.TestRunner}
 * (or {@code java -cp .../target/classes fr.enderclem.massif.tests.TestRunner}).
 */
public final class TestRunner {

    private TestRunner() {}

    public static void main(String[] args) {
        System.out.println("Massif tests:");
        int fails = 0;
        fails += FeatureKeyTest.run();
        fails += ScheduleTest.run();

        if (fails == 0) {
            System.out.println("All tests passed.");
            System.exit(0);
        } else {
            System.err.println(fails + " test(s) failed.");
            System.exit(1);
        }
    }
}
