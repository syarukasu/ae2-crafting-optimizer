# Issue #190: Real industrial recipe acceptance

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/190
- Status: Implemented locally for selected fixed-input acyclic crafting-table branches; runtime acceptance PENDING
- Target: 2.0.0 prerelease; Forge 1.20.1 modpack capture, shared planner verification on both loaders
- Related: #156, #179, #185, #167, #176, #182

## Problem and evidence

### 2026-09-21 real AE2 runtime baseline (Implemented; full acceptance pending)

The requested acceptance scope is industrial planning, wide physical execution,
accounting verification and runtime cost reduction. The sub-ten-second full-pack
benchmark remains a final goal, not a replacement for these correctness gates.

Source inspection confirms that selected external processing patterns and
interleaved/dynamic physical branches are still rejected before custody by
SelectedBranchPhysicalPlan and ExactPatternFormula. Do not remove these guards:
AAC's existing receipt-backed target executes molecular-assembler formulas, not
arbitrary industrial machine recipes. Pure planner and boundary-double tests do
not prove real-machine completion.

First add an opt-in Forge GameTest server run using AE2's shipped test plots.
Its world and logs live only under build/gametest-ae2; no production server,
client, pack recipes, saved inventories or deployed JARs are modified. Enable
AE2's own appeng.tests registration and report actual test failures. This is a
Forge runtime harness, not shared production logic; NeoForge needs its own run
configuration before equivalent runtime coverage can be claimed.

Ownership stays unchanged: AE2 owns real storage, machines and normal CPU jobs;
ACO retains its existing planner/receipt responsibilities. No new execution API,
count clamping, output synthesis or post-custody fallback is allowed here.
Completion of the baseline means a real GameTest process exits successfully and
its report lists executed tests, not merely a successful Gradle compilation.
BigInteger AAC/AQE execution, restart/cancel/concurrent reservations and live
performance remain pending until individually measured with those actual mods.

Pre-implementation: charter, regression history, class ownership, issue workflow,
testing matrix and AE2 GameTestPlotAdapter/AppEngBase registration were read.
The existing matrix row #190 remains RUNTIME/PENDING until the full gate passes.

First runtime attempt: Forge exits normally after mod loading fails because
AE2's GuideME dependency is absent, and Gradle reports BUILD SUCCESSFUL. Add
AE2's published GuideME 20.1.7 runtime dependency, explicitly load ACO's Mixin
configuration in userdev, and require a non-empty completed runtime test report.
An exit code alone must not mark acceptance as passed.

Add a separate gameTest source set and test-only mod (never included in the
distribution JAR). It registers real finite-stock processing completion,
cancellation before machine delivery, and two simultaneous plans competing for
the same finite inputs. Use AE2's real inscriber, pattern provider, cell and CPU,
not a fabricated worker receipt. The test mod installs Minecraft's JUnit reporter;
the Gradle task rejects missing, empty, failed, skipped or stale reports. Require
an ACO-injected interface on the real CPU so an accidental no-Mixin run fails.
These long-quantity tests establish a baseline only, not wide industrial execution.

Runtime evidence: 56 actual AE2 GameTests ran. All three new ACO plots passed;
the existing import_from_cauldron test failed (lava cauldron not drained).
Do not attribute that failure to ACO without a comparison run. Two ACO menu
Mixins logged unapplied injections because their selectors name only production
SRG m_38946_, not userdev broadcastChanges. Add the verified userdev alias while
retaining the production selector and require=1; do not weaken injection guards.
Owners are MEStorageMenuDisplaySaturationMixin and CraftConfirmMenuLongAmountMixin;
no inventory or GUI behavior changes. First require both selectors in a failing
boundary test, then rerun the real server without unapplied-Mixin warnings.
NeoForge already uses its runtime named namespace; this alias is Forge-specific.

The runtime harness will expose explicit `aco` (focused acceptance, default) and
`all` (all upstream AE2 plots plus ACO acceptance) scopes; it must not silently
exclude a failing upstream test from the all scope. The reporter records expected
test names, and the verifier requires exactly that set with no skipped/failed
tests, plus the ACO acceptance tests. Add real molecular-assembler cake crafting
to verify returned buckets and finite ingredients. Maintain the all-scope failure
and the optimizer-disabled comparison as separate evidence, not a passing gate.

