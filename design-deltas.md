# Design Deltas

Running log of decisions made during implementation that diverge from,
extend, or defer parts of `massif-design-rebuild.md`. The design doc is
the north star; this file is where reality parted from it — or got more
concrete than it.

Each entry: what we did, why, and how it relates to the design doc. When
a decision gets revised later, the previous entry is marked
**[superseded]** and the new one references it.

---

## Blackboard & pipeline

### P1 — Runtime-checked read/write declarations
Producers declare `reads()` / `writes()`; `ExecutionContext.read` / `write`
throws if a producer touches a key outside its declarations. Design doc
mentions topological sort but doesn't spell out this enforcement — we added
it so schedule correctness can never silently drift from the declaration.

### P1 — `Blackboard` + `Blackboard.Sealed` split
Mutable during generation, sealed (immutable snapshot) afterwards for
consumer access. Design doc describes publication/consumption but doesn't
formalize the seal. Producers' writes are rejected once sealed.

### P1 — Topological sort with declaration-order tiebreak
Kahn's algorithm with a FIFO ready queue keeps the ordering deterministic
when multiple producers have no inter-dependency. Design doc requires
determinism but doesn't specify tiebreak policy.

### P4 — `WorldWindow` as config threaded through producer constructors
`WorldWindow(centerX, centerZ, size)` is passed to window-bounded producers
at framework build time (not published on the blackboard). Each zoom/pan
rebuilds the framework with a new window. Design doc implies a "world
spec" config but doesn't formalize the shape — we chose constructor
threading because the visualiser rebuilds the framework anyway; a
`core:world_window` key can be added later if cross-phase reading becomes
useful.

---

## Zone system (Layer 1)

### P3 — `ZoneType` carries a `displayColour`
Design doc says "ordered registry of zone types"; we put an RGB int on
each type so the default visualiser can render without a separate palette
config. Cosmetic only — consumers that want their own palette just
ignore the field. Design doc is silent on this.

### P3 — `BorderField.sampleAt()` returns all three primitives at once
Design doc lists `core:border_distance(x, z)`, `core:border_normal(x, z)`,
`core:border_pairing(x, z)` as three keys. We collapsed into one
`core:border_field` whose `sampleAt(x, z)` returns a `BorderSample` with
distance, normal and `(nearType, otherType)`. Rationale: all three come
from the same two-nearest-seeds lookup; one call does the work of three.
If external consumers later need the split keys we can add them as thin
wrappers — additive.

### P3 — `ZoneGraph` scoped to a `WorldWindow`, not globally enumerable
Design doc phrases `core:zone_graph` as "full Voronoi graph". In practice
the world is infinite (jittered seeds on an unbounded grid), so "full"
is not possible; we publish the cells whose seeds fall in the current
window plus a one-region halo. Point-query users use `ZoneField` /
`BorderField` instead, which remain unbounded. Design doc's intent is
preserved — enumeration just has a scope.

### P3 — `ZoneSeedPool` abstraction
Not in design doc. Introduced as the single point of variation for seed
source (jittered vs Lloyd-relaxed), so field / border / graph producers
don't each re-implement the decision. Two impls:
`JitteredZoneSeedPool` (on-demand, unbounded) and
`RelaxedZoneSeedPool` (precomputed over a window, falls back to jittered
outside it).

### P3 — Lloyd relaxation bounded to a window
Design doc states "Voronoi cells with Lloyd relaxation" without scope.
Unbounded Lloyd requires seeds arbitrarily far away (each iteration widens
the dependency radius), so we run it only over the visualiser's current
window + one-cell halo. Out-of-window queries fall back to jittered. This
is a pragmatic scope restriction — not a semantic divergence.

### P3 — Zone-graph adjacency by grid sampling, not true Voronoi
Adjacencies are detected by sampling a dense grid and recording edges
wherever adjacent pixels have different nearest seeds. Catches every
edge longer than one pixel, misses very short ones. Placeholder until a
proper Delaunay / half-plane pass lands. Design doc doesn't specify an
adjacency algorithm.

---

## Structural plan (Layer 2)

### P4a — Mountain-cluster spine: spanning tree, tree-centre
Design doc lists "orientation (derived from cluster shape), peak count
hint". We iterated:
1. [superseded] PCA major axis + mean-position centroid — broke on
   non-convex shapes (centroid outside the cluster for C/U/rings, straight
   line for L-shapes).
2. [superseded] Graph-diameter polyline + middle-of-path representative —
   fixed C/U/rings and L-shapes but collapsed Y clusters (third arm
   disappeared).
3. **Current:** spanning tree (BFS from the diameter midpoint) + tree-
   centre representative. Y clusters keep all three arms; the
   representative is always inside the cluster; linear and curved
   clusters degenerate to the earlier polyline behaviour.

This is richer than the design doc asks for — "orientation" becomes a
tree rather than a single angle. Downstream mountain-technique producers
(Phase 6) will consume the spine tree when deciding where to place ridges.

### P4a — Mountain-cluster `id` stability
Cluster id = lowest member cell id. Stable for a fixed `(seed, window)`;
may shift when the window changes (a new "lowest" cell enters via pan /
zoom). Design doc requires a "stable ID" but doesn't address how window
reconfiguration interacts with it. Full stability arrives when clusters
are computed over a globally enumerable graph (Phase 4b / later).

---

## Visualisation

### P2 — Visualiser imports restricted to `api.*` + `blackboard.*`
Hard discipline enforced by package layout: if the visualiser can
display something, the framework published it. Design doc describes this
as "architectural discipline"; we enforce it by import review on every
commit.

### P4 — Zoom / pan rebuilds the framework
Each zoom or pan constructs a fresh `MassifFramework.of(...)` with the
new `WorldWindow` and re-runs the pipeline. Fast enough at default sizes
(~100 ms); degrades gracefully to ~500-800 ms at the 2048-block window cap.
Not in design doc — pragmatic choice for now; a future optimisation could
cache unbounded producers (`ZoneField`, `BorderField`) across window
changes.

### P2 — Saved-seed TSV at `~/.massif/seeds.tsv`
Zero-dep plain-text file, one `label\tseed` per line. Design doc is silent
on visualiser persistence; we added this because typing 18-digit longs is
painful. Editable by hand.

---

## Deferred / not yet implemented from the design doc

Tracking what's spec'd but not built so nothing falls off:

- `core:zone_weights(x, z)` — weighted zone distribution. Currently
  one-hot (dominant type only). Blocks: composition stage (Phase 7+).
- `core:drainage_basins`, `core:drainage_graph`, `core:confluence_points`
  — need per-cell elevation (Phase 5 coarse hydrology).
- `core:lakes`, `core:river_graph`, `core:water_surface` — Phase 5.
- `core:set_pieces` and friends — post-hydrology.
- `core:height(x, z)` (point-queryable, composed) — replaces the Phase 2
  `core:heightmap` placeholder. Arrives with the composition stage.
- Point inspector, key catalog UI, dependency-graph visualiser — only the
  minimal `Catalog` stdout listing so far.
- Serialisation format — deferred until schemas stabilise.
