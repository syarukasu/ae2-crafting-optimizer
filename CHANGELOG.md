# Changelog

All notable changes to this project are documented here.

## [Unreleased]

No unreleased changes.

## [1.1.0] - 2026-07-17

### Added

- A per-ME-grid shared real-time budget for standard AE2 crafting CPU pattern pushes.
- Crafting-job-local memoization for invariant emit, pattern, fuzzy-candidate, and container-return queries.
- Provider pattern generations, Assembly Matrix crafter routing, bounded IO Port cell cursors, Import Bus last-success hints, and Export Bus configured-key generations.
- Short-lived resolved recipe-intent caches for GTCEu candidate lists and Mekanism validated recipes.
- Cached Mekanism input-field access plans and recipe-test method lists to remove repeated reflection scans.
- Chunk-bucketed GTCEu multiblock intent lookup with concrete-input candidate prioritization.
- Advanced AE Pattern Provider intent capture and Quantum Computer effective co-processor pacing.
- Optional CrazyAE-style processing-pattern micro-batching with exact AE2 input, energy, waiting-output, and task accounting.
- Configurable AdvancedAE Reaction Chamber reuse of the recipe already resolved after an input change.
- Exact ExtendedAE Circuit Cutter positive/negative recipe-result sharing with live recipe revalidation.
- AE2 Overclock runtime-helper reflection metadata and MethodHandle caches plus one-tick upgrade-count caches.
- One-tick used-thread and busy-thread caches, crafter routing, and same-tick status-update coalescing for ExtendedAE Assembly Matrices.
- Optional cross-tick adjacent capability reuse tied to Forge `LazyOptional` invalidation.
- Optional generation-checked asynchronous terminal search and sorting over client-thread-created immutable projections.
- `/aco stats` and `/aco stats reset` diagnostics for execution pacing and machine cache hit rates.
- Recipe-intent/index invalidation after server datapack recipe reloads.

### Changed

- Expanded independent server config switches for calculation, execution, grid tick, bus, terminal, intent, add-on machine, and deep AE2 optimization paths.
- Import/Export Buses and Circuit Cutters are exempt from hard Grid Tick deferral and idle/slow backoff to prevent transfer starvation.
- Documentation now distinguishes active AE2 Overclock runtime-helper caches from compatibility-disabled redirects into Mixin-merged machine handlers.

### Fixed

- Removed registration of the AE2 Overclock merged-method reflection/MethodHandle redirect Mixin. Forge Mixin cannot safely redirect handlers added by AE2 Overclock's own higher-priority Mixins, and the attempted integration produced startup injection warnings on Reaction Chambers and Circuit Cutters.
- Kept AE2 Overclock's original reflection path authoritative in its Mixin-merged machine handlers while retaining runtime-helper reflection/MethodHandle and one-tick upgrade-count caches.

### Safety

- Every active standard AE2 CPU retains a configurable minimum progress allowance after the shared grid budget is consumed.
- Mekanism cache hits still run Mekanism's live recipe test before returning a recipe.
- GTCEu cache hits only reuse a candidate prefix; GTCEu's original matching and setup path remains authoritative.
- Bus simulation caching never skips a real transfer, and Circuit Cutter cache hits always pass ExtendedAE's live recipe test.
- Add-on machine optimizations do not change recipes, machine throughput, process waves, energy use, inventories, or matrix structure rules.
- Pattern micro-batching defaults off and rejects dedicated crafting machines, multiple targets by default, blocking/locked providers, container returns, directional Advanced AE patterns, unsupported namespaces, and non-atomic target capacity.
- Unsupported or disabled optimization paths always fall back to AE2 or the owning machine mod.

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
[1.1.0]: https://github.com/syarukasu/ae2-crafting-optimizer/compare/beta_1.0.0...v1.1.0
[1.0.0]: https://github.com/syarukasu/ae2-crafting-optimizer/releases/tag/beta_1.0.0
