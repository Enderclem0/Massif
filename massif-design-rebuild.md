# Massif — A Context-Accessible Terrain Generation Framework

**Design Document — Rebuild**

---

## The Thesis

Massif is a framework for producing terrain whose semantic structure is accessible to consumers rather than discarded after generation.

Existing procedural terrain systems — including Minecraft's vanilla worldgen and most terrain mods — produce blocks. The reasoning behind those blocks (why there's a mountain here, where this river flows, what's the drainage basin this point belongs to) exists temporarily during generation and is thrown away. Consumers of the terrain (renderers, mods, players) see only the final output and must re-derive any structural understanding from block-level inspection.

Massif inverts this. The framework's primary deliverable is a **blackboard of semantic data about the generated world**, published under a stable interface. The terrain itself — heightmap, biomes, blocks — is one of many things on the blackboard, not the sole output. Mountain clusters with orientations, drainage basins with confluences, lakes with spill points, set pieces with identities, zone classifications with weights — all of these are published as queryable data.

This reframing changes what becomes possible. A mod wanting to place ancient shrines on mountain ridges can query the ridge graph directly rather than heuristically scanning the heightmap for local maxima. A mod wanting forests that respond to drainage basins can consume basin membership directly. A mod wanting content that spans set pieces can query set piece instances and their metadata. None of these are possible against terrain that only publishes blocks.

The terrain generation techniques we use are the means. The semantic accessibility is the end. Every design decision in this document should be evaluated primarily on "what does this make accessible to consumers?" rather than "does this produce nicer terrain?"

---

## Design Principles

### The Blackboard Is the Product

The framework computes many things during generation. Any computed information that a consumer might plausibly benefit from is published to the blackboard. Publication is the default; non-publication requires justification.

The blackboard is not a nice-to-have extensibility feature; it is the primary interface of the framework. Internal implementations are scaffolding for producing blackboard entries. The visualizer reads from the blackboard. The Minecraft mod reads from the blackboard. Third-party mods read from the blackboard. No consumer accesses internal data structures directly.

### Semantic Richness Over Visual Quality

Given a choice between two approaches that produce equally good-looking terrain, prefer the one whose intermediate computations are more semantically meaningful. A mountain represented as a fractal aggregate is less valuable than a mountain represented as a ridge graph with explicit orientation and peak structure, even if the visual output is identical, because the latter is queryable by consumers and the former is not.

### Feature-Scoped Generation

Generation algorithms run at the natural scale of the features they produce. Mountains are generated per-cluster, rivers per-basin, set pieces per-instance. This matches algorithm scope to feature scope, which eliminates seam problems by construction and produces data that is naturally associated with the features consumers will query.

### Context Drives Composition

Terrain at any world point is the composition of contributions from multiple semantically meaningful sources: zones, mountains, basins, set pieces, border treatments. The composition rules are explicit. Consumers can query not just "what's the height here?" but "what contributed to this height?"

### Vanilla Preservation Where Possible

In the Minecraft context, the framework modifies surface terrain shape and publishes semantic data about it. Vanilla's underground generation, biome system, structure placement, and feature decoration continue to function through preserved interfaces. The framework extends vanilla rather than replacing it, which preserves the modded ecosystem.

### Evolutionary Architecture

The framework's internal architecture will evolve as new techniques are developed and as consumer needs clarify. Internal flexibility is achieved through pragmatic coupling (techniques paired with compatible upstream stages, swapped together as units) rather than over-engineered abstraction. External stability is achieved through the blackboard's stable key and schema commitments.

---

## The Blackboard

### Role

The blackboard is a shared, queryable data space populated during generation and read by all consumers. It has three responsibilities:

1. **Publication interface.** Every piece of semantic data the framework computes is published under a stable key.
2. **Consumption interface.** Any consumer (visualizer, mod, Minecraft chunk generator, third-party mod) reads from the blackboard rather than from internal APIs.
3. **Extension interface.** Third-party mods can publish their own keys, enabling them to layer semantic content on top of the framework's base.

### Key Naming and Namespacing