Results: focused real GameTests passed on both upstream AE2 15.4.10 and UELM
15.5.0. Actual inscriber completion, pre-delivery cancellation/refund, competing
finite-stock submissions and molecular-assembler returned buckets all passed.
The two menu Mixins now apply in userdev; the new source-boundary checks failed
before the alias fix (2/5) and are part of the regression suite afterward.

The first cake fixture initialized ingredients through a structure callback that
replayed: with ACO disabled it left eight milk buckets, two cakes and six empty
buckets, consistent with two initial deposits of seven and six consumed. Move
initialization to a once-only test sequence and assert empty initial ingredients;
do not change recipe/accounting code to compensate for a faulty fixture.

The all-AE2 suite still has import_from_cauldron failing with ACO enabled and
disabled. An unlinked simulation requester also exposes a planner behavior
difference; the valid concurrency fixture now uses AE2's grid-linked MachineSource.
See docs/testing/ISSUE190_GAME_TESTS.md for exact scope and remaining work.
No production deployment, server restart, release or full #190 completion claim.

### 2026-09-19 AQE CPU capacity alignment (Implemented locally)

The user's target is AQE's actual CPU capacity, not a fixed 10^64 quantity.
Read-only inspection found the installed server setting big_integer_storage_digits
is 1024; AQE defines per-core storage as 10^digits - 1 and the shared effective
structure limit as 10^16384 - 1. ACO already shares that magnitude limit and AQE
compares exact required bytes with exact available bytes before submission.
Do not add a second capacity owner or reinterpret logical CPU bytes as Java RAM.

BigExactCraftingByteCounter currently bounds amount * 8 and rational numerators
before unit division. This can reject an item/fluid/chemical charge whose final
byte cost fits the CPU. First reproduce near-boundary and mixed-denominator
cases. Keep integer bytes and the reduced fractional remainder separately,
round up once at the end, and retain magnitude limits on real counts/bytes.
Fraction arithmetic must remain bounded; no long/double conversion or truncated
count, and no changes to AQE-owned capacity, recipes or physical IO ownership.

Verify default 1024-digit and maximum 16384-digit capacity profiles, exact fit,
one-byte overflow, existing reservations, capacity shrink/reload, and physical
branch save/complete/cancel at AQE-scale quantities. Update the fixture's 4096-bit
test parameter to the actual configured ACO magnitude bound. An optional AQE
JAR bytecode check must verify the configured profiles against the supplied JAR.
Use both loaders; do not deploy, restart the server or claim live performance.

Pre-fix evidence: two new tests failed. An exact 1024-digit CPU fit with a
3402-bit magnitude budget was rejected at bytes/stack/item; mixed fractional
charges were rejected at bytes/fraction-left despite an in-range final cost.
The XML is retained in ../artifacts/2026-09-19-aqe-capacity/forge-before-fix.xml .

Implementation: BigExactCraftingByteCounter now separates whole bytes from a
reduced fractional remainder. Divide by the key's unit before scaling the whole
part, bound actual counts and final bytes, and round the combined fraction once.
Fraction temporaries are bounded by the configured bit limit plus 64 bits.
AQE remains the owner of physical/available capacity and admission.

Automated evidence: BigExactCraftingByteCounterTest covers exact boundary fits,
real magnitude overflow and 40 deterministic mixed-unit rational oracle cases.
AqeCpuCapacityContractTest checks available capacity with an existing reservation,
one-byte excess rejection without mutation, save/load and structure shrink.
PhysicalCraftingLifecycleTest exercises completed and both cancellation outcomes
with save/load after every tick at 1024/16384-digit profiles (quantity / 64 for
intermediate headroom). These are production accounting/codec tests with boundary
doubles, not an in-game AQE/AAC completion or a performance claim. The supplied
Forge AQE 2.2.7 JAR verifies capacity constants only, without loading its classes.
Full build evidence belongs in ../artifacts/2026-09-19-aqe-capacity/VERIFICATION.md.

