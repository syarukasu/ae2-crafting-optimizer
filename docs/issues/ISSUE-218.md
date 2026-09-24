# Issue #218: API-compatible demand-driven planning redesign

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/218
- Status: Implemented (runtime acceptance pending)
- Target: ACO 2.0.0, Forge 1.20.1, upstream AE2 and AE2-UELM
- Related: #217, #208, #215, #190, #206, #164

## Evidence and expected behavior

The user permits replacing all internals except the external BigInteger API.
The current native capture traverses every reachable producer before checking
whether inventory already satisfies an input. It constructs ExactBranchBytecode,
then FrozenPattern, then the real VM bytecode, although the first representation
is never executed. Unknown input observations cross the server boundary.
The public plan inspection API branches on four internal implementation classes.
These are source findings, not attribution of all production latency.

Production supreme quantum control circuit x1 took 45160 ms on dev.1 and
101451 ms on dev.2. Stock and server load were uncontrolled. New synthetic
operation-count evidence must not be reported as a production speedup.

## Ownership and invariants

AE2 owns provider priority, pattern semantics, ordinary CPU jobs, live storage
and real dispatch. ACO owns exact quantities and its existing transactions;
AQE/AAC own their CPU/worker execution. Keep public method descriptors, constants,
persisted jobs and receipt ownership. No long narrowing, double reservations,
generated physical outputs, or fallback after ownership transfer.

Live callbacks stay on the server thread. Worker arithmetic uses captured data.
Inventory is per request and is not a reusable craftability cache. Changes in
inventory do not cancel a calculation; submission must reserve current stock.
Changed patterns/recipes or requester topology must not adopt a stale plan.

## Design and write scope

1. Freeze the compiled public API surface, including public types referenced from
   its method descriptors, and verify it without loading optional CPU classes.
2. Replace NativeVmCapture's eager traversal and unused legacy graph with one
   per-order pattern catalog. Resolve producer lists only when the ordered VM
   needs an unfulfilled input. Freeze a producer's shape and input selection in
   bounded server batches, and compile it once in the request-local scope.
   Coalesce producer lookup, shape and primary-rule reads within the same
   2 ms / 32-pattern / 128-observation server slice. Do not introduce a separate
   round trip for each metadata stage. Count handoffs in a real two-thread test;
   deep empty-stock chains still require demand-driven server visits and must
   be measured in production before claiming an overall latency improvement.
   Keep all candidate priority, failed-trial rollback, return ordering, exact
   byte costs and stock accounting in the already-tested arithmetic core.
3. Preserve generic live predicates and final revalidation. Optimize only proven
   native processing input semantics, not arbitrary add-on predicates or NBT.
   Record demanded patterns and live handoffs; do not add an unbounded cache.
4. Audit the requester and amount/profile boundaries against AE2 source. Add
   regression tests before changing a verified incompatibility. Unknown dynamic
   behavior is not permission to skip checks.
5. Expose a generic internal exact-plan view behind the unchanged public API;
   remove concrete planner selection from public inspection. Retain old factory
   return types as compatibility surfaces even if not used by new calculations.
6. Keep execution/persistence ownership unchanged in this change. Existing real
   machine, reservation, cancellation and stock-churn GameTests must still pass.
   Release acceptance must also cover external CPU completion/restart and the
   production stock/amount/performance matrix. No production JAR replacement,
   restart or stable release until that evidence exists.
7. Keep development builds available, but make the release-readiness task reject
   pending runtime rows for a stable numeric version or when invoked with
   -PrequireRuntimeAcceptance. Verify that rejection without editing evidence
   rows to claim unperformed tests. The old task only warns on these rows.

## Tests first

- A stocked intermediate must not visit its producer's descendants.
- An unused alternative must not inspect its descendant patterns/return states.
- Partial stock must expand and consume the missing portion exactly.
- 1, 2, Long.MAX_VALUE, MAX+1, 10^64; empty, partial and full stock.
- Shared byproducts, returns, fluids, NBT variants, alternatives, cancellation,
  changed provider/recipe, requester detach and concurrent independent orders.
- Existing AE2 oracle quantities, pattern counts, bytes and CRAFT_LESS behavior.
- Compiled API descriptor and constant preservation; exact views for old and new
  plan types including bytes-only overflow and missing simulations.
- Both AE2 variant builds and real GameTests. Runtime acceptance is separate.

## Preflight

- [x] Read PROJECT_CHARTER, REGRESSION_HISTORY, CLASS_RESPONSIBILITIES and TESTING.
- [x] Read native capture/compiler/interpreter and AQE/AAC API consumers.
- [x] Define ownership, invariant and no-fallback boundaries.
- [x] Define failures as repeatable automated tests before source edits.
- [x] Scope to Forge 1.20.1; do not claim a NeoForge implementation.

## Release gates

