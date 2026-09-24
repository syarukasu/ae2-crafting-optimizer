# Issue #208: VM integration rebuilt on rc.4

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/208
- Status: Implemented
- Baseline: v2.0.0-rc.4, 9973a8e (Forge 1.20.1)
- Local candidate: 2.0.0-rc.6-rc4-vm.5; VM compiler/interpreter migration; not deployed
- VM source pin: f821f3e9b5e1e9c0a000c2a02835140a285fba97 (syarukasu/AE2-VM-ACO)

## Current Scope: VM Engine And Exact API Bridge

### Current Implementation (supersedes the foundation staging below)

The shipped request route now invokes the fork's PatternCompiler, exact
CraftingBytecode and CraftingVM/VmInstructionExecution. The copied rc.4
ExactBranchVM and its evaluator-specific tests are removed; the unchanged
actual-AE2 oracle cases now exercise this VM route instead. The original
unconditional one-craft aggregation failed 19 oracle cases after the arithmetic
port, so it was replaced inside the fork with slot-aware bytecode and guarded
ordered block replay. This is a substantive VM semantic change, not merely a
BigInteger type substitution and not a claim of unchanged upstream algorithms.

Replay guards cover failed trials as well as successful extractions, retain
whole-template units, delayed containers and peak reservation, and support
periodic damaged-tool states. Scratch quantities and rational byte costs stay
BigInteger. Read-only initial stock is shared; trials copy touched-key deltas,
and fuzzy indices use family-level copy-on-write rather than copying the entire
network. Pattern compilation is request-scoped. No stock-dependent plan is
shared across orders. Shared-DAG worst-case latency still needs measurement.

Capture/validation use bounded server-thread batches. Changed pattern/recipe
epochs retry with cancellable bounded backoff rather than failing the third
attempt. Storage changes alone do not invalidate the snapshot. Final adoption
still validates provider bindings; normal reservation and physical execution
remain AE2/add-on owned. ACO does not capture patterns for this route.

Local automated evidence (including the sparse-stock follow-up):
- 654 unit tests, 648 passed, 6 existing optional skips, zero failures.
- Actual AE2 oracle checks include 500 seeded shared DAGs, alternative ordering,
  fluid/bucket units, NBT identity, byproducts, container returns and byte cost.
- Closed-form tests execute orders through 10^1024; additional 10^64 tests cover
  alternate producers, byproduct reuse, reusable seeds and changing tool damage.
- Eight real AE2 GameTests pass on upstream 15.4.10 and UELM 15.5.0, including completion, cancellation, competing
  reservations, stock changes and an exact wide missing plan.
- Jar-in-Jar class/license/byte audit passes and forbids ExactBranchVM. Sources
  for the native compiler/interpreter and adapter are packaged separately.
- Evidence: build-vm-period-gate.log, build-vm5-acceptance.log,
  build-vm5-uelm.log and build-vm5-final.log. Final upstream test/build/GameTest/
  release-readiness tasks pass after removing redundant post-VM emitter rewriting.
  CI pins the exact committed fork revision above and must pass before publication.

Unverified: production Jar-in-Jar load, the live supreme/creative circuit
latency and server load, arbitrary external CPU wide execution/recovery, and
the 10-second end target. No production JAR was replaced or server restarted.

### 2026-09-24: Historical Migration Staging (superseded above)

The user explicitly rejects the rc.4-derived ExactBranchVM, not just its log
output. The target is the upstream CraftingVM's compiled-pattern/bundle demand
aggregation with BigInteger quantities throughout, and the existing exact
inventory/result bridge. Do not rename/copy the old recursive planner and
claim this implements the upstream VM. The source baseline is upstream
1.20.1-forge f9e083732ee654a99a1679bcdee7f864eb90be94, already in the fork.

Production evidence from 2026-09-24: supreme circuit x1, orders 9567/9737,
102649/135769 ms. Thread captures show sequential server round trips during
capture and validation, and repeated fuzzy-index allocation. Diagnostics
are in build/diagnostics-vm-20260924. Logging changes below remain intact.

