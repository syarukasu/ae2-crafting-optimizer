# Implementation

## Design Goal

ACO must make very large deterministic crafting-table orders scale with the
number of distinct recipe nodes, not requested quantity, without changing
inventory results or hiding physical progress.

## Mixin contract boundaries

Mixin configuration is split by correctness responsibility:

- `ae2_crafting_optimizer.mixins.json` is the required AE2 core contract for ownership, exact counts, transactions, receipts, persistence, and parent-CPU completion.
- `aco.integration.*.mixins.json` is selected only when the corresponding external mod is loaded. Its selected configuration is fail-closed and is audited against the exact supported dependency version before jobs are accepted.
- `aco.performance.mixins.json` contains cache, lookup, throttling, and UI fast paths only. It remains fail-open so the original AE2 path remains authoritative when a target changes.

The startup transformation report records the feature, target class, selected mixin, dependency version, applied state, and fail-open/fail-closed policy. A missing correctness transformation must not silently turn the mod into a decorative-only install.

It therefore separates:

- formula compilation;
- exact ownership accounting;
- physical recipe execution;
- parent CPU completion.

## Exact-count Integration Contract

The shared integration contract lives under
`com.syaru.ae2craftingoptimizer.api.contract`. It is deliberately independent
of AE2, AQE, and AAC implementation classes so those integrations can adopt it
without creating a compile-time cycle.

- `CanonicalBigIntegerCodec` is the single bounded, unsigned-magnitude codec.
- `ExactCountPayloadCodec` is the deterministic schema for request, plan, host,
  journal, and receipt payloads.
- `IntegrationCapabilitiesRegistry` exposes the immutable ACO capability
  snapshot after common setup.
- `ReceiptReservationProtocol` rejects stale or digest-mismatched transitions
  instead of silently losing ownership.
- `CraftingTableBatchSnapshot` and revision wakeups provide one consistent
  view for later AQE/AAC host implementations.

See `docs/EXACT_COUNT_API.md` for the versioned surface and adoption rules.

## Big Crafting Host lifecycle

`BigCraftingHostRegistry` retains owners only between explicit registration and
close. The AQE controller map uses identity keys and is closed on stale cluster
reform and server stop. A host close is admission-only: durable BigInteger jobs
remain available for recovery, while the controller owns cancellation, rollback,
and quarantine of pending physical work.

`BigCraftingHostRuntime.snapshot(...)` reads capacity, reservations, and all job
counts under one monitor and returns an immutable `BigCraftingHostSnapshot`. No
consumer should compose an accounting decision from separate getters.

## Compiled Formula

`CompiledRootProgram` is generation-keyed. For an eligible root it records:

- one producer per output;
- selected input for every Pattern slot;
- exact output quantity per execution;
- dependency depth;
- material-side to root-side execution order.

`VectorBatchPlanner` performs one deterministic arithmetic traversal. For each
active node:

```text
deficit = demand - usable inventory
executions = ceilDiv(deficit, output per execution)
child demand += executions * selected input per execution
```

All additions, multiplications, and divisions are checked. The normal route
uses `long`; overflow promotes the calculation to bounded `BigInteger`.

The resulting `PreparedVectorBatch` contains formulas and identities only. It
does not contain synthetic duration, energy, coolant, or completed output.

## Physical Transaction

`PhysicalCraftingTreeTransaction` is the ACO-owned state machine:

```text
VALIDATING
  -> RESERVING_BOUNDARY_INPUTS
  -> EXECUTING_RECIPES
  -> RETURNING_RESULTS
  -> COMPLETE
```

Cancellation uses:

```text
CANCELLING_THREADS
  -> RETURNING_CANCELLED_ESCROW
  -> CANCELLED
```

Any unprovable ownership state enters `QUARANTINED`.

### Boundary Reservation

All distinct boundary input keys are preflighted, then moved as one exact
batch into `ExactCraftingEscrow`.

The cursor is binary:

- `0`: the batch is not committed to parent accounting;
- full key count: every key is committed.

