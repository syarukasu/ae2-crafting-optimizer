# Issue #190: Real industrial recipe acceptance

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/190
- Status: Implemented (shared-input and emitter snapshot follow-up; full acceptance remains PENDING)
- Target: 2.0.0 prerelease; Forge 1.20.1 modpack capture, shared planner verification on both loaders
- Related: #156, #179, #185, #167, #176, #182

## Problem and evidence

Synthetic thousand-node tests exclude recipe capture and do not prove real industrial
end-to-end planning latency. The current pack has multiple creative circuit routes,
non-consumable catalysts, alternative ingredients and mod-generated recipes. Gameplay
progression and encoded ME patterns are incomplete. Source scripts alone are not the
final RecipeManager state. The server is stopped; no Java processes were running at
inspection. No production world or recipe changes are authorized by this experiment.

## Expected result

Capture the final registered recipes without requiring progression. Preserve exact
amounts, identity, alternatives, chances, catalysts and unresolved cases. Feed
auditable fixtures to the actual planner and independently check integer conservation.
Measure cold/warm/concurrent costs for 1, 100, 9220000000000000000, Long.MAX_VALUE,
Long.MAX_VALUE+1 and 10^64. The literal 922-kei value and long boundary are separate.
The eventual acceptance target is a complete correct plan under 10 seconds, not a
time for a cached arithmetic subroutine. Missing coverage must fail acceptance.

## Ownership and invariants

AE2 owns pattern suitability, candidate ordering, live inventory and execution.
GTCEu and other machine mods own recipes and catalysts. ACO owns immutable planning
and exact APIs, not machine simulation or inventory generation. Test tooling owns
only exported fixtures and private copies of server files. No live world reads on
worker threads, clamping, recipe rewrites, outputs from plans, or fallback after
custody. Concurrent planning does not itself reserve inventory.

## Implementation scope

First add opt-in tools under tools/industrial-benchmark and a private isolated server
capture. Export from final RecipeManager, use GTRecipeSerializer.CODEC for GTCEu and
retain original KubeJS recipe JSON only when its final ID remains registered. Record
unsupported runtime serializers explicitly. Collect resolved item/fluid tags and
ingredient contents, preserving stack NBT and exact counts as strings.

The tools depend on existing CompiledPattern, CompiledCraftingGraph,
CompiledRootProgram, BigCraftingPlan and the byte counters, never the reverse.
No new production owner or Mixin is planned for capture. Any production fix requires
an observed failing test and a specification update before implementation.

## Pre-implementation checklist

- [x] Read PROJECT_CHARTER, REGRESSION_HISTORY and CLASS_RESPONSIBILITIES.
- [x] Read planner performance tests, #179 and #185 boundaries.
- [x] Inspect installed KubeJS and GTCEu public APIs rather than inventing serializers.
- [x] Define absent capture and unsupported coverage as failures, not skipped success.
- [x] Fix ownership, fallback boundary and forbidden shortcuts above.
- [x] Capture is Forge-only; any common planner fix must also be tested on NeoForge.

## Tests and completion gate

- Offline capture validation: IDs, exact decimal amounts, registry references and input hashes.
- Failing malformed/unsupported fixtures must be reported explicitly.
- Small quantity comparison with actual AE2, independent wide integer replay.
- Stage timing and concurrent isolated planning; no double reservation claim from pure tests.
- Build/regression manifests on every affected loader.
- Runtime acceptance (world orders, refunds, restart and tick cost): PENDING.

## Observed compiler rejection and proposed fix

`CompiledRootProgram.compile` checks the whole graph's SCC before consulting the
snapshot's `canEmit` predicate. Thus an item supplied externally still drags its
unused recycling recipe into cycle detection. Even moving the predicate first is
insufficient: ancestors also belong to that whole-graph SCC. The existing local
topological sort already checks the actually traversed graph after emitter edges
are cut. Remove only the premature global SCC rejection; retain local cycle,
candidate, input, output, cancellation and generation checks. No live emitter is
queried on a worker: the existing immutable predicate contract is unchanged.

Regression: an assembly consumes two co-products, one is externally emittable and
also has an unused reverse recipe from the final product. Compare AE2 itself for
both ingredient orders and multiple initial stocks; test real cycles still fail,
emitter identity, checked long/BigInteger and independent concurrent invocations.
Owner: CompiledRootProgram's compile phase only. Apply the identical fix on both
loaders after observing the new test fail on the unchanged compiler.

## Observed legacy accounting defect

The map-based LongCraftingPlanner and BigCraftingPlanner count simulated co-product
withdrawals as initial stock. Their public fallback result remains non-authoritative
(`provenEquivalent=false`), but its usedInventory is wrong. Preserve a private initial
snapshot and reserve only the peak initial-minus-current deficit at each extraction.
Do not alter candidate choice, fallback eligibility, ownership or live inventories.
Forge failing tests reproduce both arithmetic paths, 10^64 orders, partial stock,
input reversal and the public fallback. Apply the same fix and tests here.

## Results