Implementation stages and owners:
- The upstream core owns exact counters, its detached simulation and an exact
  result value. These replace AE2's long-only simulation/plan types inside the
  VM, not at its inventory or physical execution boundary. Mechanical numeric
  migration must retain the upstream instruction, bundle and aggregation flow;
  verify the changed expressions and compare ordinary and wide quantities.
- Port the upstream CraftingBytecode/PatternCompiler quantity boundaries,
  retaining its opcodes and direct pattern references; add a BigInteger literal
  pool and exact request metadata. Keep old long entry points checked, never
  silently projecting an unrepresentable quantity.
- Port CraftingVM inventory, aggregation, counts and result accounting. Preserve
  upstream shared-demand aggregation, variant/return semantics and caches;
  fix incorrect accounting rather than copying unsafe saturating operations.
- Supply worker-safe captured VM inputs, not ACO graphs. Remove fine-grained
  server waits through bounded server-owned acquisition and validation batches.
- Wire the original compiled VM into NativeVm and remove the rc.4-derived
  planner from the installable JAR after the result-equivalence gates pass.
- Preserve ACO exact-stock/API/CPU integration and receipt ownership. Do not
  change physical CPU execution, restart production or release a partial port.

Tests before cutover: actual upstream compiler with long and BigInteger orders,
coefficients greater than long, shared DAGs, byproducts, containers, fuzzy/NBT,
missing/partial/full stock; same result through ACO's exact API. Keep the actual
AE2 oracle and processing GameTests; do not remove incompatible test cases.
The old route remains unmodified until the new engine passes those gates.
Uncommitted legacy CraftingVM.java edits predate this stage and must be kept.

Local verification for the migration foundation:
- `test verifyExactVmBundle verifyIssueRegressionManifest` succeeds. 650 unit
  tests, 644 passed, 6 existing skips, no failures/errors. Log:
  `build-upstream-migration-foundation.log`.
- Six compiler tests exercise exact metadata/literals for 1, 2, Long.MAX_VALUE,
  MAX+1 and 10^1024, exact division, coefficient products and encoded indices.
  These prove bytecode construction, NOT execution of those huge orders.
- Four tests execute the actual upstream CraftingVM with AE2 simulation state:
  empty/partial/full leaves, shared dependency batch rounding, intermediate
  stock increase/decrease and no direct calls to a mocked IGrid.
- `upstreamVmCompiler` is a test-only source set. The current installable bundle
  still contains ExactBranchVM; the passing bundle audit verifies that existing
  packaging, not replacement by the original upstream engine.
- Remaining: convert the core's long counters/simulation/results, port the
  existing exact result bridge, batch capture/validation, verify industrial
  variants/cycles/byproducts and actual CPU handoff, then remove the old engine.
  No production deployment, restart, prerelease or in-game performance claim.
- The fork changes are not yet committed/pinned for CI. Do not publish the ACO
  tests alone against the old fork pin, which lacks the new compiler API.

### 2026-09-24: Bounded VM Diagnostics

The current production rc.6-rc4-vm.4 logs started, quantities_ready and
quantity_calculated at INFO for every order. In the latest 5,000 server log
lines, 4,899 were those events (1,633 of each), mostly bio_fuel x120 orders.
DEBUG alone is not sufficient: Forge also writes debug.log by default.

This follow-up changes diagnostics only, independently of the requested
replacement of the rc.4-derived calculation engine (still pending). VM owns
these diagnostics; no ACO planner, arithmetic, snapshot, execution, or receipt
contract changes are authorized by this logging fix.

- Routine events are silent by default, including DEBUG. Explicitly opt in
  with -Dae2vm.diagnostics.verbose=true for per-order DEBUG traces.
- Aggregate started/completed/missing/cancelled/failed/recaptured/slow counts
  and completion mean/max milliseconds at INFO at most once per 60 seconds,
  on activity. No timer thread or world/provider access for diagnostics.
- Orders lasting at least 5 seconds are slow. Globally limit running reports
  and slow completion reports to one each per 30 seconds, not per order.
- Keep failure exceptions and stack traces at ERROR without masking failure
  propagation. Do not suppress accounting errors as routine cancellations.
