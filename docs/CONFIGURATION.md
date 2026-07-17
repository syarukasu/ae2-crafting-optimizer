# Configuration

ACO uses a Forge server config. In a dedicated server or an existing world, edit:

```text
<world>/serverconfig/ae2_crafting_optimizer-server.toml
```

Restart the server after changing optimization settings. Existing config files retain their old values; changing a source-code default does not rewrite an existing world config.

## Default-Enabled Paths

| Area | Key | Default | Purpose |
| --- | --- | --- | --- |
| Master | `enableOptimizer` | `true` | Disables all ACO behavior when false. |
| Calculation | `deduplicateActiveCraftingCalculations` | `true` | Shares identical in-flight calculations from the same requester and grid. |
| Calculation | `cacheCompletedCraftingPlans` | `true` | Briefly caches missing/simulation plans; successful plans remain excluded by default. |
| Calculation | `cachePatternLookups` | `true` | Reuses exact pattern lookups until provider/grid invalidation. |
| Calculation | `cacheCraftableSets` | `true` | Reuses filtered craftable sets until provider/grid invalidation. |
| Execution | `throttleCraftingExecution` | `true` | Caps the effective AE2 pattern-push window without changing CPU stats. |
| Execution | `adaptiveCraftingExecutionBudget` | `true` | Adjusts the active CPU budget toward the configured tick-time target. |
| Execution | `sharedCraftingExecutionBudget` | `true` | Bounds combined supported CPU pattern-push time on one ME grid. |
| Neo ECO compatibility | `throttleNeoEcoAeExecution` | `true` | Adds Neo ECO 20.3.x custom CPUs to ACO's adaptive and shared execution budgets when installed. |
| Storage view | `throttleStorageWatcherUpdates` | `true` | Buffers client-visible watcher updates for four ticks by default. |
| Deep master | `enableDeepAe2RewriteFlags` | `true` | Master switch for the deep sub-options below. |
| Deep sync | `networkForceUpdateCoalescing` | `true` | Coalesces repeated aggregate storage refreshes. |
| Deep topology | `p2pTopologyChangeOnlyRecheck` | `true` | Deduplicates equivalent P2P notifications in a short window. |
| Deep fluid | `fluidPatternRework` | `true` | Uses an exact single-fluid input fast path and falls back for ambiguous cases. |
| Intent | `enableRecipeIntentBridge` | `true` | Enables the Pattern Provider intent registry. |
| Intent | `enableGtceuRecipeIntentFastPath` | `true` | Prepends GTCEu candidates validated by GTCEu's normal recipe path. |
| Intent | `enableMekanismRecipeIntentFastPath` | `true` | Returns a candidate only after Mekanism's recipe test accepts current inputs. |
| Intent | `cacheResolvedRecipeIntents` | `true` | Reuses short-lived GTCEu candidate prefixes and live-validated Mekanism recipes. |
| Intent | `gtceuRecipeIntentSearchRadius` | `16` | Associates GT input-bus/hatch intents with nearby multiblock controllers through a chunk-bucketed lookup. |
| Intent | `gtceuRecipeIntentNearbyMaximumEntries` | `64` | Bounds nearby intents considered by one GT recipe search. |
| Add-on machines | `enableAddonMachineOptimizations` | `true` | Master switch for conservative AdvancedAE, ExtendedAE, and AE2 Overclock caches. |
| Add-on machines | `cacheReactionChamberRecipe` | `true` | Seeds AdvancedAE's existing Reaction Chamber task cache from its completed recipe lookup. |
| Add-on machines | `cacheAe2OverclockReflection` | `true` | Caches immutable Field/Method discovery inside AE2 Overclock's runtime helper classes. |
| Add-on machines | `useAe2OverclockMethodHandles` | `true` | Uses cached MethodHandles in those runtime helpers, with reflection fallback. |
| Add-on machines | `cacheAe2OverclockUpgradeCounts` | `true` | Reuses overclock/parallel card counts on the same host for one server tick. |
| Add-on machines | `cacheAssemblerMatrixThreadCounts` | `true` | Reuses one ExtendedAE matrix crafter's used-thread count within a tick. |
| Add-on machines | `cacheAssemblerMatrixBusyCount` | `true` | Reuses a matrix cluster's complete busy-thread total within a tick. |
| Add-on machines | `coalesceAssemblerMatrixStatusUpdates` | `true` | Coalesces identical same-tick matrix visual/status broadcasts. |
| Add-on machines | `cacheAssemblerMatrixRouting` | `true` | Reuses a still-available Assembly Matrix crafter until job/structure state invalidates it. |
| UEL safe paths | `cacheAdjacentCapabilityLookups` | `true` | Reuses a successful adjacent capability lookup for one server tick after Block Entity identity verification. |
| UEL safe paths | `cacheNegativeBusTransferSimulations` | `true` | Reuses exact failed bus simulations for one server tick; never skips real transfers. |
| UEL safe paths | `pruneInvalidCraftingCandidates` | `true` | Removes only null, duplicate, wrong-output, and structurally invalid candidates. |
| UEL safe paths | `memoizeCraftingCalculationQueries` | `true` | Caches only calculation-invariant queries for one job; simulated inventory amounts are excluded. |
| UEL safe paths | `coalesceCraftingProviderRefreshes` | `true` | Coalesces repeated provider refreshes and flushes before calculation. |
| UEL safe paths | `trackProviderPatternGenerations` | `true` | Rebuilds provider indexes only after exact provider content changes. |
| UEL safe paths | `incrementalIoPortProcessing` | `true` | Advances IO Port input cells from a persistent round-robin cursor. |
| UEL safe paths | `ioPortCellSlotsPerTick` | `2` | Bounds the cell slots inspected per IO Port grid tick. |
| UEL safe paths | `cacheImportBusLastSuccessfulSlot` | `true` | Tries the last successful slot first and then scans all remaining slots. |
| UEL safe paths | `cacheExportBusCandidateKeys` | `true` | Reuses configured Export Bus keys until its config generation changes. |
| UEL safe paths | `cacheCircuitCutterRecipes` | `true` | Reuses exact-input ExtendedAE Circuit Cutter candidates after its live recipe validation. |
| UEL safe paths | `cacheCircuitCutterNegativeResults` | `true` | Shares exact no-recipe results until input change or datapack reload. |
| UEL safe paths | `circuitCutterRecipeCacheSize` | `4096` | Bounds shared Circuit Cutter candidates. |

