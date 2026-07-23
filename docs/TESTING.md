# Testing

## Build

```bat
gradlew.bat clean build
```

Expected:

- Build succeeds.
- Jar is created under `build/libs/`.
- No generated `build/` or `.gradle/` directories are committed.

For the disabled next-generation engine, also run `gradlew.bat clean test build`
and follow the recovery/fairness matrix in
[EXPERIMENTAL_ENGINE.md](EXPERIMENTAL_ENGINE.md). Building the source is not
permission to deploy or enable those paths on a live world.

The automated suite must cover checked overflow, deep/recursive graphs,
generation invalidation, planner fallback, source and target receipt NBT,
per-output accounting cursors, malformed and unknown-schema journals, retained
terminal evidence, scheduler operation/time budgets, and starvation prevention.
These tests validate source invariants; they do not replace the copied-world
kill/restart matrix.

During copied-world recovery tests, turn the experimental master off with one
transaction intentionally unresolved. Confirm journal reconciliation continues
and that the affected CPU does not execute the same task through AE2's normal
path until its source receipt reaches a terminal state.

## Automated Experimental Checks

The `src/test` suite currently verifies the parts that can run without a Forge
world:

- every checked-long boundary and overflow promotion;
- immutable graph fingerprints, generation invalidation, SCC recursion,
  cancellation, stale result rejection, alternatives, fallback proof flags,
  and randomized acyclic recipe graphs;
- 64-, 128-, and 1024-decimal-digit BigInteger NBT round trips, plus the exact
  16,384-digit planner/NBT/packet ceiling;
- primitive-array Root Program tests for shared intermediates, complete missing
  lists, checked overflow promotion, referenced-key-only reads, ambiguous-route
  fallback, Shadow qualification, and generation-local rejection;
- capacity reservation, multiple-job rotation, bounded execution windows,
  waiting-output completion, clean cancellation, quarantine retention, long-job
  migration, runtime UUID persistence, per-recipe window persistence,
  parent-owned child allowance isolation, and count-memory rejection;
- status page round trips, protocol validation, malformed/trailing packet
  rejection, entry bounds, magnitude bounds, and packet-size bounds;
- int/long root-order boundary selection, exact `Long.MAX_VALUE` item and fluid
  unit conversion, out-of-range rejection, and checked existing-stock
  subtraction;
- V2 source/target receipt transitions, explicit extraction, energy, and
  per-output uncertainty barriers, rollback, bounded stale replay evidence,
  malformed-schema locking, and output cursors;
- deficit-round-robin operation/time budgets, persistence, reservation for later
  jobs, and no-starvation rotation;
- a 1,000-pattern chain planned for a 1024-digit request under a fixed time guard
  with expansion bounded by graph size rather than requested count.

An automated pass is necessary but not sufficient. Tests do not instantiate
real GTCEu/Mekanism machines, transformed Forge classes, an Arclight server, or
a multiplayer client.

## Long Root Amount Runtime Matrix

Install the same ACO build on the server and every client, leave
`enableLongRootCraftAmounts = true`, and use a copied test world.

1. Enter `2147483647` for an ordinary craft. Confirm the request behaves exactly
   like ACO-disabled AE2 and reaches the confirmation screen.
2. Enter `2147483648`. Confirm it reaches the confirmation screen without
   becoming negative, zero, or `2147483647`.
3. Enter `9223372036854775807`. Confirm the requested output in the plan remains
   exactly `Long.MAX_VALUE`.
4. For a fluid or chemical root, enter `9223372036854775.807` in a `1000`-unit
   display. Confirm the internal requested amount is exactly `Long.MAX_VALUE`.
5. Enter `9223372036854775808` and a short expression above signed long. Confirm
   the Next button remains inactive and no request packet starts a calculation.
6. Repeat step 3 with `=9223372036854775807` while the network holds zero, one,
   and the full requested amount. Confirm the calculated amounts are
   `Long.MAX_VALUE`, `Long.MAX_VALUE - 1`, and zero respectively.
7. Use Replan, Back, and Shift Auto Start. Confirm Replan retains the long
   amount, Back restores it in the input field, and Auto Start submits only the
   server-confirmed plan.
8. Close or replace the amount screen immediately after pressing Next. Confirm
   the stale container-ID packet does not affect the newly opened menu.
9. Set `enableLongRootCraftAmounts = false` on both sides and restart. Confirm
   the input returns to the AE2 int maximum and all normal int crafts still
   work.
10. Test once with mismatched client/server ACO builds. Confirm Forge rejects
    the protocol mismatch at connection instead of accepting an unknown packet.

## AppliedE Compatibility Matrix

Run the matrix separately with original AppliedE `0.14.3` and AppliedE TPS Fix
`0.14.7-fix2`; never install both because they use the same mod ID.

