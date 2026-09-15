# Issue #185: Ordered reusable-byproduct planning

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/185
- Status: Implemented (automated validation; runtime pending)
- Baseline: 2.0.0-rc.1; target: 2.0.0-rc.2, Forge 1.20.1 and NeoForge 1.21.1.
- Approved: implement byproduct reuse and publish a prerelease. No deployment or game launch.

## Evidence

CompiledRootProgram rejects COUPLED_OUTPUTS when a secondary output is reachable
from the requested root. Ae2StrictCraftingTopology additionally rejects shared
input occurrences because the old byte counter uses aggregate pattern counts.

Read upstream AE2 forge/v15.4.10 and neoforge/v19.2.17:
- CraftingTreeProcess.request: request each input in order, then insert ALL outputs.
- CraftingTreeNode.request: consume current simulated stock before emitting/crafting.
- CraftingSimulationState: requiredExtract is the maximum initial-minus-current
  inventory deficit, not all simulated withdrawals.
- CraftingCpuHelper.extractTemplates: consume whole input templates only.
- Tree bytes charge requested stacks on entry, pattern times after inputs, then
  eight bytes per constructed input-node occurrence.

## Scope and Ownership

CompiledRootProgram selects an ordered evaluator for coupled deterministic DAGs.
The evaluator retains initial stock and simulated balances, credits co-products
after input traversal, accumulates pattern times by ID, and records actual byte
charges. Existing independent single-output array evaluation remains unchanged.
Use one bulk request per branch, never one iteration per crafted item. Bound
expanded requests and check cancellation throughout. Do not access live worlds.

Preserve the public plan constructors while adding optional immutable accounting
data for ordered results. Carry it through promotion, normalization and preflight.
Preserve captured input-template sizes for exact extraction and byte arithmetic.
Do not change recipes, execution ownership, receipts, escrow, persistence or CPUs.

## Boundaries

This increment supports acyclic unique-producer, exact single-input-alternative
patterns, including shared dependencies, items, fluids, chemicals and distinct
NBT keys. It does not remove cycle/recursive-catalyst, ambiguous-producer or
dynamic-recipe guards. Missing CRAFT_LESS requests with retained AE2 search-tree
history must not be presented as proven-equivalent by this new route.
Returned containers remain outside exact input capture.

## Tests Before Adoption

- Red regression: sibling inputs consume A+B produced together exactly once.
- Partial initial B stock; producer order reversed; unequal yields and reuse of rounded surplus.
- No produced stock counted as initial reservations; maximum deficit semantics.
- Whole-template partial stock and CPU bytes compared with actual AE2 tree/state.
- Direct secondary-output requests; multiple upstream sources; full output overflow.
- Same ID with different NBT, item/fluid/chemical keys.
- BigInteger counts, cancellation, stale snapshots, old constructor compatibility.
- Cycles, unseeded catalysts and ambiguous/dynamic producers remain rejected.
- Existing full suites and both loader CI gates must pass.

## Completion Checklist

- [x] Read charter, regression index, owners, workflow and tests.
- [x] Inspect pinned AE2 algorithms and define supported boundaries.
- [x] Observe failing regression before implementation (three sibling/stock/huge regressions failed on rc.1).
- [x] Implement and run differential/boundary tests against actual AE2 on both loaders.
- [x] Local clean build and release-scope gate: 556 tests, zero failures/skips; runtime remains PENDING.
- [ ] Clean builds and current PR CI for both loaders.
- [ ] Merge and publish rc.2 with verified hashes and explicit live-test limitations.

## Implementation Evidence

- OrderedByproductPlanner owns transient simulated balances and bulk branch traversal.
- CraftingPlanTrace carries immutable request/byte accounting through promotion, normalization,
  authoritative materialization and shadow comparison. No persistent job format changed.
- Legacy independent-program fingerprints remain stable. Only the newly supported ordered
  graphs include template quantum in their fingerprint.
- Three sibling/partial-inventory/10^64 tests failed on rc.1 before implementation.
- Differential tests invoke actual CraftingCalculation.computePlan with fixture services:
  500 seeded shared DAGs, 16 input-order/stock cases, five fluid-template cases, two NBT/component cases.
- Boundary tests cover full output sums and shared producer deduplication, cancellation,
  repeat snapshot use, immutable traces, old constructors and capture-generation rejection.
- A registry-only fixture is not a Minecraft launch. Chemical addon execution, industrial
  completion, cancellation refunds, restart recovery and modpack speedup remain unverified.