Final local verification: Forge upstream AE2 and UELM each ran 623 tests, with
621 passed and two optional Neo ECO JAR contract tests skipped. NeoForge ran
631 tests, all passed. All three test/build/verifyIssueRegressionManifest runs
succeeded. The same Forge AQE JAR supplied the constant contract in all runs;
this does not establish NeoForge AQE runtime compatibility. Local artifacts and
XML reports are retained in the evidence directory. No deployment or release.

### 2026-09-19 physical lifecycle verification (Implemented locally)

Add an isolated contract integration fixture that calls the production physical
transaction through validation, exact boundary reservation, worker acceptance,
output receipt, acknowledgement and final return. Exercise long and wide counts,
save/load between transitions, cancellation and temporarily unloaded workers.
Only the world, worker and storage boundaries are doubles; the production
scheduler, formula, escrow, receipt validation and NBT codec run unchanged.
This does not prove an AAC world execution or processing-machine support.

Fault cases must reject mismatched transaction IDs/digests and unexpected output
counts rather than crediting another job's receipt. Any demonstrated defect is
fixed in the existing physical transaction owner on both loaders. No new runtime
executor, production deployment, restart or release belongs to this increment.
Test-only Mockito dependency is used for Minecraft boundary doubles.

Pre-fix evidence: the Forge contract test ran four cases, three passed and
foreignReceiptIsRejectedBeforeOutputCreditOrWorkerRelease failed: EXECUTING_RECIPES
was returned instead of QUARANTINED for a receipt with a different payload digest.
The production owner passes identity to the worker but never checks the identity
returned by that worker. Check every received snapshot before accounting or
acknowledgement. After output credit, also reject a changed output vector or
conflicting terminal state before releasing the worker receipt, including cancellation
and the second snapshot read after a retried acknowledgement.
This is an injected faulty-worker test, not evidence of this fault in the live
server or an accusation that AAC returns mismatched receipts.

Recovery review: AAC's worker acknowledgement writes its terminal receipt via
setChanged separately from the parent transaction. A same-identity RUNNING
snapshot after parent credit is therefore not evidence that the credited output
belongs to a different job. Preserve the existing wait-and-retry recovery
contract: retain credited output, do not re-credit, cancel or forget that running
worker, and require matching terminal output before release. Cover normal and
cancelled parents with a worker-save-lag contract test. This is a persistence
ordering scenario, not a reproduced live-server crash.

Implemented in PhysicalCraftingTreeTransaction on both loaders: every worker
snapshot is bound to its transaction ID and payload digest. Once credited, its
terminal state and complete output vector must agree with the saved receipt.
Both normal execution and cancellation recheck the response after retrying an
acknowledgement, before forgetting the worker receipt. No API, NBT schema or
planning algorithm was replaced.

Verification:

- Pre-fix expanded fixture: six tests, three failed on foreign identity before
  credit, foreign identity after credit, and changed outputs during cancellation.
  Preserved XML: ../artifacts/2026-09-19-receipt-validation/forge-before-fix.xml .
- PhysicalCraftingLifecycleTest: nine tests passed on all three configurations.
  Quantities 1, 100, Long.MAX_VALUE, Long.MAX_VALUE + 1 and 10^64 retain exact
  branch inputs, shared byproducts and final output. Each quantity uses three
  accepted worker requests and two storage mutations in this fixture.
- Every fixture tick round-trips the production NBT codec. Unloaded workers,
  cancellation before/after output, delayed acknowledgement, foreign receipts
  and changed terminal receipts retain owned inputs/outputs without double credit.
  A lagging worker save waits without forgetting or cancelling credited work;
  subsequent completion and cancellation return exactly the expected inventory.
- Forge upstream and UELM: each 614 tests, 612 passed, two optional Neo ECO
  20.3/20.4 JAR fixtures skipped (JARs not supplied); build and manifest passed.
  NeoForge: 622 passed, zero skipped; build and manifest passed.
- Build JARs, hashes and per-suite XML are in
  ../artifacts/2026-09-19-receipt-validation/VERIFICATION.md .
  These remain local rc.3-version candidates, not published rc.3 artifacts.

The boundaries are contract doubles, not real AAC assembly or ME storage. The
quantity-independent operation counts are not live TPS/latency measurements.
General processing-machine wide execution, dynamic/interleaved physical branches,
real CPU/worker completion and runtime latency remain PENDING. The server was
not stopped or modified; no deployment, commit, push or release was performed.

