# Configuration

ACO uses a global Forge common config. Edit this file in both the dedicated
server and client installation:

```text
config/ae2_crafting_optimizer-common.toml
```

Restart the game or server after changing optimization settings. Keep both
copies aligned: server-side gameplay behavior uses the server copy and
client-only display optimizations use the client copy. Legacy
`defaultconfigs/ae2_crafting_optimizer-server.toml` and per-world
`serverconfig/ae2_crafting_optimizer-server.toml` files are no longer read.
Changing a source-code default does not rewrite an existing common config.

## Default-Enabled Paths

| Area | Key | Default | Purpose |
| --- | --- | --- | --- |
| Master | `enableOptimizer` | `true` | Disables normal ACO optimization behavior when false. The separately configured explicit-host BigInteger API can remain available for state recovery. |
| Calculation | `deduplicateActiveCraftingCalculations` | `true` | Shares identical in-flight calculations from the same requester and grid. |
| Calculation | `cacheCompletedCraftingPlans` | `true` | Briefly caches missing/simulation plans; successful plans remain excluded by default. |
| Calculation | `cachePatternLookups` | `true` | Reuses exact pattern lookups until provider/grid invalidation. |
| Execution | `throttleCraftingExecution` | `true` | Caps the effective AE2 pattern-push window without changing CPU stats. |
| Execution | `adaptiveCraftingExecutionBudget` | `true` | Adjusts the active CPU budget toward the configured tick-time target. |
| Execution | `sharedCraftingExecutionBudget` | `true` | Bounds combined supported CPU pattern-push time on one ME grid. |
| Neo ECO compatibility | `throttleNeoEcoAeExecution` | `true` | Adds Neo ECO 20.3.x custom CPUs to ACO's adaptive and shared execution budgets when installed. |
| Deep master | `enableDeepAe2RewriteFlags` | `true` | Master switch for the deep sub-options below. |
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
| UEL safe paths | `pruneInvalidCraftingCandidates` | `true` | Removes only null, duplicate, wrong-output, and structurally invalid candidates. |
| UEL safe paths | `memoizeCraftingCalculationQueries` | `true` | Caches only calculation-invariant queries for one job; simulated inventory amounts are excluded. |
| UEL safe paths | `coalesceCraftingProviderRefreshes` | `true` | Coalesces repeated provider refreshes and flushes before calculation. |
| UEL safe paths | `trackProviderPatternGenerations` | `true` | Rebuilds provider indexes only after exact provider content changes. |
| UEL safe paths | `cacheCircuitCutterRecipes` | `true` | Reuses exact-input ExtendedAE Circuit Cutter candidates after its live recipe validation. |
| UEL safe paths | `cacheCircuitCutterNegativeResults` | `true` | Shares exact no-recipe results until input change or datapack reload. |
| UEL safe paths | `circuitCutterRecipeCacheSize` | `4096` | Bounds shared Circuit Cutter candidates. |

These defaults still need pack-specific runtime testing. "Enabled by default" is not a promise of compatibility with every AE2 addon or coremod stack.

### Compatibility-Disabled Sync Paths Since 1.2.1

The following keys remain readable for existing world configs, but their Mixins
remain unregistered in 1.2.2:

| Key | Source default | Current behavior |
| --- | --- | --- |
| `throttleStorageWatcherUpdates` | `false` | No-op; `StorageServiceWatcherThrottleMixin` is unregistered. |
| `networkForceUpdateCoalescing` | `false` | No-op; `StorageServiceDeepCoalescingMixin` is unregistered. |
| `throttleTerminalInventorySnapshots` | `false` | No-op; `MEStorageMenuSyncOptimizationMixin` is unregistered. |
| `cacheTerminalCraftables` | `false` | No-op; `MEStorageMenuSyncOptimizationMixin` is unregistered. |
| `visibleTerminalRangeSync` | `false` | No-op; both terminal range packet Mixins are unregistered. |
| `coalesceClientTerminalViewUpdates` | `false` | No-op; `ClientRepoUpdateCoalescingMixin` is unregistered. |

These paths were removed from the runtime Mixin list after stale terminal generations could conflict with live insertion. Changing the retained keys to `true` does not reactivate them.

### Compatibility-Disabled Mutable Paths in 1.2.2

These retained keys cannot reactivate the Mixins removed in 1.2.2:

