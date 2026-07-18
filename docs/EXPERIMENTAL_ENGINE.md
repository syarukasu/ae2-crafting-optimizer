# Experimental Crafting Engine

## Status

The `1.3.0` release contains ACO's opt-in next-generation crafting
backend. Its pure-Java engine, persistent transaction protocol, native adapter
boundaries, fair scheduler, BigInteger sidecar runtime, and bounded status
protocol are implemented and covered by automated tests.

The deep behavior-changing paths are deliberately **disabled by default**.
Clean build, Forge client bootstrap, and Arclight dedicated-server startup are
qualified, but source completion and startup do not replace world-recovery or
multiplayer qualification. Complete the runtime matrix below on a copied world
before any experimental child switch is enabled.
The explicit-host BigInteger API is enabled by default, but it has no effect
unless a compatible add-on such as AQE 2.0.0 registers a host.

The code was compiled against these exact integration targets:

| Dependency | Verified version |
| --- | --- |
| Applied Energistics 2 | `15.4.10` |
| Advanced AE | `1.3.5-1.20.1` |
| GTCEu Modern | `7.5.3` |
| Mekanism | `10.4.16.80` |
| Applied Mekanistics | `1.4.3` |

Optional native adapter classes are not loaded while their child switch is off.
When enabled, they are registered only when every required mod has the exact
verified version. A startup audit then verifies both registration and the
required Accessor Mixin transformations; an explicitly enabled but unsupported
combination fails with a targeted error instead of silently running a partial
integration. Enabling the experimental master also requires AE2 exactly
`15.4.10`; V2 or fair scheduling with Advanced AE loaded requires exactly
`1.3.5-1.20.1`.

## Configuration

Every behavior-changing switch is false by default:

```toml
[experimentalCraftingEngine]
enableExperimentalCraftingEngine = false
enableShadowMode = false
logShadowMismatches = true
shadowMaximumPatterns = 262144
enableCompiledCraftingGraph = false
enableTransactionalBatchingV2 = false
enableGtceuNativeBatching = false
enableMekanismNativeBatching = false
enableFairCraftingJobScheduler = false
fairSchedulerOperationsPerTick = 4096
fairSchedulerQuantum = 64
fairSchedulerTimeBudgetMillis = 4
persistBatchTransactionJournal = true
batchTransactionJournalMaximumEntries = 16384
batchTransactionReconciliationIntervalTicks = 20
nativeBatchMaximumExecutions = 65536
enableBigIntegerCraftingBackend = true
bigIntegerMaximumBits = 256
bigIntegerExecutionWindow = 65536
bigIntegerStatusPageEntries = 1024
bigIntegerRuntimeCountBudgetMiB = 256
```

`persistBatchTransactionJournal = false` prevents V2 execution. Recovery of an
already existing journal remains active even when the master switch is later
disabled, so disabling an experiment cannot silently replay an unresolved job
through normal AE2.

## Implemented Architecture

### Compiled planning graph

- Immutable patterns are indexed by output and provider-content generation.
- Stable fingerprints deduplicate identical logical patterns.
- Graph construction occurs outside the global cache lock.
- A generation is checked again before publication; a moving provider set is
  retried a bounded number of times and then rejected as stale.
- An iterative two-pass strongly connected component pass identifies recursive
  recipe groups without recursive Java stack growth.
- Graph size is hard-bounded to `1,048,576` patterns.
- Per-calculation inventory snapshots, memoized deterministic topologies,
  aggregated per-node demand, cancellation tokens, and provider/recipe
  generation guards prevent stale work from being applied.

### Long and BigInteger planning

- Checked `add`, `multiply`, and `ceilDiv` operations protect the long fast path.
- BigInteger request, inventory, intermediate multiplication, demand merge, and
  generated-output values are checked against the configured bit bound while
  planning, rather than only when the finished job is submitted.
- Deterministic symbolic plans scale with distinct graph nodes rather than the
  requested item count.
- Only arithmetic overflow promotes a calculation to `BigInteger`.
- Ambiguous alternatives, substitutions, container returns, unsupported keys,
  or an unprovable result are marked `provenEquivalent = false` and fall back to
  AE2. They are eligible for diagnostics, never authoritative execution.
- Shadow Mode compares a completed AE2 result with the experimental result and
  records bounded diagnostics. It never submits or mutates the AE2 plan.

Normal AE2 `CraftingTreeNode`, `CraftingTreeProcess`, and standard CPU NBT stay
unchanged. A CPU add-on must explicitly consume the ACO API before a BigInteger
job can become gameplay-visible. AQE 2.0.0 optionally consumes host API v3;
neither mod requires the other to load.

### Transactional batching V2

V2 uses a forward-only source receipt plus an overworld journal:

```text
PREPARED -> TARGET_ACCEPTED -> ACCOUNTED
```

The CPU-side receipt records finer ownership boundaries:

```text
STAGED
  -> EXTRACTING
  -> EXTRACTED
  -> TARGET_ACCEPTED
  -> ENERGY_ACCOUNTING
  -> ENERGY_ACCOUNTED
  -> PROGRESS_ACCOUNTED
  -> OUTPUTS_ACCOUNTING
  -> OUTPUT_ACCOUNTING
  -> ACCOUNTED
```

- Every transaction has one UUID and stable task/pattern fingerprints.
- Pattern inputs and expected outputs are exact checked aggregates.
- Input extraction records the actual modulated amount before validating it,
  so a simulation/modulation race can restore every amount already removed.
- Before target acceptance, an all-or-zero failure rolls inputs back once.
- After target acceptance, AE2 task progress and output waiting state are
  resumed from persisted cursors instead of re-pushing the target.
- `ENERGY_ACCOUNTING` is an explicit uncertainty barrier. If charging throws or
  a recovered receipt stops there, ACO quarantines it instead of charging twice.
- `EXTRACTING` and per-entry `OUTPUT_ACCOUNTING` are uncertainty barriers. A
  restart inside either side effect is quarantined instead of reinserting an
  unknown partial input aggregate or replaying a possibly inserted output.
- Malformed state, contradictory receipts, partial native acceptance, or an
  uncertain external call is quarantined and retained for inspection.
- After the overworld journal reaches a terminal phase, its matching terminal
  source and target receipts are explicitly removed. If cleanup fails, bounded
  replay evidence remains for up to `12,000` ticks; each owner stores at most
  `256` receipts and a full unresolved/evidence set causes fallback, not unsafe
  eviction.

The bounded instant dispatcher may complete multiple V2 transactions in one
CPU call, but it stops at the CPU operation limit, transaction limit, wall-time
deadline, or the first target backpressure signal.

### GTCEu adapter

- Uses typed GTCEu `7.5.3` classes and recipe capabilities.
- Resolves one exact deterministic item/fluid recipe.
- Rejects chance outputs, ingredient actions, unsupported capabilities,
  alternatives that do not collapse to one exact key/count, and conditions
  that GTCEu rejects.
- Uses GTCEu `ParallelLogic.limitByOutputMerging` to bound aggregate acceptance.
- Keeps GTCEu responsible for voltage, recipe conditions, duration, energy,
  machine processing, and final outputs.
- Larger jobs are split into checked execution windows because GTCEu parallel
  fields are `int`.

### Mekanism adapter

- Uses typed Mekanism `10.4.16.80` and Applied Mekanistics `1.4.3` classes.
- Resolves one exact item, fluid, gas, infusion, pigment, slurry, or supported
  combined recipe without converting fluids/chemicals to dummy items.
- Reads the real `CachedRecipe.baselineMaxOperations` through an Accessor Mixin.
- Accounts factory process count without invoking `CachedRecipe.process()`
  speculatively or replacing `OperationTracker`.
- Keeps Mekanism responsible for energy, tank/slot state, recipe progress,
  output blocking, and machine processing.

Both native adapters perform one aggregate, all-or-zero Pattern Provider push.
The provider's persisted send buffer owns any remainder. The durable target
receipt therefore belongs to Pattern Provider logic rather than a GTCEu or
Mekanism block entity. Unsupported machines and recipes return to AE2 before
source ownership changes.

### Fair dispatch and routing

- A deficit-round-robin scheduler gives every runnable job a minimum quantum.
- One ME grid has both an operation budget and a measured time budget.
- Earlier jobs cannot consume work reserved for later jobs.
- Job UUID, deficit, and cursor persist with supported AE2/Advanced AE CPU state.
- Pattern Provider routing candidates are cached until provider/pattern
  generation invalidation, block replacement, or datapack reload.

### BigInteger sidecar runtime

`BigCraftingEngineApi` is the explicit integration boundary for a CPU add-on.
It does not automatically patch standard AE2 or Advanced AE CPUs.

- API version: `3`.
- One shared host reconciles external signed-long job reservations and native
  BigInteger jobs against the same physical capacity.
- Runtime NBT schema: `2`, including a persistent runtime UUID.
- Count types: requested, reserved, remaining, waiting, emitted, and missing
  values remain `BigInteger`.
- Small values retain a checked long planning fast path.
- Machine execution receives only bounded long/int execution windows.
- Submitted jobs are defensively copied and schedulers expose immutable,
  runtime-bound leases rather than internal mutable job objects. Commit,
  rollback, output acceptance, and cancellation must pass through the runtime.
- Capacity is reserved atomically across all jobs in one runtime.
- Standard-job reservations reconciled by a host have both a 65,536-entry cap
  and the configured encoded-count byte budget; invalid replacements do not
  mutate the previous authoritative snapshot.
- Multiple jobs are scheduled round-robin; clean terminal jobs release their
  reservations, while uncertain cancellation is quarantined and retains it.
- One tick's operation budget is divided across the runnable jobs selected by
  the rotating cursor, so a maximum-size window cannot consume the entire tick
  while another selected small job receives nothing.
