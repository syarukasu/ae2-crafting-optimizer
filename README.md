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
- Experimental Mekanism native batching additionally requires Applied Mekanistics `1.4.3`
- Optional execution-budget integration for Neo ECO AE Extension 20.3.x
- Optional compatibility for AppliedE `0.14.3` and AppliedE TPS Fix `0.14.7-fix2`
- Designed to coexist with Advanced AE and Advanced Quantum Engineering
- Intended for large automation packs such as Astral Mekanism

This project remains independently installable from Advanced Quantum Engineering.

Advanced Quantum Engineering upgrades Quantum Computer hardware.

AE2 Crafting Optimizer adds conservative recipe-intent fast paths for large AE2 processing lines. It does not touch AE2's craft planning confirmation screen.

## Status

Release `1.4.1` is pinned to AE2 `15.4.x`. Its release artifact was clean-built
and passed the complete automated test suite. P9 startup, recovery, multiplayer,
and long-running live-world qualification remains operator-run. The optional Neo ECO integration is pinned to Neo ECO AE
Extension `20.3.x`. ACO uses Mixins against mod internals, so do not assume
compatibility with another branch or minor series without rebuilding and
testing it.

Install the same ACO jar on the dedicated server and every client. ACO uses one global Forge common config on each installation:

```text
config/ae2_crafting_optimizer-common.toml
```

Keep the server and client copies aligned. Server-side gameplay behavior uses
the server copy, while client-only display optimizations use the client copy.
Legacy `defaultconfigs` and per-world `serverconfig` files are no longer read.

Back up the world before enabling disabled-by-default deep rewrites. See [Configuration](docs/CONFIGURATION.md), [Implementation](docs/IMPLEMENTATION.md), and [Testing](docs/TESTING.md).

The development tree also contains a disabled next-generation checked
long/BigInteger planner, durable GTCEu/Mekanism native batching protocol, fair
multi-job scheduler, versioned BigInteger CPU-host API, and bounded status
channel. These are source-complete foundations, not live defaults. Read
[Experimental Crafting Engine](docs/EXPERIMENTAL_ENGINE.md) before testing them.
The current source carries the `1.4.1` patch version while P0-P8 are reviewed;
P9 startup, recovery, multiplayer, and long-running world tests are deliberately
not claimed by this source revision. See
[P0-P8 implementation status](docs/P0_P8_IMPLEMENTATION_STATUS.md).

### Experimental BigInteger Boundary

ACO does not silently convert normal AE2 or Advanced AE CPUs to BigInteger.
`BigCraftingEngineApi` is an explicit sidecar API for a separately integrating
CPU add-on. AQE 2.0.1 is the current optional consumer. It keeps large counts in
`BigInteger`, promotes only after checked-long overflow, gives existing machine
APIs bounded execution windows, persists a versioned multi-job capacity ledger,
and sends paged status through a separate strict protocol. The configured bit
limit is enforced during intermediate planning arithmetic, runtime accounting,
NBT decode, and packet decode. The exact implementation ceiling is
`10^16384 - 1`; values with 16,385 decimal digits are rejected even when they
share the boundary bit length.

For deterministic roots, ACO now compiles the reachable Pattern DAG once per
provider/recipe generation into primitive-indexed arrays. Runtime work is
proportional to the number of reached recipe nodes rather than the requested
quantity, captures only referenced inventory keys, and restarts with
`BigInteger` only after checked-long overflow. Complete Shadow accounting must
match AE2 64 times by default before that root can become authoritative.

With the experimental master enabled, `enableAtomicBigCapacityPlans` also covers
the narrower standard-GUI case where every distinct AEKey and Pattern count fits
signed `long`, but their aggregate does not. ACO keeps each counter exact, uses
BigInteger only for checked aggregate calculation, and stores an over-`long` CPU
capacity reservation in an integrated AQE host. Standard AE2 CPUs cannot accept
that Big-capacity facade.