| Key | Source default | 1.2.2 behavior |
| --- | --- | --- |
| `cacheCraftableSets` | `false` | No-op; AE2 computes terminal craftables directly. |
| `cacheAdjacentCapabilityLookups` | `true` | No-op; `BlockApiCacheTickCacheMixin` is unregistered. |
| `cacheAdjacentCapabilitiesAcrossTicks` | `false` | No-op companion key; no capability cache Mixin is registered. |
| `cacheNegativeBusTransferSimulations` | `true` | No-op; both storage simulation Mixins are unregistered. |
| `incrementalIoPortProcessing` | `true` | No-op; AE2 owns complete IO Port cell transfer. |
| `ioPortCellSlotsPerTick` | `2` | No-op companion limit. |
| `cacheImportBusLastSuccessfulSlot` | `false` | No-op; AE2 owns Import Bus extraction and rollback. |
| `cacheExportBusCandidateKeys` | `true` | No-op; the custom Export Bus key Mixin is unregistered. |
| `enableGridTickBudget` / `deferHeavyGridTickables` / `backoffIdleGridTickables` | `false` | No-op; the grid tick deferral Mixin is unregistered. |
| Grid tick interval, time, class-hint, and backoff limits | retained | No-op companion limits. |
| `limitIoBusOperationsPerTick` | `false` | No-op; standard and ExtendedAE I/O cap Mixins are unregistered. |
| `busSearchRewrite` | `false` | No-op; the fuzzy Export Bus Mixin is unregistered. |

ACO 1.2.2 deliberately leaves every live storage insertion, extraction,
rollback, cell transfer, and terminal repository generation to AE2.

The AE2 Overclock upgrade-count cache can delay recognition of a card inserted after that machine was already inspected in the same server tick. The next tick always performs a new lookup. Disabling the option restores AE2 Overclock's original repeated scan path.

## Opt-In Experimental Paths

### Next-Generation Crafting Engine

This section is a development foundation and is disabled at both the master and
behavior-changing child levels. Do not copy these values into a live world until
the qualification in [EXPERIMENTAL_ENGINE.md](EXPERIMENTAL_ENGINE.md) is complete.
The master accepts only the researched AE2 `15.4.10`; enabled Advanced AE V2 or
fair scheduling accepts only `1.3.5-1.20.1`.

| Key | Default | Purpose |
| --- | --- | --- |
| `enableExperimentalCraftingEngine` | `false` | Master switch for behavior-changing planner, V2 batching, and fair-scheduler paths. The explicit-host BigInteger API is independent. |
| `enableShadowMode` | `false` | Diagnostic comparison only; requires the master and compiled graph. |
| `enableCompiledCraftingGraph` | `false` | Builds the immutable generation-keyed graph; unsupported or unproven plans still fall back to AE2. |
| `enableTransactionalBatchingV2` | `false` | Enables durable prepare/accept/account/reconcile transactions. |
| `enableGtceuNativeBatching` | `false` | Enables exact all-or-zero GTCEu native batches; requires V2. |
| `enableMekanismNativeBatching` | `false` | Enables exact item/fluid/chemical Mekanism native batches; requires V2. |
| `enableFairCraftingJobScheduler` | `false` | Enables persistent DRR scheduling for AE2 and Advanced AE CPUs. |
| `fairSchedulerOperationsPerTick` | `4096` | Hard operation budget per supported ME grid and tick. |
| `fairSchedulerQuantum` | `64` | Deficit-round-robin allocation quantum. |
| `fairSchedulerTimeBudgetMillis` | `4` | Measured execution-time budget per grid and tick. |
| `persistBatchTransactionJournal` | `true` | Required safety dependency for new V2 execution; existing journal records are still reconciled when feature switches are off. |
| `batchTransactionJournalMaximumEntries` | `16384` | Hard journal bound, range `16..16384`; new V2 work falls back when it is full. |
| `batchTransactionReconciliationIntervalTicks` | `20` | Interval for bounded unresolved-transaction recovery scans. |
| `nativeBatchMaximumExecutions` | `65536` | Checked-long per-transaction hard cap. |
| `enableBigIntegerCraftingBackend` | `true` | Exposes API v3 to an explicitly integrating CPU add-on; does not patch normal AE2 or Advanced AE CPUs and has no effect without a registered host. |
| `bigIntegerMaximumBits` | `256` | Maximum non-negative count and planner-intermediate magnitude. Range `64..1048576` binary bits; 256 bits is about 77 decimal digits. |
| `bigIntegerExecutionWindow` | `65536` | Maximum executions exposed to a long/int machine adapter in one window. |
| `bigIntegerStatusPageEntries` | `1024` | Configured job summaries per status page; hard protocol cap `16384`. |
| `bigIntegerRuntimeCountBudgetMiB` | `256` | Aggregate encoded-count budget for one BigInteger CPU runtime. Range `32..4096` MiB. |