### 2026-09-18 lightweight execution follow-up (Implemented locally)

Verification of this increment:

- Forge upstream and Forge UELM: 605 tests each, 604 passed, one optional
  Neo ECO 20.3 fixture skipped; clean build and regression manifest passed.
- NeoForge: 613 tests passed, build and regression manifest passed after
  regenerating a corrupt extracted Minecraft build-cache JAR. The source
  server JAR matched its expected SHA-1; the old extracted btn.class failed
  CRC and ASM parsing, while the regenerated 6,148 classes passed both.
- Actual AE2 processing-pattern item/fluid input comparison passed. During
  2,000 fixed-slot observations there were zero server calls; adoption used
  one batch, and a changed input definition was rejected.
- 10,000 reads reused one immutable receipt accounting snapshot. Cancellation
  of 1,024 steps at 10^64 executions each was bounded to seven steps per call
  across 147 save/reload cycles, with no unreceived output credited.
- A missing provider no longer drops other already-polled cancellation steps.
  The fixture exercises the cancellation scheduler without a live world.

Artifacts and XML reports: ../artifacts/2026-09-18-lightweight/ .
These are unreleased local rc.3-version builds, not the published rc.3 artifacts.
No deployment, restart, live crafting completion, commit, push or release was
performed. The overall issue remains open: ordinary processing-machine wide
execution, dynamic/interleaved physical execution, and real-modpack latency
are not completed by this optimization increment.

The user requests completion using existing AE2/AQE ownership, not a second
general machine executor. Implement and test these measured code-level gaps:

- Fixed singleton inputs already proven by the immutable snapshot still use
  per-slot/per-key live server calls in Ae2BranchingInputRules. Use captured
  exact semantics on the worker and revalidate each used pattern before adoption.
  Dynamic substitutions/remainders retain server-side observation.
- Forge's physical transaction rebuilds receipt accounting and saved NBT on
  idle ticks. Backport the existing NeoForge active-step/revision mechanism,
  including cached external-CPU accounting. Keep storage intents, receipts,
  cancellation and crash recovery unchanged.
- Test idle versus changed receipts, cancellation/reload, dynamic-input exclusion,
  exact quantities and both loaders. Measure callback/rebuild counts instead of
  claiming an unmeasured server-wide speedup.

No production restart or new execution protocol is part of this increment.
Industrial processing receipt support and real-modpack latency remain explicit
acceptance gates; passing unit tests alone does not close this issue.

### 2026-09-18 selected wide branch execution (implemented locally)

Update purpose: BIGINT_IMPLEMENTATION only. No long optimization, new speed
algorithm, new addon support, deployment, restart or release in this change.

Observed: tryBranchingPlan explicitly rejects craftable wide quantities.
BigCraftingPhysicalExecution.prepare and Ae2BigCraftingExecutionManager.prepare
then independently rebuild a unique-producer CompiledRootProgram. This loses
the chosen candidate counts and cannot represent an otherwise executable
multi-producer branch. PhysicalCraftingTreeTransaction already validates a
PreparedVectorBatch against real pattern formulas, reserves exact escrow,
requires physical receipts, and owns cancellation/recovery.

Retain selected pattern IDs and exact counts as an immutable physical plan.
For the existing physical domain, build an acyclic dependency order over the
SELECTED fixed-input patterns, prove every complete stage against virtual
escrow, and retain every final surplus. Never run the branching planner again
during submission or restore, and never select another producer there.
Persist the prepared branch alongside the exact job before any physical work;
use the same prepared plan in both standard AE2 and external API consumers.
Preserve public API v1 and old constructors/NBT compatibility; missing branch
metadata must fail closed, not revert to a unique-producer plan.

Owners: SelectedBranchPhysicalPlan validates and assembles existing vector steps;
BigIntegerCraftingPlan carries the optional prepared branch; ExactCraftingJobState
persists it; the two existing consumers rebind job identity and revalidate real
patterns. Physical transaction, worker receipts and storage remain their existing
owners. Storage preflight must cover all final surplus/returned keys, not only
the requested output.

