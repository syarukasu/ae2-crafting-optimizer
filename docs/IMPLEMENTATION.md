# Implementation

## Scope

This mod keeps AE2's final crafting result authoritative. Active behavior includes diagnostics, calculation de-duplication, short-lived missing/simulation plan caching, pattern/craftable lookup caching, crafting CPU execution pacing, Pattern Provider intent capture, and GTCEu/Mekanism item/fluid/chemical recipe intent fast paths. ACO 1.2.1 leaves AE2's terminal, storage-watcher, aggregate refresh, and terminal packet paths untouched because the corresponding Mixins are unregistered.

## Mixins

- `CraftingCalculationDiagnosticsMixin`
  - Times `CraftingCalculation.run`.
  - Logs only calculations slower than `slowCraftCalculationMillis`.
- `CraftingServiceCalculationDeduplicationMixin`
  - Injects at the start of AE2's `CraftingService.beginCraftingCalculation`.
  - If the same `CraftingService`, same requester object, same dimension, same output key, same amount, and same `CalculationStrategy` already has an unfinished calculation, returns that active `Future<ICraftingPlan>`.
  - Optionally performs strict deterministic missing fast-fail when no active calculation exists.
  - Injects at method return to remember newly started unfinished calculations.
  - Can cache short-lived completed missing/simulation plans.
  - Successful completed plans are not cached unless `cacheSuccessfulCompletedCraftingPlans = true`.
  - Does not synthesize successful `ICraftingPlan` results.
- `CraftingServiceCraftableSetCacheMixin`
  - Caches `CraftingService.getCraftables(filter)` results until crafting providers or nodes change.
  - Stores immutable snapshots of AE2's reported craftable keys.
- `CraftingServicePatternLookupCacheMixin`
  - Caches `CraftingService.getCraftingFor(output)` results until crafting providers or nodes change.
  - Stores immutable snapshots of the returned pattern collections.
- `MEStorageMenuSyncOptimizationMixin`
  - Redirects open ME terminal `MEStorage.getAvailableStacks()` calls through a per-menu snapshot for a few ticks.
  - Caches the private terminal craftable set for a few ticks.
  - Does not affect live insertion/extraction or storage mutation.
  - Not registered in 1.2.1 because a stale zero-stock generation can conflict with clickable virtual slots in heavily modified clients.
- `IncrementalUpdateHelperDeepRangeMixin` and `MEInventoryUpdatePacketBuilderRangeMixin`
  - Drain terminal full/delta changes in bounded rolling ranges.
  - Keep AE2's packet format, serial mapping, filters, and final values.
  - Retain unsent keys for the next menu tick instead of discarding them.
  - Not registered in 1.2.1 so one interactive terminal generation remains coherent.
- `StorageServiceDeepCoalescingMixin`
  - Coalesces the aggregate `StorageService.onServerEndTick` rebuild into a configurable interval.
  - Direct storage insertion/extraction is untouched.
  - Not registered in 1.2.1.
- `P2PServiceTopologyDeduplicationMixin`
  - AE2 15.4.10 already runs topology callbacks only for add/remove/frequency changes, so those callbacks remain untouched.
  - Coalesces duplicate full `wakeInputTunnels` sweeps from boot/power events inside the configured tick window.
- `ExportBusFuzzySearchCacheMixin`
  - Reuses immutable fuzzy-key results for a short bounded interval.
  - Export transfer simulation and extraction still validate the current storage state.
- `CraftingCpuHelperFluidFastPathMixin`
  - Handles a single exact fluid input without building AE2's fuzzy substitute list.
  - Falls back for substitutions or fuzzy-capable keys.
- `MultiCraftingTrackerCraftRequestThrottleMixin`
  - Watches AE2's `MultiCraftingTracker.handleCrafting`.
  - If an already-finished job returns without creating a crafting link, records a short cooldown for that exact owner, slot, key, and amount.
  - Suppresses only the next identical no-job/no-link retry during that cooldown.
- `PatternProviderLogicIntentCaptureMixin`
  - Injects at return of AE2's `PatternProviderLogic.pushPattern`.
  - Records only successful Pattern Provider pushes.
  - Stores short-lived provider-side recipe intent for adjacent target candidates.
  - Does not change item insertion, pattern validity, or machine recipe selection.
