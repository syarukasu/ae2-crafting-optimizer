# ACO 2.0 planning redesign

Superseded as a design direction on 2026-10-03 by
[BigInteger API-only redesign](../design/BIGINTEGER_PLANNER_2026-10.md).
The notes below retain the dev.23 implementation history; they are not the new
architecture or evidence of production acceptance.

## Fixed contracts

- Keep the public BigInteger API binary-compatible, including plan sidecars, amount ledgers, external CPU registration, and persisted job accounting.
- Keep AE2's request, cancellation, CPU submission, and tick ownership. Planning is not physical crafting execution.
- Never narrow a requested amount, intermediate demand, byproduct, missing item, or CPU byte count to `long` inside the calculator.
- An unavailable or unproved fast path must preserve the ordered recipe semantics. It must not fabricate a craftable plan.

## Why a recipe graph is currently slow

The October 2 `emextras:supreme_quantum_control_circuit` order for one item took 28,823 ms. Stock capture took 3 ms; the worker used 7,593 ms of CPU; AE2 access waited 21,123 ms over 442 exclusive intervals. The ordered VM visited 23,185 branches and 433,740 branch entries. These are repeated stateful recipe trials and tick-bound live callbacks, not 28 seconds of reading a few hundred pattern definitions.

## Calculation model

1. Capture stock once at submission. Freeze each reached producer definition for the order; reuse the same definition on every later visit. Do not enumerate unrelated patterns.
2. Expand the reachable *deterministic* graph before arithmetic: one ordered producer per key, exact inputs, no cycle, and no return/container semantics. Build a demand program once and propagate `BigInteger` quantities through shared nodes. Preserve output batching and CPU byte accounting.
3. For that proved graph, compare its combined leaf demand with the captured stock. If a leaf is short, calculate the missing plan directly instead of first replaying a doomed non-simulation plan. An emitted leaf is not a stock shortage.
4. Alternative producers, substitute inputs, byproduct feedback, returns, and cycles remain stateful. Resolve only their relevant choices, preserve branch order, and use sparse transaction deltas. A later design stage must remove repeated equivalent trials; it may not approximate stock or silently choose a different recipe.
5. Keep live AE2/recipe callbacks outside graph arithmetic. Batch bounded callbacks, memoize only within the order, and retain the original callback for dynamic patterns. Never hold a server tick for an unbounded whole-catalog scan.
6. Materialize one AE2-compatible plan with exact BigInteger sidecar data. Execution and reservation remain separate from planning.

## Acceptance gates

- Differentially compare ready and missing plans against the ordered engine for stock empty/partial/full, one/two/`Long.MAX_VALUE + 1` orders, shared demand, byproducts, returns, alternatives, cycles, and cancellation.
- Count graph expansions, branch trials, live callbacks, exclusive intervals, server tick time, and exact item balances. Unit tests establish algebraic equivalence, not real-server speed.
- Re-run the same creative-control-circuit order on the actual server after deployment. The 10-second goal is unverified until the full recipe tree and physical execution are measured.

## First implementation slice

Resolve only unique-producer exact dependencies once when the stock capture is complete. Stop expansion at any stocked intermediate or multi-output pattern: either one can make a lookahead depend on an unused branch. Use a proved leaf deficit to skip the redundant failed pass for a deterministic shortage. All other orders keep their ordered behavior until a separate stateful solver is proven.

## Local dev.23 implementation

- The VM now builds a direct BigInteger plan for a proved exact graph with fully funded leaves, or a proved missing plan after resolving only deficient stock-only leaves. Stocked leaves do not trigger unused producer lookups.
- Ordered alternatives can bypass an earlier route only when its exact graph proves that even one batch is impossible. Partial first-route production still uses the ordered VM.
- The native capture batches pattern skeletons and defers input templates, validity, emitters, remainders, and fuzzy selection until a route is actually used. The skeleton retains every candidate's input count for AE2-compatible byte accounting.
- Multi-output patterns stay on ordered execution. Allowing them into the direct path caused unused byproduct dependencies and byte-accounting regressions in the full catalog tests.
- This is a local candidate, not the 10-second or physical-execution acceptance. The real industrial graph still needs timed planning and completion checks before deployment or release.