Keep explicit pre-custody rejection for processing machines without an existing
physical receipt route, ambiguous slot substitutions without saved concrete
choices, emitters, and cyclic/interleaved execution not representable by the
existing one-step-per-pattern contract. These remain unsupported, not completed
or silently altered. No removal of a guard before its consumer is connected.

Tests: previously rejected selected multiple-producer branches; shared byproduct
conservation; long boundary and 10^64 exact quantities; no worker-output invention;
wrong count/changed pattern/missing binding rejection; complete selected plan
NBT round trip and cancellation/recovery identity; both consumer entry points.
Build all three configurations and retain existing regression coverage.
Live completion/cancellation/restart remain unverified until a separate runtime
test is authorized and performed. Do not close the broader Issue #190.

Local verification of this increment:

- Forge upstream AE2 15.4.10: clean build and regression manifest passed;
  602 tests, 601 passed, 1 optional Neo ECO 20.3 JAR test skipped.
- Forge UELM 15.5.0-uelm: clean build and regression manifest passed;
  602 tests, 601 passed, the same optional test skipped.
- NeoForge 1.21.1: clean build and regression manifest passed;
  610 tests, 610 passed, no skips.
- SelectedBranchPhysicalPlanTest now sends the real OrderedBranchingPlanner
  result into the physical-plan assembler for 1, Long.MAX_VALUE,
  Long.MAX_VALUE + 1, and 10^64 quantities. It independently replays every
  debit/credit, verifies both chosen producers and every surplus, and exercises
  exact-job NBT, missing/corrupt metadata, changed counts, bounds and rejection.
- BigCraftingPhysicalExecutionTest adds real transaction/receipt serialization
  for multiple selected producers and cancellation intent, without crediting
  an unreceived output. It does NOT tick a real CPU, storage or worker.
- SelectedBranchConsumerContractTest is a source-wiring guard for both entry
  points and surplus preflight; it is NOT runtime completion evidence.

Unreleased build hashes (the existing rc.3 filename/version is unchanged):

- Forge upstream: EC28EFE917BD128FF76C67E2CECB896BAFAD7FC4B3F643F23CD3B313ECE0B891
- Forge UELM: 67A382937FBC0FF95ACCFE4232B36E72271425984589396F7A0A3E74365F8C01
- NeoForge: 052C8C44900DC0A96DF14AEC0FE36D2229F423B7605CDBF9431B03E71B8AF8DA

Remaining acceptance is explicit: run a branched order with intermediate counts
above signed long using loaded receipt-backed crafting workers; compare storage
before/after including surplus; cancel and restart at different ownership phases
and prove no double debit, lost output or replay. Exercise standard AE2 and the
external physical API consumer separately. These checks have not been run.
The AE2 request entry still takes a long root order; this increment connects
wide INTERMEDIATE quantities and pattern counts, not a new above-long order UI.
Processing machines, dynamic/substituted slot choices and interleaved/cyclic
physical execution are still outside this implemented domain.
No server/client restart, deployment, commit, push, release or Issue closure.


### 2026-09-18 input semantics follow-up

The user authorized fixing substitute inputs, remaining containers, and addon
pattern formats. Registered patterns must not be discarded merely for their
implementation class. Capture the immutable public IPatternDetails arrays and
their ordered template amounts; preserve the existing overflow checks. Keep the
old DAG/physical consumers restricted to proven exact single-input domains.

Extend OrderedBranchingPlanner with an input-rules boundary for alternate template
units, AE2 fuzzy inventory order, crafted substitute selection, and delayed
remainders. A new request-local AE2 input-rules adapter owns memoized isValid and
getRemainingKey observations and structural revalidation. Live calls execute only
on the server thread through the existing planning task/handshake boundary; the
worker owns exact simulation only. Do not retain mutable Level or input callbacks
in a global compiled program. Revalidate observed input semantics before adoption.
Bound distinct observations and cancellation, and avoid one server call per craft.

Use AE2's KeyCounter solely as a key-order index, not as the exact quantity ledger.
Maintain parent/child fuzzy-cache ordering, including zero keys. In repeated blocks,
new fuzzy members invalidate skipping until membership stabilizes. Containers are
returned only after all inputs succeed and contribute to CPU bytes. Preserve AE2's
primary-template rule for quantity limiting, not an invented alternate recipe.