The host API is enabled by default because it has no effect until an explicitly
integrated CPU registers a host. The compiled planner, native batching, fair
scheduler, and other behavior-changing engine switches remain default-off.
Complete the documented copied-world recovery matrix before enabling those
deep rewrites on a production world. The experimental master is
fail-fast pinned to AE2 `15.4.10`; version-sensitive
Advanced AE and native machine bridges are likewise accepted only at their
documented tested versions.

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

Craft planning, missing-item calculation, and recipe validity stay authoritative
in AE2. Active optional paths only reorder candidates or cache validated,
calculation-invariant lookups. Aggregate refresh and terminal-delta rewrites are
unregistered compatibility paths.

### Active Calculation Single-Flight

When the same requester asks the same AE2 network for the exact same output, amount, and calculation strategy while the first calculation is still running, the optimizer can return the already-running calculation instead of starting a duplicate worker task.

By default this also keeps a very short completed-plan cache for missing or simulation results. Successful completed plans are not cached unless explicitly enabled, because they can become stale when storage changes between calculation and submission.

AE2 remains responsible for the final calculation and job submission.

### Deterministic Missing Fast-Fail

For `REPORT_MISSING_ITEMS` requests, the optimizer runs a strict deterministic preflight before AE2 starts the full solver. Item, fluid, and chemical storage keys follow the same proof path.

It only returns early when the craft has a single unambiguous pattern path, each pattern has exactly one output, each input has exactly one possible input, and the optimizer can prove that a raw missing ingredient is unavailable.

If the craft is possible, ambiguous, tag-driven, substitution-heavy, recursive, emitted, or otherwise unclear, the optimizer falls back to AE2's original calculation.

This feature is disabled by default. It is available only as a strict, opt-in preflight; ambiguous requests always fall back to AE2.

### Pattern Lookup Cache

The optimizer can cache `CraftingService.getCraftingFor(output)` lookups until AE2 crafting providers or network nodes change.

This mirrors the GTNH/UEL-style "invalidate on structure change, not every query" idea without replacing AE2's crafting graph solver.

### Craftable Set Cache

ACO 1.2.2 leaves `CraftingService.getCraftables(filter)` entirely to AE2. The
retained config key defaults to `false`, and its Mixin is unregistered so a
zero-stock terminal entry cannot outlive AE2's current repository generation.

### Storage Watcher Sync Throttle

ACO has compatibility-disabled this path since 1.2.1.
`StorageServiceWatcherThrottleMixin` remains unregistered in 1.2.2, and the
retained config key defaults to `false` and cannot reactivate it.

The previous implementation buffered client-visible watcher updates only, but stale display generations could conflict with live terminal insertion in heavily modified clients. Any future replacement must preserve one coherent menu generation and pass the zero-stock insertion regression test before it can be registered again.

### Crafting Execution Budget

Very large CPUs can expose extremely high co-processor counts. AE2 uses that value as an execution window for pattern pushes, so an extreme CPU can try to push too many patterns in one server tick.

The optimizer can cap the effective per-window pattern push budget for AE2's normal Crafting CPU while leaving the CPU's displayed storage and co-processor count unchanged.

This feature is active by default because co-processors increase pattern push throughput, not crafting calculation speed. It is the main TPS protection for giant CPUs.

Advanced AE Quantum Computer CPUs use the same effective co-processor cap and measured execution-wave boundary. The integration does not wrap menu code, change the displayed value, replace task accounting, or invoke Advanced AE crafting methods reflectively.

Sequential Instant dispatch divides the original AE2 and Advanced AE execution loops into measured waves. Each wave still uses AE2's own one-pattern input extraction, `pushPattern`, energy, task-progress, and waiting-output accounting. A low fixed operation cap is not imposed: waves continue up to the CPU's original `maxPatterns` while the default `4 ms` per-CPU and `8 ms` per-grid budgets have room. Provider rejection or a full target ends the tick immediately.

Neo ECO AE Extension 20.3.x custom ECO CPUs can also join ACO's adaptive per-CPU and shared per-grid execution budgets. ACO caps the values returned by Neo ECO's own normal and fast-path tick-limit methods, then records the actual number of pattern pushes reported by Neo ECO. Neo ECO's scheduler, batch/aggressive fast paths, recipes, storage, CPU statistics, and crafting accounting remain unchanged.