Keys use namespace-prefixed names following Minecraft convention: `namespace:key_name`. The framework's own keys live under `core:`. Third-party mods use their own namespaces (`biomesoplenty:…`, `create:…`, etc.).

Key names are stable commitments. Once published, a key's name and semantic meaning should not change. Schema evolution is additive where possible, versioned where breaking changes are unavoidable.

### Initial Core Key Inventory

The framework publishes the following under `core:`, computed progressively as generation proceeds.

**Zone data:**
- `core:zone_weights(x, z)` — weighted distribution of zone types at any point
- `core:zone_type(x, z)` — dominant zone type (convenience derivation)
- `core:zone_graph` — full Voronoi graph with cell adjacencies and metadata
- `core:border_distance(x, z)` — signed distance to nearest zone border
- `core:border_normal(x, z)` — unit vector pointing across nearest border
- `core:border_pairing(x, z)` — zone types meeting at nearest border

**Structural data:**
- `core:mountain_clusters` — list of mountain clusters with footprint, orientation, member zones, peak count, generation strategy, per-cluster seed
- `core:drainage_basins` — list of basins with outlet, member zones, endorheic designation
- `core:drainage_graph` — directed graph of inter-zone water flow
- `core:confluence_points` — list of deterministic confluence locations with incoming basins

**Hydrology data:**
- `core:lakes` — list of lake records with position, footprint, surface elevation, spill direction, origin (major/refinement/set piece), basin membership
- `core:river_graph` — queryable graph of river segments with drainage area, width, elevation profile
- `core:water_surface(x, z)` — water elevation at any point (null if no water)

**Set piece data:**
- `core:set_pieces` — list of set piece instances with type, position, radius, rotation, per-instance seed
- `core:set_piece_at(x, z)` — set piece affecting a point, if any
- `core:set_piece_influence(x, z)` — blending weight of set piece at point [0..1]

**Terrain data:**
- `core:height(x, z)` — final composed surface elevation
- `core:gradient(x, z)` — gradient magnitude and direction
- `core:climate(x, z)` — 6D climate parameter vector
- `core:biome(x, z)` — final assigned biome (populated after biome source lookup in Phase 2)

**Derived feature data:**
- `core:waterfalls` — list of detected waterfall locations with flow, height
- `core:mountain_passes` — list of detected pass locations between ridges
- `core:coastline_points` — list of points on ocean-land borders with normals

**Contextual query helpers:**
- `core:nearest_mountain(x, z)` — returns cluster ID and distance
- `core:nearest_river(x, z)` — returns segment ID and distance
- `core:nearest_lake(x, z)` — returns lake ID and distance
- `core:in_basin(x, z)` — returns basin ID or null

This list is not exhaustive but establishes the standard. New keys are added as new techniques and features are implemented; each addition is a small design decision ("is this worth publishing?" — answer defaults to yes).

### Execution Ordering

Consumers declare which keys they depend on. The framework orders execution so dependencies are populated before consumers run. This is topological sort on key dependencies — simpler than a full capability-matching system because dependencies are declared at the granularity of "I need this key available" rather than "I need this algorithm upstream."

Cycles in the dependency graph are errors surfaced at configuration time.

### Stability Commitments

Core keys are public API. Their names and schemas are stable across framework versions. Changes follow semantic versioning:

- **Additive changes** (new fields in a record, new keys) are compatible with existing consumers and require only a minor version bump.
- **Breaking changes** (removing fields, changing semantics, removing keys) require a major version bump and migration documentation. The framework may support multiple major versions simultaneously during transition periods.

This commitment is what makes the blackboard a real protocol rather than "a shared hashmap with a fancy name." Consumers can depend on keys without fearing they'll silently change.

### Introspection

The framework provides tooling to inspect the blackboard:

- **Point inspector.** Given a world coordinate, return every key that applies there with its current value. Essential for debugging and for mod authors learning what data is available.
- **Key catalog.** List all registered keys with their schemas, producers, and consumers.
- **Dependency visualization.** Display the execution graph of keys and their dependencies.
- **Generation trace.** For a given generation, record which keys were produced in what order, enabling post-hoc analysis of generation behavior.

