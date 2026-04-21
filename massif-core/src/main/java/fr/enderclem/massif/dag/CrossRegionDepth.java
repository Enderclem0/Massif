package fr.enderclem.massif.dag;

/**
 * Thread-local counter enforcing scope.md invariant 2's depth-1 bound on
 * cross-region reads.
 *
 * <p>Thread-local rather than process-global: concurrent region generation on
 * different threads must not interfere. Each worker thread has its own counter.
 *
 * <p>Usage pattern (always paired):
 * <pre>
 *     CrossRegionDepth.enter();
 *     try {
 *         // compute neighbour-side data
 *     } finally {
 *         CrossRegionDepth.exit();
 *     }
 * </pre>
 */
public final class CrossRegionDepth {

    private static final int MAX_DEPTH = 1;

    // int[1] wrapper avoids boxing on every enter/exit call.
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[] { 0 });

    private CrossRegionDepth() {}

    public static int current() {
        return DEPTH.get()[0];
    }

    /**
     * Increment the counter. Throws if the increment would exceed {@link #MAX_DEPTH}.
     * Callers must call {@link #exit} in a finally block to pair with this.
     */
    public static void enter() {
        int[] cell = DEPTH.get();
        int next = cell[0] + 1;
        if (next > MAX_DEPTH) {
            throw new CrossRegionRecursionException(next);
        }
        cell[0] = next;
    }

    public static void exit() {
        int[] cell = DEPTH.get();
        if (cell[0] <= 0) {
            throw new IllegalStateException("CrossRegionDepth.exit without matching enter");
        }
        cell[0]--;
    }
}