This is intended for CrazyAE-class hardware numbers: the CPU can be huge, but server tick time remains bounded.

### Adaptive Execution Budget

The optimizer can also watch how long each active crafting CPU spends inside AE2's pattern execution loop.

If one CPU starts spending more than the configured target time in a server tick, its effective execution budget is reduced automatically. If it is consistently below the target and fully using its budget, the budget recovers gradually.

This follows the high-performance CPU idea without forcing every possible operation into one tick. AE2 still executes the same pattern pushes and still owns the final crafting state.

### Shared ME Grid Execution Budget

ACO also measures the combined pattern-push time used by standard AE2 crafting CPUs on the same ME grid. Once that grid reaches its configured budget for the current server tick, later CPU bursts are reduced to a small progress allowance instead of letting several individually safe CPUs add up to an unsafe MSPT spike.

The default is `8 ms` per ME grid per tick with at least one operation for every active CPU. This paces work only after AE2 has accepted a crafting job. It does not change planning, capacity, co-processor display, recipe validity, or job contents.

### Compatibility-Disabled Mutable I/O Paths

ACO 1.2.2 unregisters grid-tick deferral, Import/Export Bus caps, IO Port
incremental transfer, capability caching, and storage simulation caching. Their
configuration keys remain readable no-ops. AE2 exclusively owns live storage
insertion, extraction, rollback, and cell transfer.

### Failed Automatic Craft Request Backoff

Export-bus-style crafting requests that finish without creating a crafting link
can be throttled for the exact same owner, slot, key, and amount. This separate
path reduces failed craft-request spam while leaving active and successful jobs
alone; it does not cap or replace Import/Export Bus transfers.

### AE2-UEL-Inspired Optimization Paths

The retained AE2-UEL/GTNH ideas are non-mutating: prune structurally invalid
crafting candidates, memoize calculation-invariant queries for one job,
coalesce provider refreshes before calculation, rebuild indexes on exact
provider-content generations, and reuse machine recipe candidates only after
the machine mod's live validation. Mutable storage and terminal paths are not
intercepted in 1.2.2.

An additional opt-in client path projects terminal names, IDs, tags, tooltips, and sort keys on the client thread, then performs only immutable search matching and sorting in a worker. A generation check discards stale results.

The active non-mutating paths are under `[uelOptimizations]` and can be disabled independently. Removed mutable-path keys remain readable compatibility no-ops.

### Compatibility-Disabled Terminal Rewrites

Terminal inventory snapshot reuse, terminal craftable reuse, rolling range
synchronization, aggregate storage coalescing, storage watcher pacing, and client
view coalescing have been unregistered since 1.2.1. Their keys remain readable
for existing configs but are no-ops until a replacement passes the terminal
interaction regression matrix.

### Machine Intent Boundary

The optimizer now includes a conservative recipe intent bridge at the AE2 Pattern Provider boundary.

When a Pattern Provider successfully pushes a pattern, ACO records a short-lived intent containing the provider position, adjacent target candidate, pattern definition id, concrete pushed inputs, and outputs.

This is the foundation for GTCEu, Mekanism, and future Create fast paths where a machine can avoid rediscovering the recipe every tick.

GTCEu machines with a fresh intent try a small output-indexed candidate list before GTCEu's normal full recipe iterator. Concrete pushed item/fluid inputs prioritize candidates, and a chunk-bucketed nearby lookup connects input-bus/hatch intents to a multiblock controller. If no candidate works, GTCEu's original search still runs.

Repeated GTCEu searches for the same target and current intent outputs reuse the immutable candidate prefix until the intent expires.

Mekanism machines with a fresh intent for their position try output-indexed recipe candidates before Mekanism's normal lookup. Item, fluid, and chemical outputs are indexed, and candidates are returned only after Mekanism's own recipe `test` accepts the current machine inputs.

