# Massif — Modular Procedural Terrain Framework

**Scope Document v4**

---

## Project Vision

Build a region-planning terrain generation framework inspired by Hytale's WorldGen V2, delivered in two phases: **first as a standalone, engine-agnostic library with interactive visualizer** (open-source-ready), **then as a Minecraft (NeoForge/Fabric) mod integration**. The framework centers on four core innovations that vanilla Minecraft genuinely lacks: region-level hydrological planning, context-aware structure placement driven by precomputed semantic graphs, hand-authored landmarks (set pieces) integrated procedurally, and rich shared structural planning above per-region stochastic detail.

The project is structured so that Phase 1 produces a valuable artifact on its own — a reusable procedural terrain library with visualizer — and Phase 2 is a commitment made only after Phase 1 validates the approach.

---

## Core Technical Pipeline

Six-stage deterministic pipeline, with every stage a pure function of `(seed, regionX, regionZ)` (or globally pure for pre-region computations):

### 0. Global Structural Precomputation

Runs once at world generation start, before any region-level work. Produces world-scope shared data:

- **Cluster IDs** for contiguous mountain-zone clusters (via global flood-fill of the zone graph)
- **Full drainage graph** tracing water flow from every mountain zone to its terminal ocean, with confluence zone designations
- **Set piece placement plan** — global Poisson-disk sampling of landmark locations with spawn-requirement filtering, producing the list of set piece instances with their coordinates, radii, rotations, and instance seeds

This is the "compute structure globally, do stochastic detail locally" architectural commitment in concrete form. It is bounded, deterministic, and cached for the world lifetime.

### 1. Voronoi Macro-Zones + Handshake Graph + Structural Plan

Divide the world into geographical zones (mountain country, plains, ocean, etc.) at ~256–512 block resolution. Includes Lloyd's relaxation, distance-to-border blending, deterministic tie-breaking via global seed ID comparison, and three major outputs:

- **Zone classification** with set piece override: within a set piece's footprint, zone type is determined by the set piece definition rather than Voronoi lookup. Transition zones use smoothstep blending.
- **Handshake graph** defining deterministic cross-region connection points for rivers and ridges.
- **Structural Plan metadata**: mountain-range orientations for contiguous mountain-zone clusters (from precomputed cluster IDs), drainage graph extraction for the local region neighborhood, confluence point placement within confluence zones, and river width profiles along drainage paths.

### 2. DLA Mountain Spines

Fractal ridge networks grown within mountain zones via Diffusion-Limited Aggregation, seeded and constrained by Structural Plan range orientations. Post-processed with downstream weighting and multi-resolution blur. DLA does not grow inside set piece footprints; set pieces own their terrain geometry.

### 3. Inverted DLA Rivers

Dendritic river networks grown backward from ocean-border handshake nodes inland, terminating at mountain zones. DLA path selection is stochastic; river *widths* along paths are determined by the Structural Plan's drainage area computation, not by counting tributaries in DLA. Confluence zones have structured seeding (multiple inbound handshakes targeting a deterministic confluence point). Cascade set pieces impose required river paths through their footprints.

### 4. Fractal Noise with the Gradient Trick

Layered simplex/Perlin noise fills slopes and plains, with amplitude modulated by local terrain steepness. Noise parameters are zone-level properties, determined by zone type, ensuring consistency across region boundaries. Noise is suppressed or reduced inside set piece footprints where the SDF owns terrain shape.

### 5. Heightmap Composition with Set Piece SDFs

Final heightmap is composed by combining: Structural Plan elevation + DLA mountain contributions + river DLA carving + gradient-modulated fractal noise + set piece SDF contributions where applicable. Inside set piece footprints the SDF dominates; in transition zones, smooth interpolation between SDF and procedural composition; outside bounding radius, procedural composition alone.

### 6. Contextual Feature Placement

Queries the semantic graphs and fields to place features semantically: waterfalls where rivers meet cliffs, bridges where shores align, villages near rivers on flat ground, mountain passes at ridge saddles. Cross-region features are attributed by centroid — only the region containing the feature's centroid instantiates it. Proximity constraints use bounded-radius queries. Set piece definitions can declare associated structures (e.g., "ruined temple on volcano rim at 20% instance probability").

---

## Architectural Invariants

Four non-negotiable constraints that the codebase must enforce throughout:

### 1. Region Plan Purity

Region plans are pure functions of `(seed, regionX, regionZ)`. No mutable global state, no hidden dependencies, no RNG that isn't explicitly seeded and threaded. Same inputs must always produce identical outputs across threads, runs, and processes. Use `StrictMath` instead of `Math` for transcendental functions to guarantee cross-JVM determinism.