- Constant-size synchronized counters only; no retained keys, jobs or worlds.
- Regression tests: thousands of routine events generate no per-order logs;
  one interval produces one accurate summary; concurrent callers cannot
  bypass the global rates; slow events remain visible; errors retain causes;
  verbose traces require explicit opt-in; cancellation/recapture are counted.
- Preserve the fork's pre-existing CraftingVM.java edits. Do not deploy or
  restart production. Build/unit evidence is not live-server verification.

Implementation owners: fork native-engine NativeVm (event call sites), new
NativeVmDiagnostics (bounded output only), ACO test source set (unit harness).
Existing Issue #208 regression registration remains the release tracking row.

Verification: NativeVmDiagnosticsTest (8 tests) and NativeVmCaptureTest
(2 tests) pass. The full upstream AE2 unit suite passes: 640 tests, 634 passed,
6 skipped, 0 failures/errors. verifyExactVmBundle and
verifyIssueRegressionManifest pass. Evidence: build-vm-diagnostics-test.log,
build-vm-diagnostics-verify.log and build/test-results/test/TEST-*.xml.
There was no production restart/deployment, new release, or live check of the
changed logger. The rc.4-derived calculation engine has NOT been replaced by
this diagnostics patch. Existing CraftingVM.java working-copy edits remain
untouched. Local tests use the modified native module; a future release must
commit it and advance the CI fork pin, not ship the old pinned module.

The user clarified that ACO remains a crafting optimization mod using VM as
its engine. Implement the bridge, not a replacement industrial executor.
Native VM owns acquisition and calculation. ACO exposes the finished exact
counts, preserves public API compatibility and retains existing execution,
cancellation and recovery ownership. Remove the redundant ACO graph-to-VM
adapter and the physical-preparation requirement from calculation completion.

Return a distinct calculated wide result, including stocked processing plans,
without narrowing or inventing missing material. Publish its original pattern
bindings and quantities through the existing BigInteger API. Existing physical
consumers prepare their supported execution contracts only at submission.
Unprepared wide results MUST NOT fall through to vanilla long execution.
This scope does not assert that an unchanged external CPU accepts every wide
industrial plan: consumer support is distinct from successful API transfer.

Regression gates: production VM capture -> bridge -> public API with 1, 2,
Long.MAX_VALUE and larger requests, wide coefficients, missing/partial/full
stock, unchanged result quantities and no ACO pattern inspection during bridge
materialization. Preserve old receipt execution and submission backstops.
Run ordinary processing GameTests, UELM and bundle checks. Do not claim a full
wide-machine lifecycle based on bridge tests. Prerelease notes must state the
remaining consumer limitations, and no production deployment is part of this
cleanup request.

## Cleanup Implementation And Evidence

- Reproduced CountOverflowException in VmBigIntegerAccounting's second pattern
  capture: Long.MAX_VALUE * Long.MAX_VALUE, despite successful VM arithmetic.
  Evidence: build-vm-bridge-before.log and the NativeVmCaptureTest failure XML.
- Removed ExactVmPlanning and its CompiledRootProgram hooks. Kept the legacy
  public root-program tests as LegacyRootStockMatrixTest; physical/API callers
  still use that contract, so their planner was not deleted indiscriminately.
- VmBigIntegerAccounting now exposes immutable VmCalculatedCraftingPlan data.
  Public API inspection and the exact confirmation summary accept that type.
  No pattern methods are called while bridging, and no graph is sent to VM.
- VmPhysicalPlanBinding prepares the existing receipt contract only when that
  executor is selected. Prepared aliases share the same submission claim.
  Unprepared VM results are guarded from vanilla long execution, including
  direct CraftingCpuLogic callers. Existing physical limitations are retained.
- The native VM capture -> real bridge -> public API matrix passes for requests
  1, 2, Long.MAX_VALUE, Long.MAX_VALUE+1, 10^64 and empty/partial/full stock,
  with an input coefficient of Long.MAX_VALUE squared. No false missing flag
  is substituted for an unsupported physical execution domain.
