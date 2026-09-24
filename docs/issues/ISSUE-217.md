# Issue #217: Avoid replay-state copies for bulk VM producers

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/217
- Status: Implemented (phase 2 automated gates passed; live acceptance pending)
- Affected: Forge 1.20.1, native VM 0.3.1 bundled with ACO .6
- Related: #208, #215
- Local candidate: ACO 2.0.0-rc.6-rc4-vm.7-dev.2, native VM 0.3.2-dev.2
- VM pin: 0a1bb6a522ec6f07a5db61b0206189bd06f5f299 (phase 1: b2fa34edc3c10c4039bf4c10783de499a322dec6)

## Evidence and expected result

VmInstructionExecution.request constructs a Trace (copying stock, used, missing,
emitted, pattern counts and enclosing low-water maps) for every process. A unique
non-return-constrained producer already uses ceil(demand / output) executions
in one run. Its local repeat window cannot shorten that one run. Deep chains pay
for nested copies despite having no repeated local iteration to accelerate.
This is a source-level finding, not attribution of the live circuit's duration.

## Ownership and invariants

Only VM scratch execution changes. AE2/provider capture, final validation, ACO
exact accounting, external CPU execution, reservations and persistence retain
their existing owners. No long narrowing, speculative stock, skipped predicates,
new fallback, stale plan reuse or deployment. Keep ordered requests, all outputs,
returned containers, failed-branch rollback, byte cost and outer replay guards.

## Plan

For a unique producer whose existing Process.single flag is false, execute its
existing bytecode with the existing bulk count and extract the requested output
without a local Trace. Continue recording reads and minima in enclosing traces.
Keep the current traced path for multiple producers and direct return/self-input
constraints, including constrained descendants of otherwise bulk parents.
Instrument scratch copy count with a package-private counter for deterministic
tests, without adding log spam or any runtime decision based on that counter.

## Tests and acceptance

First add copy-count regression tests on the unchanged algorithm. A 64-stage
chain must keep exact output, input usage, shortages, recipe counts and byte cost
for orders 1, 2, Long.MAX_VALUE, MAX+1 and 10^64, with empty, partial and full stock,
while requiring zero scratch-state copies. Test shared byproducts and a traced
multi-producer ancestor with a bulk child; preserve returned-tool tests and
all existing real-AE2 oracle tests. Test cancellation and run both AE2 variants.
Local performance evidence is operation count, not production wall-clock speed.

## Preflight

Charter, regression history, class responsibilities, workflow, testing guide,
VM execution and existing accounting/capture tests reviewed. Scope is the Forge
selective VM module; no deployed NeoForge equivalent. No production JAR changes
or server startup/restart. The user explicitly does not require an ACO-off run.

## Implementation evidence

The old algorithm passes quantity/byte assertions but fails the zero-copy
regression: the 64-stage empty-stock order performs 192 state copies. The new
unique/bulk path reuses existing bytecode execution without its local Trace;
enclosing traces still receive reads and low-water updates. The 15 order/stock
combinations now pass with zero copies. Branch/byproduct, direct-return and
cancellation tests pass alongside existing VM accounting and capture tests.
See build-217-before.log and build-217-focused.log.

`build-217-upstream.log` and `build-217-uelm.log`: test/build/bundle audit and
eight real GameTests pass on each variant. Each full unit run has 666 tests:
660 passed, 6 existing optional-dependency skips, zero failures. The 64-stage
matrix covers all 15 order/stock combinations and keeps exact byte counts.

Development JAR SHA-256 (UELM gate build):
`FEE700B81151AB3E66DE45EF6D9E425FCE81B4553D24B58B60C18590812F3069`.
This is not a production circuit timing or proof of general server load reduction.

The development version distinguishes local artifacts from the published .6;
this change does not replace any existing release asset or production JAR.

## Phase 2: replay guard overhead

The user installed .7-dev.1. Production order 103 completed on 2026-09-24 at
14:23:50.989: supreme_quantum_control_circuit x1, simulation=true, 45160 ms.
A thread sample saw Trace.safeRepeats comparing stock key sets. This identifies
executed work, not its share of the total time. The user approved optimizing
membership checks and low-water snapshot timing, not changing branch rollback.

Implement an exact identity token for a State's stock-key domain: share through
copy/adopt, replace on insertion of a new key (including zero-count keys), keep
on quantity-only mutation. Equal tokens prove equal key sets. Different tokens
must retain exact key-set equality checks as needed; never use hash-only or
same-size equality. An exact successful comparison may share the token again.
Check the domain once per repeat iteration and reuse that result for window
reset. Replay updates existing keys only; all insertion sites must preserve
the token invariant, including read, extract, insert, copy/adopt and replay.

Copy low-water quantities only immediately before an actual positive replay,
not every attempted iteration. Keep the snapshot independent of the mutable
low-water map because replay updates the latter. Retain all numerical guards,
outer trace propagation, return ordering, byte costs, cancellation and exact
branch accounting. No state-delta journal or global plan cache in this phase.

Regression tests first: repeated quantity-only mutations need zero full-domain
scans; divergent same-size sibling branches and rollback never alias; separately
constructed equal domains are revalidated before token sharing; new zero-count
NBT keys invalidate the domain. A finite damaging-tool period over the wide
order/stock matrix must create low-water snapshots only for positive replays,
with unchanged input usage, shortages and pattern counts. Run all previous
oracle/bulk tests and both upstream/UELM build and GameTest gates. This is still
VM-owned worker state; no live API, CPU ownership or deployment change.

Phase 2 focused evidence: three performance assertions fail before the change
(`build-217-phase2-before.log`) and pass after it along with prior accounting and
native capture tests (`build-217-phase2-focused.log`). Two thousand quantity-only
domain checks go from 2000 full scans to zero. Independent equal domains require
one exact comparison before token sharing. Sibling/rollback/zero-count NBT cases
stay exact. All 15 damaging-tool order/stock combinations pass and snapshot count
equals actual positive replay count; no snapshot is made on failed replay checks.

The identity token belongs to worker scratch state, not live inventory or pattern
revisions. Domain equality is checked once per iteration. No hashing shortcut,
change in numeric guards, branch journal, or speculative quantity reuse was added.

Phase 2 full gates: `build-217-phase2-upstream.log` and
`build-217-phase2-uelm.log` both pass test/build/bundle audit, eight real GameTests
and release-readiness with explicit runtime PENDING. Each unit run: 670 tests,
664 passed, 6 existing optional-dependency skips, zero failures.

Development artifact: `aco2.0.0-rc.6-rc4-vm.7-dev.2_1.20.1.jar`, SHA-256
`E06A00365E977D981C90A14FEB8D0F5410B47FEF0F724D9A6966CF976E961131`.
No production deployment or restart was performed. The 45160 ms measurement
belongs to dev.1; dev.2 has no live circuit latency measurement yet.