### 2. Two-Layer Cross-Region Agreement (replaces original DLA Handshake Invariant)

Cross-region agreement is achieved by two complementary mechanisms:

**Layer A — Rich shared structural planning.** The Structural Plan (stage 1) produces dense zone-level metadata — range orientations, drainage graphs, confluence points, river widths, set piece placements — that both sides of any seam compute identically from shared inputs. This pushes most of the "intent" of terrain to the shared level where disagreement is architecturally impossible.

**Layer B — Bounded-depth-1 cross-region reads.** For residual disagreement near boundaries from stochastic stages (DLA, distance transforms, kernel-based derivations), stages may read neighbor region data within a border strip of width determined by kernel footprint. Neighbor computations are cached and forbidden from reading back into the current region. Recursion terminates at depth 1; per-region work is bounded by a constant multiplier on single-region cost. Runtime checks enforce that cross-region recursion never exceeds depth 1.

Unbounded cascading reads remain forbidden. The original concern — "A needs B needs C needs D" — is prevented structurally, not by forbidding cross-region reads entirely.

### 3. Handshake Property Constraint

Properties exposed at handshake nodes must be either:

- **(a)** locally computable from the Voronoi zone graph alone, or
- **(b)** globally computable via an additive or monotonic propagation rule over the zone graph.

Properties requiring full graph traversal of downstream or upstream structure are forbidden at the handshake interface. This is why rivers use **drainage area** (additive: sum of upstream contributions) rather than Strahler ordering (non-additive). Drainage area is also more physically realistic — real river width correlates with discharge, which correlates with drainage area.

### 4. Border-Aware Stages

Any pipeline stage that produces a derived field via sampling within a kernel of radius R must declare this radius. The pipeline guarantees access to neighbor regions' source data within radius R before executing the stage via bounded-depth-1 cross-region reads. DLA walkers use unclamped positions with in-region-only aggregate writes and lifespan-only termination, eliminating edge-wall bias.

---

## Set Piece System (Hand-Authored Landmarks)

A registry of parameterized templates for geological features that appear procedurally but are individually designed. Set pieces are what separate "yet another procedural terrain mod" from "terrain with landmarks worth exploring."

### Structure

Each set piece declares:

- **SDF shape definition** — idealized geometry (cone minus caldera for volcanoes, elongated disc for valleys, stepped terrain for cascades)
- **Size range** — min/max radius, becomes per-instance parameter
- **Orientation model** — rotationally symmetric, preferred axis, or fully arbitrary
- **Spawn requirements** — compatible zone types, elevation constraints, minimum distance from other set pieces
- **Variation parameters** — per-instance randomization axes (crater rim irregularity, valley floor texture, cascade step count)
- **Associated content** — biome overrides within footprint, structure spawns tied to instances, vegetation overrides
- **Interaction declarations** — effects on other pipeline stages (cascade set pieces require river paths; volcanic set pieces may affect climate parameters)

### Placement

Global Poisson-disk sampling at Structural Plan time, with per-type density calibration. Common set pieces at ~1 per 10,000²-block area; rare set pieces at ~1 per 100,000²-block area. Placement is a pure function of world seed, enabling seed-stable speedrun routing and save-file stability.

### Initial Set Piece Library

Phase 1 targets ~6–8 set pieces covering major archetypes:

- **Mountain-ringed valley** — flat buildable terrain surrounded by peaks, often with a central lake
- **Volcanic island** — ocean-spawning cone with caldera
- **Waterfall cascade** — stepped terrain with forced river paths
- **Crater lake** — circular depression with water, typically in elevated terrain
- **Inland delta** — river splits into multiple channels across flat terrain
- **Fjord** — narrow ocean inlet between steep mountain walls
- **Mesa complex** — stepped plateau formations with connecting bridges
- **Atoll** — ring of small islands around a central lagoon

### Variation Strategy

Variety emerges from four sources: per-instance randomization via instance seeds, orientation and size parameters, biome context adapting visual identity (same valley template renders differently in taiga vs desert), and occasional compound set pieces combining multiple templates.

---

## Phase 1: Standalone Core + Visualizer

**Goal:** Engine-agnostic terrain generation library, open-source-ready, with interactive visualizer demonstrating every pipeline stage including set pieces.

### Stack