A 2.0.0 stable tag requires passed compatibility/correctness gates and recorded
end-to-end evidence, not just this issue's source cleanup. Runtime evidence must
identify artifact hashes, stock, request quantity, exact result, elapsed time,
server work/wait and completion/cancel/restart behavior. Final circuit target:
the server's creative control circuit at about 922京, raw-material tree, <10 s.
Do not publish the target as achieved without the actual measurement.

## Results

Implemented in the ACO working branch `redesign/218-api-planning` and native VM
commit `12c8f141b82a7b2936aeee841e88042de7829229`. Neither repository was pushed.
Production server/client JARs have not been changed and were not restarted.

- Removed the unexecuted ExactBranchBytecode capture and eager descendant walk.
- One request-local producer catalog feeds the existing ordered exact VM.
- Preserved requester grid, null-source/no-stock behavior and optional simulated
  extraction; requester grid changes restart capture without adopting old data.
- Standard processing inputs retain exact NBT identity without a fuzzy stock
  family scan. Custom inputs and returns retain live callbacks and revalidation.
- Kept public API descriptors/constant values, added generic exact plan views,
  and stopped the obsolete compiled-graph switch hiding the active API profile.
- Added a stable release gate that rejects pending runtime acceptance. The
  stable 2.0.0 scope carries forward RC regression scopes, not only this change.

### Automated evidence (2026-09-24, Java 17.0.17, Forge 47.4.18)

| Runtime target | JUnit | Real AE2 GameTests | Bundle audit |
| --- | --- | --- | --- |
| AE2 15.4.10 | 697 passed, 0 skipped, 0 failed | 8 passed | passed |
| UELM 15.5.0-uelm | 697 passed, 0 skipped, 0 failed | 8 passed | passed |

Optional contracts used actual AQE 2.2.7, ExtendedAE Plus 1.5.5, NeoECO 20.3.0
and 20.4.0 JARs. Those were bytecode/isolated contract tests, not a launched
combined modpack. The existing industrial audit tooling passed all 10 tests.
ABI baseline: 1,182 rows from local pre-redesign commit `dda32ad`.

Fail-before/fix-after cases are retained in `build-218-*-before*.log`, including
unused descendants, ignored requester boundaries, missing generic exact views,
native NBT-family over-observation, obsolete profile gating and server handoffs.
Full logs/results are copied under `build/acceptance-218/{upstream,uelm}`.

The NBT fixture goes from 2,049 stored input observations to 1 without accepting
any of the 2,048 wrong variants. The deterministic two-thread one-pattern fixture
goes from 5 server visits to 3 (stock, emitter, combined producer metadata).
These are operation-count improvements, NOT 40% faster production crafting.
The fluid/NBT matrix also checks empty/partial/full stock at 1, 2, Long.MAX_VALUE,
MAX+1 and 10^64, comparing the exact path with the generic input path.

Canonical upstream-compiled candidate:
`build/acceptance-218/upstream/aco2.0.0-rc.7-dev.1_1.20.1.jar`
SHA-256: `3E439DC866B0AF3D0E3BFB98CFAF3794FA778E199EB85E3B4B0EE9B90C43BF21`.
UELM-compiled validation artifact:
`build/acceptance-218/uelm/aco2.0.0-rc.7-dev.1_1.20.1.jar`
SHA-256: `281E25C34636D069692AC3C204E267B70D6DE4080E3EBF9E93E694E997A0E8FD`.
These are alternative build artifacts, not two JARs to install together.

### Still blocking stable release

- The execution restriction below requires implementation, not a checkbox.
- External CPU large-job completion, cancellation/refund, persistence/restart,
  and competing orders require full-stack acceptance at fixed artifact hashes.
- The production stock/quantity/performance matrix has not been run for this
  candidate. Deep empty-stock recipe chains can still wait on server handoffs.
- Actual I/O server work, allocation and the final <10 s circuit target are
  unverified. No zero-overhead server-I/O claim is made.
- The pinned new native VM commit must be pushed before remote CI can fetch it;
  remote CI, PR review and a stable release have not been performed.

## Confirmed remaining execution restriction

VmPhysicalPlanBinding.prepare delegates the existing physical preparation to
SelectedBranchPhysicalPlan.prepare. That executor rejects external-push patterns,
non-fixed input alternatives and interleaved cyclic steps. A successful VM plan
does not prove these wide plans can be submitted/completed. This change does not
replace that receipt executor or permit material generation from computed output.
Full industrial BigInteger execution is a code-level release blocker, not merely
a missing performance benchmark. See #190 and #208 before extending the executor.

## Additional boundary finding

NativeVm.calculate accepts ICraftingSimulationRequester but does not use it.
AE2 NetworkCraftingSimulationState uses no stock when getActionSource is null;
CraftingTreeNode uses no producer when getGridNode is null. The new boundary
must preserve these cases, and re-capture if the requester's pattern grid changes.
The optional AE2 simulated-extraction setting must be honored. A wide stored
quantity cannot be rounded to long for permission simulation: require an exact
extraction proof for that case, otherwise fail explicitly without claiming stock
is available. The default setting is false in the pinned AE2 source.