These tools are not optional polish — they are what makes the blackboard usable as the framework's primary interface.

---

## The Four Layers

Generation proceeds through four conceptual layers, each publishing to the blackboard.

### Layer 1: Zones

Zones are the framework's macro-geographical classification. Each world point has a weighted distribution over zone types (ocean, mountain, plains, desert, tundra, etc.) derived from Voronoi cells at ~256–512 block resolution with Lloyd relaxation.

**What gets published:**
- Zone weight distribution at any point
- Zone graph with adjacencies
- Border primitives (distance, normal, pairing) for transition handling

**Why it matters for consumers:**
Zone weights enable mods to build features sensitive to geographic context. "Spawn ancient ruins only in desert zones" becomes a direct query. "Place coastal features along ocean-land borders" uses border primitives. "Apply climate modifications based on zone type" uses the weight distribution.

### Layer 2: Features

Features are geographical entities spanning multiple zones, computed as connected components or derived structures from the zone graph.

**Mountain clusters:** contiguous mountain-zone components. Each cluster has an orientation (derived from cluster shape), a peak count hint, a generation strategy selection, and a stable ID.

**Drainage basins:** contiguous zone components flowing to the same ocean outlet (or marked endorheic). Each basin has a drainage graph, confluence points, elevation profile along rivers.

**Set pieces:** authored landmarks placed via Poisson-disk sampling with per-type density calibration. Each instance has position, rotation, radius, per-instance seed, and a declared set piece type.

**What gets published:**
Full metadata for every feature instance. Not just existence but structure: a mountain cluster exposes its orientation so consumers can align features to it; a basin exposes its drainage graph so consumers can trace water flow; a set piece exposes its type so consumers can respond to specific landmarks.

### Layer 3: Generated Structure

Features are realized as geometry through technique-specific generation. Multiple techniques can implement the same feature type, selected per instance.

**Mountain techniques** (examples):
- Ridge graph: explicit combinatorial structure, maximum control, designed-feeling output
- Value noise derivatives with ridged fBm: base noise texture with fake erosion via gradient trick
- SCA (space colonization): organic branching growth toward attractor points

**River techniques** (examples):
- Explicit path generation from drainage graph
- SCA-based growth from outlets to headwaters

**Set piece realization:** SDF-based geometry in local coordinates, blended with procedural terrain through transition bands.

**Technique selection** is declared per feature instance in the Structural Plan. Different mountains in the same world can use different techniques, producing meaningful variety without requiring multiple worlds.

**What gets published:**
Ridge graphs, river path geometry, SDF evaluations — as queryable structured data, not just resulting heights. Consumers can ask "where are the ridge lines in this mountain?" and get polylines, not just infer ridges from heightmap maxima.

### Layer 4: Composition and Biomes

Terrain at any world point is the weighted composition of per-zone passes plus feature contributions plus set piece overrides. Each zone type implements a pass producing full terrain output as if the zone owned the point entirely. Feature contributions are invoked from within relevant passes. Set pieces override within their cores.

Biomes are assigned by vanilla's biome source (in Phase 2) consuming the composed climate parameters. Vanilla's native biome blending handles fine-scale biome transitions.

**What gets published:**
Final heightmap, gradient, climate parameters, biome assignments, and — critically — the intermediate composition data. Consumers can ask "what percentage of this point's height came from mountain contributions versus base noise?" Transparency into composition is what enables sophisticated context-aware features.

---

## The Water System

Water is modeled with global physical constraints made accessible through the blackboard.

### Two-Stage Hydrology

**Coarse hydrology** runs before detailed terrain generation. Using a zone-centroid-resolution heightmap, it identifies major depressions via priority-flood watershed analysis and places major lakes with deterministic spill-point elevations. River elevation profiles are assigned along the drainage graph, ensuring monotonic downhill flow.

**Refinement hydrology** runs after terrain detail generation. It catches significant depressions created by feature generation that weren't present in the coarse heightmap and adds smaller lakes where warranted. It verifies river monotonicity and flags violations.

### Guaranteed Properties