1. Install the selected AppliedE build and its ProjectE requirements on the
   dedicated server and every client.
2. Confirm startup reports `ACO AppliedE compatibility: detected ...` without a
   missing-class or Mixin error.
3. Add and remove an EMC Module, change the owning player's knowledge, and run a
   datapack reload. Confirm the terminal craftable set updates each time.
4. Request one known EMC item, then a large quantity of it. Confirm AppliedE
   consumes the exact EMC, creates the exact output, and the AE2 CPU finishes.
5. Cancel from the confirmation screen and cancel a submitted job. Confirm no
   temporary transmutation pattern remains craftable afterward.
6. Enable the experimental Compiled Graph, Shadow, authoritative, atomic
   capacity, and BigInteger host paths. Repeat the request and confirm AppliedE
   still uses AE2's original calculation rather than an ACO compiled plan.
7. Run `/aco stats`. `AppliedE compatibility` fallback and provider-refresh
   counters must increase; Shadow mismatch count must not increase solely from
   the excluded transmutation route.
8. Restart and repeat with the other AppliedE implementation. Compare EMC,
   outputs, CPU completion, and craftable visibility with an ACO-disabled run.

## Experimental Runtime Matrix

Do not combine the rows on the first pass. Begin from a copied world and enable
one child feature at a time.

| Stage | Enable | Required proof |
| --- | --- | --- |
| 1 | Master + compiled graph + Shadow Mode | AE2 and ACO agree for deterministic plans; every ambiguous plan reports fallback and AE2 remains authoritative. |
| 2 | V2 only | Unsupported targets run the normal AE2 path; unresolved receipts pause only their owner CPU. |
| 3 | GTCEu adapter | Exact item/fluid recipes conserve inputs, outputs, task progress, waiting state, and energy through block, power, chunk, and restart failures. |
| 4 | Mekanism adapter | Repeat Stage 3 for item, fluid, gas, infusion, pigment, slurry, and factory processes. |
| 5 | Fair scheduler | At least three jobs and two CPU implementations all progress under operation and elapsed-time pressure. |
| 6 | BigInteger API consumer | Long boundary, 64/128/1024-digit counts, multiple jobs, exact parent/child reservation accounting, cancellation, restart fingerprint validation, chunk reload, structure reform, and paged status all remain exact. |

For V2, kill the copied server independently while each source state is current:

```text
STAGED
EXTRACTING
EXTRACTED
TARGET_ACCEPTED
ENERGY_ACCOUNTING
ENERGY_ACCOUNTED
PROGRESS_ACCOUNTED
OUTPUTS_ACCOUNTING (at every output cursor)
OUTPUT_ACCOUNTING (before each individual waiting-output insertion)
```

Current schema-2 transactions recovered in `EXTRACTING` must restore exactly
the partial input list persisted by source receipt schema 3. Legacy schema-1
`EXTRACTING`, `ENERGY_ACCOUNTING`, and `OUTPUT_ACCOUNTING` must quarantine
rather than guess whether an untracked side effect completed. A quarantined
transaction is a deliberate fail-closed result; it must retain enough journal
and receipt data for inspection and must never resume the same AE2 task through
the normal path.

For every failure injection, compare these totals with an ACO-disabled control:

```text
CPU crafting inventory
Pattern Provider send buffer
target machine inputs
target machine outputs
AE energy charged
task executions remaining
waiting outputs
final network output
```

Any loss, duplication, double energy charge, stale plan, permanently calculating
screen, or job that reports complete before its outputs arrive fails the stage.

## Manual Runtime Checks

