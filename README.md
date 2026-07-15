# AE2 Crafting Optimizer

<p align="center">
  <img src="docs/aco-icon.png" alt="AE2 Crafting Optimizer icon" width="192">
</p>

[![Build](https://github.com/syarukasu/ae2-crafting-optimizer/actions/workflows/build.yml/badge.svg)](https://github.com/syarukasu/ae2-crafting-optimizer/actions/workflows/build.yml)
[![License: LGPL-3.0-only](https://img.shields.io/badge/License-LGPL--3.0--only-blue.svg)](LICENSE)

English | [日本語](README_ja.md)

AE2 Crafting Optimizer is a lightweight Forge 1.20.1 optimization mod for Applied Energistics 2 (AE2).

Its purpose is to reduce repeated processing-machine recipe discovery after AE2 Pattern Provider pushes and to keep giant crafting CPUs from spending an unsafe amount of time pushing patterns in one server tick, while preserving AE2's original crafting logic.

This mod does not modify recipes, crafting rules, storage behavior, or Quantum Computer mechanics.

## Target Environment

- Minecraft: 1.20.1
- Forge: 47.4.18+
- Java: 17
- Applied Energistics 2: 15.4.10 (15.4.x)
- Built against Applied Energistics 2 15.4.10
- Optional intent fast paths for GTCEu Modern and Mekanism
- Designed to coexist with Advanced AE and Advanced Quantum Engineering
- Intended for large automation packs such as Astral Mekanism

This project is completely independent from Advanced Quantum Engineering.

Advanced Quantum Engineering upgrades Quantum Computer hardware.

AE2 Crafting Optimizer adds conservative recipe-intent fast paths for large AE2 processing lines. It does not touch AE2's craft planning confirmation screen.

## Status

Version `1.0.0` is a development release pinned to AE2 `15.4.x`. ACO uses Mixins against AE2 internals, so do not assume compatibility with another AE2 branch or minor series without rebuilding and testing it.

Install the same ACO jar on the dedicated server and every client. The authoritative config is the per-world server config:

```text
<world>/serverconfig/ae2_crafting_optimizer-server.toml
```

Back up the world before enabling disabled-by-default deep rewrites. See [Configuration](docs/CONFIGURATION.md), [Implementation](docs/IMPLEMENTATION.md), and [Testing](docs/TESTING.md).

## Goals

- Reduce repeated machine-side recipe discovery after AE2 Pattern Providers push known processing patterns.
- Bound per-tick Pattern Provider pushes from giant crafting CPUs without changing displayed CPU capacity or co-processor count.
- Treat item, fluid, and supported chemical outputs as real intent keys instead of forcing everything through dummy item assumptions.
- Preserve vanilla AE2 behavior and compatibility.

## What This Mod Does

### Craft Calculation Diagnostics

The optimizer can log slow AE2 crafting calculations with the requested output, requested amount, missing entry count, byte cost, and elapsed time.

This is the measurement layer. It does not change AE2 behavior.

### Conservative Craft Planning

The current build does not inject into AE2's Craft Confirm menu or replace `CraftingTreeNode`.

Craft planning, missing-item calculation, and recipe validity stay authoritative in AE2. Optional deep paths only reorder candidates, pace aggregate refreshes, split terminal deltas, or cache validated lookups.

### Active Calculation Single-Flight

When the same requester asks the same AE2 network for the exact same output, amount, and calculation strategy while the first calculation is still running, the optimizer can return the already-running calculation instead of starting a duplicate worker task.

By default this also keeps a very short completed-plan cache for missing or simulation results. Successful completed plans are not cached unless explicitly enabled, because they can become stale when storage changes between calculation and submission.

AE2 remains responsible for the final calculation and job submission.

### Deterministic Missing Fast-Fail

For large `REPORT_MISSING_ITEMS` requests, the optimizer can optionally run a strict deterministic preflight before AE2 starts the full solver.

It only returns early when the craft has a single unambiguous pattern path, each pattern has exactly one output, each input has exactly one possible input, and the optimizer can prove that a raw missing ingredient is unavailable.

If the craft is possible, ambiguous, tag-driven, substitution-heavy, recursive, emitted, or otherwise unclear, the optimizer falls back to AE2's original calculation.

This feature is enabled by default with a four-tick interval. Direct storage mutation and extraction/insertion are not throttled.

### Pattern Lookup Cache

The optimizer can cache `CraftingService.getCraftingFor(output)` lookups until AE2 crafting providers or network nodes change.

This mirrors the GTNH/UEL-style "invalidate on structure change, not every query" idea without replacing AE2's crafting graph solver.

### Craftable Set Cache

The optimizer can also cache `CraftingService.getCraftables(filter)` results until crafting providers or network nodes change.

This targets repeated terminal/provider scans. It never creates craftable entries that AE2 did not report.

### Storage Watcher Sync Throttle

The optimizer can optionally buffer client-visible storage watcher updates and flush them every configured interval.

This affects visible terminal/monitor synchronization timing only. Storage contents and insertion/extraction are not changed.

This feature is disabled by default.

### Crafting Execution Budget

Very large CPUs can expose extremely high co-processor counts. AE2 uses that value as an execution window for pattern pushes, so an extreme CPU can try to push too many patterns in one server tick.

The optimizer can cap the effective per-window pattern push budget for AE2's normal Crafting CPU while leaving the CPU's displayed storage and co-processor count unchanged.

This feature is active by default because co-processors increase pattern push throughput, not crafting calculation speed. It is the main TPS protection for giant CPUs.

Advanced AE's Quantum Computer CPU execution mixin is not enabled in the conservative build. Advanced AE integration should be tested separately because applying a pseudo-mixin to its crafting logic can crash when the Quantum Computer menu initializes on some mod-loader stacks.

This is intended for CrazyAE-class hardware numbers: the CPU can be huge, but server tick time remains bounded.

### Adaptive Execution Budget

The optimizer can also watch how long each active crafting CPU spends inside AE2's pattern execution loop.

If one CPU starts spending more than the configured target time in a server tick, its effective execution budget is reduced automatically. If it is consistently below the target and fully using its budget, the budget recovers gradually.

This follows the high-performance CPU idea without forcing every possible operation into one tick. AE2 still executes the same pattern pushes and still owns the final crafting state.

### Grid Tick Budget

The optimizer can pace selected AE2 grid tickables that are expensive in large automation networks.

The default target list includes AE2 IO Ports, Import Buses, Export Buses, ExtendedAE Ex buses, ExtendedAE special export buses, and the ExtendedAE Circuit Cutter.

The optimizer measures these devices at AE2's `TickManagerService` boundary. If selected devices spend the configured budget in the current server tick, later selected devices are deferred through AE2's normal `TickRateModulation.SLOWER` scheduling.

Import and Export Buses also receive a configurable operations-per-tick cap after other speed-card mods apply their changes.

This spreads bursts over more ticks. It does not change filters, recipes, redstone behavior, inventories, storage contents, or whether a transfer is valid.

Repeatedly idle selected tickables can also receive a short backoff. This targets empty import buses, blocked export buses, IO ports with no valid move, and similar polling loops.

Export-bus-style crafting requests that finish without creating a crafting link can be throttled for the exact same owner, slot, key, and amount. This reduces failed craft-request spam while leaving active and successful jobs alone.

### Terminal Snapshot Optimization

Open ME terminals can reuse their server-side available-stack snapshot and craftable set for a few ticks.

This reduces repeated full-network scans while a terminal is open. It only delays visible updates by the configured interval; storage mutation and extraction/insertion still use AE2's live storage path.

This feature is enabled by default. The deeper range mode additionally splits a large initial/delta update across several AE2 packets and menu ticks without dropping entries.

### Machine Intent Boundary

The optimizer now includes a conservative recipe intent bridge at the AE2 Pattern Provider boundary.

When a Pattern Provider successfully pushes a pattern, ACO records a short-lived intent containing the provider position, adjacent target candidate, pattern definition id, concrete pushed inputs, and outputs.

This is the foundation for GTCEu, Mekanism, and future Create fast paths where a machine can avoid rediscovering the recipe every tick.

GTCEu machines with a fresh intent for their position try a small output-indexed candidate list before GTCEu's normal full recipe iterator. Item and fluid outputs are indexed. If no candidate works, GTCEu's original search still runs.

Mekanism machines with a fresh intent for their position try output-indexed recipe candidates before Mekanism's normal lookup. Item, fluid, and chemical outputs are indexed, and candidates are returned only after Mekanism's own recipe `test` accepts the current machine inputs.

Create machine-side fast paths are still reserved config entries.

## What This Mod Does Not Do

This mod intentionally does not:

- Modify AE2 recipes.
- Modify crafting patterns.
- Modify crafting success or failure.
- Submit crafting jobs that AE2 would reject.
- Replace AE2's crafting graph solver.
- Make an invalid craft succeed.
- Change storage contents.
- Change item insertion or extraction.
- Change network topology.
- Make buses ignore filters or redstone control.
- Force GTCEu, Mekanism, or Create machines to run a selected recipe.
- Change Quantum Computer behavior.
- Change Advanced AE structures.
- Change Advanced Quantum Engineering structures.
- Increase crafting CPU capacity.
- Increase co-processor count.
- Modify Quantum Storage.
- Modify Quantum Accelerator.
- Modify Data Entangler behavior.

All crafting decisions continue to be made exclusively by Applied Energistics 2.

## Compatibility

Required:

- Applied Energistics 2

Optional integrations and coexistence targets:

- GTCEu Modern
- Mekanism
- ExtendedAE tickable class hints
- Advanced AE
- Advanced Quantum Engineering
- EMI
- JEI
- KubeJS
- Dedicated servers
- Singleplayer
- Arclight

No Bukkit or Paper APIs are used.

Only AE2 is a hard compile/runtime dependency. GTCEu and Mekanism hooks use optional pseudo-Mixins and runtime validation; when either mod is absent, its fast path has no target. Advanced AE's Quantum Computer execution logic is deliberately not mixed into by this release.

## Configuration

Server configuration:

```toml
[general]
enableOptimizer = true

[craftingCalculation]
# Legacy no-op options kept for config compatibility.
twoStageMissingPreview = false
cancelCalculationAfterPreliminaryMissingPreview = false
skipCalculationOnCachedMissingPreview = false
useMissingPreviewCache = false
missingPreviewCacheSize = 2048
missingPreviewCacheTtlSeconds = 300

invalidateCacheOnStorageChange = true
invalidateCacheOnPatternChange = true

minimumCalculationMillisForPreview = 100
minimumRequestedAmountForPreview = 1024
previewMaximumEntries = 1
heavyProcessHints = [
  "astral_mekanism:",
  "celestial",
  "electronic"
]

# Share exact duplicate in-flight calculations from the same requester.
deduplicateActiveCraftingCalculations = true
activeCalculationDeduplicationWindowTicks = 200
logCraftingCalculationDeduplication = false

# Short-lived completed-plan cache.
# By default only missing/simulation plans are reused.
cacheCompletedCraftingPlans = true
cacheSuccessfulCompletedCraftingPlans = false
completedCraftingPlanCacheSize = 1024
completedCraftingPlanCacheTtlTicks = 40

# Disabled by default. Only returns an early missing-only result when
# the deterministic preflight can prove the request cannot complete.
fastFailMissingCrafts = false
minimumRequestedAmountForFastFail = 4096
deterministicPreflightMaxDepth = 64
deterministicPreflightMaxNodes = 4096
logFastFailMissingCrafts = false

# Read-through cache for CraftingService.getCraftingFor(output).
cachePatternLookups = true
patternLookupCacheSize = 8192

# Read-through cache for CraftingService.getCraftables(filter).
cacheCraftableSets = true
craftableSetCacheSize = 256
logPatternLookupCache = false

[craftingExecution]
throttleCraftingExecution = true

# Maximum effective co-processors one CPU may spend in
# AE2's crafting execution window.
# 264192 matches AQE's non-experimental full default structure:
# (4096 + 121 * 512) * 4.
# Raise this to 2147483646 only when explicitly testing
# AQE's experimental maximum-value core.
maxEffectiveCoprocessorsPerCpu = 264192

# Adapt each active CPU's effective execution budget based on
# observed server-side execution time.
adaptiveCraftingExecutionBudget = true

# Reduce a CPU's adaptive budget when its execution burst exceeds
# this many milliseconds in one server tick.
targetCraftingExecutionMillis = 4

# Never adapt below this effective co-processor budget.
minimumAdaptiveCoprocessorsPerCpu = 1024

# Disabled by default to avoid log spam.
logCraftingExecutionThrottling = false

[gridTickBudget]
enableGridTickBudget = false
deferHeavyGridTickables = false

# Total selected AE2 grid-tick time before later selected devices
# are deferred to later ticks.
gridTickBudgetMillisPerServerTick = 6

# 1 keeps normal AE2 scheduling unless budget/backoff triggers.
gridTickMinimumIntervalTicks = 1

# Per-device diagnostic threshold and short backoff.
slowGridTickableMicros = 2000
slowGridTickableBackoffTicks = 2

# Short backoff for repeatedly idle selected grid tickables.
backoffIdleGridTickables = false
idleGridTickableBackoffAfterFailures = 4
idleGridTickableBackoffTicks = 5

heavyGridTickableClassHints = [
  "appeng.parts.automation.ImportBusPart",
  "appeng.parts.automation.ExportBusPart",
  "appeng.blockentity.storage.IOPortBlockEntity",
  "com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter",
  "com.glodblock.github.extendedae.common.tileentities.TileExIOPort",
  "com.glodblock.github.extendedae.common.parts.PartExImportBus",
  "com.glodblock.github.extendedae.common.parts.PartExExportBus",
  "com.glodblock.github.extendedae.common.parts.PartPreciseExportBus",
  "com.glodblock.github.extendedae.common.parts.PartTagExportBus",
  "com.glodblock.github.extendedae.common.parts.PartThresholdExportBus",
  "com.glodblock.github.extendedae.common.parts.PartModExportBus"
]

# Cap import/export bus bursts after speed-card mods apply.
limitIoBusOperationsPerTick = false
maxIoBusOperationsPerTick = 4096

# Cool down repeated failed export-bus-style craft requests.
throttleExportBusCraftRequests = false
exportBusCraftFailureCooldownTicks = 40
exportBusCraftThrottleCacheSize = 4096

# Disabled by default to avoid log spam.
logGridTickBudget = false

[storageSync]
throttleStorageWatcherUpdates = true

# Client-visible synchronization interval when throttling is enabled.
storageWatcherUpdateIntervalTicks = 4

# Server-side ME terminal snapshot pacing.
throttleTerminalInventorySnapshots = true
terminalInventorySnapshotIntervalTicks = 4
cacheTerminalCraftables = true
terminalCraftableCacheTicks = 4

flushImmediatelyOnScreenOpen = true
flushImmediatelyOnCellChange = true
flushImmediatelyOnNetworkTopologyChange = true
maximumBufferedChanges = 4096

[deepAe2Rewrite]
enableDeepAe2RewriteFlags = true
patternSelectionByAvailability = false
patternSelectionMaximumCandidates = 64
networkForceUpdateCoalescing = true
networkUpdateIntervalTicks = 2
visibleTerminalRangeSync = true
terminalRangeEntriesPerTick = 4096
p2pTopologyChangeOnlyRecheck = true
p2pDuplicateWindowTicks = 1
busSearchRewrite = false
busFuzzySearchCacheTicks = 2
busFuzzySearchCacheSize = 4096
fluidPatternRework = true
logDeepAe2RewriteFlags = true

[recipeIntentBridge]
enableRecipeIntentBridge = true
capturePatternProviderRecipeIntents = true
recipeIntentTtlTicks = 20
maximumRecipeIntentEntries = 4096

# GTCEu fast path is active. It prepends output-indexed candidates
# before GTCEu's original recipe iterator.
enableGtceuRecipeIntentFastPath = true
gtceuRecipeIntentMaximumCandidates = 16
gtceuRecipeIntentIndexCacheSize = 64
logGtceuRecipeIntentFastPath = false

# Mekanism fast path validates candidates with Mekanism recipe tests.
enableMekanismRecipeIntentFastPath = true
mekanismRecipeIntentMaximumCandidates = 16
mekanismRecipeIntentIndexCacheSize = 128
logMekanismRecipeIntentFastPath = false
enableCreateRecipeIntentFastPath = false

logCapturedRecipeIntents = false
logRecipeIntentRegistryEvictions = false

[diagnostics]
logSlowCraftCalculations = true
slowCraftCalculationMillis = 500
logCacheStatistics = false
```

Recipe intent diagnostics:

```text
/aco intents
/aco intents list 10
/aco intents clear
```

## Design Philosophy

This mod follows three principles.

### Preserve AE2

AE2 remains the single source of truth.

The optimizer never replaces AE2's internal crafting logic.

### Improve User Experience

Large crafting calculations and automation bursts may take time.

The default keeps AE2's normal Craft Confirm and graph-solver paths. Safe deep defaults add short aggregate refresh coalescing, rolling terminal synchronization, duplicate P2P notification suppression, and exact single-fluid input matching. Availability-based pattern ordering and Export Bus fuzzy caching remain explicit experiments.

### Safe Optimization

The generated defaults enable diagnostics, exact duplicate active-calculation sharing, a short missing/simulation completed-plan cache, pattern/craftable lookup caches, crafting execution pacing, terminal/storage-view synchronization pacing, selected deep coalescing paths, Pattern Provider intent capture, and GTCEu/Mekanism output-indexed intent fast paths.

Preliminary missing previews, deterministic fast-fail, grid-tick deferral, IO-bus operation caps, failed Export Bus request throttling, availability-based pattern ordering, fuzzy Export Bus caching, successful-plan reuse, and the reserved Create path are disabled by default.

Craft validity, recipes, storage mutation, and final crafting results remain controlled by AE2.

## Project Separation

This repository intentionally focuses only on crafting responsiveness.

Quantum Computer hardware upgrades are implemented in a separate project:

- Advanced Quantum Engineering

Keeping both projects independent makes debugging, compatibility testing, and future maintenance significantly easier.

## Build

The project resolves Forge from Forge Maven and AE2 `15.4.10` from ModMaven. It does not depend on a local Minecraft or Prism Launcher installation.

Run:

```bat
gradlew.bat clean build
```

The generated jar is written under `build/libs/`. GitHub Actions runs the same clean build for pushes and pull requests.

## Documentation

- [Configuration and safety tiers](docs/CONFIGURATION.md)
- [Implementation details](docs/IMPLEMENTATION.md)
- [Manual test matrix](docs/TESTING.md)
- [Publishing and release procedure](docs/PUBLISHING.md)
- [CurseForge description (English / Japanese)](docs/CURSEFORGE_DESCRIPTION.md)
- [Version history](CHANGELOG.md)

## License

ACO source code is licensed under the [GNU Lesser General Public License v3.0 only](LICENSE) (`LGPL-3.0-only`).

This project does not include or redistribute Applied Energistics 2 or Advanced AE source code.

The design research used AE2 1.20.1 and AE2-UEL 1.12.x behavior and issue discussions as references. Those projects are also licensed under LGPL v3. No third-party source or assets are vendored into this repository; see [NOTICE.md](NOTICE.md) for attribution and provenance notes.

Users must install all required dependency mods separately.

Please report issues related to this optimizer to this project only.

Do not report optimizer-related bugs directly to the AE2 or Advanced AE developers unless the issue has been reproduced without this mod installed.