These defaults still need pack-specific runtime testing. "Enabled by default" is not a promise of compatibility with every AE2 addon or coremod stack.

The AE2 Overclock upgrade-count cache can delay recognition of a card inserted after that machine was already inspected in the same server tick. The next tick always performs a new lookup. Disabling the option restores AE2 Overclock's original repeated scan path.

## Opt-In Experimental Paths

| Key | Default | Main risk |
| --- | --- | --- |
| `twoStageMissingPreview` | `false` | Changes calculation presentation and has legacy companion flags. |
| `fastFailMissingCrafts` | `false` | Returns early only for strictly provable deterministic missing paths. |
| `cacheSuccessfulCompletedCraftingPlans` | `false` | A successful plan may become stale before submission. |
| `enableGridTickBudget` | `false` | Defers selected non-progress-sensitive grid tickables and can reduce automation throughput. |
| `deferHeavyGridTickables` | `false` | Requires the grid-tick master and pack-specific transfer tests. |
| `backoffIdleGridTickables` | `false` | Intentionally delays repeated idle polls. |
| `limitIoBusOperationsPerTick` | `false` | Directly caps Import/Export Bus work per tick. |
| `throttleExportBusCraftRequests` | `false` | Delays repeated failed automatic crafting requests. |
| `patternSelectionByAvailability` | `false` | Changes candidate attempt order, but not validity. |
| `busSearchRewrite` | `false` | Reuses fuzzy Export Bus search results for a short interval. |
| `cacheAdjacentCapabilitiesAcrossTicks` | `false` | Depends on external capability providers correctly invalidating their LazyOptional. |
| `asyncTerminalSearchSort` | `false` | Moves immutable projected search/sort work to a worker and discards stale generations. |
| `asyncTerminalMinimumEntries` | `2048` | Minimum terminal size for the async path. |
| `coalesceClientTerminalViewUpdates` | `false` | Can desynchronize clickable virtual slots from the repository generation on heavily modified clients. |
| `throttleTerminalInventorySnapshots` | `false` | A stale zero-stock snapshot can conflict with an insertion click. |
| `cacheTerminalCraftables` | `false` | Keeps terminal craftable transitions immediate by default. |
| `visibleTerminalRangeSync` | `false` | Splits one coherent terminal generation across packets/menu ticks. |
| `enableCreateRecipeIntentFastPath` | `false` | Reserved; no Create machine fast path is implemented in 1.0.0. |
| `enablePatternMicroBatching` | `false` | Compatibility-disabled in 1.1.1. `true` is ignored because aggregate acceptance cannot guarantee multiplied recipe outputs. |
| `maxPatternExecutionsPerMicroBatch` | `65536` | Legacy no-op retained for existing config compatibility. |
| `requireSinglePatternProviderTarget` | `true` | Legacy no-op retained for existing config compatibility. |
| `patternMicroBatchTargetNamespaces` | `["gtceu", "mekanism"]` | Legacy no-op retained for existing config compatibility. |

