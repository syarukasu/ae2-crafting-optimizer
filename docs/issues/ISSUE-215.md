# Issue #215: Demand-driven VM input observations

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/215
- Status: Implemented
- Affected: Forge 1.20.1, 2.0.0-rc.6-rc4-vm.5, VM native 0.3.0
- Related: #208, #190, PR #214
- Candidate: 2.0.0-rc.6-rc4-vm.6; native VM 0.3.1
- VM pin: 6d9ed984afd680eb8bdfd8bdd12e12ded2a084e1

## Evidence and expectation

Production order 248, 2026-09-24 12:57:48.992:
`emextras:supreme_quantum_control_circuit x1` fails with
`VM capture resident limit exceeded`, NativeVmCapture.observe:266/plan:134.
The observation map reached 1,048,576 slot/key pairs before VM execution.
The responsible live key is not recorded; do not guess its identity.

Source inspection shows eager slot/candidate cross-products and transitive
return closure independent of order size or chosen branch. Time slicing occurs
between patterns, not inside a slot's closure. One-craft orders must not require
all hypothetical future damage/NBT states to be enumerated.

## Ownership and invariants

- The VM owns pattern acquisition and bytecode execution. ACO only supplies
  exact inventory and bridges results. AE2/external CPUs retain execution.
- All live input callbacks remain on the server thread. Worker queries only
  use captured values or synchronously request a bounded server-owned batch.
- Preserve AE2 candidate ordering, whole-template units, NBT, returns,
  BigInteger counts, peak reservation and failed-branch rollback.
- Retain every actually observed predicate for final revalidation, including
  failed branches. Pattern/recipe epoch changes invalidate capture; stock
  changes alone do not. No post-custody fallback or fabricated stock.
- No arbitrary count clipping, no blanket observation-limit increase, no
  assumed equivalence between different input callbacks or damage variants.

## Implementation plan

Replace the global return closure with immutable slot shapes plus order-local
demand-driven observations. Batch the candidates seen by each VM input request;
newly returned variants are observed only when subsequently needed. Seed primary
observations in bounded acquisition batches, retain per-order memoization,
and keep the VM's state/replay intact across server handoffs. Time-slice inside
candidate batches, check cancellation, and revalidate only actual observations.
Resource safeguards apply to retained working data, not speculative closure.

For live acceptance, retain the last 64 completed/failed/cancelled calculations
in a bounded VM-owned diagnostic ring (item ID, exact request, duration, status).
Expose read-only `/aco vm recent [limit]` through the existing operator command.
Do not restore per-order log spam or retain world/grid references in samples.
This measures calculation, not CPU execution or queue time before the worker starts.

## Tests before and after

- One/two crafts with a long damage/return chain: exact counts and bounded
  callbacks; old eager implementation must fail without allocating a million keys.
- A stocked first producer must not enumerate a discarded producer's returns.
- Returned variants produced mid-plan must be validated on the server and
  changed predicates must be rejected on final revalidation.
- Batches are bounded by operations/time, and cancellation stops observation.
- Preserve actual AE2 oracle tests and wide periodic-return/stock matrices.
- Run upstream/UELM unit, build, bundle and real GameTest gates.

## Preflight

Charter, regression history, class ownership, issue workflow and testing guide
read. Scope is the selectively compiled Forge VM module; no NeoForge equivalent
is deployed by this change. No production stop, JAR replacement or restart.

## Acceptance boundary

Local tests are not proof of live supreme-circuit latency. Production acceptance
requires a later user-approved update and the same one-item order.

## Implementation and evidence

- NativeVmCapture removes eager return closure and captures slot shape/primary
  rules once. Remaining observations are demanded by VM input execution in
  candidate-order batches. Returned variants are validated when encountered.
- VmInstructionExecution stops candidate batches when demand is satisfied;
  it does not allocate the entire slot/fuzzy-family cross product.
- Live callbacks run in batches of at most 128 checks or two milliseconds
  between callbacks. One mod callback cannot be preempted. Epochs and final
  observations remain revalidated. Resident-data budgets were not increased.
- NativeVmCapture latches cancellation because Future.get can clear a worker's
  interrupt temporarily; a running server callback must not resume observation
  after that worker clears its interrupted status.
- NativeVm owns a 64-entry exact diagnostic ring. ACO's operator command reads
  it without affecting calculation, inventory or logging frequency.
- `build-215-before.log`: two new closure/unused-producer regressions fail on
  the original implementation. `build-215-bounded.log`: those plus existing
  oracle tests pass. `build-215-thread-focused.log`: all seven demand-capture
  tests pass, including server-only callbacks, cancellation and wide returns.
- The synthetic 1024-producer / 2048-variant case completes using 2304 input
  callbacks INCLUDING final revalidation. This is an operation-count bound,
  not a measured speedup for the live circuit or a full server benchmark.
- `build-215-uelm.log` / `build-215-upstream.log`: both test/build/bundle/
  release-readiness gates pass. 662 unit tests, 656 passed, 6 existing optional
  skips, zero failures; eight real GameTests pass in each AE2 variant.

Remaining: live supreme-circuit order after an approved JAR update. The exact
live key that dominated the old closure remains unknown; new budget errors
include pattern ID, slot, key and retained-observation count if encountered.
