# Changelog

All notable changes to this project are documented here.

## [Unreleased]

### Added

- Configurable AdvancedAE Reaction Chamber reuse of the recipe already resolved after an input change.
- Class-level reflection metadata caches and one-tick upgrade-count caches for AE2 Overclock.
- One-tick used-thread and busy-thread caches for ExtendedAE Assembly Matrix crafters.
- Same-tick duplicate Assembly Matrix status broadcast coalescing.
- `/aco stats` counters for all new add-on machine optimization paths.
- Crafting-job-local memoization for invariant emit, pattern, fuzzy-candidate, and container-return queries.
- Provider pattern generations, Assembly Matrix crafter routing, IO Port cell cursors, Import last-success hints, and Export configured-key generations.
- Exact ExtendedAE Circuit Cutter negative-result sharing and AE2 Overclock MethodHandle invocation with reflection fallback.
- Opt-in generation-checked asynchronous terminal search and sorting over client-thread-created immutable projections.
- Opt-in cross-tick adjacent capability reuse tied to Forge LazyOptional invalidation.
- Opt-in CrazyAE-style processing-pattern micro-batching with exact AE2 input, energy, waiting-output, and task accounting.
- Advanced AE Pattern Provider intent capture and Quantum Computer effective co-processor pacing.
- Chunk-bucketed GTCEu multiblock intent lookup plus concrete-input candidate prioritization.

### Safety

- Add-on machine optimizations do not change recipes, machine throughput, process waves, energy use, inventories, or matrix structure rules.
- Every path has an independent server config switch and optional pseudo-mixins use non-fatal injection requirements.
- Import/Export Buses and Circuit Cutters are exempt from hard Grid Tick deferral and idle/slow backoff to prevent starvation.
- Bus simulation caching never skips a real transfer, and Circuit Cutter cache hits always pass ExtendedAE's live recipe test.
- Pattern micro-batching defaults off and rejects dedicated crafting machines, multiple targets by default, blocking/locked providers, container returns, directional Advanced AE patterns, unsupported namespaces, and non-atomic target capacity.

## [1.1.0] - 2026-07-16

### Added

- A per-ME-grid shared real-time budget for standard AE2 crafting CPU pattern pushes.
- Short-lived resolved recipe-intent caches for GTCEu candidate lists and Mekanism validated recipes.
- Cached Mekanism input-field access plans and recipe-test method lists to remove repeated reflection scans.
- `/aco stats` and `/aco stats reset` diagnostics for execution pacing and machine cache hit rates.
- Recipe-intent/index invalidation after server datapack recipe reloads.

### Safety

- Every active standard AE2 CPU retains a configurable minimum progress allowance after the shared grid budget is consumed.
- Mekanism cache hits still run Mekanism's live recipe test before returning a recipe.
- GTCEu cache hits only reuse a candidate prefix; GTCEu's original matching and setup path remains authoritative.
- Advanced AE Quantum Computer execution remains outside the active mixin set.

## [1.0.0] - 2026-07-16

### Added

- Active AE2 crafting-calculation single-flight and bounded completed-plan caching.
- Pattern lookup and craftable-set caches with grid/provider invalidation.
- Configurable giant crafting CPU execution budgets and adaptive pacing.
- Terminal snapshot, visible range, storage watcher, and P2P notification pacing.
- Optional grid-tick, IO-bus, and failed automatic craft-request controls.
- Pattern Provider recipe-intent capture.
- Optional GTCEu Modern and Mekanism output-indexed recipe-intent fast paths for item, fluid, and supported chemical processing.
- Server diagnostics and `/aco intents` maintenance commands.
- Original ACO icon for Forge mod listings and repository documentation.
- Bilingual English/Japanese README and CurseForge publishing copy.

### Safety

- AE2 remains authoritative for craft planning, job submission, storage mutation, recipes, and network topology.
- Deterministic fast-fail, grid tick deferral, IO-bus caps, fuzzy bus caching, and successful-plan reuse are disabled by default.
- Advanced AE Quantum Computer execution logic is not mixed into by the conservative 1.0.0 build.

[Unreleased]: https://github.com/syarukasu/ae2-crafting-optimizer/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/syarukasu/ae2-crafting-optimizer/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/syarukasu/ae2-crafting-optimizer/releases/tag/v1.0.0
