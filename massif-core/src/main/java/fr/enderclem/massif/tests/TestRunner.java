package fr.enderclem.massif.tests;

/**
 * Zero-dependency test runner. The old region-scoped test suite was removed in
 * the Phase 0 rebuild; new tests will be added as each phase of the rebuild
 * lands. For now the runner is a no-op placeholder that exits 0.
 */
public final class TestRunner {

    private TestRunner() {}

    public static void main(String[] args) {
        System.out.println("No tests registered yet (Phase 0 rebuild).");
    }
}