- Tick scheduling selects the next unfinished task without copying the complete
  task map. Remaining/waiting totals and encoded-count memory are maintained
  incrementally, so status paging does not rescan a giant recipe graph.
- A prepared execution lease survives save/reload with the same transaction ID.
  The integrating host must reconcile its durable target receipt and call
  `commitRecovered` or `rollbackRecovered`; unresolved leases are never scheduled
  a second time.
- A compatibility facade saturates capacity/used values at `Long.MAX_VALUE`
  only for APIs that cannot represent larger values. Exact conversions use
  `longValueExact()`.
- NBT stores canonical non-negative byte arrays with schema and magnitude
  validation. Tests cover 64, 128, and 1024 decimal digits.
- Per-runtime count magnitudes have a configurable aggregate memory budget.
- `BigCraftingEngineApi.createPlanner()` and `.load()` apply the current server
  bit, execution-window, status-page, and count-memory limits for an integrating
  CPU host.

### Status synchronization

BigInteger status uses a separate strict Forge channel. It does not alter AE2's
normal packets.

- Forge channel protocol: `2`; status payload protocol: `1`. Both sides must
  have the matching ACO release when status synchronization is used.
- Status is paged and defaults to at most `1,024` jobs per page.
- One packet is hard-capped at `1 MiB` and `16,384` entries.
- Before sending, ACO encodes a probe and halves the requested page repeatedly
  until it fits the byte cap. A single entry that cannot fit fails explicitly.
- Every integer is magnitude-checked before allocation.
- Trailing, malformed, negative, oversized, and unknown-state packets fail.
- The client inbox stores only an immutable latest page for an integrating GUI.

## Persistent State

NBT is written lazily only after an experimental path creates state.

| Owner | Key/data | Purpose |
| --- | --- | --- |
| Overworld SavedData | `ae2_crafting_optimizer_batch_transactions` | Non-terminal V2 journal and quarantine evidence. |
| AE2/Advanced AE CPU logic | `acoBatchSourceReceipts` | Source ownership and accounting cursor. |
| AE2/Advanced AE CPU logic | `acoFairScheduler` | Job UUID, deficit, and rotation cursor. |
| AE2/Advanced AE Pattern Provider logic | `acoNativeBatchReceipts` | Durable aggregate target acceptance receipt. |
| Integrating BigInteger CPU add-on | `BigCraftingRuntime.save()` payload | Capacity ledger, jobs, windows, waiting state, schema, and runtime UUID. |

Unknown schemas and malformed ledgers are preserved verbatim and locked against
overwrite. ACO does not reinterpret them as empty state.

## Automated Verification

`gradlew.bat clean test build` covers:

- checked long arithmetic and overflow promotion;
- immutable graph generation, deduplication, SCC cycles, stale-generation
  rejection, cancellation, symbolic proof/fallback, and randomized DAGs;
- a 1,000-recipe/1,024-digit planner benchmark with graph-bounded expansion;
- BigInteger capacity, inventory, plan, job, execution-window, NBT, packet,
  paging, memory-budget, migration, cancellation, and output completion;
- source/target receipt state machines, malformed payload fail-closed behavior,
  extraction/energy/per-output uncertainty barriers, replay evidence, rollback,
  and an injected in-memory inventory transaction failure;
- operation/time caps, reservations, persistence, rotation, and starvation
  prevention in the fair scheduler.

These are source-level tests. They cannot prove transformed Forge classes,
actual machine capabilities, world-save ordering, Arclight behavior, or client
rendering.

## Runtime Qualification Required Before Enablement

1. Copy the world and keep the installed `1.2.2` jars archived.
2. Install the same `1.3.0` jar on the dedicated server and every client.
3. Start with only the experimental master, compiled graph, and Shadow Mode.
4. Compare possible, impossible, recursive, tag/substitution, container-return,
   fluid, and chemical plans against an ACO-disabled control.
5. Enable V2 alone, then GTCEu and Mekanism separately. Test normal completion,
   blocked input/output, no power, cancellation, chunk unload, machine break,
   datapack reload, save/restart, and server kill at every source receipt phase.
6. Verify exact conservation of CPU inventory, provider send buffer, machine
   input/output, task progress, waiting outputs, and energy.
7. Run standard AE2 and Advanced AE jobs concurrently and verify no starvation
   under both operation and MSPT pressure.
8. Integrate `BigCraftingEngineApi` only in an experimental AQE build. Test long
   boundary values, 64/128/1024-digit jobs, multiple simultaneous jobs,
   cancellation, structure reform, chunk reload, restart, and paged status.
9. Test Forge client, Forge dedicated server, Arclight, mismatched client/server
   protocol rejection, and multiplayer reconnect.
10. Enable one child path at a time in production only after its complete matrix
    passes without loss, duplication, stuck jobs, stale results, or MSPT regressions.

Neo ECO custom CPU scheduling remains on ACO's stable execution-budget path. It
does not consume the new persistent fair-scheduler or BigInteger runtime.
