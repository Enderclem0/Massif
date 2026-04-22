package fr.enderclem.massif;

/**
 * CLI entry point. Phase 0 of the rebuild has cleared the previous region-scoped
 * / DLA pipeline; subsequent phases will wire the blackboard-centric framework
 * described in {@code massif-design-rebuild.md} back in.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        System.out.println("Massif: Phase 0 — framework rebuild in progress.");
        System.out.println("See massif-design-rebuild.md for the new design.");
    }
}