Mekanism also caches class-level reflection plans and a short-lived resolved recipe. A cache hit still runs Mekanism's live recipe `test`, so changed inputs immediately reject the cached recipe and fall back to normal candidate discovery.

Create machine-side fast paths are still reserved config entries.

### Transactional Pattern Batching API

ACO 1.2.2 unregisters the 1.2.0/1.2.1 transactional execution Mixins. Their
API and config keys remain for compatibility, but standard and Advanced AE CPUs
do not call them.

The development tree contains a separate V2 prepare/accept/account/reconcile
protocol with source, target, and world-journal NBT. It is isolated behind a
new master and child switches that all default to `false`; it is not part of
the active 1.2.2 runtime. See
[Experimental Crafting Engine](docs/EXPERIMENTAL_ENGINE.md).

Normal runtime throughput uses Sequential Instant instead. GTCEu and Mekanism
inputs are not multiplied into one aggregate stack, so machine slots and tanks
apply their normal backpressure through AE2.

### Add-on Machine Optimization

AdvancedAE Reaction Chambers reuse the recipe that their own `findRecipe` call already resolved after an inventory change. The original recipe finder remains authoritative; ACO only prevents the immediately repeated lookup from rebuilding the same input list and searching again.

ACO caches reflection metadata and MethodHandles inside AE2 Overclock's own runtime helper classes and reuses overclock/parallel card counts within one server tick. Recipe checks, energy use, output insertion, and the actual accelerated work remain in AE2 Overclock. Direct redirects into machine handlers added by AE2 Overclock's own Mixins are compatibility-disabled in 1.1.0 because Forge Mixin cannot safely inject into those merged methods; those handlers retain AE2 Overclock's original reflection path.

ExtendedAE Assembly Matrix crafters reuse used-thread and total busy-thread counts within one server tick. Every job, inventory mutation, thread-state change, load, stop, and crafting execution invalidates the relevant cache. The busy-count path also captures ExtendedAE Plus's 32-thread-aware result instead of replacing it. Identical visual/status broadcasts from one matrix cluster are coalesced only within the same tick; formation, destruction, crafting threads, and pattern execution are unchanged.

These optional integrations are enabled by default under `[addonMachineOptimizations]` and can be disabled independently.

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
- Change Quantum Computer capacity, structure rules, displayed co-processor count, or crafting result.
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
- Advanced AE Reaction Chamber
- ExtendedAE Circuit Cutter and Assembly Matrix
- AE2 Overclock
- ExtendedAE tickable class hints
- Advanced AE
- Neo ECO AE Extension 20.3.x
- AppliedE 0.14.3
- AppliedE TPS Fix 0.14.7-fix2
- Advanced Quantum Engineering
- EMI
- JEI
- KubeJS
- Dedicated servers
- Singleplayer
- Arclight

No Bukkit or Paper APIs are used.

Only AE2 is a hard runtime dependency. GTCEu, Mekanism, Advanced AE, and Neo ECO hooks use optional pseudo-Mixins with non-fatal injection requirements; when an optional mod is absent, its target is not applied. AppliedE compatibility uses the shared public AE2 Pattern interfaces and exact implementation-name guards, so neither AppliedE implementation is linked or bundled. Neo ECO 20.3.0 is a compile-only signature target and is not bundled in the ACO jar.

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

# Experimental opt-in. Returns the first strictly proven blocker immediately,
# ending AE2's full missing-item calculation for that request.
fastFailMissingCrafts = false
minimumRequestedAmountForFastFail = 1
deterministicPreflightMaxDepth = 64
deterministicPreflightMaxNodes = 4096
logFastFailMissingCrafts = false

# Read-through cache for CraftingService.getCraftingFor(output).
cachePatternLookups = true
patternLookupCacheSize = 8192

# Read-through cache for CraftingService.getCraftables(filter).
cacheCraftableSets = false
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

# Bound the combined standard AE2 CPU execution time per ME grid.
sharedCraftingExecutionBudget = true
sharedCraftingExecutionMillisPerGrid = 8
minimumSharedOperationsPerCpu = 1

# Disabled by default to avoid log spam.
logCraftingExecutionThrottling = false