- Upstream AE2: 632 unit cases, 626 passed / 6 skipped / 0 failures; all 8
  focused GameTests passed, including a stocked wide processing-pattern result
  and exact confirmation summary. This fixture does not run a wide machine;
  the separate ordinary processing test verifies real inscriber completion.
- VM source pinned to syarukasu/AE2-VM-ACO commit
  babfca55a9b4364a623536b771e1345fcf664f72. CI checks out that commit and ships
  only the verified bundle, never the thin build intermediates.
- UELM 15.5.0-uelm: the same 632 unit cases (626 passed, 6 skipped) and all
  8 focused GameTests passed. No new full-suite baseline claim is made here.
- The published mc/1.20.1 branch is ahead of rc.4 by #203/#211. This recovery
  deliberately retains the tested rc.4-based tree rather than merging back
  LinearWidePlanning, the legacy ACO repeat planner and the standalone vmProbe
  path. This follows the explicit rc.4-baseline instruction. Existing rc.5
  commits remain in Git history; no branch is force-pushed.

## Previous Result: 2026-09-23 Native Entry (Superseded)

The native-engine module in the VM fork now owns CraftingService's calculation
entry, acquisition, input observations, bytecode evaluation, and ordinary
CraftingPlan materialization. VmBigIntegerAccounting supplies exact stock and
consumes completed quantities; it never sends an ACO graph into the VM.
The arithmetic library AND the native mod/hook are embedded as separate JARs.

Verification of the current candidate:

- 633 JUnit cases: 627 passed, 6 skipped, 0 failed. The actual AE2 oracle now
  also checks production NativeVmCapture plus ExactBranchVM, including shared
  DAGs, byproducts, fluid quanta, NBT, substitute inputs and returned containers.
- Upstream AE2 15.4.10: all 7 focused GameTests passed. These include actual
  processing completion, cancellation, competing reservations, stock churn,
  depletion before submission, returned containers, and a 10^64 missing order.
- UELM 15.5.0-uelm: all 7 focused tests passed.
- UELM full suite after the dispatch fix: 59 passed, 1 failed. The same cauldron
  failure occurs WITHOUT ACO/VM (baseline: 52 passed, 1 failed); do not call the
  full suite green. XML/log evidence is retained under build/uelm-*.xml/log.
- The old ACO ordinary execution wave override caused the provider face
  round-robin regression. Excluding this hook in native-VM mode fixes that
  test; exact transaction, receipt, return and submission hooks remain.
- Upstream AE2 plus the installed Thunderbolt 2.0.0-beta.2 JAR: all 7 focused
  GameTests passed, with actual route=vm-native completion logs.
- Both nested JARs match their built bytes; manifest, native bootstrap, classes
  and licenses passed verifyExactVmBundle.
- Public BigIntegerCraftingPlanView now retains exactRequestedAmount. Its old
  constructor remains available. The rc.4 GUI/protocol is NOT a new BigInteger
  order-entry GUI.

Release remains BLOCKED: VmBigIntegerAccounting.wideResult still uses the
existing SelectedBranchPhysicalPlan contract for craftable wide results. That
contract only accepts fixed crafting-table DAGs, and rejects processing,
dynamic inputs and interleaved cycles. Consequently a native quantity result
can be correct but the Future can still fail at the physical handoff. Do not
publish/deploy this as a completed general industrial migration, do not fake
missing materials or outputs, and do not relax input-custody validation to make
this gate pass. Complete the calculation/result and physical handoff boundary
and verify a stocked wide processing order before release.

No GitHub publication, deployment, production stop/start, or client launch was
performed. Production remains stopped and retains the prior rc.6 JAR with hash
743A987D5620E1F31F01D93D6B5D55900910E0D0AC3F1C63DA64B45EFEE7061C.
Current unshipped candidate hash:
17A08E0154164A67EEDD13F46F7D98B83BC23D27D701834786CE1DB05063944E.

The sections below retain historical decisions/evidence; superseded adapter
and thin-JAR descriptions are NOT the current implementation or current
production startup diagnosis.

## User direction and scope

