# Changelog

All notable changes to this project are documented here.

## [Unreleased]

- No unreleased changes.

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

[Unreleased]: https://github.com/syarukasu/ae2-crafting-optimizer/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/syarukasu/ae2-crafting-optimizer/releases/tag/v1.0.0
