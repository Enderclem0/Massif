package fr.enderclem.massif.tests;

/**
 * Minimal zero-dependency assertion helper for the required-tests suite.
 * Mirrors the surface JUnit's AssertionError would throw so test output is
 * comparably readable. Lives under main/java so tests run via the same
 * {@code exec:java} machinery as everything else in the project — keeping with
 * scope.md's zero-external-deps philosophy.
 */
public final class TestAssert {

    private TestAssert() {}

    public static void assertTrue(boolean cond, String message) {
        if (!cond) throw new AssertionError(message);
    }

    public static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " — expected " + expected + " got " + actual);
        }
    }

    public static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " — expected " + expected + " got " + actual);
        }
    }

    public static void assertEquals(double expected, double actual, double tol, String message) {
        if (Math.abs(expected - actual) > tol) {
            throw new AssertionError(message + " — expected " + expected
                + " got " + actual + " (tol=" + tol + ")");
        }
    }

    public static <T> void assertThrows(Class<? extends Throwable> expected, Runnable r, String message) {
        try {
            r.run();
        } catch (Throwable t) {
            if (expected.isInstance(t)) return;
            throw new AssertionError(message + " — expected " + expected.getName()
                + " got " + t.getClass().getName() + " (" + t.getMessage() + ")");
        }
        throw new AssertionError(message + " — no exception thrown; expected " + expected.getName());
    }
}