- `GTCEuRecipeLogicIntentFastPathMixin`
  - Pseudo-mixin targeting `com.gregtechceu.gtceu.api.machine.trait.RecipeLogic`.
  - Injects at return of `searchRecipe`.
  - Wraps GTCEu's original recipe iterator with a small intent-derived candidate prefix.
  - Does not consume inputs, setup recipes, or bypass GTCEu `checkRecipe`.
  - Falls back to GTCEu's original iterator when no intent/candidate exists or reflection/indexing fails.
- `MekanismRecipeIntentFastPathMixin`
  - Pseudo-mixin targeting Mekanism recipe machine `getRecipe(int)` bridge methods.
  - Tries output-indexed item, fluid, and chemical candidates for the target machine position.
  - Returns a candidate only after Mekanism's own recipe `test` accepts the current machine inputs.
  - Falls back to Mekanism's original recipe lookup when no safe candidate exists.
- `AdvancedAeReactionChamberRecipeCacheMixin`
  - Observes the return value of AdvancedAE `ReactionChamberEntity.findRecipe`.
  - Stores that exact recipe in AdvancedAE's existing `cachedTask` field so the following `getTask` call does not search the unchanged inputs again.
  - Does not select a different recipe or bypass AdvancedAE's own finder.
- `Ae2OverclockRuntimeCacheMixin` and `Ae2OverclockParallelRuntimeCacheMixin`
  - Cache reflection metadata used by AE2 Overclock's own runtime helper classes through `ClassValue` tables.
  - Invoke cached helper Methods through MethodHandles with reflection fallback.
  - Cache overclock and parallel card counts per machine for one server tick.
  - Do not alter process time, parallel multiplier, energy use, input consumption, or output insertion.
- `Ae2OverclockMachineReflectionCacheMixin` is intentionally not registered in 1.1.0.
  - AE2 Overclock adds the target Reaction Chamber and Circuit Cutter handlers through its own higher-priority Mixins.
  - Forge Mixin rejects redirects into methods merged by another Mixin, so ACO leaves those reflection calls untouched instead of producing startup injection warnings.
- `ExtendedAeAssemblerMatrixCrafterCacheMixin`
  - Reuses `usedThread()` within one server tick.
  - Invalidates before thread execution and on jobs, inventory changes, thread-state changes, loading, and stopping.
- `ExtendedAeAssemblerMatrixClusterCacheMixin`
  - Runs before ExtendedAE Plus's default-priority `getBusyCrafterAmount` handler so a same-tick cache hit can return before another full scan.
  - Reuses `getBusyCrafterAmount()` within one server tick and invalidates on crafter updates.
  - Coalesces only an identical `updateStatus(boolean)` call on the same cluster in the same tick.
  - Does not intercept matrix formation, destruction, pattern registration, or crafting thread execution.
- `ExtendedAePlusAssemblerMatrixBusyCaptureMixin`
  - Runs after ExtendedAE Plus and captures the add-on's own 8/32-thread-aware busy total.
  - Feeds that exact result into the shared one-tick matrix cache; ACO does not reimplement or flatten ExtendedAE Plus crafter capacity.
- `StorageServiceWatcherThrottleMixin`
  - Redirects storage watcher `onStackChange` calls through a small buffer when `throttleStorageWatcherUpdates = true`.
  - Flushes on `StorageService.onServerEndTick`, storage node changes, and cache invalidation.
  - Not registered in 1.2.1.
- `CraftingCpuLogicExecutionBudgetMixin`
  - Redirects AE2's `CraftingCPUCluster.getCoProcessors()` call inside `CraftingCpuLogic.tickCraftingLogic`.
  - Caps the effective execution window before AE2 calls `executeCrafting`.
  - Wraps the `executeCrafting` call and records elapsed server-side execution time for adaptive pacing.
  - Applies a second, shared budget keyed by `CraftingService`, which corresponds to one ME grid's crafting service.
  - Uses an exponentially weighted nanoseconds-per-operation estimate to fit later CPU bursts into the remaining grid budget.
  - Grants every active CPU `minimumSharedOperationsPerCpu` after the shared budget is consumed to avoid starvation.
  - Does not change the CPU's real co-processor count, display value, storage, job validation, or crafting result.
  - Enabled by default because AE2 co-processors increase pattern push throughput, not craft calculation speed.