Tests must compare the actual AE2 calculator for substitution priority, NBT variants,
mixed fluid/item template units, fuzzy craftable selection, remainder reuse, rollback,
and missing quantities. Unknown addon classes are tested through their public API,
with changed/malformed semantics rejected explicitly. Keep AppliedE's separately
documented live-EMC boundary and quantity-wide branching execution out of this change.


### 2026-09-18 implemented and locally verified

- Ae2CompiledPatternFactory captures unknown public IPatternDetails structures
  instead of rejecting their class. Exact-only DAG and physical consumers keep
  their existing eligibility checks. Checked EAEP scaling and the explicit
  AppliedE live-EMC boundary are retained.
- BranchingInputRules separates immutable simulation from Ae2BranchingInputRules'
  request-local, memoized server observations. Alternate units and actual AE2
  fuzzy candidate order are preserved, including NBT and damage variants.
- OrderedBranchingPlanner returns containers after successful input collection,
  retains tool reservations, rolls back failed candidates, and aggregates only
  repeated transitions with stable stock-read intervals and fuzzy membership.
- Ae2PlanningInventorySnapshot freezes referenced fuzzy variants and key order;
  it excludes the requested output without dropping other variants.
  Ae2AuthoritativeCraftingPlanner revalidates observations in batches of at most
  64 through the server boundary before materializing the result. No whole
  observation set is scheduled as one server task, and no live API runs on the
  calculation worker. This is not a measured per-tick time guarantee.

BranchingInputSemanticsTest has 12 passing tests on each loader. It compares the
actual AE2 calculator for alternate stocks, fuzzy craftable selection, NBT order,
mixed fluid/bucket units, delayed returns, failed candidates, and changing tool
damage with replacement. Comparisons include exact used/missing/emitted amounts,
pattern executions, requested output, multiplePaths and native CPU bytes.
The tool test includes 20,000 operations. A separate 10^64 request proves exact
arithmetic and repetition skipping while reserving one reusable tool; it is a
calculation-only proof, not a physical execution test. Snapshot immutability,
invalid remainder callbacks, changed validity/shape, and server-thread callbacks
are also covered. Test registry bootstrap on NeoForge is shared/idempotent so
isolated and full-suite runs do not depend on class execution order.

Clean build and verifyIssueRegressionManifest:
- Forge / upstream AE2: 594 tests, 593 passed, one skipped, no failures/errors.
- Forge / UELM: 594 tests, 593 passed, one skipped, no failures/errors.
- NeoForge 1.21.1: 602 tests, all passed, none skipped.
The Forge skip is the optional NeoECO 20.3 fixture whose JAR is unavailable.
ExtendedAE Plus 1.5.5 and NeoECO 20.4 optional JAR fixtures were supplied.
These fixtures do not establish full addon runtime or EAEP post-plan smart-scaling
compatibility.

No Minecraft launch, production server restart, deployment, merge, or release
was performed. Version metadata remains the existing rc.3 for this local work.
The observed 80 incomplete snapshots were 80 calculation declines, not 80
identified unique patterns. Their live class identities and acceptance rate,
whole-pack latency, and quantity-wide branching physical execution remain
PENDING. Do not close Issue #190 or claim end-to-end completion from these tests.


### 2026-09-16 live follow-up

ACO rc.3 recorded 54 planning starts in the current Forge 1.20.1 server run.
All declined the authoritative route: 40 MULTIPLE_PRODUCERS, 13 incomplete
pattern snapshots, one generation change. These are not 54 failed crafts.
The absolute control circuit order of 100 at 21:58:59 declined solely because
the root program cannot represent multiple producers. Preserve server uptime.

AE2 15.4.10 CraftingTreeNode tries producers in service order, rolls back failed
child inventories, and exhausts a candidate one operation at a time before
trying the next. Picking the first candidate or merely deleting the compiler
guard is not equivalent. CraftingTreeProcess preserves input order; recursion
exclusion depends on ancestor keys, not a global SCC.

