# Configuration

ACO uses one Forge Common Config:

```text
config/ae2_crafting_optimizer-common.toml
```

The server copy is authoritative for gameplay. Use the same ACO JAR and
matching configuration intent on every client and server.

## Master Switch

`enableOptimizer` disables new optimization work while preserving recovery of
already persisted transactions.

Disabling a feature must never delete a live receipt or assume that another
owner rejected work.

## Calculation

Important calculation options include:

| Key | Default | Purpose |
| --- | ---: | --- |
| `enableAqeBigCraftingProfile` | `true` | Activates the narrow AQE compiled/checked profile only when Advanced AE and AQE are installed. |
| `enableInsaneAeBigCraftingProfile` | `true` | Activates the same strict calculation profile when InsaneAE is installed, so InsaneAE does not apply a competing calculation batch. |
| `enableLongRootCraftAmounts` | `true` | Adds the signed-long root-order path while preserving AE2's original int path. |
| `enableCompiledCraftingGraph` | `true` | Reuses a generation-keyed deterministic graph where the active profile allows it. |
| `enableShadowMode` | `true` | Compares eligible compiled results against AE2 without changing normal results. |
| `enableExactBigIntegerInventorySnapshots` | `true` | Keeps exact sidecar stock while AE2 sees a saturated long facade. |
| `enableAtomicBigCapacityPlans` | `true` | Allows exact planning above signed-long aggregate limits for supported hosts. |
| `bigIntegerMaximumBits` | implementation ceiling | Bounds all BigInteger intermediates and persistence. |

The exact decimal ceiling is `10^16384 - 1`.

## Physical Crafting Tree

The compatibility section name remains `[exactVectorCrafting]`.

| Key | Default | Purpose |
| --- | ---: | --- |
| `enabled` | `true` | Enables strict physical crafting-table tree transactions. |
| `enableAqeBigIntegerParents` | `true` | Offers an eligible AQE parent to the physical path before checked-long child windows. |
| `maximumPatternNodes` | `1024` | Maximum distinct physical recipe steps in one transaction. |
| `maximumUniqueInputKeys` | `128` | Maximum distinct exact ME boundary-input keys. |
| `maximumUniqueOutputKeys` | `128` | Maximum distinct final and fixed-return output keys. |
| `maximumStartsPerGridPerTick` | `1` | Maximum new ownership transfers per grid and tick. |
| `maximumActiveStagesPerGridPerTick` | `256` | Maximum active, setup-ready, or dependency-ready physical step operations per grid and tick. Dependency-blocked steps return their claim. |
| `maximumActiveTransactionsPerGrid` | `4` | Maximum concurrent physical parent transactions per grid. |
| `gridTimeBudgetMillis` | `2` | Soft main-thread scheduling budget measured from the grid's first Exact Vector operation. Trees up to 64 steps retain a full-scan guarantee inside the count limit. |
| `logVectorDiagnostics` | `false` | Enables bounded acceptance, recovery, and quarantine logs. |

Deleted direct-executor, artificial duration, fixed tree-energy, coolant, and
Compiled Crafting Island options cannot reactivate those removed paths.

## CPU Execution Budget

The CPU budget settings cap work started in one tick, not CPU storage or
displayed co-processors.

Recommended behavior:

- keep the hard co-processor cap high enough for the intended hardware;
- use adaptive per-CPU timing;
- keep the shared grid time budget enabled;
- retain a minimum progress allowance so one CPU cannot starve;
- lower the time target before lowering hardware capacity.

Sequential Instant continues AE2's original execution loop in measured waves.
It is not a whole-tree output conversion.

## Transactional Batch V2

The V2 protocol uses:

- source receipt;
- target receipt;
- persistent world journal;
- prepare, accept, account, reconcile, and forget phases.

Compatible adapters may enable it independently. ACO never treats an adapter
as atomic merely because it reports a large limit.

GTCEu and Mekanism native adapters remain separate from the physical
crafting-table tree. Their own recipe, tank, energy, and output checks remain
authoritative.

## Machine Intent

Recipe Intent options control candidate lookup and cache sizes. They may reduce
repeated recipe discovery, but never bypass the machine mod's live validation.

If an add-on version differs from the pinned integration range, disable its
intent path until the class and method layout has been re-audited.

## Compatibility-Disabled Paths

Old mutable terminal, storage watcher, bus transfer, IO Port, capability, and
full-storage simulation rewrites remain unregistered. Retained TOML keys are
read-only migration no-ops and cannot enable their removed Mixins.

This protects terminal insertion, Import/Export Bus behavior, and container
slot synchronization.

## Recovery

Do not delete transaction NBT or receipt data to clear a stuck job. Enable
diagnostics, preserve the world, and inspect:

- parent transaction ID;
- Worker transaction ID and payload digest;
- exact boundary before/after values;
- escrow contents;
- Pattern and recipe generations;
- quarantine reason.

An uncertain transaction is intentionally stopped rather than replayed.