- `AdvancedAeCraftingCpuLogicExecutionBudgetMixin`
  - Optionally redirects only `AdvCraftingCPU.getCoProcessors()` inside the server crafting tick.
  - Applies ACO's hard effective co-processor cap without reflectively invoking Advanced AE methods or touching menu code.
  - Leaves Quantum Computer storage, displayed statistics, structure formation, and job state unchanged.
- `NeoEcoCraftingCpuExecutionBudgetMixin`
  - Optional pseudo-mixin targeting Neo ECO AE Extension 20.3.x `ECOCraftingCPULogic`.
  - Caps the return values of Neo ECO's own `getOperationLimit()` and `effectiveFastPathTickLimit()` methods through ACO's existing adaptive per-CPU and shared per-grid budgets.
  - Measures `executeCrafting(...)` and records Neo ECO's returned pattern-push count, so later ticks use measured cost rather than a guessed operation cost.
  - Uses ACO's server tick clock, shared with the standard AE2 execution wrapper, so both CPU implementations debit the same `CraftingService` budget in one tick.
  - Does not replace Neo ECO's normal, batch, or aggressive fast path; does not alter recipes, storage, CPU statistics, energy accounting, simulated crafts, status batching, or job persistence.
  - Is absent at runtime when `neoecoae` is not installed. Neo ECO 20.3.0 is present only as a compile-time signature target and is not bundled.
- Transactional batch execution Mixins are removed in 1.2.2. Standard AE2 and Advanced AE now transfer every crafting input through their original execution paths.
- `AdvancedAePatternProviderIntentCaptureMixin`
  - Captures the same short-lived input/output intent from Advanced AE Pattern Providers as from standard AE2 providers.
- Grid tick, Import/Export Bus, and IO Port Mixins are removed in 1.2.2. Their configuration keys remain readable compatibility no-ops.
- `CraftingServiceInvalidationMixin`
  - Clears adaptive execution state, calculation de-duplication state, completed plan cache, failed craft-request throttle state, craftable-set cache, and pattern lookup cache when crafting providers or nodes change.

The build intentionally does not include the previous Craft Confirm GUI or CraftingTreeNode solver replacement mixins.

## Active Calculation Single-Flight

Large AE2 craft requests can be accidentally started more than once while the first worker calculation is still running. This is especially easy when a player retries a request because the confirmation screen still says it is calculating.

The optimizer applies a single-flight table around AE2's own calculation future. The key is deliberately narrow:

```text
CraftingService identity
Level dimension
Requester class + requester identity
AEKey output
requested amount
CalculationStrategy
```

This avoids sharing plans across different requesters while still suppressing exact duplicate in-flight requests from the same screen/requester path.

Entries are ignored once the future is done, cancelled, or older than `activeCalculationDeduplicationWindowTicks`. The table is also cleared when AE2 crafting providers or network nodes change.

## Completed Plan Cache

The completed plan cache is intentionally short-lived. Its default TTL is `40` ticks.

By default it stores only:

- simulation plans,
- plans with missing items.

Successful plans are not cached unless `cacheSuccessfulCompletedCraftingPlans = true`. That option exists for explicit stress testing, but it is not the safe default because successful plans can become stale when storage contents change between calculation and submission.

Storage cache invalidation and crafting provider invalidation clear the completed plan cache.

## Deterministic Missing Fast-Fail

The fast-fail path is disabled by default. When enabled, it only runs for `CalculationStrategy.REPORT_MISSING_ITEMS` and requests at least `minimumRequestedAmountForFastFail`.

It recursively follows only strict deterministic pattern paths:

- exactly one pattern for the requested key,
- no emitable item shortcuts,
- exactly one pattern output,
- exactly one possible input per input slot,
- positive output and input amounts,
- no recursion,
- under configured depth and node limits.

If any condition is not met, the preflight returns control to AE2's normal solver.

When a raw missing ingredient is found, the optimizer verifies the missing stack against AE2's live `MEStorage.extract(..., Actionable.SIMULATE, ...)` before returning a missing-only `ICraftingPlan`.

This path never returns a successful plan. It only answers "this request cannot complete because this one ingredient is missing".

## Pattern Lookup Cache