[gridTickBudget]
# Compatibility section in 1.2.2: grid-tick deferral and I/O caps are no-ops.
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

[uelOptimizations]
# Compatibility no-op in 1.2.2; no capability cache Mixin is registered.
cacheAdjacentCapabilityLookups = true
# Compatibility no-op companion key.
cacheAdjacentCapabilitiesAcrossTicks = false
# Compatibility no-op in 1.2.2.
cacheNegativeBusTransferSimulations = true
pruneInvalidCraftingCandidates = true
memoizeCraftingCalculationQueries = true
coalesceCraftingProviderRefreshes = true
trackProviderPatternGenerations = true
incrementalIoPortProcessing = true
ioPortCellSlotsPerTick = 2
cacheImportBusLastSuccessfulSlot = false
cacheExportBusCandidateKeys = true
# Compatibility key; its client Mixin remains unregistered in 1.2.2.
coalesceClientTerminalViewUpdates = false
# Opt-in generation-checked projected search/sort worker.
asyncTerminalSearchSort = false
asyncTerminalMinimumEntries = 2048
# Stops orphaned AE2 Page Up/Down repeats after the physical mouse button is released.
fixStuckAe2ScrollbarRepeat = true
cacheCircuitCutterRecipes = true
cacheCircuitCutterNegativeResults = true
circuitCutterRecipeCacheSize = 4096

[addonMachineOptimizations]
enableAddonMachineOptimizations = true
cacheReactionChamberRecipe = true
cacheAe2OverclockReflection = true
useAe2OverclockMethodHandles = true
cacheAe2OverclockUpgradeCounts = true
cacheAssemblerMatrixThreadCounts = true
cacheAssemblerMatrixBusyCount = true
coalesceAssemblerMatrixStatusUpdates = true
cacheAssemblerMatrixRouting = true

[storageSync]
# Compatibility-disabled since 1.2.1; retained keys cannot register the removed Mixins.
throttleStorageWatcherUpdates = false

# Client-visible synchronization interval when throttling is enabled.
storageWatcherUpdateIntervalTicks = 4

# Server-side ME terminal snapshot pacing.
throttleTerminalInventorySnapshots = false
terminalInventorySnapshotIntervalTicks = 4
cacheTerminalCraftables = false
terminalCraftableCacheTicks = 4

flushImmediatelyOnScreenOpen = true
flushImmediatelyOnCellChange = true
flushImmediatelyOnNetworkTopologyChange = true
maximumBufferedChanges = 4096

[deepAe2Rewrite]
enableDeepAe2RewriteFlags = true
patternSelectionByAvailability = false
patternSelectionMaximumCandidates = 64
# Compatibility key; aggregate refresh Mixin remains unregistered in 1.2.2.
networkForceUpdateCoalescing = false
networkUpdateIntervalTicks = 2
# Compatibility key; terminal range Mixins remain unregistered in 1.2.2.
visibleTerminalRangeSync = false
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

# Compatibility-disabled since 1.1.1. These legacy keys are retained so old
# configs remain readable; enablePatternMicroBatching=true is ignored.
enablePatternMicroBatching = false
maxPatternExecutionsPerMicroBatch = 65536
requireSinglePatternProviderTarget = true
patternMicroBatchTargetNamespaces = ["gtceu", "mekanism"]

[transactionalPatternBatching]
enableTransactionalPatternBatching = false
maxTransactionalPatternBatchExecutions = 65536
enableSequentialPatternProviderBatchAdapter = false
maxSequentialProviderExecutionsPerCall = 256
enableInstantPatternDispatch = true
instantPatternDispatchTimeBudgetMillis = 4
instantPatternDispatchProbeOperations = 65536
instantPatternDispatchMaximumWaveOperations = 65536
maxInstantPatternDispatchTransactions = 1024
requireSingleTransactionalBatchTarget = true
transactionalBatchTargetNamespaces = ["gtceu", "mekanism"]