- Water surfaces are flat at explicit elevations (published as `core:water_surface`)
- Rivers flow monotonically from source to outlet
- All rivers reach oceans or endorheic terminals
- Lakes sit in actual depressions with computed spill directions
- River width scales with drainage area (published as river segment metadata)
- Waterfalls occur at river-cliff intersections (published as `core:waterfalls`)

### Acknowledged Limitations

The framework does not simulate:
- Cubic-meter flow conservation (drainage area is the proxy)
- Erosion dynamics over geological time (gradient trick approximates visual effect)
- Seasonal variation (rivers are static)
- Groundwater and aquifer interactions (delegated to vanilla in Phase 2)
- Sediment dynamics (set pieces handle deltas and alluvial features)

These limitations are documented rather than hidden. Consumers know what the water system models and what it doesn't.

### What Gets Published

Lake records with position, footprint, surface elevation, spill direction, spill location, origin type (major/refinement/set-piece), basin membership. River graph with segments, drainage area, width, elevation profile, start and end. Water surface elevation queryable at any point. Endorheic designations for special handling.

---

## Set Pieces

Set pieces are hand-authored landmarks appearing procedurally with parameterized variation.

### Design Philosophy

Common enough to find through normal exploration, rare enough to feel special. Varied through parameterization (rotation, scale, per-instance seed, biome-aware appearance) so the same set piece type produces visibly different instances. Designed to create memorable locations that reward exploration — the framework's answer to "where should players build bases" (valleys with lakes) and "what should players remember about their world" (volcanic islands, dramatic cascades).

### Initial Library

The framework ships with set piece types covering major archetypes:
- Mountain-ringed valley with lake
- Volcanic island
- Waterfall cascade
- Crater lake
- Fjord
- Mesa complex
- Inland delta
- Atoll

Each type is a parameterized template with spawn requirements, size ranges, biome overrides, and optional associated structures.

### Integration With Other Systems

Set pieces override zone classification within their cores. They can mandate water configurations (cascade set pieces force rivers through their paths; caldera set pieces place lakes). They suppress DLA/SCA/ridge generation within their cores where they control geometry. Their footprints are published so downstream consumers can respond to them.

### What Gets Published

Instance list with type, position, radius, rotation, per-instance seed, applicable zones, biome overrides, associated structure declarations. Consumers can ask "am I inside a volcanic island?" and get yes/no; "what set piece am I blending with?" and get instance data; "what set pieces are within 500 blocks of me?" and get a list.

---

## Transitions

The framework handles three categories of transitions at different scales, each with its own mechanism.

### Zone Transitions (smooth blending)

Zone weights are continuous; per-zone passes produce full output weighted by distribution. Transitions emerge from weighted composition without special-case logic. This handles the majority of zone boundaries — foothills, climate gradients, biome blending — naturally.

### Border Features (sharp geometry)

Some zone pairs produce sharp geographic features at their borders: coastlines, cliffs, escarpments. A border treatment registry declares which zone pairs produce sharp features. Zone passes query border primitives and shape their output accordingly. Symmetric querying by both adjacent passes produces consistent terrain at the border zero-crossing without either pass reading the other's output.

### Set Piece Transitions (override blending)

Set pieces have a core radius where they fully control output and a bounding radius beyond which they have no influence. Between them, a transition band blends set piece output with procedural output. This is distinct from zone transitions because set pieces override rather than compose.

### Biome Transitions (vanilla)

Biome blending at Minecraft's native scale is handled by vanilla's blend_density and biome source logic. The framework ensures smooth climate parameter inputs; vanilla produces smooth biome outputs.

### What Gets Published

Border primitives at any point. Set piece influence weights. Zone weight distributions. Consumers can query "how much does this point belong to each zone?" and build features that respond to specific transition regimes.

---

## Architectural Invariants

### Determinism

All framework outputs are pure functions of world seed and world coordinates. No mutable shared state between computations. No dependence on generation order. Two runs with the same inputs produce bit-identical outputs, across threads, processes, and platforms. StrictMath is used for transcendental operations to ensure cross-JVM consistency.

### Feature-Scoped Purity