- **Core library:** Java 21 on JVM (for eventual Minecraft mod compatibility with no FFI overhead)
- **Visualizer:** Swing with `BufferedImage` + `Graphics2D` (zero external dependencies, ships with JDK)
- **Core has zero rendering dependencies and zero Minecraft dependencies**
- **Prototype philosophy:** Start with zero external dependencies. Add them only when they earn their place through concrete need.

### Deliverables

- Engine-agnostic region plan generator implementing all six pipeline stages plus global structural precomputation
- Set piece registry with 6–8 authored landmarks
- Queryable API producing both **sampled fields** and **structured graphs**
- Interactive visualizer with 2D layered views, 3D preview, live parameter tuning, and set piece inspection
- Serialization format with validated round-trip fidelity
- Benchmark harness targeting 30–50ms per region
- Comprehensive test suite (see Required Tests section)
- Documentation and example seeds

### Explicitly Out of Scope for Phase 1

- Node editor (sliders suffice for prototyping)
- Any Minecraft integration
- Authoring tools beyond visualizer controls

### Staged Delivery

| Stage | Description | Duration |
|-------|-------------|----------|
| 0 | Walking skeleton: primitives, pipeline abstractions, stub stages, Swing visualizer with grayscale heightmap | 1 week |
| 1 | Voronoi zones + handshake graph + Structural Plan + initial set piece infrastructure (2 initial set pieces) | 4 weeks |
| 2 | Fractal noise baseline | 1 week |
| 3 | Mountain DLA with blur, handshake constraints, border-strip mode, unclamped walkers, optimization pass | 4 weeks |
| 4 | Inverted DLA rivers with drainage area, confluence handling, cascade set piece integration | 3 weeks |
| 5 | Gradient trick integration, set piece SDF composition, final heightmap | 3 weeks |
| 6 | Contextual placement with centroid attribution and bounded proximity | 3 weeks |
| 7 | Set piece authoring library (additional 4–6 set pieces) | 2 weeks |
| 8 | 3D preview | 2 weeks |
| 9 | Serialization, benchmark harness, test suite completion | 2 weeks |

### Phase 1 Timeline

- **Full-time solo:** ~6 months
- **Nights-and-weekends:** ~12 months

### Required Tests (Regression Protection)

Tests that must pass before any Phase 1 stage is considered complete:

1. **Voronoi tie-breaking:** Adjacent regions classify 10,000 points along shared borders identically
2. **Cross-region continuity:** DLA aggregates match across all region boundaries with no cross-region reads detectable beyond depth 1
3. **Aggregate identity:** Full DLA and border-strip-mode DLA produce identical aggregates within the border strip
4. **Density uniformity:** Aggregate density near region edges (excluding handshake areas) statistically matches interior density
5. **Gradient seamlessness:** Finite-difference gradient matches across region borders
6. **Biome classification seamlessness:** Same biome selected from both sides of every border point sample
7. **Parallel execution determinism:** Sequential and heavily-parallel schedules produce byte-identical output
8. **Set piece placement determinism:** Same seed produces identical set piece placements and parameters
9. **Handshake property constraint:** All handshake-exposed properties verified as locally-computable or additively-propagatable
10. **Performance benchmark:** p95 region generation time under 50ms on reference hardware

### Portability Design Principles

- Keep the core purely functional; no hidden state; all RNG seeded and threaded explicitly
- Define the public API surface narrowly and document formally
- Keep coordinate systems explicit; world coordinates in core only
- Abstract randomness behind `RegionRng(seed, rx, rz, salt).nextDouble()`
- Serialization from day one of data stability (stage 9)
- Benchmark core in isolation; validate 50ms target before any Minecraft work

### Decision Point After Phase 1

After stage 6 is complete, commit explicitly to one of: proceed to Phase 2, open-source the standalone library, or both.

---

## Phase 2: Minecraft Mod Integration

**Goal:** NeoForge/Fabric mod consuming the Phase 1 library to produce modular region-planned terrain in Minecraft 1.21+, with a module-merging protocol for third-party content packs.

### Critical Architectural Position: Surface Terrain Mod

Phase 2 is fundamentally a **surface terrain mod**, not a full worldgen replacement. The framework's region plan contributes to vanilla's 3D density function as a **surface density contribution**, not as a replacement for the density function itself.

**What Phase 2 modifies:**

- Surface terrain shape (elevation, ridges, rivers, valleys, set pieces)
- Biome assignment via climate parameter biasing (biomes preserved, not replaced)
- Surface feature/structure placement through the framework's contextual placement system

**What Phase 2 delegates to vanilla unchanged:**