1. Start a Forge 1.20.1 client or dedicated server with AE2 15.4.10.
2. Confirm `config/ae2_crafting_optimizer-common.toml` is generated.
3. Request a small craft, such as a normal furnace-pattern output.
4. Confirm the craft confirmation screen leaves `Calculating` normally and shows AE2's standard result.
5. Request a large craft that cannot complete due to missing ingredients.
6. Confirm AE2's normal missing-item result appears without any preliminary preview behavior.
7. Enable `logCraftingCalculationDeduplication = true`, request the exact same large craft twice before the first calculation finishes, and confirm the second request logs an active calculation reuse.
8. Change a pattern provider or crafting node and confirm no stale calculation is reused afterward.
9. With `cacheCompletedCraftingPlans = true` and `cacheSuccessfulCompletedCraftingPlans = false`, repeat the same impossible request and confirm the missing/simulation result can be reused without allowing a craft to start.
10. Add/remove storage or change pattern providers and confirm the completed-plan cache is cleared.
11. With `fastFailMissingCrafts = true`, request impossible deterministic one-path crafts blocked by an item, fluid, and chemical in turn; confirm each returns a normal missing-items plan rather than a stuck calculating screen.
12. Repeat with a craft that has substitutions, tags, multiple possible patterns, or can complete successfully, and confirm AE2 performs its normal calculation.
13. Enable `logPatternLookupCache = true`, perform repeated large craft calculations, and confirm pattern-lookup cache hits appear without stale recipes after provider changes. Craftable-set caching is unregistered in 1.2.2.
14. Push an AE2 processing pattern into a GTCEu item-output machine and confirm the recipe still starts normally.
15. Push an AE2 processing pattern into a GTCEu fluid-output machine and confirm the recipe still starts normally.
16. Push an AE2 processing pattern into a Mekanism item-output machine and confirm the recipe still starts normally.
17. Push an AE2 processing pattern into a Mekanism fluid-output or chemical-output machine and confirm the recipe still starts normally.
18. Run `/aco intents list 10` shortly after a Pattern Provider push and confirm target position and outputs are recorded.
19. Run `/aco intents clear` and confirm captured intents and machine output indexes are cleared.
20. With `throttleCraftingExecution = true`, start a large craft on a high-co-processor CPU and confirm the craft progresses without one CPU monopolizing server tick time.
21. With `adaptiveCraftingExecutionBudget = true`, keep `targetCraftingExecutionMillis = 4` and confirm repeated heavy crafts do not create sustained MSPT spikes.
22. Start large jobs on at least three standard AE2 crafting CPUs attached to one ME grid, leave `sharedCraftingExecutionBudget = true`, and confirm every job progresses while combined CPU execution remains near the configured grid target.
23. Run `/aco stats` and confirm shared-budget limits/deferred operations increase during that test.
24. With Neo ECO AE Extension 20.3.x installed, confirm startup logs report its detected version and `execution budget true`.
25. Start one large job on a Neo ECO CPU and one on a standard AE2 CPU on the same grid. Confirm both progress and neither bypasses the shared grid target.
26. Toggle `[compatibility.neoEcoAe].throttleNeoEcoAeExecution = false`, restart, and confirm Neo ECO returns to its own configured normal/FastPath limits while standard AE2 pacing remains enabled.
27. Repeat a Neo ECO batch/aggressive FastPath craft and confirm input, output, energy, status, cancellation, save/reload, and completion match a run without ACO.
28. Repeatedly process the same Pattern Provider recipe through GTCEu and Mekanism, then confirm `/aco stats` reports machine cache hits.
29. Run `/reload`, repeat one GTCEu and one Mekanism processing recipe, and confirm the machines rebuild their indexes and still select the correct recipes.
30. Place several AE2 Import Buses and Export Buses with speed-card upgrades on a large ME network.
31. Confirm item movement still respects filters, redstone mode, craft-only mode, and scheduling mode.
32. Set the retained grid-tick, I/O-cap, IO Port, capability-cache, and storage-simulation keys to `true`, restart, and confirm those keys remain no-ops: AE2 still performs every live transfer and no removed-Mixin behavior appears.
33. Configure an Export Bus with a crafting card for an item that cannot currently be crafted and confirm the separate failed-request cooldown does not block a later valid craft after stock or patterns change.
34. Test ExtendedAE Ex Import Bus, Ex Export Bus, and Precise Export Bus if ExtendedAE is installed.
35. Test an AE2 IO Port moving high-capacity cells and confirm cells still move to the correct slots and contents are not lost.
36. Test the ExtendedAE Circuit Cutter with normal recipes and auto-export enabled. Confirm recipes complete and outputs leave the machine when possible.
37. Process several recipes in an AdvancedAE Reaction Chamber, including item and fluid outputs, and confirm input consumption, energy use, output, and auto-export remain exact.
38. Install/remove AE2 Overclock overclock and parallel cards in a Reaction Chamber and Circuit Cutter. Confirm the new count applies no later than the next server tick and the configured speed/parallel behavior remains correct.
39. Add/remove Pattern Providers and immediately request their recipes. Confirm provider changes are visible and no stale craftable recipe remains.
40. Insert an item whose ME stock is zero through a terminal, then extract and reinsert it. Confirm no item disappears and the visible amount matches live storage.
41. Run two ExtendedAE Circuit Cutters with different item/fluid inputs. Confirm each selects the correct recipe, a blocked output remains blocked, and changing input resumes the correct recipe without restart.
42. Repeat a no-recipe Circuit Cutter input on two machines, add the missing datapack recipe, run `/reload`, and confirm both machines immediately discover it.
43. With `asyncTerminalSearchSort = true`, search a terminal above the threshold using plain, `@`, `#`, `$`, and `*` terms; change the query rapidly and confirm only the newest generation appears.
44. Form an ExtendedAE Assembly Matrix containing several crafter blocks, pattern blocks, and speed blocks. Start enough jobs to use multiple internal threads and confirm every job completes.
45. If ExtendedAE Plus is installed, include its 32-thread crafter core and confirm the matrix busy count and scheduling still use all supported threads.
46. Break and reform that matrix, reload its chunks, and restart the server. Confirm formation, patterns, busy state, names, and stored jobs recover normally.
47. Run `/aco stats` after the add-on tests and confirm runtime-helper reflection, upgrade-count, Reaction Chamber, Circuit Cutter, or Assembly Matrix counters increase for installed integrations. Direct redirects into machine methods merged by AE2 Overclock remain compatibility-disabled.
48. Disable each `[addonMachineOptimizations]` sub-option separately, restart, and confirm its counter stops increasing while the machine remains functional through the original add-on path.