2026-09-23 explicit release decision: finish and verify native VM migration
BEFORE prerelease/deployment/startup. A hotfix-only release is not authorized.

Native implementation: the fork's native-engine owns the CraftingService HEAD
hook, server-owned pattern/stock acquisition, input observations, exact bytecode
execution on a cancellable worker, and normal AE2 plan materialization. ACO
does not pass CompiledRootProgram/captured pattern graphs into this entry.
The old ACO normal-calculation interception/dedup must be disabled when this
bundled native engine is active. ACO registers only a BigInteger stock and wide
result accounting extension. AE2/add-on execution remains unchanged.

Each job owns its captures; stock changes alone do not invalidate detached math.
VM-owned pattern/recipe epochs and actual used bindings/observations are checked
before returning the plan. Real input API calls run on the server, never from
the worker directly. Cancellation propagates without fallback; structural
changes fail explicitly rather than silently using mixed pattern semantics.
Normal quantities return native CraftingPlan. Wide quantities are handed to
existing ACO exact result/physical contracts with no count narrowing. Unknown
wide physical domains must remain explicit limitations, not synthesized output.
The native entry is tested with stock churn, depleted stock at submission,
parallel requests, missing inputs and actual machine completion. Packaging must
contain the native hook as well as the arithmetic library; merely adding the
library is insufficient. Historical private APIs remain for compatibility.

### Ordered VM follow-up

SUPERSEDED by explicit user correction: ACO must NOT capture/translate patterns
for VM. The VM fork owns the native beginCraftingCalculation entry, pattern
lookup, compilation and evaluation. ACO owns only exact-count inventory/result
and existing physical accounting integration. Remove the just-added production
ExactVmBranchPlanning adapter; do not ship its intermediate build. The detached
VM branch interpreter/tests remain experimental fork work, not evidence of a
completed native integration. Audit the upstream entry and saturation sites
before marking the replacement native integration Ready.

Latest production evidence (06:39 startup, 2026-09-23) is a DIFFERENT artifact:
aco2.0.0-rc.6_1.20.1.jar, not rc4-vm.1/.2. debug.log:121685-121696 records
supreme circuit x1, 121735-121741 glass x4000, 121798-121804 tungsten carbide
plate x1. Each capture is accepted, followed by three GENERATION_CHANGED
declines and StalePlanningSnapshotException from inputObservationOnServer /
Capture.requireCurrentGenerations. latest.log:6382,6401,6424 records failed
orders. At 06:45:20 stats show started=2, aborted=2, planComplete=0 and six
generation declines (the third order starts afterwards). Thunderbolt selected
VANILLA; these logs do not prove any VM evaluation. Pattern/recipe values in
the decline lines remain 1550/1, but the exception does not expose the current
revision that failed, so do not claim a precise changed component from logs
alone. rc.6 source also checks StorageRevisionTracker in requireCurrentGenerations.
Server saved/stopped at 06:45:38-47 without any action by this task.

Installed rc.6 SHA-256 is 743A987D5620E1F31F01D93D6B5D55900910E0D0AC3F1C63DA64B45EFEE7061C.
It DOES embed aco-exact-vm-0.1.1.jar and Jar-in-Jar metadata; the earlier
thin-JAR finding must not be reused for this startup.

Test-harness correction status: Ready. BranchingInputSemanticsTest's native
processing-pattern fixture implicitly depended on a different test registering
AEKey types. Initialize the existing TestKeyTypes helper explicitly so both
isolated and full-suite runs exercise the same fixture. No production behavior
change or claim of native-VM completion follows from this correction.

The user requires the actual industrial order to use VM calculation, not just
to remove the old ACO work cutoff. Add a detached ordered bytecode interpreter
to the VM fork. Compile captured pattern inputs, remainders, all outputs and
execution charges into instructions. Preserve AE2 candidate order, failed-trial
rollback, fuzzy input observations, catalyst reuse and prefix inventory peaks.
All quantities and instruction coefficients in the VM are BigInteger. ACO
widens AE2's existing long pattern coefficients exactly at the adapter boundary.
Use proven read intervals to skip repetitions even for small nested branches;
do not infer repeatability from matching output alone. Keep the old evaluator
as a regression oracle, not the production route for these orders.