Each geographical feature (mountain cluster, drainage basin, set piece) is computed as a pure function of its identity and shared data (zone graph, world seed). No cross-feature computation leakage. Feature outputs are cacheable by feature ID without concerns about context sensitivity.

### Blackboard Stability

Core keys never change semantic meaning. Schemas evolve additively or through explicit versioning. Consumers can depend on keys without fear of silent changes.

### Composability

Framework outputs combine with vanilla outputs and with other mod outputs through the blackboard protocol. The framework does not assume exclusive control over any world property. Blockings (set pieces forcing specific biomes, for instance) are explicit and documented, not silent overrides.

---

## Phase 1: Standalone Framework + Visualizer

### Goal

A complete, open-source-ready terrain framework with an interactive visualizer that reads exclusively from the blackboard. This validates that the framework's semantic data is sufficient for meaningful consumers and produces a useful artifact independent of the Minecraft integration.

### Stack

- **Core framework:** Java 21 on JVM for eventual Minecraft compatibility
- **Visualizer:** Swing with BufferedImage and Graphics2D (zero external dependencies; ships with JDK)
- **Dependency philosophy:** Start with zero external dependencies in the core. Add only when genuinely needed.

### Module Structure

```
massif/
├── massif-core/                  (framework, zero Minecraft deps)
│   ├── blackboard/               (the blackboard interface and implementation)
│   ├── primitives/               (coords, RNG, grids)
│   ├── pipeline/                 (stage orchestration)
│   ├── stages/                   (zone, structural plan, hydrology, generation)
│   ├── techniques/               (swappable generation algorithms)
│   └── api/                      (public surface for consumers)
└── massif-visualizer/            (Swing consumer of blackboard)
```

The visualizer imports only from `api` and `blackboard`. This enforces the architectural discipline: if the visualizer can show something, it's because the framework published it. If it can't, the framework needs to publish more.

### Core Deliverables

- Complete blackboard implementation with publication and consumption APIs
- Zone system with Voronoi, weighted distributions, border primitives
- Structural Plan computing mountain clusters, drainage graphs, confluence points
- Coarse and refinement hydrology
- At least two mountain generation techniques (enabling comparison)
- At least one river generation technique
- Initial set piece library (6-8 types)
- Zone pass implementations for major zone types
- Composition stage producing final heightmap from blackboard data
- Interactive visualizer with blackboard inspection, layer toggles, parameter tuning
- Point inspector tool for querying blackboard at any world coordinate
- Serialization format for blackboard state (when schemas stabilize)
- Performance benchmarks and optimization
- Comprehensive tests verifying invariants

### Suggested Build Order

The order is designed so early stages produce visible output through the blackboard-reading visualizer, providing motivation and feedback throughout.

1. **Blackboard skeleton** — publication and consumption APIs, basic introspection
2. **Walking skeleton** — stub stages publishing trivial keys, visualizer reading them, end-to-end data flow working
3. **Zones** — Voronoi computation, weighted distributions, border primitives, visualizer showing zones
4. **Structural Plan** — mountain clusters and drainage graph, visualizer showing cluster outlines and basin colorings
5. **Coarse hydrology** — major lakes and river elevation profiles, visualizer showing water bodies and flow directions
6. **First mountain technique** — probably ridge graphs for simplicity; visualizer shows rendered mountains
7. **First river technique** — explicit path generation from drainage graph
8. **Zone passes** — per-zone terrain passes, composition stage, visualizer shows composed terrain
9. **Border treatments** — coastlines first; visualizer shows sharp coastal transitions
10. **Set pieces** — initial library with SDF-based geometry
11. **Refinement hydrology** — catching DLA-induced depressions
12. **Second mountain technique** — enabling comparison; visualizer supports side-by-side mode
13. **Contextual placement** — waterfalls, mountain passes, derived features
14. **3D preview** in visualizer
15. **Serialization** when schemas are stable
16. **Performance optimization** based on benchmarks
17. **Documentation** and reference content

At each stage, the visualizer gains new capabilities because the blackboard gains new keys. The progression is visible and motivating.

### Quality Gates

Before moving to Phase 2:

- Visualizer reads exclusively from the blackboard (no private APIs)
- All invariants have regression tests
- At least two techniques for mountain generation with visible differences
- Generated terrain is visibly more intentional than vanilla Minecraft's noise-based output
- Third-party developers can write a consumer using only public documentation (validated by at least one external test consumer)
- Performance benchmarks meet targets determined during implementation

### Decision Point

After quality gates pass, commit explicitly to one of:
- Proceed to Phase 2 Minecraft integration
- Open-source the standalone framework as a final artifact
- Both

---

## Phase 2: Minecraft Integration

### Goal

A NeoForge/Fabric mod consuming the Phase 1 framework to produce terrain in Minecraft 1.21+, with the blackboard extended to support Minecraft-specific concerns and made available to other mods.

### Integration Philosophy

The framework contributes to vanilla's density function rather than replacing it. Surface terrain shape is framework-controlled; caves, aquifers, ores, underground biomes, and structure placement continue to function via vanilla's existing systems. Biomes are biased through climate parameters, not replaced.

This preserves the modded ecosystem. Mods using biome modifiers, structure sets, or feature decoration pipelines continue to work. Mods wanting to build on framework semantic data consume the blackboard.

### Core Components

**Custom chunk generator** composing vanilla's density function with framework surface contributions. Preserves vanilla caves, aquifers, underground generation.

**Region plan cache** with ticketed lifecycle tied to chunk tickets. Soft references on eviction. Disk serialization for cold storage with zero-copy binary format. Predictive prefetch watching player velocity.

**Underground integration** for declared volumes: lake suppression (prevent drainage breaches), river channel protection (stable streambeds), set piece underground rules (volcanic tubes, mesa strata). Deep underground remains vanilla beyond configurable depths.

**Blackboard exposure to mods** through the framework's existing blackboard API, now accessible from within the mod's runtime. Mods can read all `core:` keys and publish their own under their namespaces.

