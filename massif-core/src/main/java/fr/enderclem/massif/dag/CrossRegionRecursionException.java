package fr.enderclem.massif.dag;

/**
 * Thrown when a border-aware layer attempts to trigger a cross-region read at
 * recursion depth greater than 1 — the enforcement mechanism for scope.md
 * invariant 2 (Two-Layer Cross-Region Agreement).
 *
 * <p>Depth 1 means: region R reads neighbour N's border-strip output. Within
 * that read, N is forbidden from reading any further neighbour (including R).
 * If N attempts such a read, this exception is raised so the architectural
 * breach is visible immediately rather than producing a cascade freeze.
 */
public final class CrossRegionRecursionException extends RuntimeException {

    public CrossRegionRecursionException(int attemptedDepth) {
        super("Cross-region recursion depth " + attemptedDepth
            + " exceeds maximum 1 — invariant 2 breach. A neighbour-side compute "
            + "tried to read another neighbour, which would reintroduce the cascade trap.");
    }
}