Owners: ExactVmBranchPlanning translates captured ACO values; the fork's
ExactBranchBytecode and ExactBranchVM own compilation/quantity evaluation.
AE2/ACO still own capture, observation scheduling, materialization, bytes
validation, execution selection and receipts. Unknown observations fail with
their actual reason, never fabricated materials or clamped counts. Production
VM logs must be emitted after interpreter completion, not merely selection.

Acceptance: compare the new interpreter against real AE2 and the rc.4 oracle
for alternatives, rollback, NBT, fluids, returns, cycles, craft-less and
byproducts. Test requests 1, 2, MAX, MAX+1, 10^64 with empty/partial/full stock;
test VM coefficients above long separately. Assert bounded work for the old
nested 1024x1024 regression, exact stock accounting, and cancellation. Audit
the embedded tested library. Do not deploy or stop production. UI/protocol and
unsupported wide physical execution remain separate, explicitly reported gates.

2026-09-23: both live instances contain the thin intermediate JAR, SHA-256
090DA10744F99FBDE7A5D6C7C945A8D934339D9927C672012C0D34135B29C0F4,
with no Jar-in-Jar metadata or VM library. The previously linked distribution
(B110F99C...) includes both, but build/libs also exposed a similarly named thin
JAR. Keep intermediate JARs outside the distribution directory. Produce one
unambiguously named JAR under build/distributions and validate that exact file.
Use declared Gradle output paths, not moving a task output over another JAR.
Preserve old artifacts and never replace running production JARs.

The observed sand/glass/nether-star calculations use ordered-branching.
The failed supreme circuit uses that same evaluator. Bundling the library does
not make those recipes VM-eligible. Log the actual selected route and, when
available, the root rejection reason at INFO; do not label the branch path VM.

The user rejected the rc.5-based implementation and explicitly requested rc.4
plus the VM integration. Work in this independent worktree. Preserve the old
dirty rc.5/rc.6 workspace; do not copy its whole build or source tree.

Retain rc.4's public APIs, order UI/protocol, selected-branch execution, receipts,
byproduct/alternative planning, lifecycle and configuration. Import only the
detached VM adapter, compiler dispatch, VM tests and Jar-in-Jar packaging.
Do not import LinearWidePlanning, rc.5 ordered-repeat changes, rc.6 network
protocol 5 or BigInteger GUI. Existing rc.4 exact APIs remain available; the
new wide GUI requires its own later compatibility gate.

VM source: ../../references/AE2-VM-aco-exact/exact-engine (version 0.1.1).
Build only this module, never the upstream mod's deployment tasks. It retains
LGPL license/provenance and produces a source archive as well as the library.
The embedded JAR must match the tested JAR byte-for-byte and contain no second
mod entry point, config or mixins.

## Upstream migration regression: partially replenished stock

The original upstream `CraftingVM.tryFastPath` reused a missing plan whenever
current stock still did not cover the old missing amount. Reproduction with
the actual upstream compiler and VM: one diamond needs 16 iron; first request
has zero iron, second request has one iron. The second plan returned used=0
and missing=16 instead of used=1 and missing=15. This is an isolated Java test,
not evidence of the current deployed native adapter executing this code.
The missing-plan cache must revalidate the exact consumed stock, including
partial replenishment, not merely ask whether the old deficit is covered.
Gate: `UpstreamVmAccountingTest` stock matrix and shared-dependency rounding.

The upstream core also queried `IGrid.getStorageService()` lazily during
aggregation and swallowed any exception as empty stock. The fork must instead
reuse the initial inventory of the supplied simulation. This removes the core's
direct live-grid access; it does not itself prove that the caller supplies a
detached simulation or that every pattern resolver is worker-safe.

The actual upstream execution test also reproduces stale complete-plan reuse:
diamond needs 16 gold; gold needs 2 iron; iron stock is 1000. After first
calculating with zero gold, adding one gold still returns used(gold)=0 rather
than 1. Warm reuse must check all reachable craftable-key stock, including
keys with zero stock at capture time, not just the old plan's used-item keys.