The pattern lookup cache wraps `CraftingService.getCraftingFor(AEKey)` with a weakly keyed per-service cache.

This follows the UEL-style principle of reusing provider lookups until provider topology changes. It does not change pattern validity.

The cache is cleared when `CraftingService.refreshNodeCraftingProvider`, `addNode`, or `removeNode` runs.

## Craftable Set Cache

The craftable set cache wraps `CraftingService.getCraftables(AEKeyFilter)` with a weakly keyed per-service cache.

This is useful for terminals and repeated provider scans. The cache stores only AE2's returned key set. It does not inspect patterns, create new craftable keys, or change recipe validity.

The cache is cleared on the same crafting provider/node invalidation paths as the pattern lookup cache.

Because some AE2 filter lambdas are short-lived, this cache is most effective when the same filter object is reused. Per-menu terminal caching covers the common open-terminal case where AE2 creates a fresh filter closure.

## Storage Watcher Sync Throttle

The storage watcher throttle is compatibility-disabled in 1.2.1. Its config key remains readable and defaults to false, but `StorageServiceWatcherThrottleMixin` is not registered.

When enabled, calls to `IStorageWatcherNode.onStackChange` are buffered per watcher and key. The last amount for each key is retained and flushed every `storageWatcherUpdateIntervalTicks`, or sooner when buffered changes exceed `maximumBufferedChanges`.

The retained implementation is not reachable at runtime. A replacement must preserve a coherent terminal generation and prove that a zero-stock insertion cannot be dropped before this path is registered again.

## Terminal Snapshot Pacing

`MEStorageMenu` normally refreshes craftables and calls `MEStorage.getAvailableStacks()` on every server-side broadcast tick while the terminal is open.

The optimizer can reuse a per-menu `KeyCounter` snapshot for `terminalInventorySnapshotIntervalTicks` and a per-menu craftable set for `terminalCraftableCacheTicks`.

The returned `KeyCounter` is copied before reuse so AE2's later diffing does not mutate the cached snapshot.

The deep range path keeps AE2's packet protocol but drains the helper's pending changes in bounded rolling ranges. It is not a client-requested virtual-page protocol; every key is still synchronized and searchable after the range completes.

Snapshot pacing, craftable-set reuse, client view coalescing, and rolling ranges are not registered in 1.2.1. Their config keys remain no-ops for existing TOML compatibility.

## Crafting Execution Budget

AE2 calculates an execution window from `getCoProcessors() + 1`, subtracts recent `usedOps`, then calls `executeCrafting(maxOps, ...)`. `executeCrafting` can push up to that many patterns through available providers.

For normal CPUs this is fine. For CrazyAE-class hardware numbers, returning a huge co-processor count directly can create a very large server-tick burst.

The optimizer caps only the effective co-processor value seen by AE2's normal crafting tick execution loop. CPU selection, GUI display, storage capacity, job submission, pattern providers, and AE2's final crafting state remain unchanged.

This feature is enabled by default because it is the direct TPS protection for giant CPUs: the CPU can remain large, but it cannot spend an unbounded amount of one server tick pushing patterns.

Advanced AE Quantum Computer execution receives the same hard effective co-processor cap. The Advanced AE path intentionally does not use the standard CPU's reflective execution wrapper, adaptive timing sample, or per-grid shared budget. This keeps the integration at one server-side value read and avoids the previous menu-time class-loading failure.

Neo ECO AE Extension 20.3.x uses a separate `ECOCraftingCPULogic`, so the standard AE2 redirect cannot see it. ACO injects only at Neo ECO's two existing limit-return methods and around its existing `executeCrafting(...)` call. The lower of Neo ECO's own limit and ACO's limit wins. The actual return value from Neo ECO remains the completed-operation count used for adaptive measurement.

The generated default cap is `264192` effective co-processors per CPU. This keeps the optimizer safe by default while matching AQE's non-experimental full structure.

For explicit AQE experimental-core stress testing, set `maxEffectiveCoprocessorsPerCpu = 2147483646`, which is `Integer.MAX_VALUE - 1`. This lets AQE's experimental maximum-value core reach AE2's execution loop while still allowing AE2 to add one execution slot without integer overflow.