New scope: add an immutable, exact-input ordered branching evaluator alongside
the single-producer DAG evaluator. It owns only calculation-local simulation,
candidate trials, stock minima and CPU charges, never real inventory. Coalesce
repeated successful trials only with a proof: all recorded inventory reads must
stay within the same full/partial extraction intervals under the repeated net
delta. Include failed trial reads in that proof; discard their writes. Keep
node overhead separate from repeated work. Bound exploration and cancellation.
No quantity clamp, inventory-dependent cache reuse, live worker reads, inferred
recipe outputs, or execution ownership changes. Unsupported input domains must
remain explicitly diagnosed, not silently treated as terminals or empty inputs.

Owners: OrderedBranchingPlanner (pure exact simulation); the existing immutable
snapshot (candidate order/completeness); Ae2AuthoritativeCraftingPlanner (adoption
and binding). Inspection found that the physical consumers rebuild a single-
producer root program; publishing a wide-count branching job would therefore
create another stuck order. Do not change execution ownership or publish that
job as executable. Retain exact arithmetic and report this boundary explicitly
before submission; quantity-wide physical branching remains PENDING. Capacity-
only overflow can use the existing BigCapacityCraftingPlan contract.
Tests compare actual AE2 CraftingCalculation, not a
second copied solver, for partial stock, missing inputs, candidate switches,
recycling, byproducts, templates, and NBT. Wide-count tests independently replay
conservation and check quantity-independent repeats. Both loaders must retain
the same semantics. Runtime acceptance and current unsupported patterns remain
PENDING until observed; tests alone cannot close this issue.

Installed AdvancedAE 1.3.6's AdvProcessingPattern inherits AEProcessingPattern's
getInputs/getOutputs unchanged (javap inspection). Accept processing subclasses
only when reflection confirms both declarations are AE2's implementation. Do
not whitelist arbitrary overridden input semantics or assume every incomplete
snapshot was caused by this class. The per-output candidate order is also lost
by the existing global discovery index: retain the captured service order for
the branching evaluator, including shared co-products.

Capture diagnostics will record the actual rejected pattern class and input-slot
reason once per pattern identity per publication. Do not attribute the 13
incomplete snapshots to an addon without this evidence. Wide missing plans may
use the existing non-submittable BigIntegerSimulationPlan, with multiplePaths
preserved. This does not authorize quantity-wide branching execution.

Generation changes in ordinary planning will retry ACO with a fresh coordinated
capture, at most three attempts. Reacquire storage, graph, recipe and configuration
revisions together on the owning server thread; never relabel the old stock.
Do not synchronously rebuild the full pattern index as part of an order. If the
published index is not ready or changes keep racing, surface the stale failure
rather than starting AE2's expensive standard calculation. Preserve explicit
wide rejection and cancellation. A detached worker must remember whether it
has resumed AE2's pause handshake before scheduling another server capture.
If refreshed planning declines, do not return to vanilla with the caller's old
inventory: preserve the stale failure and require a new request. Initial unsupported
captures keep their existing pre-custody fallback contract.

2026-09-17 refresh: the last 100 declines (20:26:25 through 22:15:15) contain
80 incomplete snapshots, 19 multiple-producer declines and one generation change.
This later sample supersedes the 54-request sample for current frequency, not
for individual causes. No pattern-class evidence exists in the deployed logs.

Installed ExtendedAE Plus 1.5.5 ScaledProcessingPattern/ScaledProcessingPatternAdv
were inspected with javap. They are exact processing wrappers with final input
and output methods, but use unchecked long multiplication. Accept only these
known wrappers around unchanged AE2 processing inputs, verifying reflection
declarations and every multiplier/output product BEFORE reading wrapped values.
Retain the real wrapper as the execution binding. Never accept a positive value
that wrapped through long overflow. Test the actual optional Forge JAR through
an isolated class loader, without starting Minecraft; no dependency is mandatory.
This removes a proven unsupported class, but does not prove it caused every
incomplete live snapshot or validate the full addon on NeoForge.

EAEP's CraftingSimulationStateMixin.onBuildCraftingPlan also applies smart scaling
AFTER planning. ACO's direct CraftingPlan construction does not run that optional
postprocessor. The wrapper capture tests do not establish equivalent smart-scaling
finalization or runtime performance. The wrappers may be created only at this later
stage and are not evidence of the current live capture failures. This compatibility
boundary requires separate end-to-end validation before claiming full EAEP support.

### Earlier offline acceptance experiment