- Cave generation (spaghetti, cheese, noodle caves)
- Aquifer computation and fluid propagation
- Ore distribution
- Underground biome selection (Lush Caves, Dripstone, Deep Dark)
- Bedrock layer
- Vanilla structure spawning (villages, strongholds, trial chambers) — biased by biome selection but not replaced
- Modded feature injection via biome modifiers
- Modded structure spawning via standard mechanisms

The integration pattern: the custom chunk generator replaces *the spline-based surface contribution* within vanilla's density function composition. Everything downstream (caves, aquifers, surface rules, features, structures) runs through vanilla's normal pipeline, reading surface elevation as one of its inputs.

### Core Components

**Custom chunk generator** composing region plan surface contributions with vanilla's 3D density function for caves, aquifers, and overhangs. Preserves vanilla biome system and surface rule pipeline.

**Ticketed region plan cache with disk serialization:**

- Predictive prefetch tied to player velocity vectors (critical for Elytra flight)
- Async-aware cache get returning `CompletableFuture<RegionPlan>` on miss
- Request coalescing for concurrent misses in same region
- Binary zero-copy serialization format (FlatBuffers or hand-rolled) for hot-path deserialization under 5ms
- Async fallback to partial plan (Structural Plan only, no DLA) for unprefetched synchronous requests, with chunk regeneration when full plan completes

**Contextual placement framework** extending Minecraft's `PlacementModifier` system additively.

**Module loading and merging protocol** with:

- Climate-parameter-space conflict detection
- Additive density function composition
- DAG-based plan stage scheduler
- Zone-level (not region-level) parameter overrides to preserve cross-region consistency

**Authoring interfaces:**

- JSON schema for declarative module authoring
- Kotlin/Java DSL for code-first module authoring

**Mod compatibility layer** ensuring vanilla biome tags are preserved, standard decoration pipeline runs unmodified, and biome modifiers from other mods function as expected.

### Explicitly Out of Scope for Phase 2 v1

- Visual node editor (post-v1 stretch goal)
- In-engine hot-reload with chunk regeneration (architectural impossibility)
- Full 3D density function replacement (compatibility cost too high)
- Custom cave generation
- Custom aquifer logic

### Staged Delivery

| Stage | Description | Duration |
|-------|-------------|----------|
| 1 | Region planner integration with custom chunk generator; vanilla density function composition | 7 weeks |
| 2 | Ticketed cache + async-aware + coalescing + prefetch + binary serialization + partial-plan fallback | 5 weeks |
| 3 | Voronoi zone biasing of vanilla biomes (minimum-viable mod release) | 2 weeks |
| 4 | River integration with vanilla surface rules | 3 weeks |
| 5 | Mountain DLA integration | 3 weeks |
| 6 | Full terrain pipeline (gradient trick + fractal + set pieces) | 4 weeks |
| 7 | Contextual placement API | 4 weeks |
| 8 | Module loading and merging protocol | 5 weeks |
| 9 | Mod compatibility testing (5+ popular mods) | 2 weeks |
| 10 | DSL + JSON schema documentation | 2 weeks |
| 11 | Reference modules, docs, polish | 4 weeks |

### Phase 2 Timeline

- **Full-time solo:** ~9 months
- **Nights-and-weekends:** ~18 months

### Total Project Timeline

- **Full-time solo:** ~15 months for both phases
- **Nights-and-weekends:** ~30 months for both phases

---

## Risk Register

| Risk | Severity | Mitigation |
|------|----------|------------|
| Cross-region agreement mechanisms fail in production | Critical | Two-layer approach (shared plan + bounded reads) with explicit tests for seamlessness |
| Cascade trap from unbounded cross-region recursion | Critical | Depth-1 enforcement at runtime; architectural invariants prevent re-entrance |
| Global graph properties at handshake break invariant | Critical | Handshake Property Constraint; additive propagation only (drainage area, not Strahler) |
| 3D density function integration breaks caves/aquifers | Critical | Explicit surface-contribution-only architecture; vanilla cave/aquifer pipeline preserved |
| 50ms region generation target unachievable | High | Optimization pass in Phase 1 stage 3; fallback to larger regions or partial-plan async if needed |
| Disk I/O stutter on Elytra region transitions | High | Predictive prefetch; async-aware cache; binary zero-copy serialization; partial-plan fallback |
| Cache memory pressure on busy servers | High | Ticketed lifecycle; soft references on eviction; disk serialization for cold storage |
| Mod ecosystem compatibility breakage | High | Explicit compatibility testing stage; preserved vanilla biome tags and decoration pipeline |
| Set piece authoring slower than estimated | Medium | 3–5 days per polished set piece is the baseline; Phase 1 ships with ~6 polished, additional ones added post-launch |
| Set piece density balance wrong on first try | Medium | Initial math-based calibration; explicit tuning pass in Phase 2 post-integration |
| Aesthetic tuning takes longer than scheduled | Medium | Phase 1 standalone architecture provides ~100× faster iteration vs in-mod |
| Module-merging API design wrong on first try | Medium | Build reference modules in parallel with API; plan for v2 revision post-launch |
| Scope drift into standalone project without porting | Medium | Soft commitment point after Phase 1 stage 6 |
| Months 1–2 motivation dip on nights/weekends pace | Medium | Voronoi visualizer in week 2 provides early visible artifact |
| Vanilla compatibility edge cases (y=320, structures) | Medium | Compatibility layer constraining pipeline output; first-class vanilla integration testing |

