# Issue #208: VM integration rebuilt on rc.4

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/208
- Status: Implemented
- Baseline: v2.0.0-rc.4, 9973a8e (Forge 1.20.1)
- Local candidate: 2.0.0-rc.6-rc4-vm.4; awaiting prerelease checks, not deployed

## Current Scope: VM Engine And Exact API Bridge

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

## Owners and invariants

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
