# Issue #218: API-compatible demand-driven planning redesign

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/218
- Status: Ready (public snapshot source preparation; vm-refs.8 locally verified, production acceptance pending)

## Public snapshot preparation (2026-10-06)

The user authorizes publishing the current source to GitHub and a separate
NeoForge 1.21.1 snapshot to CurseForge. Publish the Forge refs.8 source and its
matching VM fork first, pin the public fork commit in CI, and document a clean
checkout build. Exclude local logs, backups, credentials and build outputs;
replace workstation-specific documentation paths with example paths.
This preparation must not change Forge runtime behavior or relabel rc.5 as
the current 1.21.1 implementation. Port and validate the native VM separately
against AE2 19.2.17 before uploading a NeoForge artifact. Public API compatibility,
exact quantities and AE2 execution ownership remain mandatory.

Owners: build.gradle and build.yml for source/bundle reproducibility; README and
release notes for public scope; NativeVm and its platform adapter for a separately
verified port. Re-run quantity/API tests and inspect nested metadata. Production
deployment is not authorized by this publication request. Wide execution,
cancellation/refunds, restart recovery, concurrent-order acceptance and the live
sub-10-second target remain pending; neither snapshot is a stable 2.0.0 release.

## Requester quantity search (2026-10-06)

ME Requester 1.1.5 uses CRAFT_LESS and retries missing orders. Its installed
RequestState/PlanState/MissingState bytecode was inspected. Existing logs do not
identify callers, so live requester load attribution is not established.
VmInstructionExecution currently performs a full feasibility attempt followed by
up to one attempt per quantity bit. Captured stock, input observations, process
metadata and demand programs are already order-local; do not duplicate them.

Owner: VmInstructionExecution quantity search using VmDemandProgram coefficients.
For a unique fixed-input producer graph with no stocked expanded intermediates,
derive a BigInteger upper bound from shared raw-material coefficients. Integral
program periods supply an exact funded lower bound. Materialize aligned results
directly; search only the unresolved rounding interval with existing VM trials.
Fractional graphs that share a manufactured intermediate retain the existing
search: comparison exposed quantity-equal but capacity-different results
(347 versus 307/323 bytes) because exploratory trials instantiate different
conceptual AE2 nodes. Fractional unshared graphs can use bounded search; integral
shared graphs can use direct arithmetic. This is not a quantity or work cap.
The expanded 500-case matrix also exposes an existing ordered-versus-aggregated
capacity discrepancy (468 versus 460 bytes), reproduced with the new entry
disabled. Retain the union of previously visited aggregate capacity shapes,
rather than overwriting a deeper shape with a later stocked-boundary summary.
Capacity-only shapes do not contain inventory or change quantities/selection.
Never ignore stocked intermediates with a manufacturing route, alternative
producers, reused outputs, dynamic tools or cycles. Those retain ordinary VM
execution and shared order-local observations. No cross-order stock/plan cache,
new callback bypass, retry throttling or physical execution change is allowed.

Before implementation, add failing requester work-count regression and quantity
fixtures covering empty/partial/full stock, shared DAGs, output rounding,
intermediate stock, alternative producers and values through 10^1024-1. Compare
all ledgers and capacity with ordered CRAFT_LESS and native AE2 where applicable.
Keep refs.7's funded missing-route fix. Measure synthetic work/time separately
from production; candidate JAR only, no deployment/restart/release.

Implemented and locally verified: fixed shared demand falls from refs.7's 266
work units to one, with all ledgers/capacity unchanged. The arithmetic benchmark
against the unoptimized ordered reference is 45.330 to 1.965 ms median on the
upstream run, not a production latency comparison. Both AE2 variants pass the
full suite and bundle gates, including the original 480-shortage regressions.
Native processing capture also verifies independent stock views and no further
world reads during CRAFT_LESS. Outer ACO class bytes remain unchanged from
refs.7. [Evidence and candidate hash](evidence/ISSUE-218-REQUESTER-2026-10-06.md).

## Craftable intermediate shown as missing (2026-10-06)

The user reports printed logic circuits as missing in a supreme quantum circuit
plan, despite available gold and a registered producer. The subsequent standalone
printed-circuit order succeeds (the second screenshot is that later order, not
stock already reserved when the first order began). The first screen shows
about 12K available circuits, 480 missing and 1760 to craft. Installed refs.6
orders 4/9/11 finish missing simulations in 27176/21346/22328 ms, with no
logged cancellation/failure. There is no per-item snapshot in those log lines,
so the particular 480-item route cannot yet be reconstructed from the logs.

Code inspection finds that the missing simulation permits the first alternate
producer to commit imaginary missing inputs without trying later funded routes.
In particular a cyclic first producer can report the craftable circuit itself
missing, although a second producer could use actual gold. This is a candidate
explanation, not yet a confirmed reproduction of the saved live network.

Owner: VmInstructionExecution, its ordered producer trials and missing pass.
First add an independent regression with partial circuit stock, funded gold
producer, an unavailable/cyclic higher-priority producer and an unrelated true
shortage. Both producer priority and exact used/pattern/byproduct counts matter.
Also cover partial supply from multiple routes, complete stock, and BigInteger
orders. The missing pass must first consume feasible producer paths with real
stock, then simulate only the unfunded remainder. Failed trials remain isolated;
true missing resources must still be shown. No time/work cutoff, guessed stock,
new live query, API/CPU/dispatch change or second solver is allowed.

The complete suite exposed two historical native-AE2 oracle comparisons whose
missing plans deliberately retain the first route. Do not force that result
back into the repaired VM. Preserve every exact successful-plan comparison and
the historical planner's comparisons. For these two fixed-input randomized
networks, check the VM's missing plans against independent stock bounds and
material-balance equations instead of identical producer counts. This is not
a general scheduling proof; the explicit funded/cyclic/partial/byproduct
regressions still assert exact independently derived quantities and producer
counts. Do not broadly disable missing-plan comparisons elsewhere.

Scope: local reproduction, focused VM correction, regression/build evidence and
candidate JAR. No production deployment/restart or release. The live 480-item
report remains unverified until the original order is repeated with matching
available inventory. Existing wide execution and lifecycle boundaries remain.

Implemented results: missing-pass producer trials now consume funded alternatives
before simulating the unfunded remainder. Independent regressions preserve actual
shortages, partial stock, failed-trial rollback, byproducts and quantities through
10^1024-1. A captured real AEProcessingPattern fixture supplies 12480 circuits
from 12000 existing circuits and 480 gold without another world read. Both AE2
variant suites and bundle gates passed; this is not a replay of the live network.
The outer ACO classes remain byte-identical to installed refs.6. Candidate hash,
retained fail-before evidence and verification limitations:
[Missing-route evidence](evidence/ISSUE-218-MISSING-ROUTES-2026-10-06.md).

## Input rules and stocked demand (2026-10-06)

The user approved the optimization proposal. Installed refs.5 completed supreme
quantum circuit x1 in 15195 ms, including 11486 ms of world-boundary wait and
2903 INPUT_VALIDITY entries. Absolute overclocked circuit x4000 took 29961 ms,
with 17953 ms worker CPU and 130455 branch trials. These are different orders;
simulation=true is not evidence that missing quantities are correct.

Owners: NativeVmPlanningContext owns the order-local input observations. A small
native rule adapter may freeze only audited vanilla recipe/ingredient semantics;
it must not invoke arbitrary recipes/items on a detached worker or infer tool
durability transitions from samples. Opaque rules keep the existing exclusive
boundary, but an actually encountered candidate can be observed across relevant
captured inputs in the same interval. Do not recursively enumerate future NBT
states, recapture stock, or generate hypothetical tool inventories.
The grouped read stops between independent callbacks when AE2's existing read
interval expires. Unobserved peers remain eligible for their later demand; no
new wall-clock budget, dispatch limit or background task is introduced.

VmDemandProgram owns immutable arithmetic summaries. VmInstructionExecution may
consume stocked intermediate boundaries in bulk, then expand their producers
only for the remaining demand. Preserve input priority, integer batch rounding,
all outputs, peak reservations and memory charges. Never delete the current
zero-intermediate check without replacing its arithmetic assumptions. Invalid
cyclic/returned-input summaries continue through the same ordered VM, not a
second calculator or an AE2 fallback. Quantity types remain BigInteger.

Fail-before cases: shared stocked dependencies must remain bounded as depth and
quantity grow; empty/partial/full stock must match ordered quantity and byte
accounting. Repeated independent inputs observing one actually encountered NBT
key must not acquire separate world boundaries. Frozen known rules must match
real AE2 inputs and preserve exact remainders; custom rules remain exclusive.
Cover multi-output recipes, alternate producers, returned tools, cancellation,
long-max/max+1 and AQE-scale quantities. Record operation counts and timings
separately; synthetic fixtures are not the production industrial order.

The randomized stock/alternative oracle exposed path-dependent capacity:
batching a fractional child period preserves item quantities but can instantiate
different conceptual AE2 nodes (441 vs 505 bytes). Alternative producers are
originally tried one craft at a time, so their arithmetic summary must have an
integral one-craft period. Use stocked boundaries to obtain such summaries;
otherwise retain ordered execution and its existing exact periodic replay.
This is an accounting equivalence requirement, not a tick/work/quantity limit.

References: AE2 forge/v15.4.10 AECraftingPattern.Input is the semantic authority;
LLVM loop-invariant specialization motivates freezing rules rather than answers
for every possible future tool state. Existing guarded VM bulk execution is
retained, without introducing solver/runtime dependencies.

Write scope: VM fork input boundary (including the existing read-window query)
and demand executor/compiler; focused tests,
issue/responsibility/evidence documents, candidate version metadata. No public
API descriptor, physical execution, dispatch speed, saved data, server/client
installation, startup, GitHub push or release changes. Wide physical execution,
cancel/refund, restart recovery, concurrent orders and live <10 second acceptance
remain pending. Candidate JAR and local verification only.

Implemented results: exact native recipe rules, encountered-key input batching
within the existing AE2 read interval, and cached stocked demand summaries are
in vm-refs.6-local. Both AE2 variant suites and bundle gates pass. A seeded
400-case oracle preserves used/missing/produced quantities, pattern counts and
capacity; an independent quantity matrix extends through 10^1024-1. The
synthetic stocked x4000 case reduces branch trials from 3720 to 627, with
upstream median arithmetic time 230.49 ms to 39.68 ms versus the ordered
reference. This is not an installed-version end-to-end speed measurement.
Outer ACO classes and the exact arithmetic library are byte-identical to the
installed refs.5 artifact. No deployment, startup or publication was performed.
Hashes, commands, limitations and retained reports:
[input/stock evidence](evidence/ISSUE-218-INPUT-STOCK-2026-10-06.md).

## Remove test-only graph solver from distribution (2026-10-05)

The user requests cleanup and a rebuilt candidate. The vm-refs.4 installable JAR
is 2930678 bytes; compressed SMTInterpol alone occupies 1515007 bytes. Native
graph adapters are already test-only, but the exact library still ships its
graph package and ACO still bundles the solver and exports its 58 MB sources.
There is no graph/SMT runtime fallback to preserve.

Owners: ACO build.gradle owns the installable Jar-in-Jar composition; the fork's
exact-engine/build.gradle owns the runtime library and comparison fixtures.
Move graph compilation and SMT dependencies into an explicit test-fixture source
set/artifact. Retain all historical comparison tests. Ship only the runtime
exact VM and native VM, with their actual licenses and corresponding sources.
Do not strip live API classes or drop an entire library just to hit 1000 KB.

Fail-before regression: the exact VM JAR loaded by tests contains graph classes;
assert those classes and solver packages are absent. Extend the distribution
gate to reject graph/SMT classes, metadata and runtime dependencies, while still
checking the two required nested JARs, exact bytes, bootstrap and licenses.
Verify both AE2 variants and compare retained class bytes against vm-refs.4.

Compilation exposed a remaining runtime graph dependency: NativePatternCatalog
contains RecipeGraph, and NativeCraftingServiceMixin still populates it on
provider changes. Its instance consumers are now only the comparison adapter;
the VM uses only its static scaled-coefficient reader. Move that catalog and its
graph hook into comparison fixtures, remove the unused provider injections,
and retain the coefficient reader verbatim in a small runtime owner. This is
required to remove the solver without leaving unresolved runtime class types.
The fork-private catalog hook is not the public ACO BigInteger API.

Keep BigInteger API, arithmetic, physical dispatch, saved data and compiler flags
unchanged. Version the smaller exact library as 0.1.6 and ACO as
2.0.0-rc.7-vm-refs.5-local; native VM becomes 0.3.10-vm-refs.5. Extend the runtime
exclusion gate and a compiled-Mixin regression to cover the retired catalog.
No publication, production deployment, launcher edit or Minecraft startup.
The GC access violation in hs_err_pid20540.log is not fixed by this packaging
change; neither build success nor smaller bytes establish crash or gameplay
acceptance. Existing wide execution and sub-ten-second gates remain pending.

Result: upstream and UELM build/test/bundle gates pass. The final upstream JAR is
1359504 bytes (53.61% smaller); all 531 outer ACO classes and all 14 retained exact
VM classes are byte-identical to refs.4. Only the retired native catalog hook and
the coefficient-reader call site change. See
[packaging and startup evidence](evidence/ISSUE-218-PACKAGING-2026-10-05.md).

The user additionally requests startup repair. The latest client crash is a
NightConfig parsing failure; two client TOML files contain only NUL bytes. With
explicit approval, back up and restore only those two files, parse them with the
installed NightConfig 3.6.4 and verify SHA-256. This is configuration recovery,
not evidence that ACO caused or fixed the separate GC access violations. The
client's Java 21 fatal log and server's Java 25 fatal log both name GC Thread#8.
No JAR deployment, launcher change, startup or restart is authorized/performed.

## Exact rejection must not acquire a world interval (2026-10-05)

The user requires correctness before speed. The running vm-refs.3 JAR matches
SHA-256 2E860A3EF4EA68E2EDA01EE3F4ABAF559EF8F2EA2E2A809F27A7E8EB44CE0AAE.
Order 4 completed as missing in 19067 ms, including 14628 ms of AE2 access wait
and 3349 INPUT_VALIDITY boundary entries. Those counters do not prove shortages
correct or attribute every boundary to the defect below.

NativeVmPlanningContext already rejects a non-primary key locally when a native
AECraftingPattern forbids substitution. But isCandidatePrepared ignores this
known decision, so prepareCandidates acquires a live interval even when observe
only performs that local rejection. An unseen returned NBT state also opens the
whole order's live scheduler even if every relevant input can reject it locally.

Owner: NativeVmPlanningContext.LiveInput and prepare's domain classification.
Keep the native exact-key predicate, input ordering, remainders, consumed and
missing quantities, bytes and BigInteger arithmetic unchanged. Unknown inputs
with substitution/custom semantics must retain their existing exclusive reads.
No detached recipe callbacks, synthetic acceptance, future tool-state expansion,
new execution limit, API change or physical-execution modification is permitted.