---

## Success Criteria

### Phase 1 succeeds if:

- Standalone visualizer produces terrain visibly superior to vanilla on coherence, river quality, structural integration, and landmark presence
- Set piece library includes at least 6 polished, visually distinct landmarks
- API is clean enough that porting to Minecraft mod is mechanical, not a rewrite
- Library is open-source-ready as a standalone artifact
- All 10 required regression tests pass
- Performance benchmark hits p95 under 50ms on reference hardware

### Phase 2 succeeds if:

- A Minecraft world generated with the mod feels meaningfully more intentional than vanilla
- Set pieces are discoverable during normal play at intended rarity
- At least one third-party module author successfully ships a content pack using the framework
- Performance is within acceptable margins of vanilla chunk generation speed
- No disk I/O stutter observable during Elytra flight on a modest server setup
- At least 5 popular worldgen-adjacent mods work correctly alongside the framework

### The project as a whole succeeds if:

Either Phase 1 alone becomes a used and useful open-source library in the procedural-generation community, or Phase 2 ships a mod that demonstrates region-planning worldgen with authored landmarks in Minecraft. Either outcome individually justifies the project; both is the upside case.

---

## Summary: What This Project Is and Isn't

**It is:** A region-first, semantically-structured terrain framework that brings planning-level coherence and authored landmarks to Minecraft. A mod framework designed to be extensible by third-party module authors. Specifically a surface terrain mod that composes with, rather than replaces, vanilla's 3D density function.

**It is not:** A replacement for vanilla biomes (biomes are preserved and biased). Not a replacement for vanilla caves, aquifers, or underground generation. Not a real-time hot-reloadable in-engine editor. Not a feature-parity clone of Hytale WorldGen V2. Not a node-editor product in v1.

**The highest-leverage bets:** Inverted DLA for rivers with drainage-area width assignment, Voronoi handshake graph with rich Structural Plan for cross-region continuity, set pieces as hand-authored landmarks integrated procedurally, and Phase 1 as a standalone artifact that de-risks ~90% of research uncertainty before any mod work.

---

## Technical Foundation (Week 1 Skeleton)

### Module Structure

```
massif/
├── pom.xml                    (parent, Java 21, no deps)
├── massif-core/
│   └── pom.xml                (minimal, inherits from parent)
└── massif-visualizer/
    └── pom.xml                (core + exec-maven-plugin)
```

### Core Package Layout

```
fr.enderclem.massif
├── Main.java                  (CLI entry for smoke tests)
├── primitives/                (coords, RNG, grids — no dependencies)
├── pipeline/                  (stage interface, context, orchestrator)
├── stages/                    (stub implementations, replaced as pipeline lands)
├── data/                      (immutable output records)
├── setpieces/                 (SDF definitions, registry — added at stage 1)
└── api/                       (public surface: TerrainFramework, RegionPlan)
```

### Key Abstractions

- **`PipelineStage`** — pluggable unit; each pipeline step implements this
- **`RegionContext`** — mutable shared state during pipeline execution; sealed into immutable `RegionPlan`
- **`RegionPlan`** — sealed interface, public immutable output
- **`TerrainFramework`** — top-level entry point consumers call
- **`RegionRng`** — SplitMix64-based deterministic RNG seeded by `(seed, rx, rz, salt)`
- **`SetPiece`** — interface for landmark definitions; implementations declare SDF, spawn requirements, variation parameters

### Dependency Philosophy

Start with **zero external dependencies**. Add them only when:

1. A specific concrete need exists (not speculative)
2. The value clearly exceeds the cost (both for this project and the eventual mod)
3. No reasonable in-project implementation could cover the need

Every dependency in the core eventually ships with the Minecraft mod. Treat additions as architectural decisions, not conveniences.