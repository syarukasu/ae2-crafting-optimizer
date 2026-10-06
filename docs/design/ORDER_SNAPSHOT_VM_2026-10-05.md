# Order snapshot VM calculation

Issue: #218. Candidate: `2.0.0-rc.7-vm-refs.2-local`.
This describes the connected VM path, not the superseded graph/SMT calculator.

The `vm-refs.3-local` follow-up preserves this acquisition and arithmetic design.
It retains a compiled block lazily in per-order process metadata instead of
re-entering compiler validation on every block. Returned-tool semantics and the
open-domain boundary below are unchanged.

## Ownership

1. The existing accounting boundary obtains accessible stock for this order.
   Exact quantities stay BigInteger; unknown exact quantities are not guessed.
2. NativeVmPlanningContext acquires reachable ordered producers, emitters,
   definitions, template units and scaled coefficients on the owning thread.
   It includes all outputs when collecting the finite candidate key domain.
3. Candidate validity and remainder observations are captured once per input
   identity and full AEKey. Input families restrict comparisons without dropping
   NBT. Negative and null answers are observations too. The domain is collected
   before these observations, so traversal order cannot hide a later byproduct.
4. One existing AE2 worker owns the calculation. It uses this order's stock and
   pattern observations, not new live stock, a second planner or old craft counts.
5. The result is passed through the existing ordinary/wide API boundary. Planning
   creates no physical output and performs no inventory extraction or delivery.

## Arithmetic

The VM shares immutable dependency programs. Exact acyclic demand programs
combine common coefficients and apply stock, whole-batch rounding and missing
counts in BigInteger. Cases needing ordered stock alternatives, shared byproducts,
returns or producer choices use the same VM's transactional branch state and
proven periodic effects. Neither this repair nor compilation adds an order-size
cutoff, int/long clamp, execution-rate budget or provider throttle.

Patterns account for every output together. Input return capability is not
inferred from the primary template alone: an alternative can return a container
or reusable catalyst when the primary does not. An accepted alternative matching
a declared output can also seed the next craft. These cases use ordered effects
with exact repetition, so a single seed is not multiplied into the order quantity.
Returned items become available only after all inputs of that craft are acquired.

The existing accounting charge includes these returns. AE2 itself omits
alternative-only returns in its primary-template classification; a fixture with
seven consumed water buckets therefore correctly charges seven more bytes than
that AE2 reference. This intentional correction is checked with explicit quantities,
not by weakening all oracle comparisons.

## Boundaries still open

The finite domain covers stock, encoded templates, selected craftable keys and
declared outputs. It does not enumerate arbitrarily many hypothetical tool NBT
states. If a callback generates a new key in an input family outside that domain,
the existing synchronized compatibility callback is still needed. Such an order
does not yet satisfy the universal no-world-read objective. It is not safe to run
an arbitrary mod callback unsynchronized or to pretend its answer is null.

The original reported copper-wire false shortage has no retained order-time
snapshot. Later saved provider/cell data is useful evidence but is not the same
network, producer priority, access filter or stock as that rejected order.
The repaired return and candidate-domain cases must not be called proof that the
original production report is fixed.

## Acceptance

Quantity checks cover zero, partial and funded stock; 1, 2, Long.MAX_VALUE,
Long.MAX_VALUE+1 and 1024-digit orders; shared dependencies; all outputs; alternate
containers and catalysts; and network changes after capture. Frozen-domain tests
disable all world callbacks before worker execution. API descriptors, bundles and
both AE2 variants are separate compatibility gates, not completion evidence.

Performance data reports acquisition and VM time separately. The 48-stage native
processing-pattern matrix is synthetic, not the live creative-circuit tree.
Private saved-coefficient replay excludes live crafting callbacks, external cells
and exact provider ordering, so it cannot establish full industrial correctness.
Production false-shortage resolution, the live 9,220,000,000,000,000,000-circuit target,
wide physical execution, cancellation/refund, restart recovery and concurrent-job
acceptance remain unverified. The user requested candidate JAR only: no production
change, restart or release accompanies this candidate.