### Follow-up scope: shared inputs and emitter snapshots

The ordered evaluator is selected only for coupled outputs. A single-output DAG
with a material shared by two inputs still fails Ae2StrictCraftingTopology even
though the existing ordered simulator can preserve its extraction order and bytes.
Select that evaluator for shared-input DAGs only when every slot has one exact
alternative. Retain the independent tree evaluator and all dynamic-input guards.
Do not select a producer arbitrarily or enable ambiguous recipes in this change.

Snapshot.compileRootOutcome rechecks registered/compiled producer counts for
emitter leaves, undoing the earlier emitter-pruned compiler proof. Skip producer
validation only for captured emitters; keep it for every non-emitter and retain
generation checks. A secondary output of a captured pattern can still be present
in the pure graph while an emitter has zero captured producers, reproducing this
failure without unsupported live reads.

Before adoption, require failing tests for strict shared-input acceptance and real
Snapshot outcome checks; compare duplicate slots, repeated intermediates, partial
stock, missing stock, whole fluid templates, and CPU bytes against actual AE2.
Exercise exact wide quantities and independent concurrent plans on both loaders.
Tests must detect any remaining tree-accounting mismatch before broadening the
authoritative path. Owners remain CompiledRootProgram, OrderedByproductPlanner,
and Ae2ImmutablePlanningGraphCache. No execution/persistence/API version changes.

Follow-up implemented in CompiledRootProgram and Ae2ImmutablePlanningGraphCache.
Four new failing tests reproduced the defects before the production changes.
Ae2PlanningInventorySnapshotTest covers real Snapshot outcomes and retained
non-emitter/exact-domain rejection. ReusableByproductAe2OracleTest adds 250
seeded shared-DAG cases and 24 repeated-fluid-slot cases against actual AE2,
including used stock, missing amounts, emitted amounts and CPU bytes.
SharedInputPlanningTest covers 1, 100, 9220000000000000000, Long.MAX_VALUE,
Long.MAX_VALUE+1 and 10^64, independent conservation and CPU bytes, cancellation,
and concurrent evaluations of an immutable snapshot. This is not a reservation
or live-world concurrency test.

SymbolicCraftingPlannerTest now checks six ordered requests instead of five
unique indexed keys. The second shared-material request consumes prior surplus;
the producer still runs once and consumes exactly one raw item. It also checks
100 bytes from the ordered trace, rather than dropping the stock assertions.

Full clean builds pass on upstream Forge (562 tests), UELM (562 tests) and
NeoForge (573 tests), with zero failures, errors or skipped tests. The regression
manifest passes. This change does not establish complete modpack-tree latency.
The existing ordered request-expansion bound still applies; shared DAGs are not
claimed to have linear cost for every graph shape. No production deployment or
runtime verification was performed for this follow-up.

### Previous bounded fixes and capture results

Two bounded correctness/performance defects are fixed:
- Emitter-pruned cycle handling: four failing tests before the change; all five
  tests pass after it. Six additional scenarios match actual AE2 counts, stock,
  missing items, emitted items and CPU bytes.
- Legacy map stock accounting: all three new tests fail before the change and
  pass afterward, including 10^64 and the non-authoritative public fallback.

Local build and full automated suites pass on upstream Forge (554 tests), UELM
(554 tests) and NeoForge (565 tests), with zero failed/skipped tests. Capture-tool
audit has ten passing Node tests. Forge-only capture tooling is on the 1.20.1
branch; it is not part of either runtime JAR.

An isolated pack capture (no production world/player data or Bukkit plugins) found
two actual root recipe IDs:
- kubejs:circuit_assembler/evolved_creative_control_circuit
- kubejs:syaru_nano_forge/syaru_nano_forge/creative_control_circuit_batch

The real-core diagnostic builds an eligible candidate index in hundreds of
milliseconds, but BOTH root routes still decline at MULTIPLE_PRODUCERS.
The Forge first-tick capture contains 59,229 recipes (30,306 GT), with zero capture
or normalization errors. The all-candidate coverage reaches 41,460 recipes; 8,441
of these have unsupported semantics. This is not an encoded ME graph. The Forge
diagnostic records the last visited key and its actual candidates when the
compiler rejects MULTIPLE_PRODUCERS; it must not choose one arbitrarily.

The diagnostic's first MULTIPLE_PRODUCERS keys are minecraft:redstone (61
candidates, nano forge root) and kubejs:gtnh_qio_cleanroom_backplane (four
candidates, circuit assembler root). This depends on the recorded candidate
order, and does not claim all 65 candidates are present in the live ME network.
Normalized input SHA-256:
57BC2D2A8777AE8531F78D23148C9E9EF24BEB19DCCC2D0FEAE98A9F531E9EF1.

Twelve one-machine quantity cases pass independently; those omit the dependency
tree and are explicitly NOT end-to-end acceptance. Unsupported serializers,
producer selection and submit-ready plans remain unresolved. No production JARs,
world inventories, recipes or progression were changed. Do not close this issue
or claim the sub-10-second goal, concurrent reservations or runtime completion.