When `adaptiveCraftingExecutionBudget = true`, the hard cap above is treated as the maximum. Each active crafting CPU also receives a weakly keyed adaptive cap. If its execution burst takes longer than `targetCraftingExecutionMillis`, the adaptive cap is reduced proportionally and bounded by `minimumAdaptiveCoprocessorsPerCpu`. If it is below half the target and consumes its whole budget, the cap recovers gradually.

When `sharedCraftingExecutionBudget = true`, ACO additionally keeps a weakly keyed budget state per `CraftingService`. Actual elapsed execution time is accumulated for the current game tick. Later CPUs on that grid are limited using the owning CPU's measured cost per operation. Once the shared target is consumed, each remaining active CPU receives only the configured minimum progress allowance for that tick.

The first observed burst for a CPU has no cost estimate, so it still uses the existing per-CPU cap. Its measured cost is then available for subsequent ticks. This avoids guessing a universal operation cost across very different pattern providers and modded machines.

This is the mod's CrazyAE-style safety layer: giant CPU values are allowed, but execution is paced by measured server cost instead of assuming every possible operation should happen in one tick.

### Transactional Pattern Batching

The 1.1.0 experiment aggregated N processing inputs, then immediately registered N multiplied outputs in AE2's `waitingFor` inventory. AE2 Pattern Provider success can include partial insertion plus a retained `sendList`, so that return value was not an N-execution commit receipt. ACO 1.1.1 disabled that implementation, and its legacy config gate still always returns false.

ACO 1.2.0 introduces `PatternBatchAdapter`, `PatternBatchContext`, `PatternBatchResult`, and `PatternBatchApi`. The contract requires a zero result to perform no mutation and a positive result to represent exactly that many durably accepted complete executions. The API validates result bounds centrally. Multi-target contexts are not offered to a native adapter unless it explicitly declares support.

Adapters can narrow an offered batch through `limitExecutions` before ACO extracts aggregate inputs. ACO also limits the offer by exact CPU inventory availability and energy, avoiding large extract/reinsert cycles when only a smaller batch can run.

`PatternBatchBudget` adds the hard deadline used by instant dispatch. The executor may continue through multiple batches and ready task entries in one CPU invocation, but stops at the effective CPU operation allowance, configured transaction count, or wall-clock deadline. This is an aggressive dispatch scheduler, not zero-tick external-machine execution.

`BatchedCraftingExecutor` now accepts only exact external-processing patterns with one possible key per input and no remaining container. It extracts a checked aggregate from the CPU inventory, but expected outputs, energy, and task progress are scaled from the adapter's returned accepted count. Unaccepted extracted inputs are re-injected before control returns.

The built-in `SequentialPatternProviderBatchAdapter` preserves one original `pushPattern` call per execution. It checks `isBusy()` before every call and stops on the first rejection or backpressure event. This removes the old semantic mismatch while still combining exact input extraction and CPU bookkeeping. Future GTCEu/Mekanism native adapters may reduce provider calls only if they can provide the same durable accepted-count guarantee.

See [BATCH_API.md](BATCH_API.md) for the adapter contract and registration example.

AQE's non-experimental full default structure remains:

```text
(4096 modified core + 121 * 512 modified accelerators) * 4 Multi-Threader = 264192
```

If the server shows MSPT spikes during real large crafts, the first fallback values are `264192`, `131072`, and `65536`.

For the adaptive path, the first fallback is lowering `targetCraftingExecutionMillis` from `4` to `2`; if crafts become too slow, raise it back to `4` or `6`.

## Grid Tick Budget

The grid tick budget addresses a different bottleneck from crafting calculation or crafting CPU execution.

AE2 IO Ports, Import Buses, Export Buses, ExtendedAE Ex buses, and the ExtendedAE Circuit Cutter are normal `IGridTickable` devices. In large packs they can become expensive because they repeatedly scan filters, adjacent inventories, cells, storage services, and export crafting state. With `ae2_overclocked` installed, several of these devices can also return `URGENT`, which keeps them scheduled aggressively.

The optimizer hooks the AE2 tick manager boundary for measurement and opt-in pacing:

```text
TickManagerService -> unsafeTickingRequest(TickTracker, ticksSinceLastCall)
```

