# P0-P8 Implementation Status

This document describes the ACO `1.3.1` runtime-qualification candidate. P9
startup and gameplay qualification is performed separately by the pack
operator; source completion alone is not a release qualification claim.

## Completed Source Phases

- P0: conservation ledger, ownership proof, malformed-state locking, rollback,
  and fault-oriented receipt tests.
- P1: immutable provider-generation and recipe-generation keyed compiled graph,
  bounded SCC analysis, and stale publication rejection.
- P2: generation-cached primitive-array Root Programs, checked-long symbolic
  planning, checked AE2 tree arithmetic guards, complete missing aggregation,
  referenced-key inventory validation, overflow promotion, strict authoritative
  topology, full-accounting Shadow qualification, cancellation, and standard-AE2
  fallback.
- P3: bounded BigInteger planning with an exact `10^16384 - 1` ceiling, exact
  capacity accounting, execution windows, NBT/packet magnitude limits, and
  optional AQE host API v3 integration.
- P4: shared capacity reservation, persisted multiple-job state, bounded
  round-robin leases, stale-generation rejection, child-job binding, and paged
  status synchronization.
- P5: provider-owned payload escrow, source receipt schema 3, exact partial
  extraction recovery, target payload digests, forward-only journal phases, and
  fail-closed quarantine for unprovable side effects.
- P6: exact GTCEu 7.5.3 item/fluid recipe verification, condition and voltage
  checks, native output-parallel limit, and standard-AE2 fallback.
- P7: exact Mekanism 10.4.16 plus Applied Mekanistics 1.4.3 item/fluid/chemical
  recipe verification, deterministic output checks, factory/CachedRecipe bounds,
  and standard-AE2 fallback.
- P8: source tests, dependency-signature compilation, Mixin/static audit,
  documentation, and clean ACO/AQE builds.

## Safety Decisions Based on Earlier Failures

- The authoritative planner is injected into `computePlan`, not at the head of
  `CraftingCalculation.run`; AE2's worker lifecycle and completion notifications
  remain intact.
- A native adapter cannot report a positive count without a durable ownership
  proof matching transaction ID, execution count, and complete payload digest.
- `pushPattern == true` is not treated as proof of aggregate completion.
- Every real source extraction is recorded before the next extraction. Recovery
  restores the receipt list, never the unproven planned total.
- GTCEu and Mekanism still execute their own recipes. ACO does not call
  `setupRecipe` or `CachedRecipe.process` speculatively.
- Ambiguous recipes, chance outputs, unsupported capabilities, combinatorial
  matching beyond the hard search budget, stale generations, or version drift
  disable only the fast/native path and return to AE2.
- The maximum default transaction and BigInteger execution window is `65,536`.
  CPU, grid, transaction, and wall-time budgets still apply.
- All behavior-changing experimental switches remain false by default.

## Deliberate Fail-closed Boundaries

AE2 15.4.10 does not expose idempotent transaction IDs for energy extraction or
waiting-output insertion. A crash persisted exactly inside `ENERGY_ACCOUNTING`
or `OUTPUT_ACCOUNTING` is quarantined. This can require operator recovery, but
ACO will not guess and create an item duplication or a second energy charge.

Source receipt schema 3 removes the former ambiguity for input extraction.
Legacy source schema 1 recovered in `EXTRACTING` still quarantines because it
contains no exact partial-extraction evidence.

## Automated Result

The P0-P8 source suite currently contains `153` tests with zero failures. It
covers pure planning, overflow, 64/128/1024-digit counts, persistence codecs,
multi-job scheduling, conservation, receipt state machines, malformed payloads,
and bounded ambiguous matching.

Automated tests cannot prove transformed Forge runtime behavior, actual machine
Capabilities, chunk/world save ordering, Arclight behavior, multiplayer packet
compatibility, or long-running TPS. Those are P9.

## P9 Operator Matrix

Do not change the version or publish a release until all of these pass on a
copied world:

1. Forge client, Forge dedicated server, and Arclight startup.
2. AE2 and Advanced AE standard crafting with every experimental child switch
   disabled.
3. Compiled planner Shadow Mode comparison for possible, impossible, recursive,
   tag, substitution, item, fluid, and chemical requests.
4. GTCEu and Mekanism V2 separately, including blocked input/output, no power,
   cancellation, machine break, chunk unload, restart, and forced stop at every
   receipt phase.
5. Exact before/after conservation for CPU inventory, provider send buffer,
   machine inputs/outputs, progress, waiting outputs, and energy.
6. AQE BigInteger jobs at long boundaries and configured large counts, multiple
   simultaneous jobs, cancellation, structure reform, restart, and status GUI.
7. Multiplayer reconnect, mismatched client/server rejection, sustained MSPT,
   memory, and GC observation.