### Transactional Pattern Batching

| Key | Default | Notes |
|---|---:|---|
| `enableTransactionalPatternBatching` | `true` | Enables accepted-execution-count adapters. Unsupported cases fall back before input transfer. |
| `maxTransactionalPatternBatchExecutions` | `65536` | Maximum exact executions prepared for one adapter transaction; the adapter may accept fewer. |
| `enableSequentialPatternProviderBatchAdapter` | `true` | Enables the conservative built-in adapter that preserves one AE2 push per accepted execution. |
| `maxSequentialProviderExecutionsPerCall` | `256` | Bounds original AE2 pushes inside one conservative adapter transaction. |
| `enableInstantPatternDispatch` | `true` | Continues through ready tasks and batches in the same CPU call; does not make machines zero-tick. |
| `instantPatternDispatchTimeBudgetMillis` | `4` | Hard wall-clock deadline for one instant-dispatch CPU call. |
| `maxInstantPatternDispatchTransactions` | `1024` | Hard transaction-count boundary in addition to operation and time limits. |
| `requireSingleTransactionalBatchTarget` | `true` | Requires deterministic one-side routing before native-style adapters are considered. |
| `transactionalBatchTargetNamespaces` | `["gtceu", "mekanism"]` | Limits eligible adjacent machine namespaces. |

The old aggregate-inventory keys above do not control this API. They remain disabled because `pushPattern() == true` does not prove complete aggregate insertion or N machine recipe executions.

Enable one experimental path at a time and repeat the relevant section in [TESTING.md](TESTING.md).

Import/Export Buses and ExtendedAE Circuit Cutters are always exempt from hard Grid Tick deferral and idle/slow backoff. Their work is optimized through bounded operations and validated caches so a late iteration position cannot starve them.

## Giant CPU Defaults

```toml
[craftingExecution]
throttleCraftingExecution = true
maxEffectiveCoprocessorsPerCpu = 264192
adaptiveCraftingExecutionBudget = true
targetCraftingExecutionMillis = 4
minimumAdaptiveCoprocessorsPerCpu = 1024

# Combined standard AE2 CPU target for one ME grid in one tick.
sharedCraftingExecutionBudget = true
sharedCraftingExecutionMillisPerGrid = 8

# Each active CPU still makes forward progress after the shared budget is spent.
minimumSharedOperationsPerCpu = 1

[compatibility.neoEcoAe]
# Active only when Neo ECO AE Extension 20.3.x is present.
throttleNeoEcoAeExecution = true
```

ACO does not lower the CPU's displayed storage or co-processor count. It limits how much of AE2's pattern-push execution loop one CPU may spend in a server tick. Raising the maximum can increase throughput and MSPT together.

The shared budget is deliberately separate from the per-CPU adaptive target. A `4 ms` per-CPU target alone does not prevent four active CPUs from consuming roughly `16 ms` together. The shared `8 ms` target bounds that aggregate burst while retaining a one-operation progress allowance per active CPU. Neo ECO's normal and fast-path limits are fed through the same budget when `throttleNeoEcoAeExecution` is enabled; its own lower limits still win.

## Recovery Switch

When diagnosing a suspected ACO regression, first set:

```toml
[general]
enableOptimizer = false
```

Restart and reproduce with the same world, network, request, and machine. Include both runs in the issue report.