**Mod compatibility testing** with major worldgen-adjacent mods (Create, YUNG's series, Terralith-style biome packs, major structure mods). Framework worlds must support modded content correctly.

### Explicitly Out of Scope for v1

- Visual node editor (separate product; post-v1 stretch goal)
- In-engine hot-reload with chunk regeneration (architecturally prohibitive)
- Other dimensions (overworld-only in v1; other dimensions as follow-up if demand exists)
- Full subterranean generation overhaul (vanilla's density functions handle this)
- Runtime blackboard modification by mods (blackboard is populated during generation; runtime mutation is complex and can be added later if needed)

### New Blackboard Keys for Phase 2

Phase 2 adds Minecraft-specific keys:
- `core:underground_volumes` — declared volumes with special rules
- `core:vanilla_structure_intersections` — framework-relevant data about vanilla structure placements
- `core:chunk_generated(cx, cz)` — whether a specific chunk has been generated
- Additional keys as needed for Minecraft-specific consumers

### Success Criteria

- Minecraft worlds feel meaningfully more intentional than vanilla
- Rivers connect logically, mountain ranges have range-scale structure, set pieces provide memorable landmarks
- Vanilla caves, aquifers, ores, and underground biomes generate correctly
- At least one third-party mod ships a feature consuming framework blackboard data that couldn't have been built against vanilla alone
- Major worldgen-adjacent mods work correctly in framework worlds
- No disk I/O stutter during normal gameplay including Elytra flight

---

## Extensions to Watch For

Not in v1 but worth noting as directions the architecture supports naturally:

**Runtime blackboard updates.** If mods want to modify framework data at runtime (e.g., "this mountain was partially demolished by a raid event"), the blackboard can support this with careful attention to consistency.

**Alternative frontends.** The blackboard protocol enables other consumers beyond the visualizer and Minecraft mod. A web-based terrain viewer. A terrain export tool for other game engines. A data analysis tool for studying worldgen behavior. The framework doesn't have to build these; they become possible.

**Research-oriented extensions.** Academic work on procedural terrain could use the framework as a reference implementation or comparison baseline. Published papers introducing new techniques could plug in as additional generation strategies.

**Cross-world analysis.** Since worlds are deterministic from seeds, tooling could pre-compute many worlds, analyze them, and surface seeds with interesting properties. "Find me a seed with a volcanic island near a mountain range and a major river."

These aren't commitments. They're directions the architecture enables without needing to be explicitly designed for.

---

## Risk Register

| Risk | Category | Mitigation |
|------|----------|------------|
| Blackboard becomes large and unwieldy | Architectural | Start with minimal initial keys; add as needed; periodic audits; strong conventions for naming and schemas |
| Key schema evolution breaks consumers | Compatibility | Additive changes preferred; versioning for breaking changes; deprecation periods; comprehensive documentation |
| Performance overhead of blackboard abstraction | Performance | Efficient implementation (typed accessors for hot paths, per-chunk caching); profile from day one; optimize hot keys |
| Generation performance target unachievable | Performance | Benchmark during Phase 1; optimize iteratively; larger regions as fallback; async generation for unprefetched areas |
| Vanilla cave/aquifer interaction produces visible issues | Integration | Underground suppression volumes for framework features; preserve vanilla beyond shallow depths; explicit testing |
| Mod ecosystem compatibility breaks | Integration | Preserve biome identity; standard feature pipelines unchanged; dedicated compatibility testing with popular mods |
| Architectural discovery requires upstream changes | Evolution | Accept that architecture evolves; use additive struct evolution; clear separation of concerns; strong invariant tests |
| Set pieces feel repetitive or out of place | Content | Per-instance variation; biome-aware appearance; density calibration; playtesting required in Phase 2 |
| Water system unrealistic in edge cases | Content | Two-stage hydrology catches major issues; monotonic flow by construction; explicit limitations documented |
| Floating-point determinism issues across JVMs | Determinism | StrictMath for transcendentals; cross-platform determinism tests; documented guarantees |
| Third-party mod blackboard collisions | Ecosystem | Namespaced keys; tag-based aggregation for cooperative cases; documentation encouraging namespace hygiene |

---

## Success Criteria

### Phase 1

- Visualizer consumes only from the blackboard, demonstrating that published data is sufficient for a meaningful consumer
- Generated terrain is visibly more coherent and intentional than vanilla Minecraft
- At least one external developer writes a small consumer using only public documentation and finds the blackboard interface usable
- All architectural invariants have passing regression tests
- The framework ships as a usable open-source library for procedural terrain generation beyond Minecraft's needs

### Phase 2

- Minecraft worlds generated by the framework produce the "readable terrain" experience: players can learn to read geography, find memorable landmarks, build in compelling locations
- Vanilla and modded content ecosystems function correctly within framework worlds
- At least one third-party mod ships a feature that depends on framework blackboard data — specifically, a feature that couldn't have been built against plain vanilla Minecraft
- Performance meets targets on modest hardware during normal gameplay

### The Deeper Criterion

Either phase's success is justified independently. Phase 1 succeeds as an open-source contribution to the procedural generation field. Phase 2 succeeds as a meaningful addition to Minecraft's worldgen ecosystem. Achieving both is the upside case. But the core criterion — across both phases — is that **the framework's semantic data enables consumers to do things they couldn't do otherwise**. If consumers are building context-aware features using the blackboard, the framework has succeeded regardless of whether the terrain itself wins aesthetic comparisons.

---

## What This Project Is

A platform for producing procedural worlds whose structural reasoning is preserved as queryable data. A commitment to semantic accessibility as the primary product. A demonstration that procedural generation can be designed around consumer empowerment rather than just visual output. A framework that enables kinds of mods and consumers that weren't possible before because the information they need was being thrown away.

## What This Project Is Not

A replacement for vanilla Minecraft worldgen. A full 3D density function replacement. A node-based visual editor. A real-time in-engine editing tool. A physical simulation of hydrology, erosion, or tectonics. A solution to every worldgen problem.

## The Core Bet

That making procedural worldgen semantically accessible is valuable enough to justify the architectural effort, and that the difference between "terrain as output" and "terrain as queryable structured data" is the difference between yet another worldgen mod and a new kind of worldgen platform.

Everything else is implementation.