Fail-before: use the real AECraftingPattern.Input with a recipe that returns a
different NBT key from a non-substitutable input. After T0 capture, plans must
finish with the read boundary closed, reject that returned key, and consume or
report exactly N original inputs for N crafts. Cover empty/partial/full stock,
1/2/1000/long-max/max+1/1024-digit quantities, plus a substitutable counterpart
that must observe and reuse the actual returned key. Explicitly compare live
boundary entries before/after; timings are not a production speed claim.

Candidate JAR only. No production writes, restart, deployment, publication or
push. The original copper-wire mismatch and full physical acceptance remain open.

Final upstream 15.4.10 and UELM 15.5.0 local verification passed. Candidate SHA-256:
`131BB751AD9C520E12A82017A5F40E50A7E1AA9B2208E1AFD78A8A225A915B7D`.
The 531 outer ACO classes are byte-identical to installed vm-refs.3; changed
native classes are confined to NativeVmPlanningContext, VmInstructionExecution
and their nested classes. Evidence and exact remaining boundaries:
[fixed-candidate and replay-search verification](evidence/ISSUE-218-EXACT-CANDIDATES-2026-10-05.md).

References selected after the user's instruction to use existing optimization
implementations rather than invent a new planner:

- [PostgreSQL 17 ExecReadyInterpretedExpr / ExecJustConst](https://github.com/postgres/postgres/blob/REL_17_STABLE/src/backend/executor/execExprInterp.c):
  select simple specialized evaluation before entering generic interpreter dispatch.
  Here, native fixed-input rejection must finish before acquiring a generic live
  callback interval. No PostgreSQL dependency or copied executor is introduced.
- [Truffle partial evaluation](https://github.com/oracle/graal/blob/master/truffle/docs/PartialEvaluation.md):
  specialize stable interpreter structure and leave dynamic values as runtime
  inputs. Here, native input type/substitution policy and encoded AEKey are order
  constants; quantities, stock and unknown custom returns are not constants.
- [AE2 15.4.10 AECraftingPattern.Input](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/pattern/AECraftingPattern.java):
  exact key match succeeds; without substitution any different key fails, while
  returned items remain recipe-owned. This is the actual semantic authority.

The fail-before matrix preserved all quantities but entered ten unnecessary
world boundaries across 18 stock/quantity cases. The substitutable control uses
one real callback for a newly returned key. This does not establish that all 3349
entries in the production log are fixed-input rejection.

Verification exposed a second performance defect in the existing periodic VM:
the finite 32-use tool fixture can exceed 65536 requests for a 2^63 order with
2^57 existing tools, depending on the key iteration order. The failing UELM
report is retained under build/issue218-exact-candidates/uelm-initial. Do not
remove this case or weaken exact tool replacement accounting. Inspect the
existing affine trace bounds and compare accelerated execution with its ordered
reference. Any additional runtime correction requires unchanged quantity results
and bounded work for both sufficient and partially supplied tool orders.

The trace included a one-off consumed item (start=1, delta=-1, lower=1), so
replay correctly returned zero. The performance defect is retaining that failed
trace for up to 1048576 iterations: windowSize doubled after successful replay
and every new key, not only after exhausting an unsuccessful search window.
[SymPy 1.14 cycle_length](https://github.com/sympy/sympy/blob/1.14.0/sympy/ntheory/generate.py#L916)
uses geometric search windows advanced on window exhaustion. Adopt that search
scheduling principle: grow only after a full unsuccessful window, restart short
after key-domain change or successful replay. Keep all affine quantity guards,
minimum-stock accounting and exact replay equations unchanged; no state-equality
approximation, quantity bound or nmax abort from the reference belongs here.

## Preserve semantics while removing repeated VM work (2026-10-05)

The user requests optimization, not removal of returned-tool accounting. Combine
the order-local reference capture with the dev.20 VM execution model, retaining
subsequent exact scaled quantities and alternate-return fixes. Do not restore
dev.20's live reference loop or blindly discard later demand aggregation.

Owner: VmInstructionExecution's per-order process metadata. The current run()
re-enters PatternCompiler.compileIfAbsent/getCompiled for every executed pattern
block; this repeats output/input validation even though compilation is cached.
Dev.20 retained the compiled block in process metadata. Restore that reuse lazily,
so unused alternatives and directly aggregated patterns are not compiled early.
Neither returned NBT transitions nor stock accounting may be approximated.

Fail-before: a many-use returned-tool fixture repeatedly reads immutable primary
output metadata during execution. Fix-after: read/compile once per demanded
pattern while preserving exact used/missing tools, raw consumption, craft counts,
bytes, cancellation and returns. Compare a test-only dev.20 arithmetic fixture
with the current arithmetic on identical captured inputs; no second runtime
calculator or new thread/configuration belongs in the candidate. The archived
dev.19 source bundle is eligible as the dev.20 arithmetic baseline only after
confirming the dev.20 change was diagnostics-only and identifying its SHA.

Measure empty/partial/funded stock and wide quantities, including finite returned
tool states. Quantities and call counts are assertions; timing is evidence, not a
flaky unit-test threshold or a claim about the production circuit. Both AE2
variants and the final upstream bundle require verification. Candidate JAR only:
no production changes, startup, release or push.

Implemented: a lazy compiled block in shared ProcessMetadata replaces the repeated
compiler/cache entry from run(). Existing capture, demand aggregation, ordered
effects, tool damage, return classification and BigInteger coefficients are not
changed. The finite 32-use tool fixture proves exact tool requirements through
1024-digit orders. Its partial-stock 1000-craft case previously re-read primary
metadata 170 times; it now reads it twice, matching the archived VM design.
The connected-context test also verifies only ceil(N/32) tools are required,
only the deficit is manufactured, and at most 32 unique remainder observations
are made, independent of N. This is not a claim of zero callbacks for unknown
future tool states.

Baseline identity caveat discovered during audit: the current dev.20-named
distribution has SHA-256 47B9101927013D1374AD9DCDA7BF1143380162BCAC45E25F392471B0D11A713D,
not the historical 358203CB49AE453FBECA552FF2B1F658ED846982C169D4FEA178FC7D8683BACA.
The recovery copies have 8A81E3859DA63199B0A66F080F94AB62671AEAEED58AD4C641A3E1D9B6B8D8B0.
Therefore the comparison fixtures are explicitly an archived dev.19 source-based
ordered VM, not verified original dev.20 binary acceptance. Archive SHA-256:
E30EA71F785B511EB8B4554902B9B0504EAC750C3CF81F07151B419EE4FDE8CD.
The archive also uses the current compiler and snapshot adapters in this test.
Do not report these measurements as an exact deployed-dev.20 speed comparison.
The reproducible before/after metadata-count correction does not depend on
attributing that historical archive to a particular deployed JAR.

## Order-snapshot calculator completion (2026-10-05)

The user reaffirmed the completion contract: preserve public BigInteger API
behavior and exact quantity ownership; calculate from order-start accessible
stock and registered patterns; share common work; retain BigInteger throughout;
prove used/missing/byproduct/return quantities for empty, partial and funded
stock with alternative producers. Planning is not physical execution. No new
delivery throttle, ordinary dispatch replacement, or capacity-derived quantity
clipping belongs to this change.

Owners remain NativeVm/NativeVmPlanningContext for acquisition and the fork's
compiler/executor for request-local arithmetic. VmAccounting remains the stock
and final-result boundary. Inspect saved production patterns and inventories to
derive meaningful replay fixtures without starting or mutating production.
Order-188's exact live snapshot was not retained; a later saved world must not
be advertised as that same snapshot. Fix a reproduced accounting failure before
calling the reported false-shortage bug resolved.

Implementation gates: frozen references must not query the mutable network
during arithmetic; unknown callback semantics must never be silently treated as
absent stock or invalid producers. Scope reuse to immutable pattern constants,
not old stock or solved craft counts. Measure complete cold/warm plans and
quantity equality, not test counts. Keep production restart/deployment and
unmeasured final industrial acceptance explicitly separate.

Concrete corrections selected after source inspection:

- Preserve alternative-only remainders: a primary input with no return does not
  prove that an accepted alternative has no return. Snapshot-backed inputs expose
  whether their captured candidate domain can return items; unknown semantics
  remain conservative. Reusable inputs use the existing exact periodic executor,
  not a per-item loop or a quantity cap. Keep unused candidate exceptions deferred.
- Build the finite candidate domain from stock, declared templates, selected
  craftable keys and every reachable declared output before arithmetic. Observe
  validity and returns for that domain once, indexed by primary-key family and
  full AEKey. Do not recursively expand hypothetical future tool NBT states.
- Freeze producer/emitter decisions before leaving acquisition. A worker cannot
  discover a changed network midway through an order. Finite closed candidate
  domains need no AE2 read scheduler. Genuinely new opaque returned states still
  need a compatibility boundary until a correct detached semantic adapter exists;
  this is an explicit remaining completion gap, not a claimed frozen snapshot.

The first independent fail-before case reuses an alternative catalyst (primary
has no remainder) with raw stock zero/partial/funded through 1024-digit orders.
The second disables all world callbacks after capture and consumes an NBT
alternative produced by an earlier dependency, after changing the live provider
and validity answers. These target accounting and snapshot semantics directly.

Candidate built and checked as `2.0.0-rc.7-vm-refs.2-local`, native VM
`0.3.7-vm-refs.2`. The candidate is local only; the user explicitly selected no
production changes or startup. The final bundle targets installed AE2 15.4.10;
UELM 15.5.0 compatibility was checked separately. Public API and physical-dispatch
classes are byte-identical to the installed vm-refs.1 JAR. New source changes are
limited to candidate acquisition and VM input/return classification.

The synthetic 48-stage native-processing matrix verified all craft, used and
missing quantities through 1024 digits, with zero worker world reads and one
aggregate request per plan. In a fresh JVM, first capture+plan was 144.500 ms
(51.989 ms acquisition and 92.510 ms plan/worker scheduling); subsequent matrix
cases were about 3-16 ms. These are not live creative-circuit or server-load
acceptance. See [candidate evidence](evidence/ISSUE-218-ORDER-SNAPSHOT-2026-10-05.md)
and [connected design](../design/ORDER_SNAPSHOT_VM_2026-10-05.md).

## Production false-shortage report (2026-10-04)

Installed vm-refs.1 SHA-256 matches the local candidate. Order 188 for
`emextras:supreme_quantum_control_circuit` x1 completed as missing in 26,454 ms,
including 22,272 ms AE2 read wait, 30 ms preparation and 2 ms stock capture.
The user identifies `gtceu:fine_copper_wire` as a craftable intermediate reported
missing despite registered producers and sufficient source stock. The existing
`aco vm recent 16` command confirms wire x1,000,000,000 ready in 6/7 ms (orders
199/201), and x10,000,000,000 ready in 54 ms (order 200). The screenshot shows an
accepted physical wire job. These are different order snapshots; do not claim
their complete inventories are identical or infer the rejected branch from the
summary counters alone.

Owner: NativeVmPlanningContext reference preparation and VM demand/branch
accounting. Keep public API, physical execution and long Pattern Provider
delivery unchanged; never hide missing entries, fabricate stock or add transfer
rate/operation caps. Investigate the selected intermediate producer, residual
demand and alternative branch rollback before changing runtime arithmetic.

Regression gap: NativeVmOracleHarness skips `prepare(output)` although the
production entry calls it. Make the actual AE2 comparison use this same entry,
then compare direct and nested whole-batch production with partial intermediate
stock and shared/multiple producer branches. Obtain actual registered patterns
and accessible stock before attributing the reported production mismatch to a
specific local fixture. No production script modification without permission;
no diagnostic JAR deployment or restart. A failing reproduction is required
before a production correction is called verified.

Read-only investigation results:

- Fixed the oracle entry to call `prepare(output)` and initialized test key types
  explicitly so this comparison no longer depends on another test running first.
  Funded direct/nested eight-output production and seeded alternative/shared DAG
  comparisons pass against AE2. They do not reproduce the production report.
- Read the saved `world/dimensions/javd/void/region/r.0.-1.mca` using Minecraft's
  NBT reader with `RandomAccessFile(..., "r")`. The provider at `(41,136,-112)`,
  south part, pattern slot 5, contains an exact processing pattern from one
  `minecraft:copper_ingot` to eight `gtceu:fine_copper_wire`, with no extra input
  or stack NBT. The saved quantum CPU also contains this pattern in its wire job.
  Registered consumers include two wires per six microchip processors. Saved
  disk data is not proof of active grid membership or order-188 accessible stock.
- Private read-only extraction outputs and helper sources are in
  `build/issue218-false-shortage/`. Do not publish the full world-derived exports.
- Inspected the installed JAR bytecode of `CraftingExecutionBudget`: ordinary
  operation counts pass through unchanged. The remaining int bound is the native
  AE2 coprocessor-count boundary, not a long transfer-quantity/rate cap. Retain
  exact-job ownership and do not change physical dispatch to repair planning.
- No production runtime correction is verified or applied. No JAR was rebuilt,
  deployed, or restarted by this investigation. A temporary live read-only
  pattern/stock command was proposed for permission; it has not been installed.

## VM reference repair (2026-10-04)

The user explicitly selected the previous BigInteger VM calculator over the
new graph/SMT calculator after the production graph.3 regression. Do not extend
the global integer solver or roll back the graph.3 startup compatibility fix.
The production thread dump has the server in NativeOrderSnapshot.candidate /
AECraftingPattern.isItemValid and an AE2 worker in SMTInterpol.checkSat. These
are separate problems, not evidence that more diagnostic logging will fix them.

Use the fork's existing PatternCompiler/CraftingVM arithmetic and AE2 worker
pool. Keep one immutable order stock snapshot and the public BigInteger API.
NativeVmPlanningContext owns ordered metadata and per-input observations. Capture
the reachable declared pattern references and primary input decisions together;
do not recursively enumerate hypothetical returned NBT/durability states.
Validity and remainder answers (including null) are keyed by input identity and
full AEKey for this order, never by item ID and never across orders. Batch actual
uncached alternatives in one AE2-owned read rather than one read per predicate
and another per remainder. Preserve declared order and per-template units.

Opaque new candidates encountered during VM execution still require the existing
AE2 exclusive read boundary. This repair must not claim all custom/world-dependent
callbacks are frozen at T0 or move those callbacks unsafely to an unsynchronised
worker. No second executor, full-durability-domain expansion, periodic recapture,
global SMT search, legacy ACO arithmetic, transfer limiter or quantity cutoff.
Retain known scaled-pattern coefficients in BigInteger before long overflow.
The scheduler-only NativeVmCalculation constructor must bypass the old ACO
diagnostic/shadow/authoritative capture before any configuration or revision
lookup; superclass construction is not authorization to start a second planner.

Regression gates: real VM completion with callbacks disabled after preparation;
one primary remainder observation, no speculative future-tool-state expansion;
NBT-sensitive per-order caching; unused alternative failures deferred until use;
stock/missing/returns at 1, 2, Long.MAX_VALUE and wider orders; scaled wrapper
inputs/outputs, cancellation and the existing AE2 oracle tests. Verify both
AE2 variants and the reobfuscated final bundle. Keep graph arithmetic tests as
fixtures, not the connected runtime calculator. No production replacement or
restart in this repair. Full runtime speed, wide execution, cancellation/refund,
recovery, concurrent orders and sub-10-second acceptance remain pending.

Implemented as `2.0.0-rc.7-vm-refs.1-local`, using native VM `0.3.6-vm-refs.1`.
Both upstream AE2 15.4.10 and UELM 15.5.0-uelm passed the test, manifest and
reobfuscated bundle gates. The final UELM test report has 896 tests, no failures
or errors, and three skipped optional-addon tests. The installed ExtendedAE Plus
scaled-pattern and MEGACells bytecode checks ran. The regression tests prohibit
post-preparation world reads for prepared candidates, distinguish NBT variants,
and preserve exact quantities through `10^1024 - 1`; these are not production
speed or execution acceptance. No deployment or restart was performed for this
planner repair. See [VM reference evidence](evidence/ISSUE-218-VM-REFERENCES-2026-10-04.md).

## Graph startup repair without rollback (2026-10-04)

Keep the new graph/SMT calculator, catalog and public BigInteger API unchanged.
The supplied server crash `crash-2026-10-03_22.45.27-fml.txt` records
MEGACells 2.4.6 `ForgePlatform.<clinit>` calling
`FMLJavaModLoadingContext.get()` while AE2MEGAThings 1.0.8 owns the active
Kotlin loading context. Forge's unchecked language-context cast fails.
The timing of the ACO replacement may expose this order-dependent failure;
the stack alone does not establish why this load order occurred.

Owner: a MEGACells-only compatibility redirect replaces that single context
lookup with the registered `megacells` container's extension. Use a separate
ModLoadingContext instance: never modify the loader thread's active context,
force another mod constructor, cancel a class initializer or replace Forge's
global lookup. Keep the original MEGACells event-bus assignment and all other
initialization. The optional target must not become a mandatory dependency.
No new config, planner cache, executor or transfer limit.

Build owner: the existing Gradle client/server development profiles must load
the native graph mod and both Mixin configs, matching the distribution. Build
a separate graph.3-local candidate using Java 17, reobfuscate both mod JARs,
and validate the final bundle. Do not overwrite graph.1/graph.2 artifacts.

Fail-before/fix-after: reproduce the real Forge context cast with a foreign
language extension; prove owner-context selection returns the MEGACells bus
under foreign/no/own active contexts without changing the thread-local state.
Inspect the installed MEGACells bytecode for the exact redirect site. Verify
Mixin registration/ownership and the absence of hard MEGACells class references.
Retain graph arithmetic, API compatibility and bundle gates for both AE2 variants.

Runtime boundary: this known Forge exception is separate from JVM GC/C2 access
violations and Windows 0x1A/0x41792 dumps. The latter record a corrupt page table,
not the writer responsible. This change must not claim to fix them without
evidence. No production file replacement, configuration rewrite or launch.
The user subsequently authorized isolated server/client startup; the results
below do not constitute full production acceptance. Wide execution, refunds,
recovery, concurrent orders and the production
sub-10-second goal remain unaccepted; this is not a stable release.

Implemented as `2.0.0-rc.7-graph.3-local`. The Forge context-cast regression
fails before the repair and passes after it, including foreign-Java, foreign-
language, own-context and absent-thread-context cases. The installed MEGACells
2.4.6 bytecode has the exact optional redirect site. Both AE2 variants build;
API and final Jar-in-Jar audits pass. These are not game-launch acceptance.
See [startup evidence](evidence/ISSUE-218-GRAPH3-STARTUP-2026-10-04.md).

Authorized runtime check (2026-10-04): the copied server reached `Done` after
restoring 14 NUL-filled configs in the test copy only. The copied client reached
the title-screen render loop and produced a normal F2 title image after restoring
two NUL-filled configs. Both sides applied the real MEGACells Mixin and initialized
ACO/AQE. Window-capture output remained white, so desktop presentation awaits user
confirmation. Server shutdown saved the world but Arclight reported one remaining
thread before exiting. Production configs/JARs/worlds were not changed; no join,
crafting, native-crash cure or complete 2.0.0 acceptance is claimed.

## Design follow-through, no rollback (2026-10-03)

Implemented as `2.0.0-rc.7-graph.2-local`. Upstream AE2 15.4.10 and UELM
15.5.0-uelm tests and bundle/manifest gates passed. The final candidate is built
against UELM. Runtime acceptance and the reported startup failures are NOT
resolved by these tests. Details and hashes are in the implementation record.

The user reaffirmed BIGINTEGER_PLANNER_2026-10.md as the implementation target,
not a return to a pre-graph calculator. Keep the public BigInteger descriptors,
execution ownership and installed client/server files unchanged in this work.

Gap to close: graph.1 re-reads all processing coefficients and rebuilds the
same dependency topology on every order. Add a crafting-service-owned catalog,
updated on provider add/remove/refresh, retaining immutable native processing
coefficients and AE2's provider order. Invalidate only affected root programs.
An already captured program remains valid for its T0 order. Stock, emitters,
requester visibility and opaque callback results must never be cross-order
cached. Unknown/custom callbacks retain the current order-local capture.

Owners: NativePatternCatalog owns provider-change indexing and frozen native
processing definitions, NativeOrderSnapshot owns each order's T0 quantities,
RecipeGraph owns reusable dependency topology, and ExactGraphPlanner owns pure
BigInteger arithmetic. Compile a frozen processing program on the AE2 worker,
not in a second executor. No solver construction or world reads in provider
registration. No fallback to the retired planner.

Required regressions: two orders share a program but not stock; same-tick
provider replacement/removal/priority change invalidates affected programs;
unrelated programs survive; a captured program survives later provider changes;
unknown mutable input callbacks are still freshly captured; scaled coefficients
are captured before long overflow; no worker calls reach a live provider.
Audit Java target, reobfuscation and Jar-in-Jar metadata on the new candidate.
Do not overwrite graph.1 or call build success a startup/crash fix. The reported
black screens, wide execution/refund/recovery/concurrent-order acceptance and
production sub-10-second goal remain unverified. No launch/deployment/release.

## BigInteger API-only design reset (2026-10-03)

The user fixed public BigInteger API compatibility as the retained contract and
authorized a fresh internal design informed by computation research and public
implementations. The proposed design is
[BIGINTEGER_PLANNER_2026-10.md](../design/BIGINTEGER_PLANNER_2026-10.md).
It specifies immutable order inputs, shared demand propagation, exact integer
constraints for coupled choices, and explicit startup/order accounting for
returns and cycles. SMTInterpol is the local constraint backend;
production performance and general cyclic/dynamic coverage remain unverified.
Status: Implemented locally as `2.0.0-rc.7-graph.1-local`; build/API/bundle
verification passed for upstream AE2 15.4.10 and AE2-UELM 15.5.0-uelm.
The dev.23 notes below are historical and do not constitute acceptance of the
replacement. Full 2.0.0 runtime acceptance remains PENDING.

Compatibility requirement for the connected snapshot: ExtendedAE Plus 1.5.5
scaled processing wrappers must be read as original coefficients times the
declared multiplier in BigInteger, not as already-overflowed long getters.
Validate the known final wrapper and unchanged underlying AE2 input contract;
retain the original wrapper as the execution binding. Verify both input-only
and output-only positive wrapped overflow using the actual optional JAR.

Packaging: keep the unmodified solver binary in Jar-in-Jar, but place its full
corresponding sources beside the installable JAR under `sources/`. They contain
a large upstream regression corpus and must not inflate the runtime archive.
Ship a versioned source notice and audit the separate source bytes as well.

Implementation scope: a pure Java indexed reaction graph, shared DAG demand
propagation, exact integer constraints for coupled producers, and compressed
execution-order/seed accounting. The native entry captures stock and reachable
pattern semantics together before dispatching to AE2's existing pool. There
must be no world/grid/pattern callback or second executor in arithmetic. Pattern
handles stay in the native adapter, outside the pure engine. Immutable metadata
may be reused only with identity/lifecycle ownership; order-dependent callbacks
are evaluated once per captured key, never shared between orders.

Owners: exact-engine graph package owns quantities and planning; native-engine
snapshot/adapter owns AE2 reads and bindings; VmAccounting owns stock visibility
and the public exact result. Existing host/receipt/physical execution owners and
API descriptors are unchanged. This phase does not claim generic wide physical
execution or change persistence. Unsupported semantic capture and solver UNKNOWN
must be explicit errors, not false shortages or implicit legacy fallbacks.

Regression requirements: shared intermediate stock, lot rounding, both outputs
of one recipe, scarce stock shared by alternative inputs, returned containers,
catalyst startup, unseeded cycles, cancellation, concurrent isolated orders,
root/intermediate/bytes beyond long, 3402-bit quantities, no live reads after
capture, and public ABI/saved-job compatibility. Verify real pattern semantics
where fixtures exist, not just synthetic numeric examples. Report exact workload
and timings separately from production acceptance. No deployment/restart/release
is authorized by this implementation request.

Implementation and evidence:
[GRAPH_PLANNER_IMPLEMENTATION_2026-10.md](../design/GRAPH_PLANNER_IMPLEMENTATION_2026-10.md).
The runtime now calls NativeOrderSnapshot -> NativeGraphCalculation ->
ExactGraphPlanner, then the unchanged public accounting/host boundary.
The old CraftingCalculation subclass, live-access monitor, compiler and
bytecode VM are test fixtures only, excluded from the native runtime JAR.
This is a calculation replacement, not a rewrite of AE2/AQE execution.

Covered: immutable T0 input behavior, actual AEProcessingPattern and
AECraftingPattern input/return semantics, lot rounding, shared intermediates,
co-products, scarce alternative resources, catalyst startup, selected cyclic
schedules, long+1 and 3402-bit requests, wider intermediates, byte-only overflow,
captured generation metadata, interruption, independent order snapshots and
unchanged public ABI. At graph.1 there was no shared incremental catalog;
graph.2 adds one for recognized native processing definitions, while opaque
input semantics are still captured afresh per order. Other general limitations:
opaque unbounded remainder-state transitions need an adapter; an unschedulable
selected integer model reports an error rather than enumerating every possible
different cyclic model. Selected-order evidence is not a new physical executor.
Wide completion, cancellation/refund, restart recovery, concurrent physical
orders, real add-on interoperability and the production sub-10-second goal
remain runtime acceptance work, not claims made by these unit tests.

## Historical evidence before graph.1-local

The sections below are retained to preserve failures and measured baselines.
They do not describe the new planner's live-read behavior.

## Production live-read breakdown (2026-10-01)

Status: Implemented locally; runtime measurement pending. On production rc.7-dev.19, order 13 for one
`emextras:supreme_quantum_control_circuit` completed its plan in 56,377 ms:
53,785 ms AE2-world wait, 2,390 ms VM CPU, 824 access intervals and 30,573
live reads. The server also reported multi-second tick delays. Current timing
does not identify which callbacks caused those reads, so changing input
semantics now would be a guess.

Owner: the VM fork's `NativeVmAccess` counts read kinds per request;
`NativeVmPlanningContext` labels existing reads; `NativeVmTiming` reports only
aggregate counts in the existing per-order slow summary. No per-read logging,
new executor, quantity change, cache, concurrency change or server restart in
this diagnostic step. Unknown/custom callback semantics remain live. Then use
the measured dominant kind to specify a separate behavior-preserving change.

Fail-before: the slow summary exposes only `ae2Reads`, with no breakdown.
Fix-after: a test verifies each labeled kind totals the existing `ae2Reads`
without changing callback exclusivity, cancellation or plan accounting.
Both AE2 variant test/build suites and the bundle audit passed for local
dev.20. `NativeVmAccessTest` covers the fail-before summary and verifies
labeled counts preserve the total and one exclusive interval. Production
remains on dev.19 until an explicit restart. This diagnostic candidate does
not claim lower latency.

The upstream rebuild on Oracle Java 17.0.12 crashed inside the JVM C2 compiler
while compiling `VmInstructionExecution.request`; it is not a Minecraft-server
crash or a passed build. A complete rerun on Temurin 17.0.17 passed. Final
upstream candidate:
`build/distributions/2.0.0-rc.7-dev.20/aco2.0.0-rc.7-dev.20_1.20.1.jar`,
SHA-256 `358203CB49AE453FBECA552FF2B1F658ED846982C169D4FEA178FC7D8683BACA`.
- Target: ACO 2.0.0, Forge 1.20.1, upstream AE2 and AE2-UELM
- Related: #217, #208, #215, #190, #206, #164

## Missing-branch demand program invalidation (2026-10-02)

Status: Implemented locally; static verification passed, production timing pending. Production dev.20 logged a 50-item supreme quantum control circuit
simulation at 6,496 ms (6,170 ms AE2 access wait, 281 ms VM CPU, 7,739 live
reads, 1,291 branch trials). A later order for the same output was still
running at 943,745 ms and was not logged with a final requested amount; it is
not evidence that a particular quantity completed or that this cache is its
dominant bottleneck. Stock is already captured once. Dynamic input predicates
and returns must remain live; no blanket memoization of those callbacks.

The VM demand compiler currently recompiles every cached program containing
an unresolved stock-only leaf whenever *any* producer key is discovered. An
unrelated discovery cannot affect the recipe demands, but a program's generation
also controls which aggregate shape is used for conceptual node accounting.
A naive reuse-only change broke three shared DAG work-budget tests. A first
generation-preserving trial still broke two: it treated a newly known *empty*
producer list as unrelated. That is wrong because `stockOnly` means producer
status is unknown; a confirmed empty list permits safe missing-demand aggregation.
A safe trial must advance the unchanged program and its child shapes to the
current generation, while recompiling whenever an unresolved leaf gains a
known producer-list entry, including an empty list. Keep the existing ordered VM execution,
BigInteger quantities, alternative-producer selection, returns, cancellation,
and AE2 callback scheduling unchanged. The owner is the fork's
`VmDemandProgram.Compiler`, not ACO's accounting or physical execution layer.

Fail-before: compiling a program with an unresolved leaf, then discovering an
unrelated producer and asking for the program again rereads its pattern inputs.
Fix-after: the same program is reused with identical quantities; discovering an
empty list for that leaf invalidates it and allows missing aggregation, while a
nonempty producer list incorporates the new subgraph. Test
empty, partial and full stock, byproducts, alternative producers and a wide
BigInteger amount against the ordered VM oracle. Measure work/CPU and live
wait separately on a real 50/100-item order before claiming a user-visible
speedup. This change does not resolve the separate dynamic-return access wait.
Before changing dynamic-return behavior, record a bounded per-order count by
input class and item ID, plus null/non-null results, in the existing slow-order
summary. No per-call log, callback memoization, world access or extra scheduling
is authorized by this diagnostic. Compare the breakdown to the existing
`INPUT_REMAINDER` total and identify the actual repeated source before
classifying another callback as immutable.

Implemented in the fork's `VmDemandProgram.java`, `NativeVmPlanningContext.java`
and `NativeVmTiming.java`, with regressions in ACO's
`VmDemandAggregationTest`, `NativeVmTimingTest`, and
`NativeVmDeclaredInputTest`. The compiler reuses a program after unrelated
discoveries, advances its child generations, and recompiles when an unknown
leaf becomes known even if the ordered producer list is empty. One test covers
100 unrelated additions, confirmed-empty and nonempty producer discoveries;
the existing shared DAG, return, alternative, partial-stock and 3402-bit
oracles remained unchanged. The bounded remainder breakdown records only
actual live callbacks, including null outcomes; it does not cache or bypass
dynamic inputs. The first reuse-only and first generation-preserving trials
failed the shared-DAG work guard and were rejected before packaging.

Upstream AE2 and UELM `build` both passed with full unit tests, regression
manifest and nested VM bundle audit. Local candidates only:
`build/distributions/2.0.0-rc.7-dev.21-local/aco2.0.0-rc.7-dev.21-local_1.20.1.jar`
(SHA-256 `6996DBC60DA7D05FFD8DE185BEC342AF037A5C27C4CB2A33C6CDA739A849D9BE`),
and `build/distributions/2.0.0-rc.7-dev.21-uelm-local/aco2.0.0-rc.7-dev.21-uelm-local_1.20.1.jar`
(SHA-256 `58848E8A2EFC2F09BD4870A6DAB9F1F6B416E8C851DEC3845648E923C6C5AD27`).
The above was the packaging state before the 2026-10-02 runtime trial. The
upstream-AE2 candidate was then installed on both the Arclight server and
`syarukasutime1.2.46` client with matching SHA-256. Both launched and the
player joined. A 50-item `emextras:supreme_quantum_control_circuit` plan was
identified as VM order 37; the last progress line reported 153600 work and
127729 ms elapsed, still running. No completed plan or exact accounting result
was obtained. The player manually closed Minecraft and the server; this trial
is not evidence of a JVM crash. The active JAR on both sides was restored to
dev.20 (SHA-256 `358203CB49AE453FBECA552FF2B1F658ED846982C169D4FEA178FC7D8683BACA`).
Do not run a 100-item acceptance or claim the candidate improves latency.

The follow-up fork change shares immutable per-pattern process metadata only
within a single VM calculation; actual return keys and stock remain live.
The existing 30-second running log now includes a lazy timing snapshot so a
future long plan exposes world wait and VM CPU even if cancelled. Targeted
accounting, repeated-pattern and log-budget tests pass, but neither change is
yet a demonstrated solution for the 50-item slowdown.

The follow-up upstream-AE2 candidate is
`build/distributions/2.0.0-rc.7-dev.22-local/aco2.0.0-rc.7-dev.22-local_1.20.1.jar`
(SHA-256 `3050A4A8F19D7F55E23A9ADFE469F69321B20C2FA33BB4D297F542DDCC958ED2`).
Full unit tests, the regression manifest and nested-bundle audit passed. It
has not been installed on the server or client and has not been timed in game.

## VM-to-physical execution redesign (2026-10-01)

Status: Ready for implementation; full runtime acceptance is pending. The
current `VmCalculatedCraftingPlan` preserves exact totals and pattern counts,
but not the selected input/return decisions or an executable ordering. At
submission, `VmPhysicalPlanBinding` reconstructs a branch with
`SelectedBranchPhysicalPlan`, whose receipt contract admits only fixed-input
crafting-table patterns and an acyclic, one-step-per-pattern DAG. A VM plan for
processing, dynamic inputs, returns or interleaved cycles is therefore not an
executable receipt. Unit test totals cannot establish otherwise.

Ownership: the VM calculates and records exact decisions; AE2 owns ordinary
provider dispatch, machine timing and output observation; ACO owns only the
explicit exact-job reservation/receipt/rollback it actually performs; external
CPU add-ons own their host execution. Keep the public BigInteger ABI and saved
job/receipt compatibility. Never infer selected inputs from totals, credit a
declared output before the machine produces it, or fallback after ownership.

Implementation boundary:

1. Record a compact immutable execution program in the VM result, including
   concrete selected inputs, return/container effects, pattern identity and
   order. It must represent repeated BigInteger work without expanding one
   event per craft, and must exclude failed branch trials. Preserve the current
   numerical accounting as an independent check against the recorded program.
2. Pass that program through the wide-plan sidecar unchanged. The server-thread
   submission boundary revalidates live pattern identity and current stock,
   but must not choose a new branch or recalculate the VM's plan.
3. Keep the existing crafting-table receipt codec for its current jobs. Add a
   separate, versioned processing receipt with explicit dispatched, waiting,
   observed, credited, cancelled and quarantined phases. Use AE2/provider-owned
   dispatch and observed machine outputs, not synthetic result credit.
4. Route only plans whose entire program has a matching physical executor.
   Unsupported cycles or dynamic callbacks remain explicit blockers before
   stock ownership; do not silently reduce them to fixed-input DAGs.

Fail-before acceptance: a fixed processing pattern currently plans but returns
`INCOMPLETE_PLAN` at `SelectedBranchPhysicalPlan.prepare`; a processing pattern
with a return item, a mixed crafting/processing tree and an interleaved cycle
must also be traced separately. The first vertical slice must prove one real
processing order from calculation through submission, provider dispatch,
machine output, exact completion and cancellation/refund. Follow with
Long.MAX_VALUE+1, empty/partial/full stock, restart and concurrent orders.
The creative-control-circuit time target is a later measured gate, not a unit
test assertion or a reason to skip execution checks. No release/deployment
claim follows from this Ready specification.

## Native processing primary-input lookup (2026-10-01)

Status: Implemented in the local VM fork and verified by focused tests on
AE2 15.4.10 and UELM 15.5.0; production latency is unmeasured. The VM
already reads the ordered producer
list for each demanded output. `LiveInput` then asks `getCraftingFor(primary)`
again before trying fuzzy stock, even when the concrete source is exactly AE2's
`AEProcessingPattern` with its exact `Input` and one template. Bytecode checks
for AE2 15.4.10 and UELM 15.5.0 show that this input's `isValid` matches only
its template and `getRemainingKey` is null. A fuzzy candidate cannot become a
different valid key in that case. Skip this redundant producer/fuzzy lookup
and infer exact validity/no return from the pinned native implementation;
retain `canEmitFor`, source identity and all custom/substitution behavior.
Do not infer exactness for subclasses. The real AE2 and UELM input classes
must be compared with the wrapper for different NBT keys and return values.

Fail-before: a native one-template processing input with no producer for its
ingredient calls `getCraftingFor(ingredient)` while constructing its VM wrapper.
Fix-after: no such call, with identical exact quantities for empty, partial
and full stock; a custom/dynamic input still follows its original live path.
This removes one query per qualifying input, not the entire world-wait budget.
Real order latency and server impact remain pending until measured on the same
artifact and workload. This is separate from the wide physical execution gap.

## Order-start stock and pattern catalog (2026-10-01)

Status: Superseded by the demand-local VM correction below. The eager catalog
was implemented locally, but it captures every advertised pattern on the server
thread, including unrelated patterns. It is not AE2's native planning method
and has no demonstrated performance benefit. The preceding per-input change is insufficient: VM traversal still
asks AE2 for each demanded output's producer list and emitter status after the
order has started. Capture accessible BigInteger stock and the requester's
ordered producer lists at order start on the server thread, before the VM task
is submitted. Include all AE2-advertised craftable keys plus the requested
root; preserve each output's producer order, exact native input metadata, and
pattern identities for later binding. Keep the requester's producer service
distinct from the grid's emitter service, matching the pre-snapshot behavior.
During VM arithmetic, producer and emitter lookups use only this request-local
catalog. No global plan cache,
later recapture, stock refresh or fallback to an empty list on capture errors.

Unknown MOD-defined input predicates/returns may depend on arbitrary live
world state; AE2's public pattern interface cannot snapshot their behavior.
Keep those specific callbacks under AE2's exclusive access until an explicit
immutable rule exists. Do not silently assert that this exception is solved by
catalog capture. At submission, live stock/pattern identity must still be
revalidated before ownership transfer; the order-start view is a planning
snapshot, not a reservation or authority to synthesize outputs.

Fail-before: a worker asks `getCraftingFor` and `canEmitFor` while descending a
multi-stage plan. Fix-after: the real calculation path reads stock and catalog
before scheduling and does zero such worker lookups, including missing,
partial-stock and alternative-producer cases. A changed provider after capture
does not alter the in-flight plan; submission remains separately guarded.
Compare quantities and producer order on both AE2 variants. Production latency,
server-thread capture cost and full physical acceptance require live evidence.

Implementation: `NativeVm.calculate` now creates one stock snapshot and one
`NativeVmPlanningContext` on the server before passing the order to AE2's pool.
`captureCatalog` records the requester's ordered producer lists and the grid's
emitter keys separately. VM traversal reads only those request-local maps for
producer/emitter availability. It does not require exact stock for unused
catalog entries. Modified VM fork files: `NativeVm.java`,
`NativeVmCalculation.java`, `NativeVmPlanningContext.java`. The ACO contract
tests in `NativeVmDeclaredInputTest` and `NativeVmLifecycleTest` verify worker
lookup isolation, an unused incomplete-stock entry and the pre-schedule capture.
Both AE2 15.4.10 and UELM 15.5.0 passed `test` and
`verifyIssueRegressionManifest` locally. No Minecraft launch or live timing
was performed. MOD-defined live input predicates and returns remain an explicit
exception; their behavior is not frozen by AE2's public catalog API.

## Demand-local VM correction (2026-10-01)

Status: Implemented and locally verified on both AE2 variants; production
timing and wide processing execution remain unverified. Keep AE2's order lifecycle and one start-of-order accessible
stock snapshot. Remove the eager all-craftables catalog and the extra
pre-schedule capture. A VM request asks AE2 for a producer list only when its
output is first demanded, then retains that ordered list for the rest of the
request. Cache emitter answers by key within that request. The requester grid
owns producer lookup and the calculation grid owns emitter lookup, as before.
Unknown custom input predicates/returns remain under AE2's exclusive interval;
do not reclassify them as immutable or clamp BigInteger values to long.

Fail-before: current order creation enumerates all craftables and materializes
unused pattern inputs before handing work to AE2's pool. Fix-after: zero
`getCraftables` calls per order; an unused malformed/unavailable pattern cannot
block an unrelated plan; repeated demand for one key asks AE2 once, including
empty producer results; same-key emitter checks ask AE2 once. The first lookup
must preserve AE2 producer order. Stock stays fixed during calculation and
submission rechecks actual stock/pattern validity before ownership transfer.
AE2's own `CraftingCalculation` constructor may query the requested root once
while creating its placeholder tree; that native constructor lookup is not an
ACO catalog capture and must be accounted for separately in call-count tests.
No extra worker, retry loop, global cache, or physical execution claim follows
from this change. Validate upstream AE2 and UELM with focused demand tests,
full contract suites, and real order timing before claiming performance.

Implementation: the VM fork's `NativeVm.java` no longer performs an eager
pre-schedule catalog capture; `NativeVmCalculation.java` no longer exposes the
capture-only access method. `NativeVmPlanningContext.java` reads each demanded
ordered producer list once (including an empty answer), each service/key
emitter answer once, and reuses its wrapped pattern for repeated demand. It
does not invoke `getCraftables`. ACO's `NativeVmDeclaredInputTest` and
`NativeVmLifecycleTest` verify the demand/no-enumeration behavior, while
the unchanged AE2 constructor root lookup is accounted for explicitly.
Full `test`, `verifyIssueRegressionManifest`, `build`, and isolated
`runGameTestServer` passed with AE2 15.4.10 and UELM 15.5.0 (16 real GameTests
per variant). The production server still has rc.7-dev.13; no production
server/client JAR was replaced. This does not close the separate
`VmPhysicalPlanBinding` processing receipt or 10-second acceptance gates.

The same order-local boundary now recognizes an AE2 `AEProcessingPattern$Input`
when the containing pattern inherits `AEProcessingPattern`, as AdvancedAE's
`AdvProcessingPattern` does in the installed 1.3.6 JAR. Its inherited input
checks the encoded key and has no container return; its extension controls
input direction, which remains on the original pattern for physical dispatch.
An anonymous subclass fails the zero-live-read test before this change and
passes afterward. Other mod-defined input classes still use live callbacks.
This is a focused callback reduction, not a measured production speedup.

## Dead runtime code cleanup (2026-10-01)

Status: Implemented; started from Ready. Both AE2 variant builds and cleanup/API
checks pass. Local dev.19 only; no deployment, restart or release. An intermittent
historical-fixture cancellation failure is retained in the evidence below.

- Remove SymbolicCraftingPlanner: its only main-source caller clears a cache
  that the facade no longer owns. No production caller invokes its planning
  methods. CompiledRootProgram remains the owner of the actual algorithm.
- Preserve its shared-surplus and independent-byproduct regression cases by
  invoking CompiledRootProgram directly. Do not remove the algorithms used by
  BigCraftingEngineApi or the exact physical execution manager.
- Remove four unused global PatternCompiler collections from the bundled fork:
  COMPILED_PATTERNS, FUZZY_GROUPS, PROCESSING_INPUT_KEYS, EXACT_PROCESSING_KEYS.
  Each is only initialized, never read or written. Keep the request-local State,
  scoped compilation, public compiler methods and pattern invalidation intact.
- Remove unused imports in the audited main source set. No API descriptors,
  saved quantities, receipt formats, config switches or Mixin scheduling change.
- Both deleted ACO classes (including #161's NeoEcoVersionCompatibility) are
  absent from the frozen public ABI and the installed AQE 2.2.7, AdvancedAE 1.3.6,
  ExtendedAE 1.4.15, ExtendedAE Plus 1.5.5 and InsaneAE 1.1.0 class/resource
  references. This audit does not establish compatibility with unknown add-ons.
- Fail-before contract: retired classes must be absent from the runtime
  classpath and the four fields absent from PatternCompiler. Then rerun full
  tests, the unchanged public ABI baseline, both AE2 builds and bundle audit.
  Record removals and verification, without claiming a measured speedup.

Evidence: [dev.19 cleanup](evidence/ISSUE-161-218-DEAD-CODE-2026-10-01.md).
Both full reruns report 831 tests, zero failures/errors/skips. The first upstream
run failed the unchanged test-only catalog cancellation read-count assertion
(18 reads versus <=15); focused and full reruns pass without relaxing that test.
This is a residual test reliability issue, not proven runtime cancellation safety.
The published API baseline and PatternCompiler public descriptors are unchanged.
No current Minecraft/GameTest, production performance or gameplay claim is made.

## Clarified acceptance contract (2026-09-24)

## Request-local native input evaluation (2026-09-30)

Status: Implemented and verified locally on both AE2 variants; production timing
pending. Specified Ready before implementation. User requested both planning and
machine Recipe Intent.
Production still runs dev.13: order 3819 (gt_ev_cleanroom_line x100) took
124823 ms, with 117878 ms world wait and 100369 live reads. Order 3839
(qio_drive_base x200000) took 34253 ms with 33304 ms wait and 23753 reads.
These logs do not identify a per-callback breakdown or prove a quantity bug.

Owner: NativeVmPlanningContext, with a read-only accessor for the recipe actually
owned by AECraftingPattern. Reuse input validation only within one request,
for exact vanilla ShapedRecipe/ShapelessRecipe and exact vanilla Ingredient
classes (the #179 policy). Preserve complete AEKey/NBT identity. Primary native
crafting-key validity is already unconditional in AE2 and needs no world read.
Remainder reuse is narrower: those recipe types plus exact vanilla Item or
BucketItem classes only. Unknown recipes/items and changing custom callbacks
remain live. Do not infer purity from a namespace or superclass. No new worker,
catalog, whole-network scan, quantity limit or submission fallback is allowed.

Before/fix tests: repeated substituted native input queries must acquire at most
one live result per eligible key, including false and null results; custom
recipes/ingredients/items retain live behavior. Test NBT alternatives, containers,
empty/partial/full stock and long-overflow quantities. Keep native scheduling,
API, cancellation and real provider regressions. Production timing remains
PENDING until the newly built artifact is actually installed and measured.

The user's latest direction preserves the public BigInteger API compatibility
for AQE/AAC and exact quantities, reservation, cancellation and recovery. It
does not preserve any particular internal implementation, including API internals.
See PROJECT_CHARTER and TESTING for the authoritative scope and acceptance matrix.

Calculation algorithms, data structures, threads, caches, VM adoption/removal,
Jar-in-Jar packaging and old optimizations are replaceable. The demand-driven VM
change below is a completed experiment, not the required final architecture.
Keep its regression evidence, not an obligation to keep its engine or classes.
Local API bookkeeping optimizations alone do not satisfy this redesign.

Completion requires correct consumption, missing quantities and byproducts for
ordinary and wide orders, fast full plans with sufficient or insufficient stock,
bounded server work under concurrent planning and I/O, and the specified creative
control circuit plan at about 922京 in under 10 seconds. That target remains
unverified. The processing/cycle execution gap remains an implementation gap,
not merely a missing runtime checkbox.

The initial amendment changed requirements only. The subsequent bounded slices
below were specified as Ready before implementation. Production deployment,
restart and publication remain outside this work; the full redesign is not done.

## Reuse declared exact input semantics (2026-09-26)

Status: Implemented and verified locally on both AE2 variants; production latency pending.
Specified Ready before source changes. User approved after the dev.12 measurement.

Production dev.12 order 2, emextras:supreme_quantum_control_circuit x1,
2026-09-26 20:58:52: elapsed 138525 ms, CPU 7671 ms, world-access wait
129864 ms, 2771 intervals and 207036 reads. The plan completed with missing
materials. Subtracting wait gives 8661 ms, not a measured optimized duration.

NativeVmPlanningContext already declares exactKey only for the existing
native fixed-input path, after checking the primary key and absence of a
remainder. PatternCompiler.DetachedInput specifies that non-null exactKey
means this exact key and no return. However, LiveInput still enters live
access for repeated validity/remainder queries, including Process creation
and addReturn. Honor that existing declaration inside the input wrapper:
exact equality is local and the declared exact key has no return. Do not
add another planner, task queue, pattern snapshot or observation cache.

Owner: NativeVmPlanningContext.LiveInput. NativeVmAccess, AE2 scheduling,
stock capture, VM arithmetic, candidate order and CPU/API ownership stay
unchanged. Do not broaden the exact-input classification. Inputs without
that declaration, including returned containers and custom/dynamic inputs,
must retain live callbacks. NBT identity, fluid units, cancellation and
BigInteger quantities must not change. No guessed return values or caching
of arbitrary callback results.

Fail-before tests: repeated reads of real native exact processing and fixed
crafting inputs must not acquire further intervals; a worker must finish
those reads without additional ticks. Exact NBT variants remain rejected.
Dynamic inputs and non-null returns must still reflect live callbacks.
Compare missing/used/craft/byte totals for empty, partial and full stock at
1, 2, Long.MAX_VALUE, MAX+1 and 3402-bit quantities. Exercise the real
mixed-in AE2 lifecycle and both AE2 builds, plus the public API contracts.

Candidate rc.7-dev.13. Build only: no production deployment, restart,
publication or claim that the live circuit is below 10 seconds.

Verification: both AE2 15.4.10 and UELM 15.5.0-uelm pass 807 JUnit/contract
cases (zero failure/error/skip), 14 real Forge GameTests, bundle audit and
automated regression gates. Three new cases failed on dev.12: both tickless
query probes and the operation-count case (8192 repeated live reads -> zero).
The mixed-in AE2 test also finishes declared input queries while the test
server advances no ticks. Initial acquisition still synchronizes. The native
fix changes only the wrapper's use of its existing exact declaration and
retains interruption checkpoints; it introduces no new cache or classification.

Native fork commit: 2b764576d6eb0a6c7884e9294cd6b78f5c70f3e6 (local, not pushed).
Evidence, variant JARs, sources, XML reports and hashes: build/issue218-declared/README.md.
The first full-build invocation failed in Gradle option parsing before tasks;
using explicit environment project properties passed all gates without source
workarounds. No production JAR was changed and no server/client was restarted.

## Continuous arithmetic, exclusive live access (2026-09-26)

Status: Implemented and verified locally on both AE2 variants; live acceptance pending.
Specified Ready before source edits. The user explicitly requests removal of the remaining
tick-budget wait during VM arithmetic. This supersedes the dev.11 requirement
to put the whole calculation in AE2's exclusive simulation slice.

Scope: Forge 1.20.1, both AE2 variants. Keep AE2's pool and TickHandler job
registration. NativeVmCalculation delegates its simulateFor hook to a small
request-local access monitor. When only arithmetic is running, simulateFor
returns immediately. VM checkpoints check cancellation and release an expired
exclusive interval without waiting for another tick. Only a live grid/pattern
callback may wait for AE2 to grant its exclusive interval. Reuse the current
interval for consecutive callbacks; do not enqueue server futures per input.

NativeVmAccess owns this synchronization only, not recipes, quantities, stock,
caches, retries or physical execution. NativeVmPlanningContext must route every
live provider lookup, emitter lookup, input metadata/predicate/remainder through
this access boundary. Do not release an interval in the middle of a nested
callback. NativeVm retains one stock capture and the existing AE2 pool.
Remove the obsolete pause/finish invoker from the shipped mixin configuration.

Invariants: server and live callbacks never run concurrently; pure arithmetic
can finish without further ticks; no second executor, snapshot/revalidation
pipeline, long narrowing or order cutoff. Cancellation/failure/server shutdown
wakes any waiting side, without exposing a partial plan. Exact inventory and
CPU/API ownership are unchanged. Do not claim arbitrary world callbacks can be
run concurrently or that all synchronous world access is free.

Tests: reproduce that dev.11 pauses the entire worker; then test continuous
checkpoints without ticks, nonblocking server polls during pure computation,
exclusive/nested live reads, expired intervals, interruption while waiting,
failure cleanup and concurrent independent orders. Keep the real AE2 GameTest
checking live callbacks while the server is waiting; add a real worker/tick
test proving arithmetic proceeds while the server progresses independently.
Run current quantity oracles, wide capacity/API contracts and both builds.
Candidate rc.7-dev.12. No deployment, restart, push or release in this request.
Production elapsed time and the final creative-circuit target remain unmeasured.

Verification: each variant passes 799 JUnit cases, zero failures/errors/skips,
and 13 real Forge GameTests. Java 17.0.17, Forge 47.4.18, AE2 15.4.10 and
UELM 15.5.0-uelm. Optional bytecode contracts use AQE 2.2.7, ExtendedAE Plus
1.5.5 and NeoECO 20.3.0/20.4.0. These optional JARs are not a combined modpack
runtime test. The first GameTest launch exposed a test-only split Java package;
the probe was moved into the test module's own package before successful reruns.

The mixed-in continuous-arithmetic probe completes 3402-bit work for longer
than a normal slice while the test server advances no ticks. Existing tests
still verify exclusive live callbacks, concurrent orders, stock churn, returns
and wide exact plans. Quantity calculations and physical execution logic are
unchanged. Legacy capture/catalog cases in the JUnit total remain historical.
The removed pause invoker is no longer registered. New diagnostics report
ae2WorldWaitMs, ae2ExclusiveMs, ae2Intervals and ae2Reads without per-call logs.

Candidate: ACO 2.0.0-rc.7-dev.12 with native VM 0.3.3-dev.12. Local native fork
commit: 5e9a53e986a490b86a75d348822e89bd4285b2e5 (not pushed).
Artifacts, exact hashes, XML reports and logs: build/issue218-continuous/README.md.
No production deployment, restart, publication or full 2.0.0 acceptance.

## AE2 lifecycle with BigInteger VM only (2026-09-26)

Status: Implemented and verified locally on both AE2 variants; live acceptance pending.
Specified Ready before editing.
The user explicitly rejects the detached planning/revalidation
pipeline and requests AE2's calculation behavior with VM BigInteger arithmetic.
No production deployment, restart or publication is authorized by this change.

Verified source: AE2 15.4.10 CraftingCalculation.run registers with TickHandler,
handlePausing/ simulateFor alternate the worker and server within AE2's budget,
and finish wakes the server even on interruption. Pattern predicates run in that
exclusive calculation interval. CraftingService supplies CRAFTING_POOL. Stock
is captured once at construction. CPU submission/execution remains AE2-owned.

Owners: NativeVmCalculation uses AE2's existing scheduler, monitor, thread pool
and cancellation lifecycle; NativeVmPlanningContext feeds actual AE2 inputs and
producer order directly into the BigInteger VM. ACO retains exact stock/result
accounting and the public API, not another calculator or pattern snapshot layer.
The scheduling base has a null-source, empty requester and placeholder quantity;
its long tree is never run. Real requested/used/missing/byte quantities always
come from the VM's BigInteger request and single exact stock capture.

Remove NativeVmCapture and NativeVmPatternCatalog from shipped code. Retain them
only as explicitly historical test fixtures, not as a fallback. Remove their
per-input server futures, full adoption revalidation, observation/shape mirrors,
whole-grid prefetch, order recapture/backoff and separate worker pool from the
production route. Keep stock provenance checks, exact branch arithmetic,
interruption and real AE2/AQE reservation/submit checks. No completed plan reuse.
Pattern callbacks may run only within AE2's exclusive calculation interval;
this is not permission to read live worlds concurrently from an arbitrary worker.
AE2 still waits for its time slice; do not claim zero server cost or <10 seconds.

Fail-before/runtime checks: the actual mixed-in CraftingService must use AE2's
simulation lifecycle, with zero per-input server handoffs and no capture/catalog
classes in the installed JAR. Test concurrent jobs, interruption/server stop,
one stock capture, direct predicate/returned-container semantics, requester
boundaries and exact 1/2/MAX/MAX+1/3402-bit plans. Move quantity-oracle harnesses
to the new context. Keep legacy snapshot tests labeled historical, not production
acceptance. Build both AE2 variants and run actual Forge lifecycle GameTests.
The final production stock/quantity/performance matrix remains pending.

Implementation removes the separate pool, shared catalog and capture machinery
from the runtime bundle. ACO's CraftingCalculation constructor capture is also
bypassed for the scheduler-only subclass; a configuration switch cannot silently
start a second planner. Legacy capture/catalog tests run against test-only copies.
The production oracle and capacity harnesses now use NativeVmPlanningContext.
The alternate-only container-return gap remains separate and is not claimed fixed.
AE2's existing 5 ms/tick default budget is unchanged; removing RPCs does not
establish the production latency target. No production files were replaced.

Verification: Java 17.0.17, Forge 47.4.18, AE2 15.4.10 and UELM 15.5.0-uelm.
Each variant passes 788 JUnit tests (including historical fixtures), zero skipped,
12 real Forge GameTests, public API contracts and nested bundle audit. Runtime
tests include a 3402-bit missing-stock order and native pool/monitor ownership.
Evidence and alternative build artifacts: build/issue218-ae2-lifecycle/.
Native fork commit: 8a86640497aea6a4157541ed2188625320922ae9 (local, not pushed).
No production benchmark, deployment, restart, publication or full 2.0.0 acceptance.

## Sub-unit replay guards (2026-09-25)

Status: Implemented and verified locally on upstream AE2 and UELM; live acceptance pending.
Specified Ready before implementation. No deployment, restart or publication.

The user tested dev.9 on production. Order 14 (supreme quantum circuit) was
still running at 275169 ms, followed by a cancellation in the next summary.
There is no completed timing or quantity comparison for this order. The
30-second JFR in build/issue218-live-20260925-2234 samples repeated stock/guard
reads, not the full order or a proof of the complete remaining latency cause.

Confirmed code defect: skipping a candidate whose stock is below one input
unit uses Trace.read, which pins an exact starting quantity. A byproduct can
change that quantity while it remains unusable, needlessly preventing replay.
Owner: VmInstructionExecution only. Record the exact integer interval
0 <= available < unit for this zero-consumption branch. Translate it to the
same before-state coordinates as other guards, retaining nested propagation,
failed-branch observations, key-domain checks, rollback and BigInteger values.
Do not relax the exact guard on partially consumed stock or skip the threshold
at which the preferred candidate becomes usable. Preserve the unoptimized
copying executor as a differential oracle. No callback/thread/API change.

Fail-before: a preferred, gradually accumulating byproduct below a very large
input unit must not force one VM request per craft. Check the crossing at
unit-1, unit and unit+1, small ordered comparisons, missing/full/partial stock,
returned tools, failed alternatives and existing wide-quantity regressions.
No claim of live speedup until the corrected artifact completes that order.

The initial test also exposed a separate existing contract gap: Process decides
whether to collect returned containers from primary keys only. A custom input
with a return on an alternative but none on its primary loses that return.
Do not call that behavior fixed here. The replay fixture uses ordinary consumed
alternatives instead; existing primary-tool/remainder tests remain unchanged.

Local candidate: ACO `2.0.0-rc.7-dev.10`, native VM `0.3.3-dev.10`, local fork
commit `4735aabaf7d19452cdcfbbe7cbc26554df296ef3` (not pushed). The final
consumed-input fixture fails with the old equality guard at 8192 requests;
with the interval guard it takes three requests for 10^12, MAX-1 and MAX, and
four for MAX+1. The exact consumed iron drops by one only after crossing MAX.
189 small stock/threshold combinations match the copying ordered executor,
including exact bytes, missing counts, used stock and pattern counts.
Both upstream AE2 15.4.10 and UELM 15.5.0 pass all 783 tests and 11 Forge
GameTests without skipped tests, on the default Java 17.0.17 test JVM.
Evidence is in `build/issue218-subunit-guards/`. Production latency is not
measured for this candidate. The preceding cancellation cannot establish a
complete comparison or isolate this defect as the full cause of live slowness.

## Replay journals and fixed crafting inputs (2026-09-25)

Status: Implemented and verified locally; production performance pending.
Specified Ready before implementation. User authorized a corrected local build.
No production deployment, restart or publication in this slice.

Live dev.8 order 810 (supreme quantum circuit x1, missing simulation) took
165961 ms, including 116019 ms blocked handoffs and 49937 ms VM wall time
excluding handoffs, not CPU time. INPUTS made 4054 handoffs. The retained
30-second JFR has 281/766 VM samples nearest VmBranchMap.copyInto; it does not
measure the whole order. State copies were 26865. Evidence is in
build/issue218-live-20260925-2023, not a synthetic claim of achieved latency.

Owners: VmInstructionExecution/worker-local maps and NativeVmCapture in the
bundled fork. Replace replay's whole before-state copies with scoped lazy
before-value journals. Writes record a key's original value only once per live
snapshot. Discarded child branches must not modify parent journals or minima;
commits must preserve transient low-water marks even for net-zero stock deltas.
Replay iterates changed keys and extracted minima, not the whole visited stock.
Close journal scopes on failure and success. Preserve ordered guards, nested
replay, exact bytes, returns, byproducts and BigInteger quantities throughout.

Pinned AE2 AECraftingPattern.Input accepts only the primary when canSubstitute
is false. Classify that exact implementation with its exact outer class on the
server. Reuse its captured primary validity/remainder, reject other keys without
a live callback, and revalidate the proof and consulted return before adoption.
Do not classify subclasses/custom callbacks from a one-template list alone.
Unknown/dynamic input callbacks remain server-owned, batched and revalidated.
Do not increase slice limits, speculate over every recipe/input pair, add a
second planner, narrow counts, remove cancellation or change CPU ownership.

Fail-before: replay snapshots must not copy a large already-visited unrelated
stock; real non-substituting AECraftingPattern inputs with thousands of NBT
variants must not add INPUTS handoffs. Cover primary remainders, changed returns,
substitution-enabled/custom patterns and cancellation. Compare journal replay
to copying execution and actual AE2 oracles, including nested failed trials,
net-zero catalysts, fluid/NBT variants and empty/partial/full stock for
1, 2, MAX, MAX+1 and 10^1024-1. Build/test both AE2 variants, preserve any JVM
failure evidence, and keep live latency and the final <10 s goal pending.

Results: ACO `2.0.0-rc.7-dev.9`, native VM `0.3.3-dev.9`, local fork commit
`530ba6c5b64bcff8d04ee02946697db308aaa9b1` (not pushed).

- Both performance regressions fail before the implementation: 64 replay
  checkpoints copy the visited ledger, and fixed crafting scans 2048 wrong NBT
  variants through 83 INPUTS handoffs / 156 total including validation.
- After: the same checkpoints perform zero full state copies. A 10000-key
  journal opens with zero key hashing and records only the two changed keys.
  The fixed crafting fixture performs zero INPUTS / five total handoffs.
- Scoped journal tests cover nested before-values, insertion order, null/zero,
  exact wide values, failed-branch isolation, explicit commits, closed scopes
  and linear flattening of journal-wrapped branch layers.
- Existing copying-mode/ordered-VM comparisons and actual AE2 oracle fixtures
  retain equality for quantities, bytes, byproducts, failed alternatives, tools,
  fluids, cancellation and 3402-bit stock matrices. The two older assertions
  requiring a full copy were replaced with actual branch/replay checks plus
  zero copies; their quantity assertions were not weakened.
- Upstream AE2 15.4.10 and UELM 15.5.0 each pass 781 JUnit tests (no skips)
  and 11 Forge GameTests on the default Java 17.0.17 test JVM. No C1 workaround
  was needed in these runs; earlier dev.8 JVM failures remain historical evidence.
- The new GameTest creates a real fixed AECraftingPattern for iron blocks,
  seeds 18 exact ingots and 48 wrong NBT variants, orders MAX+1, and verifies
  the ninefold BigInteger shortage, zero INPUTS handoffs and unchanged real stock.
  Existing real assembler substitution and container-return completion pass.
- Headless fixed-input JUnit isolates Forge constructor-only facilities;
  it uses the real private AE2 Input methods and server-owned recipe callbacks.
  The GameTest covers the unmodified constructor under Forge transformers.

Evidence/artifacts: `build/issue218-replay-journal/`. Full state copies are not
replaced by cached stock-dependent plans. Dynamic callbacks still use the server;
this is not a claim that all 4054 production handoffs have been eliminated.
No production deployment/restart, live re-order, GitHub push or release. The
supreme-circuit elapsed time and final creative-circuit <10 s goal remain unverified.

## Restore shared-demand aggregation (2026-09-25)

Status: Implemented; upstream automated verification passed, UELM verification
conditional on the test JVM configuration (see results below). Specified Ready
before implementation. User requested a corrected local build; no deployment,
restart, GitHub publication or release is authorized by this request.

The fork replaced upstream f9e0837's shared bundle/demand aggregation with
ordered per-path execution. Even a fixed exact-input DAG can consequently
expand once per path. Trimming replay copies does not address this regression.
Retain the existing AE2 quantity/byte oracles, not this costly execution choice.

Owner: the bundled VM compiler/executor. Restore request-local compiled demand
programs which combine repeated subrecipes and apply exact BigInteger totals.
Build programs only from metadata already demanded by the VM: no new whole-grid
capture, speculative live callbacks, cross-order stock/result cache or second
ACO planner. Preserve the native VM/BigInteger API bridge and AE2 CPU ownership.

Programs aggregate fixed exact inputs, per-pattern quantities, external needs,
net outputs and rational byte cost. Shared subgraphs are memoized instead of
expanded per path. Batch divisibility, intermediate stock and ancestry remain
semantic conditions, not quantity caps or time cutoffs. Byproducts are credited;
when consumed inside a block, ordered semantics remain necessary. Dynamic
alternatives, returned/damaged tools and cycles continue within the same exact
VM until their own effects can be combined without changing results. Never
restore unconditional multiplication of a captured state-dependent result.

Readiness clarification from the first focused test: sufficient raw stock never
resolves a leaf's producer. Such an unqueried input is a stock-only boundary,
not evidence that it has no recipe. A compiled block may use it only while its
entire demand is covered by stock. When it is exhausted, normal demanded
resolution proceeds. Compiler metadata grows monotonically during the order;
compact node shapes must union with later expanded descendants for exact bytes.

The conceptual AE2 tree-node byte charge must also be computed from the compact
program, without materializing an exponential tree. Previously visited nodes
and failed alternatives must remain represented. Requests are not rounded to
long, stopped early, retried due to ordinary stock movement, or sent back to
AE2 because a program cannot be aggregated.

Fail-before test: a shared exact processing DAG must not exceed a bounded test
work budget even for one requested item with empty stock. Test 1, 2, MAX,
MAX+1 and 10^1024-1 with empty/partial/full raw stock; compare counts and bytes
to independent DAG formulas and smaller ordered executions. Cover stocked
intermediates, rounding, byproduct reuse, ancestor-dependent cycles, emitters,
NBT/fluid units, cancellation, and original AE2 oracle cases. Build and audit
both upstream AE2 and UELM artifacts, retaining their separate hashes. Local
fixtures do not establish production latency or the creative-circuit <10 s goal.

Results: ACO `2.0.0-rc.7-dev.8`, native VM `0.3.3-dev.8`, local fork commit
`1758d3415f1e9471305426b23f5cb502b5206ab1`. The fork commit is not pushed.

- Fail-before: the 24/48-stage shared-DAG tests exceed the fixture's 8192-work
  budget. That budget belongs only to the test, not production order handling.
- After: the 24-stage one-item missing plan uses 26 request visits and no state
  copies. An identical 16-stage graph uses 18 visits instead of 4197, with equal
  quantities, per-pattern counts and exact byte requirements. Not wall-clock data.
- Empty/partial/full raw stock at 1, 2, MAX, MAX+1 and `10^1024-1` matches an
  independent DAG formula. A one-item 96-stage order also exceeds long in its
  intermediate demands and conceptual tree-node bytes without narrowing.
- 150 seeded ordered-VM comparisons and 160 actual AE2 processing-pattern oracle
  cases cover mixed stock, batch sizes, shared branches and byproducts. Returned
  tools, cycles, emitters, exact fluid units and cancellation retain coverage.
- Production native-capture fixtures cover the 40-stage stock/quantity matrix
  on the actual asynchronous entry, with no in-VM server handoffs for captured
  fixed processing metadata. Stock-only boundaries never imply absent recipes.
- Default JVM, upstream AE2 15.4.10: 771 unit tests, 10 real AE2 GameTests,
  bundle audit and automated dev-release gates pass, no test skips.
- UELM 15.5.0: all 771 unit tests and 10 GameTests pass with the test-only
  `-XX:TieredStopAtLevel=1` JVM option. Default-JVM verification is NOT green:
  the first run had a save/load/save NBT equality failure; that isolated test
  subsequently passed without changing persistence. Full retries crashed in
  G1 concurrent GC, and a SerialGC trial crashed during C2 compilation of
  `org.mockito.internal.MockitoCore::mock`. Another default-G1 retry reproduced
  the GC crash. The relationship between the equality failure and JVM crashes
  is unknown. Retain every failure log; do not label this a persistence fix.
- Added a first-differing-NBT-path assertion message while retaining the exact
  NBT equality condition. No physical execution code was changed by this slice.
- Existing timing summaries include `aggregatedBlocks`; no new per-step log
  events, public API changes, order limits or production JVM options were added.

Evidence: `build/issue218-demand-aggregation/README.md`, with separate upstream
and conditional UELM artifacts, XML, logs, hashes and retained failures. This
restores fixed shared-demand aggregation, not the entire upstream algorithm for
state-dependent recipes. Ordered execution remains inside the same VM when
stock, alternatives, returns, cycles or internal byproduct reuse require it.
No production JAR was replaced, no server/client was restarted, and no artifact
was published. The production speed target and full industrial wide execution
acceptance remain pending.

## Live-profile replay-copy slice (2026-09-25)

Status: Implemented and verified locally; production acceptance pending.
Specified Ready before implementation. Deployment/restart/publication stay out of scope.
The user approved improving the problems exposed by dev.6 live order 8.
`build/issue218-live-order7-20260925/` records supreme quantum circuit x1:
339097 ms, simulation=true, vmActiveMs=269992, handoffBlockedMs=69098,
79117 state copies and 51679 branch trials. A 30-second JFR contains 1581
VM execution samples; 1045 include State.copy/adopt and 971 include nested
VmBranchMap.entrySet materialization. These inclusive samples overlap.
The older dev.4 observation had different stock/graph/load, so the increased
branch count is not proof of a version-only regression.

Confirmed source work: replay snapshots copy used quantities, fuzzy indexes
and all enclosing low-water maps although replay reads none of these copies.
Nested VmBranchMap.entrySet recursively materializes every parent map.
Replay also copies the entire after-state, despite computing independent
per-key deltas on existing keys. Finishing the last repeat creates an unused
next window. These are worker-only scratch-state problems, not AE2 inventory
or physical crafting ownership changes.

Candidate-batch investigation: scanning ahead calls State.read for sub-unit
keys before knowing whether a preceding candidate already satisfies the input.
This can add unused keys/guards to a replay. First reproduce this with a
changing byproduct and an unused high-unit alternative. If reproduced, defer
those guards until the ordered cursor actually reaches that candidate; retain
guards for all demanded sub-unit candidates and keep bounded server batches.
Do not attribute the entire live regression to this issue without evidence.

Implementation: materialize nested trial layers into one ordered map; keep
branch writes/commit/discard unchanged. Limit before-snapshots to stock,
emitted/missing quantities, pattern counts, byte cost and key-domain token.
Apply after-state deltas without cloning mutable state, preserving independent
minimum snapshots and outer guards. Avoid starting another trace after demand
is satisfied. No new cache, worker, dependency or alternate calculator.

Tests first: count hash work for nested-map snapshots; inspect replay snapshots
for irrelevant copied maps; reproduce unused-sub-unit guards blocking replay.
Retain ordered priority, exact NBT/fluid units, shared byproducts, damaged-tool
returns, failed-branch rollback, peak reservations, cancellation and bytes.
Compare all plan fields against the existing reference modes and AE2 oracle;
exercise empty/partial/full stock at 1/2/MAX/MAX+1 and 3402-bit quantities.
Run both AE2 variants, real GameTests, bundle and regression/release gates.
Record deterministic copied work separately from wall-clock performance. Do
not claim the full creative-circuit target or production improvement from
synthetic evidence. Add no per-step logs; preserve bounded diagnostics.

Write scope: fork VmInstructionExecution/VmBranchMap, ACO VM regression tests,
responsibility/evidence docs and dev.7 bundle/version metadata. Existing dirty
changes remain intact. Charter, regression history, class responsibilities,
workflow, #217/#218 and testing contract reviewed before implementation.

Results: candidate `2.0.0-rc.7-dev.7`, native VM `0.3.3-dev.7`, local fork commit
`24c8648aaf915f332d97b80f997ee11513d0bb6b`. This commit is not pushed; publish
the fork ref before expecting remote CI to fetch the updated pin.

- Before: all four new regression tests fail on the intended performance or
  copied-state assertion, after numerical checks. Existing tests still pass.
- After: 48 nested layers over 256 base keys take 656 hash operations to
  snapshot instead of 13816. This is a deterministic work count, not a timing.
- The returned-seed / unused sub-unit alternative fixture takes 3 request
  visits instead of 2049. The catalyst, byproducts, pattern counts and bytes
  remain exact. This reproduces a guard bug, not the whole live recipe graph.
- A completed 3402-bit replay needs 2 before-state copies instead of 4 total
  state copies; it no longer clones the after-state or creates a finished window.
- Before-snapshots no longer contain other traces' low-water maps, fuzzy indexes
  or used-item maps. Live minimum and rollback maps remain intact and snapshots
  remain independent of subsequent writes.
- Added a 1/2/MAX/MAX+1/10^1024-1 catalyst stock matrix with absent/one/full seed
  supply, comparing all plan fields with the reference mode and exact formulas.
- Both upstream AE2 15.4.10 and UELM 15.5.0 pass 757 unit tests (no failures or
  skips), 10 actual AE2 GameTests, bundle audit and automated release gates.
  Strict runtime acceptance still rejects #218. Optional add-on checks are
  bytecode/isolated contract tests, not a launched combined industrial pack.
- Evidence: `build/issue218-replay-copy/README.md`. Public API descriptors,
  captured stock, receipt/reservation ownership, physical execution and logging
  policy are unchanged. Production JARs, client and server were not modified.

Remaining: remeasure the same production item/quantity on this candidate, with
stock and graph conditions recorded. The dev.6 live branch-count increase is
not fully explained by the small reproduced case. Server INPUTS waits, generic
wide execution/lifecycle and the full creative-circuit <10 s target remain
unverified; no percentage speedup for the production order is claimed.

## Ordered input batch slice (2026-09-25)

Status: Implemented and verified locally; production acceptance pending.
Specified Ready before implementation. The user approved improvement after live order 14 on dev.4.
The supreme quantum control circuit x1 missing plan took 101528 ms: 95806 ms
server queue wait, including 3407 INPUTS handoffs / 91004 ms input queue wait.
Two live stacks stopped in NativeVmCapture.prepareQueries/callServer.
Evidence: `build/issue218-live-order14-20260925/`. This was not dev.5, and stock,
pattern graph and concurrent load were not fixed against earlier orders.

Source finding: VmInstructionExecution starts a new observation batch for every
input template, even when each family contains only one stocked candidate.
FrozenInput caches per slot/key but cannot merge these separate handoffs. A
large substitution set can therefore wait once per template instead of once
per bounded batch. Keep the existing VM and BigInteger arithmetic; no ACO graph,
new global cache, extra worker pool or asynchronous world access.

Implementation scope: the native VM's ordered input candidate loop and
request-local observation/validation bookkeeping. Flatten only the current
input's template/fuzzy-candidate traversal into batches of at most 128 scanned
candidates. Preserve template priority, fuzzy ordering, per-template units,
sub-unit replay guards, current virtual stock, delayed returns and rollback.
Never enumerate future damaged returns or all slot/stock combinations.

Batch observation may now see a later template that the chosen earlier input
does not need. Defer per-candidate RuntimeException outcomes until that query
is actually read; do not suppress demanded failures. Cancellation, epoch changes,
fatal errors and resource-bound failures stay immediate. Only actually consulted
input observations are validation dependencies; native exact-key primary proofs
must still be included. Keep callbacks server-owned and the 128-check / 2 ms
slice, with cancellation inside the batch. Do not narrow any exact quantities.

Write scope: native VmInstructionExecution/NativeVmCapture, regression tests,
an isolated GameTest recipe/plot, responsibility/evidence docs, native bundle
metadata and ACO rc.7-dev.6 metadata. Actual AECraftingPattern constructor and
substitution behavior are exercised in GameTest with Forge access transformers;
the headless callback-count fixture uses an IPatternDetails implementation.
Review amendment (Ready before code): preserve the zero-input-handoff case when
already observed candidates satisfy the order. Add a readiness query to the
fork-internal PatternCompiler.DetachedInput contract (not the public ACO API).
Consume a cached ordered prefix before requesting the remaining bounded batch;
never skip candidate priority to prefer a cached later alternative. Include a
full-stock primary fixture proving no speculative input callback/handoff and
force an uncached earlier candidate in speculative-failure tests.
Verification amendment (Ready before test-only correction): the full suite
exposed an existing order-sensitive NBT assertion in SelectedBranchPhysicalPlanTest.
Its taskTotals map equality passes, but save/load/save lists differ only by the
enumeration order of the same three pattern counts. readPatternCounts returns
Map.copyOf, which does not promise list order. Normalize only that count list
for the NBT assertion, retaining comparison of every entry/field and the ordered
physical steps. Do not change production persistence or the planner for this.
Before implementation, reproduce many single-candidate templates causing many
handoffs. Tests must cover an actual AECraftingPattern, ordered variable-unit
item/fluid alternatives, NBT distinctions, empty/partial/full stock, 1/2/MAX/
MAX+1/3402-bit quantities, unused versus demanded callback failures, invalidation,
bounded work and cancellation. Run the existing AE2 differential/replay suite,
both AE2 variants, GameTests and bundle/release gates. Record operation counts
separately from production speed; production deployment/restart stays out of
scope and full 2.0.0 runtime acceptance remains pending.

Results: local candidate `2.0.0-rc.7-dev.6`, native VM `0.3.3-dev.6`, fork commit
`11fe95385222eec95a0c9db05a0f9196703a3b1d`. VM commits are local only; the pinned
fork ref must be pushed before publishing/running remote CI. No ACO public API,
stock ownership, physical executor, logging policy or production configuration
changed in this slice. The previous dev.5 capacity correction remains included.

- Before: 6 regression cases, 2 expected batching failures. The 384-template
  fixture used 383 INPUTS handoffs / 399 total handoffs. Variable-unit missing
  stock used 259 INPUTS handoffs. Numerical assertions passed before each
  handoff assertion failed; this was not a compile/bootstrap failure.
- After: 12 batch cases pass, including an already captured primary satisfying
  a wide order without any INPUTS handoff. The 384-template fixture uses 3
  INPUTS handoffs / 19 total. The variable-unit matrix uses at most 3 INPUTS
  handoffs, with 1/2/MAX/MAX+1/10^1024-1 orders and empty/partial/full stock.
- Both upstream AE2 15.4.10 and UELM 15.5.0 pass 752 tests, zero skipped, and
  10 GameTests. Actual AECraftingPattern alternatives execute in a molecular
  assembler and consume all four finite ingredients. Existing cancellation,
  competing reservations, returned containers and wide API tests remain green.
- Build, Jar-in-Jar verification, issue manifest and automated release gate pass.
  `requireRuntimeAcceptance` still rejects release for pending #218 production
  acceptance. No production speedup or 10-second target is inferred from these
  synthetic callback counts or isolated GameTests.
- Saved tests, before failures, JARs, hashes and commands:
  `build/issue218-template-batch/README.md`. Production remains PID 27068 on
  upstream AE2 15.4.10 and the unchanged ACO dev.4 JAR. No restart or deployment.

## AQE default-capacity contract slice (2026-09-25)

Status: Implemented and verified locally; production acceptance pending.
Specified Ready before implementation. The user selected the BigInteger Quantum Core's
`10^1024 - 1 bytes` (3402 bits) as the capacity acceptance baseline.
This is CPU byte capacity, not a fixed maximum order count. Keep the original
creative-circuit performance target; it is not replaced by ordering this many
items or by passing a small synthetic graph.

Evidence: production `logs/latest.log` at 00:07:07 and 00:07:08 reports that
capacity with backend `aco:big_crafting_v3`. Installed AQE 2.2.7 bytecode declares
1024 default digits, 16384 maximum effective digits and 12 structure headroom
digits. Production ACO `maximumBits=54427` already represents 3402-bit values.
No production config, CPU block or capacity increase is required.

Before this fix the VM adapter's `maximumCount()` advertised `2^maximumBits - 1`,
whereas the public API additionally enforces the exact decimal implementation
limit `10^16384 - 1`. Use the existing public API limit as the common authority.
Do not shrink all quantities to 3402 bits or encode AQE constants into the VM:
combined CPU capacity and intermediate quantities are separate concerns.

Owners: NativeVm performs calculation; VmBigIntegerAccounting exposes limits
and bridges completed quantities; public BigCraftingEngineApi/HostRuntime own
exact plan views and capacity reservations. AQE owns physical execution and its
current capacity. No new calculator, capacity cache or reservation mechanism.

Write scope: VmBigIntegerAccounting.maximumCount, native capacity integration
tests, existing AQE capacity/codec tests, requirements/evidence and dev.5 metadata.
First demonstrate a failing VM/public maximum equality test. Exercise the real
NativeVm.calculate async entry, server-owned capture, real AE2 processing
patterns and the production result adapter at 1/2/MAX/MAX+1 and near the selected
capacity with empty/partial/full stock. Independently check bytes, consumed and
missing counts, shared byproducts and pattern times. A plan exceeding a CPU's
capacity must finish calculating exactly, then fail capacity admission without
clamping. Cover full capacity, +1-byte rejection, NBT restore, release and status
packet roundtrip, including byte-only overflow with long-sized quantities.

These tests use controlled grid/stock fixtures, not a production world or an
AQE machine executing the full industrial graph. Preserve existing API ABI,
reservation ownership and resource bounds. Run both AE2 variants and GameTests;
keep runtime acceptance pending. No production deployment, restart or release.

### Capacity-slice results

Local candidate: `2.0.0-rc.7-dev.5`. The native VM remains `0.3.3-dev.4`,
revision `60df470f440993a26b9bb38f3bccdfc25243f784`. No native fork change was
needed. The only production-code change in this slice delegates the advertised
maximum to `BigCraftingEngineApi.maximumSupportedAmount()`. No API descriptor,
AQE capacity, configuration, numeric representation or physical executor changed.

The initial focused run had 11 tests, with only the VM/public-limit equality
test failing. All 11 pass after the fix. The new async-VM fixtures verify:

- Orders 1, 2, MAX, MAX+1 and values immediately below/above capacity admission,
  each with empty, partial and full stock; exact consumed/missing quantities,
  shared byproduct reuse and pattern counts match an independent formula.
- For the two-pattern fixture, exact bytes are `9 * order + 32`. The largest
  fitting order uses `capacity - 4`; the next uses `capacity + 5`. An over-capacity
  plan still computes correctly, without pretending sufficient stock implies
  sufficient CPU capacity. This is a controlled graph, not the industrial target.
- The public host reserves the fitting plan and remaining 4 bytes, rejects
  another byte without state mutation, and preserves exact reservations across
  NBT restore and a legacy long-sized reservation projection. Release is exact
  and idempotent. This is API lifecycle coverage, not physical job cancellation.
- Byte-only overflow reaches the exact bridge even when every quantity fits
  long. The status codec round-trips 3402-bit capacity/amounts without truncation.

| Runtime target | JUnit | Real AE2 GameTests | Bundle audit |
| --- | --- | --- | --- |
| AE2 15.4.10 | 740 passed, 0 skipped, 0 failed | 9 passed | passed |
| UELM 15.5.0-uelm | 740 passed, 0 skipped, 0 failed | 9 passed | passed |

Java 17.0.17 / Forge 47.4.18. Optional bytecode contracts used actual AQE 2.2.7,
ExtendedAE Plus 1.5.5 and NeoECO 20.3.0/20.4.0 JARs, not a combined running
modpack. The issue manifest and ordinary development release gate pass; full
production acceptance remains pending. The explicit
`verifyIssueRegressionReleaseReadiness -PrequireRuntimeAcceptance -x test`
gate correctly rejects pending #218, as recorded in
`build/issue218-aqe-capacity/runtime-gate-expected-failure.log`.

Evidence and alternate build artifacts:

- `build/issue218-aqe-capacity/before/`: failing equality regression and XML.
- `build/issue218-aqe-capacity/upstream/`: unit XML, GameTest XML, build log and
  `aco2.0.0-rc.7-dev.5_1.20.1.jar`.
  SHA-256: `04AE7AF6BEE1E9E66ED4CFB4407A5491ECE7074A89CF8CA2F056A8AC6FC0B5CA`.
- `build/issue218-aqe-capacity/uelm/`: alternate-target evidence and JAR.
  SHA-256: `BEF2B9111ACF2E6EF2239117FF8126D4F502EDB09B48147A162FA384CF103701`.

Install at most one of these alternative outer JARs. Production already had
dev.4 installed when inspected; this work neither deployed dev.5 nor restarted
the server/client. No new production speed, industrial completion, cancellation
or recovery claim follows from these capacity-boundary tests.

## Missing-stock work reduction slice (2026-09-24)

Status: Implemented and verified locally; production acceptance pending.
Specified Ready before implementation. The user approved optimization after the production dev.3 run.
Order 7, supreme quantum control circuit x1, finished a missing plan in 24807 ms:
18490 ms queued on the server, 2290 handoffs, 5958 ms VM wall time excluding
handoff waits, 5910 state copies and 3336 branch trials. The installed JAR hash
matches the retained upstream dev.3 artifact. Evidence is retained under
`build/issue218-live-missing-20260924/`. Idle post-completion stack samples do
not identify the dominant callback or add-on pattern type.

Source findings and scoped changes:

- The generic fuzzy-input loop prepares live validity/return observations
  before reading available quantity. Skip candidates with less than one input
  unit, recording the quantity comparison in enclosing replay guards. Check
  current request-local stock, not initial stock: produced byproducts and returned
  variants must become usable later. Preserve fuzzy order and per-template units.
- A process that can satisfy the remaining demand with one execution cannot
  use a local repeated-block shortcut. Avoid only that local replay snapshot;
  preserve alternative-producer transactions, rollback and enclosing guards.
- Frozen inputs backed by an actual native AEProcessingPattern Input retain
  exact-key/no-return semantics even inside another pattern wrapper. Recognize
  those inputs without trusting an unknown outer pattern's shape or callbacks.
  Unknown shapes remain server-captured and revalidated. Do not classify arbitrary
  one-template inputs or wrappers with their own callbacks as native.
- Add bounded per-order server handoff categories/counts/queue times and captured
  pattern-class counts to the existing diagnostics, not per-call logging. This
  identifies remaining producer/observation/validation waits without changing
  log frequency. Do not guess which deployed wrapper dominates the sample.

Owners/write scope: fork VmInstructionExecution, NativeVmCapture, NativeVmTiming,
NativeVmPatternCatalog call-site attribution, NativeVm finalization attribution,
native tests, issue/responsibility/regression documents and dev.4 build metadata.
VM BigInteger arithmetic, candidate priority, exact bytes, API descriptors,
physical execution and production installation stay unchanged. No new global
plan cache or worker-side world/provider access. More aggressive add-on adapters
and multi-input prefetch require measured demand and separate semantic proof.

Tests first: unused zero/sub-unit NBT candidates must trigger no extra live
callbacks; consumed then replenished variants must remain selectable; repeated
missing simulations, wrong NBT, fluids, byproducts, returned tools and alternative
producers must match unoptimized/AE2 quantities and bytes through MAX+1 and 10^64.
One-shot failed/successful branches must keep rollback with zero local replay
copies, including under an enclosing wide replay. Cancellation must still stop
long candidate scans. Verify diagnostic categories and bounds. Run both AE2
variant suites, real GameTests and bundle/API gates. No production latency claim
until the same installed workload is measured again; no deployment or restart.

Local verification (rc.7-dev.4, native VM 0.3.3-dev.4):

- Before the implementation, three new regressions failed: unusable variants
  caused 2048 predicate calls including validation and 69 handoffs; a wrapper
  containing native exact inputs caused 91 handoffs; a missing one-shot branch
  made 4 replay state copies. The enclosing-wide-replay baseline passed.
- Afterward the same empty/sub-unit fixture makes 2 predicate calls and 5-7
  handoffs, the exact-input wrapper 5 handoffs, and one-shot branches 0 replay
  copies while retaining rollback trials. Server slice timing can change the
  number of handoffs; these counts are fixtures, not the production graph.
- New tests also cover supplied variant stock, source input-implementation
  replacement, byproduct replenishment of skipped stock, and exact wide parent
  replay/producer priority. Existing fluid/NBT/return/AE2 oracle tests pass.
- Upstream AE2 15.4.10 and UELM 15.5.0 each pass 735 unit tests (no skips),
  9 real AE2 GameTests, build, bundle and regression manifest gates. Real AQE,
  ExtendedAE Plus and Neo ECO JARs were supplied to the optional contract tests;
  those contract tests are not full industrial-world integration acceptance.
- Evidence: `build/issue218-missing-work/`, including before-failure XML and
  separate upstream/UELM logs, XML and JARs. Native fork revision:
  `60df470f440993a26b9bb38f3bccdfc25243f784` (local only). The local CI pin follows
  that revision; publish the fork revision before any later remote CI run.
- The rc.7-dev.4 automated release-scope gate passes. Its strict
  `-PrequireRuntimeAcceptance` form correctly rejects pending #218 runtime
  acceptance; that expected failure is retained, not converted to PASS.
- No production JAR changes, server restart, GitHub push or release. Remaining
  dynamic wrapper waits need the new per-order category/type report. The 24.807 s
  production baseline and final creative-circuit <10 s target are NOT improved
  timing claims until measured with the installed replacement.

## Shared pattern snapshot slice (2026-09-24)

Status: Implemented and verified locally; production acceptance pending.
Specified Ready before implementation. The user approved retaining VM arithmetic and improving its data
boundary, not changing physical crafting. The current request-local processing
catalog still repeats producer/shape reads for each order and makes worker/server
round trips along unchanged chains. Inventory already belongs to one request.

Keep the ordered BigInteger VM/compiler, public API and all execution/reservation
owners unchanged. Add a bounded, server-owned immutable metadata snapshot for
the hooked native CraftingService. Reuse it only for the same grid, server,
provider epoch and recipe epoch. Acquire a fixed version for each calculation;
never share virtual stock, observations, plans or craftability results. Build a
new version in cancellation-aware 2 ms / 32-entry slices. Limits bound retained
grids and metadata; oversized/unknown services retain the existing demand path,
not a different calculator or a partial snapshot. Clear retained data on server
stop and recipe reload, and invalidate on provider add, removal and refresh.

Only native AEProcessingPattern exact/no-return semantics can become worker-only
metadata. Preserve priority, NBT identity, fluids, all outputs and emitters.
Custom pattern callbacks/return predicates remain server-owned and order-local;
never evaluate them merely because an unrelated pattern exists in the catalog.
Do not walk dependency trees to build metadata or compile unused patterns.
On an unchanged epoch native metadata needs only a generation/requester check;
custom observations retain final validation. Do not publish interrupted/partial
builds. Changes during capture invalidate it instead of mixing versions.

Owners: NativeVmPatternCatalog (fork) owns only bounded immutable metadata and
its lifecycle; NativeVmCapture owns request stock, demanded compilation and
dynamic input observations. NativeCraftingServiceMixin only signals provider
changes. No ACO graph adapter, worker-side world reads, inventory polling or
physical preflight is added to calculation.

Write scope: these native classes, NativeVm lifecycle, new capture regression
tests, the real-AE2 pattern refresh GameTest, responsibility/acceptance documents
and local development metadata.
Before changing production code, demonstrate failing tests for zero in-VM
handoffs on native chains, cross-order metadata reuse, changing stock, priority,
add/remove/recipe invalidation, cancellation and snapshot isolation. Cover
1/2/MAX/MAX+1/9220000000000000000/10^64 with empty/partial/full stock and retain
the custom/NBT/container/byproduct oracle tests. Run upstream and UELM unit,
GameTest and bundle gates. Production latency and the <10 s target stay pending.
No production deployment, restart, GitHub publication or stable release.

## Previous implementation slice: bounded processing catalog

The current cold request resolves one producer key per server handoff. A deep
native processing chain therefore crosses the server boundary at every level,
even though its encoded inputs have exact-key, no-return semantics. Unit tests
currently cover only a one-pattern handoff count. Production logs do not split
the reported 101451 ms into wait and execution time, so this is not attribution
of that entire delay.

- NativeVmCapture owns one request-local catalog. Within the existing 2 ms,
  32-pattern and 128-observation slice, look ahead through single-producer native
  AEProcessingPattern chains only. Stop at stocked intermediates, emitters,
  alternate producers and custom pattern implementations. Do not enumerate an
  unrelated network or share inventory-dependent results across requests.
- Lookahead is metadata only: preserve original VM execution order and arithmetic.
  Actual demanded producers/inputs remain the validation boundary. Unused
  prefetched branches cannot add plan quantities, missing items, or reservations.
  Incomplete stock proofs must not trigger speculative traversal.
- Keep live calls on the server and cancellation checked inside each slice.
  Frozen metadata is worker-readable only after the server handoff completes.
- Tests first: multi-level native processing chains with 1, 2, MAX, MAX+1 and
  922京 requests, all stock cases, exact independently derived leaf quantities,
  bounded handoffs/callback work, stock barriers, alternative/custom boundaries,
  unused byproducts, changed demanded patterns and simultaneous isolated orders.
- Retain public API ABI, AE2 oracle, container/NBT, cancellation and recovery tests.
  This slice does not remove the industrial physical-execution release blocker.

Write scope: VM fork NativeVmCapture and its native capture tests in ACO; issue,
responsibility and acceptance evidence documents. No external API descriptors,
persisted receipt format or production installation changes in this slice.

## Current execution-budget correction

ExactVectorGridTickBudget currently exempts requests of up to 64 stages from the
elapsed-time gate, even after another job consumed the grid's time budget. Many
small jobs can therefore keep getting whole execution ranges after the budget.
Keep one initial operation for liveness, but apply the same elapsed-time gate to
every subsequent claim regardless of tree size. Quantity accounting, receipt
ownership and saved data are unchanged. Add a failing small-job regression and
retain capacity/settlement tests. This bounds admission, not the duration of one
third-party callback or the entire server. Physical per-operation preemption and
live combined-load measurement remain separate requirements.

Additional write scope: ExactVectorGridTickBudget and its unit tests.

Extend this correction to the existing physical scheduler, without changing
receipt contents or worker ownership. The grid budget owns a remaining-time
predicate, supplied by both callers to PhysicalCraftingTreeTransaction. Keep
the existing tick descriptor for compatibility. Check between recipe advances
and cancellation operations, permitting the first operation for liveness. A
yield must preserve unvisited cancellation candidates and all pending receipts.
Never interrupt the middle of a storage mutation or worker acknowledgement.
Test completion and cancellation with a forced yield after each operation,
reloading the persisted transaction after every tick. The old unbounded
validation/preflight and individual third-party callbacks are not solved by
this cooperative scheduler change.

Returned unused stage reservations must not renew the first-operation time
exception. Storage reconciliation and dependency waits still consume server
work even when lastConsumedOperations is zero. Remember a positive admission
for the tick separately from outstanding/consumed stage slots, and test a fully
returned claim against an exhausted time budget.

Additional write scope: PhysicalCraftingTreeTransaction,
BigCraftingPhysicalExecution, Ae2BigCraftingExecutionManager and lifecycle tests.
Local candidate metadata may advance to rc.7-dev.2 / native 0.3.3-dev.2 so old
artifacts are not overwritten. These are not stable-release declarations.
Record the tested native changes in a local fork commit and update the workflow
pin to that exact revision. Source publication and remote CI remain pending;
do not push either repository or publish a release in this implementation slice.
Keep responsibility-generator descriptions and regression evidence in sync.

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

## Earlier demand-driven implementation scope (partial, not an architecture lock)

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

## Current dev.3 results (2026-09-24)

ACO candidate: `2.0.0-rc.7-dev.3`. Native fork:
`f56130cad95fd9eb3f107bc9601cd6fadc65cff5`, version `0.3.3-dev.3`.
The workflow pins the local fork commit; neither repository has been pushed.
Production files, installed JARs and server/client processes were not changed.

Only the VM data boundary changed in this slice. The ordered compiler/VM
arithmetic, public BigInteger API and physical execution owners are unchanged.

- Shared metadata is immutable, revisioned and bounded; each request still
  takes its own stock snapshot. Live stock movement cannot invalidate it.
- Native exact processing metadata is worker-readable; custom callbacks retain
  server ownership and final validation. Unused custom patterns are not queried.
- Provider add/refresh/remove and recipe reload invalidate the cache. Cancelled
  or changed-generation construction never publishes partial data. Server stop
  clears retained grids, and exceeding limits retains the bounded demand route.
- The 96-stage native fixture reduced server handoffs *during VM execution*
  from 5 to 0. The new cold capture used 10 handoffs and final validation used 1.
  These are separate phases: zero in-VM handoffs is not zero server work, and
  is not a production latency claim. Initial full metadata setup remains a cost
  to measure, especially on large networks; whole-request cold speed is unproven.
- All 16 new shared-catalog tests pass. The exact stock matrix covers
  1, 2, MAX, MAX+1, 9220000000000000000 and 10^64 under empty/partial/full stock.
  Coverage includes priority, NBT, fluid units, byproducts, emitters, concurrent
  order isolation, malformed/unknown patterns, cancellation and bounded retention.
- The new actual-AE2 GameTest warms a plan, changes input quantity 1 to 2,
  removes the pattern and re-adds it at quantity 3. Plans follow each change
  without stock mutation; existing completion/reservation/cancellation tests pass.

| Runtime target | JUnit | Real AE2 GameTests | Bundle audit |
| --- | --- | --- | --- |
| AE2 15.4.10 | 728 passed, 0 skipped, 0 failed | 9 passed | passed |
| UELM 15.5.0-uelm | 728 passed, 0 skipped, 0 failed | 9 passed | passed |

Java 17.0.17, Forge 47.4.18. Optional contracts use actual AQE 2.2.7,
ExtendedAE Plus 1.5.5 and NeoECO 20.3.0/20.4.0 JARs. These are isolated contract
tests, not a combined production modpack launch. The shared-catalog test failed
three performance assertions before implementation; quantity assertions passed.
Fail-before evidence: `build/issue218-shared-catalog-before.xml` and
`build-218-shared-before.log`.

Retained evidence and development artifacts:

- `build/issue218-shared-catalog/upstream/`: JUnit XML, GameTest XML/debug log,
  gate logs and `aco2.0.0-rc.7-dev.3_1.20.1.jar`.
  SHA-256: `CF185E7527491AF39B9ADF83CB7B780DC7911CB0AF3E9CFE5A61A8295C6F4003`.
- `build/issue218-shared-catalog/uelm/`: alternate-target evidence and JAR.
  SHA-256: `5662594B56DEA8B69A89B0B6EFED2CD1527FD2628C1EE07EA3BCEBC348BE9FE0`.
- `build/issue218-shared-catalog/aco-vm-native-0.3.3-dev.3-sources.jar`.
- `build/issue218-shared-catalog/runtime-gate.log`: the explicit
  `-PrequireRuntimeAcceptance -x test` gate correctly rejects pending #218
  production acceptance. Ordinary development build/manifest/bundle gates pass.

The two outer JARs are alternative builds, not two mods to install together.
Custom dynamic callbacks still need live server capture. Cold setup, full
creative-circuit latency, combined server load and wide industrial physical
execution/recovery remain uncompleted. The earlier execution restrictions below
still block a stable 2.0.0 release. This slice does not claim the final <10 s goal.

## Previous dev.2 results (2026-09-24)

ACO working tree: `redesign/218-api-planning`, local candidate
`2.0.0-rc.7-dev.2`. Native fork: `dcddf5c0f6b4440d83207101d5f0178acf363f2d`,
version `0.3.3-dev.2`. The workflow pins this revision, but it has not been pushed.
No production files, installed JARs, server processes or client processes changed.

Implemented and tested:

- Native exact processing lookahead is bounded by the existing server slice.
  A 96-stage fixture fell from 112 server handoffs including validation to 19.
  This is not an elapsed-time or production speedup claim.
- Exact leaf usage/missing counts and per-pattern counts match an independent
  arithmetic oracle for 1, 2, MAX, MAX+1, 9220000000000000000 and 10^64 orders,
  with empty, partial and full stock. Concurrent request catalogs stay isolated.
- Stocked intermediates, custom patterns and incomplete stock proofs stop
  speculative traversal. Unused byproduct branches do not affect adoption checks.
  Demanded branches are revalidated, cancellation stays latched, and failed
  capture cannot publish a partial multi-output pattern.
- Small physical jobs no longer bypass shared elapsed-time admission. Refunding
  unused slots does not renew the first-operation allowance. Both physical
  callers yield between atomic steps and cancellation work; unvisited receipts
  are requeued. Forced-yield lifecycle tests save/reload after every tick and
  verify either exact completion or exact cancellation without double credit.
- Existing public BigInteger API descriptors and receipt schemas are unchanged.
  Legacy tick callers retain their descriptor; the new cooperative overload is
  used by ACO's two production callers.

| Runtime target | JUnit | Real AE2 GameTests | Bundle audit |
| --- | --- | --- | --- |
| AE2 15.4.10 | 712 passed, 0 skipped, 0 failed | 8 passed | passed |
| UELM 15.5.0-uelm | 712 passed, 0 skipped, 0 failed | 8 passed | passed |

Java 17.0.17 / Forge 47.4.18. Optional bytecode contracts used AQE 2.2.7,
ExtendedAE Plus 1.5.5 and NeoECO 20.3.0/20.4.0. They were not a launched
combined modpack. Industrial-capture audit tooling: 10 tests passed.
`verifyIssueRegressionReleaseReadiness -PrequireRuntimeAcceptance -x test`
fails as intended on pending #217/#218; no stable-release gate was bypassed.

Evidence and separately retained build artifacts:

- `build/acceptance-218-dev.2/upstream/`: unit XML, GameTest XML/logs, sources,
  and `aco2.0.0-rc.7-dev.2_1.20.1.jar`.
  SHA-256: `1E47DC1DF955DF13B5E0B7E29532FFBBA08CF222872A99584951FE26C6387190`.
- `build/acceptance-218-dev.2/uelm/`: the same evidence for the alternate compile.
  SHA-256: `A90FC0106B1901F8DB40DA4BEBD5BC4AC01FD88701682E97E74594B047D44C7B`.
- Fail-before XML: `build/issue218-processing-catalog-before.xml`,
  `build/issue218-capture-atomicity-before.xml`,
  `build/issue218-shared-budget-before.xml`,
  `build/issue218-refunded-time-budget-before.xml`.
- `build/acceptance-218-dev.2/runtime-gate.log`: expected release rejection.

These artifacts are alternatives, never install both. Generic processing,
dynamic/cyclic wide execution, production CPU lifecycle, preflight/validation
server-work bounds and the full creative-circuit <10 s goal remain uncompleted.
The receipt executor restrictions below are still code-level blockers.

## Earlier dev.1 results

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
