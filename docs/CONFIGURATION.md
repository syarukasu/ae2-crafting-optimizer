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
| Storage view | `throttleStorageWatcherUpdates` | `true` | Buffers client-visible watcher updates for four ticks by default. |
| Terminal | `throttleTerminalInventorySnapshots` | `true` | Reuses terminal inventory snapshots briefly. |
| Terminal | `cacheTerminalCraftables` | `true` | Reuses the terminal craftable set briefly. |
| Deep master | `enableDeepAe2RewriteFlags` | `true` | Master switch for the deep sub-options below. |
| Deep sync | `networkForceUpdateCoalescing` | `true` | Coalesces repeated aggregate storage refreshes. |
| Deep sync | `visibleTerminalRangeSync` | `true` | Splits large terminal deltas into bounded ranges. |
| Deep topology | `p2pTopologyChangeOnlyRecheck` | `true` | Deduplicates equivalent P2P notifications in a short window. |
| Deep fluid | `fluidPatternRework` | `true` | Uses an exact single-fluid input fast path and falls back for ambiguous cases. |
| Intent | `enableRecipeIntentBridge` | `true` | Enables the Pattern Provider intent registry. |
| Intent | `enableGtceuRecipeIntentFastPath` | `true` | Prepends GTCEu candidates validated by GTCEu's normal recipe path. |
| Intent | `enableMekanismRecipeIntentFastPath` | `true` | Returns a candidate only after Mekanism's recipe test accepts current inputs. |

These defaults still need pack-specific runtime testing. "Enabled by default" is not a promise of compatibility with every AE2 addon or coremod stack.

## Opt-In Experimental Paths

| Key | Default | Main risk |
| --- | --- | --- |
| `twoStageMissingPreview` | `false` | Changes calculation presentation and has legacy companion flags. |
| `fastFailMissingCrafts` | `false` | Returns early only for strictly provable deterministic missing paths. |
| `cacheSuccessfulCompletedCraftingPlans` | `false` | A successful plan may become stale before submission. |
| `enableGridTickBudget` | `false` | Defers selected grid tickables and can reduce automation throughput. |
| `deferHeavyGridTickables` | `false` | Requires the grid-tick master and pack-specific transfer tests. |
| `backoffIdleGridTickables` | `false` | Intentionally delays repeated idle polls. |
| `limitIoBusOperationsPerTick` | `false` | Directly caps Import/Export Bus work per tick. |
| `throttleExportBusCraftRequests` | `false` | Delays repeated failed automatic crafting requests. |
| `patternSelectionByAvailability` | `false` | Changes candidate attempt order, but not validity. |
| `busSearchRewrite` | `false` | Reuses fuzzy Export Bus search results for a short interval. |
| `enableCreateRecipeIntentFastPath` | `false` | Reserved; no Create machine fast path is implemented in 1.0.0. |

Enable one experimental path at a time and repeat the relevant section in [TESTING.md](TESTING.md).

## Giant CPU Defaults

```toml
[craftingExecution]
throttleCraftingExecution = true
maxEffectiveCoprocessorsPerCpu = 264192
adaptiveCraftingExecutionBudget = true
targetCraftingExecutionMillis = 4
minimumAdaptiveCoprocessorsPerCpu = 1024
```

ACO does not lower the CPU's displayed storage or co-processor count. It limits how much of AE2's pattern-push execution loop one CPU may spend in a server tick. Raising the maximum can increase throughput and MSPT together.

## Recovery Switch

When diagnosing a suspected ACO regression, first set:

```toml
[general]
enableOptimizer = false
```

Restart and reproduce with the same world, network, request, and machine. Include both runs in the issue report.