# GTCEu fast path is active. It prepends output-indexed candidates
# before GTCEu's original recipe iterator.
enableGtceuRecipeIntentFastPath = true
gtceuRecipeIntentMaximumCandidates = 16
gtceuRecipeIntentIndexCacheSize = 64
gtceuRecipeIntentSearchRadius = 16
gtceuRecipeIntentNearbyMaximumEntries = 64
logGtceuRecipeIntentFastPath = false

# Mekanism fast path validates candidates with Mekanism recipe tests.
enableMekanismRecipeIntentFastPath = true
mekanismRecipeIntentMaximumCandidates = 16
mekanismRecipeIntentIndexCacheSize = 128
logMekanismRecipeIntentFastPath = false

# Reuse short-lived candidates. Mekanism still validates live inputs;
# GTCEu still runs its original candidate checks.
cacheResolvedRecipeIntents = true
resolvedRecipeIntentCacheSize = 8192
enableCreateRecipeIntentFastPath = false

logCapturedRecipeIntents = false
logRecipeIntentRegistryEvictions = false

[diagnostics]
logSlowCraftCalculations = true
slowCraftCalculationMillis = 500
logCacheStatistics = false

[compatibility.neoEcoAe]
# Applies only when Neo ECO AE Extension 20.3.x is installed.
throttleNeoEcoAeExecution = true

[compatibility.appliedE]
# AppliedE keeps ownership of EMC accounting and request-sized temporary patterns.
enableAppliedECompatibility = true
forceAe2PlannerForTransmutationPatterns = true
treatAppliedEProviderAsDynamic = true
```

Recipe intent diagnostics:

```text
/aco intents
/aco intents list 10
/aco intents clear
/aco stats
/aco stats reset
```

## Design Philosophy

This mod follows three principles.

### Preserve AE2

AE2 remains the single source of truth.

The optimizer never replaces AE2's internal crafting logic.

### Improve User Experience

Large crafting calculations and automation bursts may take time.

The default keeps AE2's normal Craft Confirm, graph-solver, terminal, watcher, and packet paths. Safe deep defaults retain duplicate P2P notification suppression and exact single-fluid input matching. Availability-based pattern ordering and Export Bus fuzzy caching remain explicit experiments.

### Safe Optimization

The generated defaults enable diagnostics, exact duplicate active-calculation sharing, a short missing/simulation completed-plan cache, pattern lookup caching, crafting execution pacing, Pattern Provider intent capture, GTCEu/Mekanism intent fast paths, and Neo ECO execution pacing when that optional mod is present. Mutable storage, terminal craftable, and legacy transactional execution Mixins remain unregistered.

Preliminary missing previews, deterministic fast-fail, failed Export Bus request throttling, availability-based pattern ordering, successful-plan reuse, and the reserved Create path are disabled by default. Grid-tick deferral, IO-bus caps, mutable bus/IO Port caches, terminal snapshot/craftable reuse, watcher pacing, aggregate refresh coalescing, visible range splitting, and client view coalescing are unregistered compatibility paths in 1.2.2.

Craft validity, recipes, storage mutation, and final crafting results remain controlled by AE2.

## Project Separation

This repository intentionally focuses only on crafting responsiveness.

Quantum Computer hardware upgrades are implemented in a separate project:

- Advanced Quantum Engineering

Keeping both projects independent makes debugging, compatibility testing, and future maintenance significantly easier.

## Build

The project resolves Forge from Forge Maven, AE2 `15.4.10` from ModMaven, and the optional Neo ECO `20.3.0` compile-only signature target from CurseMaven. It does not depend on a local Minecraft or Prism Launcher installation.

Run:

```bat
gradlew.bat clean build
```

This branch generates `build/libs/ae2-crafting-optimizer-1.4.1.jar`. Building
it does not authorize deployment or feature enablement before the runtime
matrix is complete. GitHub Actions runs the same clean build for
pushes and pull requests.

## Documentation

- [Team development specification and design references](docs/TEAM_DEVELOPMENT_SPEC.md)
- [Experimental engine, BigInteger API, and runtime qualification](docs/EXPERIMENTAL_ENGINE.md)
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