No empty scheduler, receipt, or BigInteger job NBT is written while these paths
remain unused. BigInteger status uses Forge channel protocol `2` and payload
protocol `1`, with a hard `1 MiB` packet cap. Server and clients must use the
same ACO jar before an integrating add-on uses status synchronization.

| Key | Default | Main risk |
| --- | --- | --- |
| `twoStageMissingPreview` | `false` | Changes calculation presentation and has legacy companion flags. |
| `fastFailMissingCrafts` | `true` | Returns early only for strictly provable deterministic item, fluid, or chemical missing paths. |
| `cacheSuccessfulCompletedCraftingPlans` | `false` | A successful plan may become stale before submission. |
| `throttleExportBusCraftRequests` | `false` | Delays repeated failed automatic crafting requests. |
| `patternSelectionByAvailability` | `false` | Changes candidate attempt order, but not validity. |
| `asyncTerminalSearchSort` | `false` | Moves immutable projected search/sort work to a worker and discards stale generations. |
| `asyncTerminalMinimumEntries` | `2048` | Minimum terminal size for the async path. |
| `enableCreateRecipeIntentFastPath` | `false` | Reserved; no Create machine fast path is implemented. |
| `enablePatternMicroBatching` | `false` | Compatibility-disabled in 1.1.1. `true` is ignored because aggregate acceptance cannot guarantee multiplied recipe outputs. |
| `maxPatternExecutionsPerMicroBatch` | `65536` | Legacy no-op retained for existing config compatibility. |
| `requireSinglePatternProviderTarget` | `true` | Legacy no-op retained for existing config compatibility. |
| `patternMicroBatchTargetNamespaces` | `["gtceu", "mekanism"]` | Legacy no-op retained for existing config compatibility. |

### Sequential Instant Dispatch and Legacy Batch API

The 1.2.0/1.2.1 execution Mixins for this legacy section are unregistered in
1.2.2. The old transaction keys remain readable but cannot intercept standard
or Advanced AE CPU execution. `enableInstantPatternDispatch` is independent:
it time-slices AE2's original `executeCrafting` waves without aggregating
inputs or replacing AE2 accounting.

| Key | Default | Notes |
|---|---:|---|
| `enableTransactionalPatternBatching` | `false` | Compatibility no-op in 1.2.2; no execution Mixin is registered. |
| `maxTransactionalPatternBatchExecutions` | `65536` | Maximum exact executions prepared for one adapter transaction; the adapter may accept fewer. |
| `enableSequentialPatternProviderBatchAdapter` | `false` | Legacy adapter switch; it is not connected to CPU execution. |
| `maxSequentialProviderExecutionsPerCall` | `256` | Bounds original AE2 pushes inside one conservative adapter transaction. |
| `enableInstantPatternDispatch` | `true` | Runs normal AE2 one-pattern pushes in measured waves until `maxPatterns`, backpressure, or a time budget stops the tick. |
| `instantPatternDispatchTimeBudgetMillis` | `4` | Per-CPU wall-clock budget per server tick. |
| `instantPatternDispatchProbeOperations` | `65536` | Cold-start size of one measured wave; this is not a per-tick cap. The first unmeasured wave can exceed the time target. |
| `instantPatternDispatchMaximumWaveOperations` | `65536` | Maximum size of one measured wave; several waves may run in the same tick. |
| `maxInstantPatternDispatchTransactions` | `1024` | Experimental V2 aggregate-adapter limit only; it does not limit Sequential Instant. |
| `requireSingleTransactionalBatchTarget` | `true` | Requires deterministic one-side routing before native-style adapters are considered. |
| `transactionalBatchTargetNamespaces` | `["gtceu", "mekanism"]` | Limits eligible adjacent machine namespaces. |

The legacy transaction keys do not control V2. Sequential Instant deliberately
uses AE2's original extraction, energy, provider, task, and `waitingFor`
accounting. GTCEu and Mekanism Native Batch remain separate default-off V2
experiments because `pushPattern() == true` alone does not prove N complete
machine executions.

Enable one experimental path at a time and repeat the relevant section in [TESTING.md](TESTING.md).

Import/Export Buses, IO Ports, and ExtendedAE Circuit Cutters use their original
tick scheduling in 1.2.2. Circuit Cutter recipe candidates remain
live-validated; ACO does not defer its machine tick.

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