Before a selected non-progress-sensitive tickable runs, the optimizer checks the selected-device budget for the current server tick. If the budget is already spent, it returns `TickRateModulation.SLOWER` for that call. After it runs, ACO records elapsed time and may apply a short backoff. Import/Export Buses and Circuit Cutters are explicitly progress-sensitive: they are measured but never canceled or backed off by this layer.

The exemption prevents repeated `SLOWER` returns from combining with backoff and starving a bus or processing machine that appears late in grid iteration order.

Import/Export buses also get an operations-per-tick cap. This cap is applied after AE2 or speed-card addons calculate the value. It prevents a single bus from doing an integer-saturated burst while preserving filters, redstone control, scheduling mode, craft-only mode, and storage validity.

Repeated idle returns can also trigger a short backoff. This targets polling loops where the selected tickable keeps reporting no work through `SLOWER` or `IDLE`.

Export-bus-style craft request throttling is separate from the grid tick boundary. It hooks `MultiCraftingTracker` and only records failures after a completed job leaves no crafting link. This avoids hammering the crafting solver with the same impossible request every tick while still allowing active jobs and new requests to proceed.

Grid tick budgeting is disabled by default because AE2 buses are correctness-sensitive and must be opt-in tested per pack.

The opt-in grid tick values are intentionally conservative:

```text
gridTickBudgetMillisPerServerTick = 6
slowGridTickableMicros = 2000
slowGridTickableBackoffTicks = 2
idleGridTickableBackoffAfterFailures = 4
idleGridTickableBackoffTicks = 5
maxIoBusOperationsPerTick = 4096
```

If automation becomes too slow but MSPT is stable, raise `gridTickBudgetMillisPerServerTick` first. If MSPT still spikes, lower `maxIoBusOperationsPerTick` or increase `slowGridTickableBackoffTicks`.

## AE2-UEL-Inspired Safe Optimization Layer

Version 1.2.2 retains only non-mutating calculation, provider-refresh, recipe-validation, and machine-intent optimizations. Capability, terminal craftable-set, Import/Export Bus, IO Port, storage simulation, and live transfer Mixins are removed. AE2 remains the sole owner of mutable storage amounts and every insertion, extraction, rollback, cell transfer, and terminal repository generation.

## Machine Intent Boundary

The requested "intent" direction for GT/Mekanism-style machine lines is now represented inside ACO at the AE2 Pattern Provider boundary.

Implemented in this mod:

- successful Pattern Provider push capture,
- successful Advanced AE Pattern Provider push capture,
- short-lived recipe intent registry keyed by dimension, target position, and target side,
- execution-count aggregation and chunk-bucketed spatial lookup,
- `/aco intents` diagnostics,
- GTCEu item/fluid output-indexed candidate prefixing before the normal recipe iterator,
- Mekanism item/fluid/chemical output-indexed candidate selection before the normal machine recipe lookup,
- pattern lookup reuse,
- craftable set reuse,
- exact duplicate craft calculation de-duplication,
- short-lived missing/simulation plan reuse,
- export-bus-style failed craft retry cooldown,
- IO/import/export/circuit-cutter tick pacing.

Compatibility-disabled in 1.2.1:

- terminal snapshot pacing,
- terminal craftable reuse,
- storage watcher pacing,
- aggregate storage refresh coalescing,
- rolling terminal range packets,
- client terminal view coalescing.

Implemented deep AE2 rewrite flags under `[deepAe2Rewrite]`:

- `patternSelectionByAvailability`
- `networkForceUpdateCoalescing`
- `visibleTerminalRangeSync`
- `p2pTopologyChangeOnlyRecheck`
- `busSearchRewrite`
- `fluidPatternRework`

`patternSelectionByAvailability`, `p2pTopologyChangeOnlyRecheck`, `busSearchRewrite`, and `fluidPatternRework` gate active implementations and return directly to AE2's original path when disabled. `networkForceUpdateCoalescing` and `visibleTerminalRangeSync` remain readable compatibility keys but have no registered Mixins in 1.2.1. The master switch disables every active deep path at once.

Not implemented in this mod:

- rewriting Create machine recipe execution,
- replacing the terminal protocol with client-requested virtual pages,
- replacing AE2's graph solver or provider priority model,
- replacing import/export transfer validation,
- replacing AE2 15.4.10's existing first-class `GenericStack` fluid model,
- forcing a captured intent directly into GTCEu, Mekanism, or Create recipe execution without each mod's own validation path,
- replacing pattern providers with a custom machine scheduler,
- batching dedicated crafting-machine plans or bypassing AE2's storage/crafting APIs,
- changing machine inventory insertion/extraction semantics.