Intermediate cursor values are invalid because the mutation is a single
logical ownership transfer.

### Recipe Scheduling

Each `ExactCraftingStep` has a durable `StepReceipt`.

A step may reserve inputs only when escrow contains the full exact amount.
After reservation, ACO selects a Provider-owned AAC Pattern Bus and sends one
`CraftingTableBatchRequest`.

The request contains:

- parent and step transaction IDs;
- payload digest;
- encoded AE2 Pattern;
- exact execution coefficient;
- selected per-slot inputs;
- expected exact outputs.

Order quantity never determines loop count or Thread count.

The selected concrete key for every tag or alternative-input slot is persisted
with the step. After restart, ACO requires that exact key and amount to remain
valid for the encoded Pattern; it never silently switches to another tag
member.

Resolved one-craft formulas are cached as an indexed array for the currently
validated provider and recipe generations. Polling an active transaction does
not rebuild Pattern formulas until either generation changes.

### Physical Output

AAC runs one real recipe assemble through Neo ECO. ACO accepts output only when
the Worker's exact receipt equals the compiled per-step formula.

The output is credited to escrow once. Dependent steps become runnable only
after that credit.

The final root and fixed remaining outputs must be the complete escrow content.
ACO then inserts that exact content into ME storage. There is no code path that
creates final output from `PreparedVectorBatch.finalOutputs()`.

## Worker Receipt Protocol

The physical target states are:

```text
RUNNING
OUTPUT_READY
ACKNOWLEDGED
CANCELLED
QUARANTINED
```

`OUTPUT_READY` and `ACKNOWLEDGED` both require a non-empty exact output map.

The acknowledgement order is:

1. Worker records exact terminal output in its own Block Entity NBT.
2. Worker releases the Neo ECO Thread.
3. ACO observes the terminal receipt.
4. ACO credits output exactly once.
5. ACO asks the Worker to forget the receipt.

This ordering supports either parent-first or Worker-first chunk saving.

AAC keeps transient transaction-to-Worker and transaction-to-Thread indexes.
The first lookup after restart or structure replacement rebuilds an index from
persisted Neo ECO state; ordinary progress polling is direct lookup.

## Exact Storage Mutation

`ExactNetworkStorageBridge` accesses only audited storage mounts whose complete
quantity is available as `BigInteger`.

Before mutation, ACO stores:

- operation UUID;
- direction and purpose;
- exact amount per key;
- exact before amount per key;
- derived exact after amount per key.

On recovery, `ExactMutationReconciler` classifies every key:

- current equals before: retry this key;
- current equals after: do not replay this key;
- neither: quarantine.

After retry, ACO re-reads all keys and requires the complete after map before
advancing parent escrow. Work is proportional to distinct keys, never amount.

## Parent Job Commit

The physical transaction inserts final output into ME before
`BigCraftingJob.commitPreparedVector` runs.

The parent commit only:

- validates transaction identity and unchanged task offset;
- completes the exact parent task count;
- releases reserved capacity;
- updates the parent state.

It does not create or insert output.

## Planner-local Exact Inventory Snapshot

`PlanningExactInventorySnapshot` builds a saturated AE2 `KeyCounter` facade
and an exact `BigInteger` sidecar only when ACO starts an authoritative
crafting calculation. The shared `NetworkStorage#getAvailableStacks` path is
not redirected.

This separation keeps terminal serials, buses, watchers, normal insert/extract,
and ordinary AE2 cached inventory under AE2 ownership. The planner-local
snapshot still enumerates mounted storage in AE2 priority order, deduplicates
identical mount instances, preserves `10^64` exact cell amounts, and exposes
`Long.MAX_VALUE` only as the compatibility facade.

For a normal full-grid ME terminal,
`MEStorageMenuGridSnapshotReuseMixin` copies
`StorageService#getCachedInventory()`. This is AE2's own dirty-flag-controlled
snapshot, not ACO's removed multi-tick menu cache. The optimization applies
only when the menu inventory and grid inventory are the same object. Portable
cells, ME chests, partitions, and add-on-specific inventory views use their
original `MEStorage#getAvailableStacks()` path.