## Legacy Transactional API Compatibility

1. Toggle the retained 1.2.0/1.2.1 transactional keys and restart.
2. Confirm neither standard AE2 nor Advanced AE CPU execution is intercepted; both use their original 1.2.2 path.
3. Confirm old API consumers still link, but no legacy transaction commit metric increases.
4. Qualify the separate, default-off V2 protocol only with the copied-world matrix in [EXPERIMENTAL_ENGINE.md](EXPERIMENTAL_ENGINE.md).

## Sequential Instant Dispatch

1. Keep legacy batching and both GTCEu/Mekanism Native Batch switches disabled, then enable `enableInstantPatternDispatch`.
2. Submit the same large processing craft to a standard AE2 CPU and an Advanced AE Quantum Computer.
3. Confirm `/aco stats` increases `Sequential Instant` waves and completed operations while both legacy and V2 batch counters remain zero.
4. Confirm every GTCEu item/fluid and Mekanism item/fluid/chemical output exactly matches the requested amount and the CPU reaches completion.
5. Fill one target input or output, repeat the order, and confirm the first rejected wave stops dispatch until capacity returns without losing inputs.
6. Confirm one tick is not capped at 65536 operations: that value bounds one probe/wave only, while actual work is limited by `maxPatterns`, `4 ms` CPU time, and `8 ms` grid time.
7. Run `/aco stats` and compare `max wave` with Spark MSPT. A single wave may cross the deadline, but no second wave may begin after the CPU or grid budget is exhausted.

Compatibility-disabled sync checks retained in 1.2.2:

1. Set the retained terminal, watcher, aggregate refresh, range, and client coalescing keys to `true`, restart, and confirm ACO still leaves AE2's original terminal and storage synchronization paths active because the related Mixins are unregistered.
2. Insert an item whose ME stock is zero through the terminal, then extract and reinsert it. Confirm no item is lost and the visible amount converges to live storage.

## Deep Rewrite Checks

1. Enable the deep master switch and the three active feature switches (`patternSelectionByAvailability`, `p2pTopologyChangeOnlyRecheck`, and `fluidPatternRework`), then repeat one normal item craft and one normal fluid processing craft.
2. Give one output two valid patterns, toggle `patternSelectionByAvailability`, and confirm only the attempted order changes while AE2's success/missing result remains the same.
3. Confirm `networkForceUpdateCoalescing = true` remains a no-op in 1.2.2 and AE2's original aggregate refresh path remains active.
4. Confirm `visibleTerminalRangeSync = true` remains a no-op in 1.2.2 and AE2's original coherent terminal packet path remains active.
5. Add/remove and retune P2P tunnels several times in one tick. Confirm every structural change applies, then toggle grid power and confirm duplicate wake sweeps do not alter the final input/output topology.
6. Set `busSearchRewrite = true` and confirm it remains a no-op in 1.2.2; AE2 alone selects and exports fuzzy variants.
7. Craft with one exact fluid input and then with fluid substitutions. Confirm the exact case uses the fast path and substitutions fall back to AE2.
8. Disable each deep sub-switch separately and repeat its test to confirm behavior returns to the original AE2 path.

## Disable Checks

Set:

```toml
[general]
enableOptimizer = false
```

Expected:

- No preliminary missing preview behavior.
- No long root-order input; AE2's original int maximum and packet path are restored.
- No storage watcher throttling.
- No active calculation de-duplication.
- No completed-plan cache.
- No deterministic missing fast-fail.
- No pattern lookup cache.
- No craftable-set cache.
- No terminal snapshot pacing.
- No crafting execution budget cap.
- No adaptive crafting execution pacing.
- No shared per-grid crafting execution pacing.
- No grid tick budget deferral.
- No IO bus operations cap.
- No AdvancedAE/ExtendedAE/AE2 Overclock machine caches.
- No export-bus-style craft request throttle.
- No pattern micro-batching or Advanced AE Pattern Provider intent capture.
- AE2 behaves as if this mod were absent.