Create remains reserved behind a config-visible fast path switch. Mekanism is implemented conservatively: ACO only returns candidates that Mekanism's own recipe `test` accepts for the current machine inputs.

## GTCEu Recipe Intent Fast Path

The GTCEu path follows the GTNH/UEL-style intent idea without replacing GTCEu's machine logic.

Flow:

```text
AE2 Pattern Provider push succeeds
ACO records target position + concrete input/output intent
GTCEu RecipeLogic.searchRecipe runs on that target machine
ACO finds exact or nearby input-bus/hatch intents through chunk buckets
ACO resolves intent output ids and prioritizes candidates containing the concrete pushed input ids
ACO prepends matching candidates to GTCEu's original iterator
GTCEu handleSearchingRecipes/checkMatchedRecipeAvailable/checkRecipe/setupRecipe stay unchanged
```

Safety rules:

- If the intent is missing or expired, original GTCEu search runs.
- If the output index has no candidates, original GTCEu search runs.
- If a candidate no longer matches the machine inputs, GTCEu rejects it and the original iterator continues.
- If reflection fails because GTCEu changed internals, the original iterator is returned.
- Candidate count is capped by `gtceuRecipeIntentMaximumCandidates`.
- Nearby matching is capped by `gtceuRecipeIntentSearchRadius` and `gtceuRecipeIntentNearbyMaximumEntries`.
- The output index is cleared on server stop and `/aco intents clear`.
- Output-index and resolved-candidate caches are also cleared after a server datapack recipe reload.
- Repeated searches for the same target and fresh output-key set reuse an immutable candidate prefix; GTCEu still validates every candidate.

This reduces repeated full recipe discovery when AE2 has just pushed a known processing pattern into a GTCEu machine, while preserving GTCEu as the final validator and executor.

## Mekanism Recipe Intent Fast Path

The Mekanism path uses the same Pattern Provider intent but hooks Mekanism machine `getRecipe(int)` bridge methods instead of the shared cache classes. This is required because the cache classes do not know the machine position, while the intent registry is keyed by target block position.

Flow:

```text
AE2 Pattern Provider push succeeds
ACO records target position + concrete input/output intent
Mekanism machine getRecipe(int) runs on that target machine
ACO finds fresh intents for the machine position
ACO resolves intent output item/fluid/chemical ids against a cached per-Mekanism recipe type output index
ACO reads the current Mekanism input handlers for that machine/cache index
ACO returns a candidate only if Mekanism's recipe test accepts those live inputs
Mekanism CachedRecipe creation, operation tracking, input consumption, output insertion, and energy use stay unchanged
```

Safety rules:

- If the intent is missing or expired, original Mekanism lookup runs.
- If no output-indexed candidates exist, original Mekanism lookup runs.
- If candidates do not match the current item, fluid, gas, infusion, pigment, or slurry inputs, original Mekanism lookup runs.
- If reflection fails because Mekanism changed internals, original Mekanism lookup runs.
- Candidate count is capped by `mekanismRecipeIntentMaximumCandidates`.
- The output index is cleared on server stop and `/aco intents clear`.
- Output-index and resolved-recipe caches are also cleared after a server datapack recipe reload.
- Input-handler field discovery and recipe `test` method discovery are cached per Java class.
- A resolved recipe is reused only while the latest intent signature matches and Mekanism's live recipe `test` still accepts current inputs.

This targets machines such as enrichment/crushing/smelting factories, combiner, metallurgic infuser, purification/injection/osmium compressor, chemical crystallizer, chemical infuser, washer, oxidizer, electrolytic separator, pressurized reaction chamber, rotary condensentrator, pigment machines, and related Mekanism recipe machines.

## Safety Boundaries

The mod does not:

- mutate AE2 inventory state,
- alter recipe registration,
- replace AE2's crafting tree solver,
- return a successful handcrafted plan,
- bypass AE2 submit validation,
- change AE2 CPU storage or co-processor accounting,
- alter bus filters, inventories, redstone behavior, or transfer validity.