## Advanced AE Exact Job Accounting

Gameplay orders whose individual Pattern or storage counters exceed signed
`long` still create one normal Advanced AE CPU and one normal `CraftingLink`.
ACO does not create a second completion job for them.

Normal `long` jobs and exact `BigInteger` jobs use the same Advanced AE
`ExecutingCraftingJob` accounting lifecycle. The same runtime objects hold both
views:

- each Advanced AE `TaskProgress` stores the exact remaining Pattern count;
- the real `ListCraftingInventory waitingFor` stores exact expected output;
- the real `ExecutingCraftingJob` stores exact final-output remaining;
- the original `long` fields contain only `0..Long.MAX_VALUE` compatibility
  projections for unchanged AE2 and Advanced AE APIs.

Physical receipts follow the normal Advanced AE accounting order:

1. accepted Pattern work decreases its real task count;
2. that Pattern's expected output is added to the real `waitingFor`;
3. credited physical output is removed from the same `waitingFor`;
4. returned final output decreases the real final-output counter;
5. only when all three counters are terminal does the normal
   `CraftingLink` finish.

While an exact physical transaction owns the job, unsolicited matching stacks
from the generic Advanced AE `insert` route are rejected. Only a verified
physical receipt may advance the same real counters, preventing an unrelated
machine output from being counted twice.

The saved exact receipt ledger is replay protection, not a second completion
authority. It stores cumulative absolute dispatch, introduced-output, and
credited-output totals in the same job NBT. On load and save, ACO requires the
ledger and real runtime counters to match exactly. It never force-zeroes a
counter because a physical transaction merely reports `COMPLETE`.

## Fallback

Fallback is valid only before `prepareVectorExecution` transfers ownership.

After a receipt exists:

- missing hardware waits;
- a generation mismatch starts exact cancellation;
- a completed receipt resumes;
- an uncertain mutation quarantines.

The checked-long child-window route is never started for the same owned parent.

## Normal Long Jobs

Normal AE2 and Advanced AE jobs use the same public
`CraftingTableBatchRequest` and the same AAC/NeoECO one-assemble proof through
Transactional Batch V2.

Their CPU inventory and task accounting remain AE2/Advanced AE authoritative.
The adapter reports one physical operation while preserving the exact accepted
execution count.

## Processing Patterns

GTCEu, Mekanism, fluid, and chemical processing Patterns are not executed by
the physical crafting-table adapter.

They remain real machine boundaries. A downstream crafting-table step cannot
reserve a machine output until that output is present in the owning CPU or ME
inventory through the original machine path.

## BigInteger Persistence

Counts are encoded as canonical signed-magnitude byte arrays with:

- schema version;
- fixed maximum byte length;
- non-negative or positive validation as appropriate;
- no `longValue()` truncation;
- exact conversion only with checked boundaries.

The implementation decimal ceiling is `10^16384 - 1`.

## TPS Boundaries

- Formula work scales with reached nodes.
- Storage work scales with distinct boundary keys.
- Worker work scales with accepted physical recipe steps.
- Requested quantity is a multiplication coefficient only.
- New transaction starts, active steps, and active transactions are bounded per
  ME grid.
- A soft wall-clock budget defers additional scheduler work.

## Removed Architecture

The following implementation paths were deleted:

- direct whole-tree final-output conversion;
- `ExactVectorExecutorRegistry`;
- `AqeStandardVectorExecutionRuntime`;
- Compiled Crafting Islands and their backend registry;
- synthetic Vector duration;
- synthetic fixed tree-energy and coolant schedules;
- display-only completion delays.

Old schema data from those executors is not resumed as current physical work.
It is rejected or quarantined rather than interpreted under a new ownership
contract.

## Dedicated Server Safety

Common code does not reference Minecraft client classes. ACO uses no Bukkit,
Paper, Spigot, or Arclight API.

Version-sensitive Mixins are pinned to the documented dependency series and
must fail closed when their required target disappears.