## Owners and invariants

### 2026-09-24 migration acceptance correction (Ready)

Connecting the actual upstream CraftingVM, rather than ExactBranchVM, passes
wide arithmetic through 10^1024 but fails 19 of 651 regression tests (6 skips).
Failures include scaled fuzzy-stock consumption, fluid/bucket template units,
returned-container timing, byproduct reuse, alternate-producer rollback,
emitter pruning, CRAFT_LESS and byte accounting. No production JAR was changed.
The original one-craft bundle aggregation is not valid for state-dependent
inputs. Merely changing its numeric types does not establish AE2 correctness.

Fix these semantics inside the fork's compiler/VM instruction execution, not
by copying or invoking the rc.4 planner. Input instructions must preserve their
slot, template unit, accepted keys and delayed returns. Ordered execution must
consume prior byproducts and roll back failed producer trials. Bundle replay
is permitted only with guards proving the same inventory decisions remain
valid; the request, recipe coefficients, temporary quantities and byte charges
remain exact. Read live pattern observations only through the VM capture's
server-thread boundary. ACO remains a result/accounting adapter. The unchanged
AE2 oracle suite is the gate; do not relax comparisons or publish a failing
candidate. Runtime submission/completion remains a separate gate.

ExactVmPlanning translates immutable CompiledRootProgram data. The VM evaluates
eligible fixed single-output, single-candidate non-shared acyclic graphs using
BigInteger. rc.4 owns all other recipe semantics, capture, binding, capacity,
physical execution and custody. Never approximate exact quantities, move live
world/storage access off-thread, generate outputs from a plan, or fall back
after custody transfer. A VM limit error must not silently truncate a count.

This is not universal industrial VM coverage. Recipe coefficients crossing
AE2's long API, exact order UI, full-size BigInteger physical completion and
actual modpack latency are separate acceptance gates, not established here.

## Tests before acceptance

Run the rc.4 regression suite with the real VM library, including quantity and
stock matrices (1, 2, MAX, MAX+1, 10^64; empty/partial/sufficient), cancellation,
rounding and existing byproduct/shared-ingredient/branch oracle tests.
Bring over test-only real AE2 plots separately from production rc.5 changes.
Named broadcastChanges aliases may be added to the two existing SRG-only menu
injections for Forge userdev tests; production descriptors and behavior stay
unchanged. Audit all changed production paths against the baseline.

The separately reproduced storage-churn fault is tracked under #190. It is
present in rc.4 as well; do not present it as proven introduced by rc.5.
Deployment/restart and actual GUI/world acceptance are not implied by a build.

## Local verification 2026-09-22

`test build runGameTestServer verifyIssueRegressionManifest` exited 0.
627 unit tests: 621 passed, 6 existing skips, zero failures/errors.
The standalone VM ran 7 passing tests. Six real AE2 GameTests passed, including
stock churn completion, exhausted-stock submission rejection, ordinary machine
completion, cancellation/refund, competing reservations and returned containers.
Jar-in-Jar verification matched the exact tested VM bytes and checked licenses,
manifest and absence of an upstream mod bootstrap. These runtime tests use the
development classpath; production Jar-in-Jar loading still needs a pack restart.

User explicitly requested JAR preparation only. No live mod directory was
changed and neither production process was stopped. The rc.4 protocol is 4;
the previous experimental rc.6 uses 5, so both sides need matching replacement
JARs at a later coordinated restart. No deployment, release or push occurred.

## 2026-09-23 vm.2 verification

The sole new installable file is under build/distributions. Thin JARs go to
build/intermediates/jars with NOT-FOR-INSTALLATION in the classifier. Jar-in-Jar
uses a declared filename, reobfuscated in place, without moving it over another
task's output. Old vm.1 artifacts are retained only as historical files.
Bundle-byte/license/manifest checks pass. The GameTest gate now requires a
quantity_calculated route=vm-fixed INFO event, observed for real AE2 certus
dust orders. This proves evaluation on the development classpath, not live
Jar-in-Jar discovery or supreme-circuit VM eligibility. No live JAR was changed.