Synthetic thousand-node tests exclude recipe capture and do not prove real industrial
end-to-end planning latency. The current pack has multiple creative circuit routes,
non-consumable catalysts, alternative ingredients and mod-generated recipes. Gameplay
progression and encoded ME patterns are incomplete. Source scripts alone are not the
final RecipeManager state. The server was stopped at that earlier inspection.
The September 16-17 follow-up above runs against an active server, kept running
throughout local implementation. No production world or recipe changes are authorized.

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

## 2026-09-17 local implementation and verification

- Added `engine/OrderedBranchingPlanner.java`: ordered exact-input trials, rollback,
  stock minima, byproduct reuse, proven repeated intervals and AE2 byte rounding.
- Updated `engine/Ae2ImmutablePlanningGraphCache.java` and `Ae2PlanningGraphSnapshot.java`:
  immutable output-specific priority and rejected-pattern diagnostics.
- Updated `engine/Ae2CompiledPatternFactory.java`: inherited AE2 processing inputs
  and checked EAEP scaled wrappers, without accepting unknown dynamic inputs.
- Updated `engine/Ae2AuthoritativeCraftingPlanner.java` and `BigIntegerSimulationPlan.java`:
  adoption, exact missing plans, binding before reattachment and bounded recapture.
- Added `OrderedBranchingPlannerTest` and `OrderedBranchingSnapshotTest`; expanded
  the actual AE2 oracle and planner policy tests. Randomized comparison includes
  700 generated graph cases, plus 20,000-operation repeats and 10^64 conservation.
- Forge-only `ScaledProcessingPatternCaptureTest` uses the installed EAEP 1.5.5 JAR;
  `testsupport/TestKeyTypes` shares idempotent registry setup with physical API tests.
- Clean `build verifyIssueRegressionManifest` passed on all three local configurations:
  Forge upstream AE2 15.4.10: 582 tests, 581 passed, one skipped;
  Forge AE2 UELM 15.5.0: 582 tests, 581 passed, one skipped;
  NeoForge 1.21.1: 590 tests, all passed. Zero failures/errors.
- The skipped Forge test requires the unavailable NeoECO 20.3 JAR. NeoECO 20.4 and
  all three real EAEP wrapper tests ran. Their optional JAR flags were supplied.
- Source version remains 2.0.0-rc.3: these local artifacts are NOT a new release
  and have NOT replaced deployed JARs. No server restart, client launch, world edit,
  new-runtime log capture, PR, merge or release was performed.
- Still PENDING: identify the actual uncaptured live patterns using the new
  diagnostics; dynamic/alternative input coverage; EAEP smart-scaling finalization;
  quantity-wide branching physical execution; real craft completion and latency.
  Do not close #190 or claim that every standard-path fallback has been removed.
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

The public map-based LongCraftingPlanner and BigCraftingPlanner add every extraction
to usedInventory, including simulated co-products and rounded surplus. A root
consuming A+B from a single raw-to-A+B operation therefore claims B existed in initial
stock when it did not. The graph overload of OverflowPromotingCraftingPlanner still
exposes these maps when compilation is unsupported (`provenEquivalent=false`). Do not promote
that result to authoritative or change producer selection as part of this fix.

Retain a private initial-inventory snapshot and record the peak positive
initial-minus-current deficit at each simulated extraction, as AE2 and the ordered
compiled path do. Keep exact counts, immutable caller stock, cancellation and the
non-authoritative flag. Regression tests must fail first for both arithmetic paths,
partial stock and reversed input order, plus a 10^64 request and the public fallback.

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
The final first-tick capture contains 59,229 recipes (30,306 GT), with zero capture
or normalization errors. The all-candidate coverage reaches 41,460 recipes; 8,441
of these have unsupported semantics. This is not an encoded ME graph. Extend only
the diagnostic probe to record the last visited key and its actual candidates
when the compiler rejects MULTIPLE_PRODUCERS; do not choose one arbitrarily.

Final tooling review: an empty resolved vanilla ingredient can mean an empty tag,
not just a vacant crafting slot. The capture does not yet preserve that distinction.
Mark those rows unsupported instead of erasing the input. A missing resolved output
must also remain an explicit unsupported boundary. Regression-test these cases
before changing normalization; this does not alter production recipe behavior.

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
