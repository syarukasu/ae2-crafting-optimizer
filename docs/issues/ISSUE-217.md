# Issue #217: Avoid replay-state copies for bulk VM producers

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/217
- Status: Implemented (phase 3 automated gates passed; live acceptance pending)
- Affected: Forge 1.20.1, native VM 0.3.1 bundled with ACO .6
- Related: #208, #215
- Local candidate: ACO 2.0.0-rc.6-rc4-vm.7-dev.3, native VM 0.3.2-dev.3
- VM pin: 2ab37f82e064eb04d146fc7e58223eaabc9899d8 (local commit; push fork before publishing ACO)

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

## Phase 3: attribution before live performance claims

Production dev.2 order 76 completed at 2026-09-24 14:48:52.634 in 101451 ms,
requested=1, simulation=true, route=vm-native. This is worse than dev.1's
45160 ms observation, but stock and server load were not controlled. Keep dev.1
as the comparison baseline; do not claim the phase 2 guards caused the regression.

Owner: NativeVmCapture owns server handoffs and phase timing; NativeVm owns
order lifetime; NativeVmDiagnostics owns bounded reporting. VmInstructionExecution
owns scratch branch state, not AE2 reservations or physical execution.

Add per-order phase wall times, server queue wait/action time, handoff counts and
VM worker active wall time (not CPU time). Include recaptures and retain timing
in the bounded recent-order ring. Keep slow-log rate limits and routine silence.
Record exceptional completion too. Never time every bytecode instruction.

Replace only multi-producer trial copies with sparse branch overlays. Unchanged
keys read through to their parent; success commits changed entries; failure drops
the overlay. Preserve key order, fuzzy indexes, low-water maps, byte costs and
outer trace guards, including failed-trial observations. Trace replay snapshots
remain unchanged. Retain a package-private copying trial mode for differential
tests, not a user configuration. Compare exact plans and all previous oracle tests
for empty/partial/full stock and 1/2/MAX/MAX+1/10^64 orders. Add nested failure,
byproduct, returned-tool, fluid and cancellation coverage before claiming parity.

Phase 2 key-domain and minimum-snapshot changes must pass the same comparisons;
they are not assumed to improve live latency. Server query batching already
exists (32 structural keys / 128 observations, 2 ms budget). Do not duplicate it,
enumerate future return closures or weaken final validation. Further changes to
handoffs require the new runtime timings. No deployment/restart/release in this
phase. Runtime timing and physical execution acceptance remain PENDING.

### Phase 3 evidence

The new performance regression fails against copying trials, then passes after
sparse overlays: 386 copied trial entries become 7 changed entries for a branch
after 128 previously visited stock keys. This is a fixture operation count,
not an allocation benchmark or measured production speedup. Trace snapshots
still copy state. Parent maps are unchanged during trials; nested success commits
only updates/additions, and nested failure discards them. Fuzzy ordering, lows,
guard reads, bytes and arbitrary-precision quantities retain their ownership.

Differential tests compare copying/unoptimized guards, copying/optimized guards
and sparse/optimized guards for nested failures, shared byproducts, returned tools
and fluids at 1/2/MAX/MAX+1/10^64 with empty/partial/full stock. All plan quantities,
pattern counts, bytes and flags match. This isolates mechanisms; it does NOT
replay the production inventory snapshot or benchmark the historic dev.1 JAR.
The phase 1 bulk path and the proven phase 2 guard reductions are retained.

`build-217-phase3-upstream.log` and `build-217-phase3-uelm.log`: each full run has
678 tests, 672 passed, 6 existing optional-dependency skips, zero failures/errors;
eight real AE2 GameTests, build and Jar-in-Jar audit pass on each variant. The
release-readiness gate explicitly reports runtime PENDING. Focused before/after
logs: `build-217-phase3-before.log`, `build-217-phase3-focused.log`.

Final UELM-gate artifact: `aco2.0.0-rc.6-rc4-vm.7-dev.3_1.20.1.jar`.
SHA-256: `92CDFE30537AB046399B2BF2A056201EEEDBB2BD62A0EB28B3C76CDDF37A0030`.
One installable JAR with the VM inside. No production/client JAR changes,
restart, GitHub push or release. The new workflow pin is a local commit: publish
the fork commit before pushing/running remote ACO CI. Local gates use the fork
checkout directly and have passed; remote CI is not claimed.

### Reading the next order

Slow completion retains the existing 5-second threshold / global 30-second
log limit and appends timing to the SAME log line. `/aco vm recent 1` shows the
retained order timing even if its slow line was rate-limited. Fast routine orders
remain quiet. Recent history stays at 64 entries with no world references.

- `stockMs`, `prepareMs`, `vmMs`, `validateMs`, `finalizeMs`: phase wall times,
  including synchronous handoffs in those phases; retries accumulate.
- `serverQueueMs`, `serverWorkMs`: cross-thread handoff queue wait and action
  wall time; nested direct calls are not counted twice.
- `handoffBlockedMs`: total worker wall time awaiting handoffs, including action
  and wake-up delay. It overlaps phase times: do not add it to `vmMs`/`elapsedMs`.
- `vmActiveMs`: VM phase wall time minus handoff blocking. NOT CPU time; OS
  scheduling and GC can still be included. Do not call all of it arithmetic.
- `stateCopies`, `branchTrials`, `branchEntries`: scratch-state copy calls,
  branch attempts and touched trial entries (including discarded trials).

`elapsedMs` keeps its prior meaning (worker start through plan result), excluding
worker executor queue delay. Recapture sleep and result bookkeeping can account
for gaps between phase sums and elapsed time. Exceptional/cancelled timing is
available in recent history up to the moment recorded, not a successful plan.
Next acceptance remains the actual circuit x1 at matching stock/load, followed
by the full quantity/stock matrix. No under-10-second claim is supported yet.
