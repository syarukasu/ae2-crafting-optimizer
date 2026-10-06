# Testing

## Runtime Packaging Cleanup (vm-refs.5-local)

`RetiredRuntimeCodeTest` inspects the actual exact VM JAR for graph/SMT classes
and the compiled native entry for the retired provider-cache hook. The library
exclusion failed against vm-refs.4 before cleanup. Historical graph quantity
tests still run through separate fixture outputs, not through a runtime solver.

`verifyExactVmBundle` requires exactly two nested runtime artifacts, matching
reobfuscated bytes and licenses, and exactly their matching source archives.
It rejects the old graph catalog, graph/solver classes, extra Jar-in-Jar entries,
and comparison artifacts in runtimeClasspath. Run tests and this gate on both
AE2 variants; compare retained class bytes against vm-refs.4, and measure actual
archive size. No size target permits deleting a public API or live entry point.
This packaging check is not startup, GC-crash or production-planning acceptance.

## VM Reference Candidate (vm-refs.1-local)

The connected calculator is the fork's BigInteger VM again, with batched,
order-local reference acquisition. `NativeVmReferenceBatchTest` reproduces
repeated tagged-catalyst validity reads before the repair. It also closes the
live-read boundary after preparation and completes VM plans for 1, 2,
Long.MAX_VALUE and 10^1024-1, with empty/partial/full stock and exact shortages.
Future returned NBT states are not eagerly generated. Exceptions from unused
stocked alternatives remain deferred; demanded exceptions remain visible.

`NativeGraphSnapshotTest.installedScaledPatternIsExpandedBeforeItsLongGettersOverflow`
checks both the historical graph and connected VM against the real optional
ExtendedAE Plus wrapper using `-PeaepPatternJar=<path>`. This includes overflow
in inputs alone or outputs alone, original wrapper binding and wide-output
detection. The graph adapter is a test fixture, not a runtime fallback.

`NativeVmAqeCapacityTest` now calls the connected VM result bridge. Keep the
public ABI and AE2 oracle checks. `verifyExactVmBundle` requires the VM compiler
and core in the nested runtime and rejects the graph snapshot/calculation adapter.
These tests do not establish production speed, startup, wide physical execution,
refund/recovery or concurrent-order acceptance. No production deployment is
implied by a successful candidate build.

## Graph Startup Candidate (graph.3-local)

`MegaCellsModContextTest` invokes the real Forge context lookup with a foreign
language extension. It reproduces the ClassCastException before the repair and
checks that the repair selects the MEGACells owner's bus without mutating the
loading thread. Also cover a foreign Java caller (wrong-bus bug), no caller,
the correct caller and an absent owner. Only mod containers and buses are fixtures;
Forge's ModLoadingContext and FMLJavaModLoadingContext behavior is real.

Pass `-PmegaCellsJar=<megacells-forge-2.4.6-1.20.1.jar>` to check the installed
bytecode against the Mixin target. `MegaCellsBootstrapContractTest` also checks
the optional redirect's compiled annotations and client/server dev profiles.
It is not a transformed-class or full Forge launch test. Without the optional
JAR property, the installed-bytecode test is skipped, not accepted.

Build both `-Pae2Variant=upstream` and `-Pae2Variant=uelm`, preserving separate
reports. Check the final reobfuscated bundle, not the intermediate JAR.
The graph.3 change leaves graph.2 calculator/native/SMT and public API class
bytes unchanged. It does not establish a cure for GC/C2 access violations,
Windows page-table corruption, or successful gameplay startup.

The separate-folder runtime check on 2026-10-04 now records actual Mixin exports
on client and server, server `Done`, and the client's own F2 title-screen image.
It required test-only recovery of existing NUL-filled TOMLs (14 server, 2 client).
The server saved on shutdown but Arclight reported one remaining thread; desktop
window capture stayed white despite the rendered game image. This is not production
world/join/crafting acceptance or proof that intermittent native faults are cured.
See [the full startup conditions and evidence](issues/evidence/ISSUE-218-GRAPH3-STARTUP-2026-10-04.md).

## Shared Pattern Programs (graph.2-local)

`NativeSharedProgramTest` exercises actual AEProcessingPattern definitions,
the native order capture and the connected graph calculator. Check these
behaviors, not just aggregate historical test counts:

- A provider registration freezes known processing coefficients; T0 capture
  does not compile the numeric graph. The AE2 worker compiles it once.
- Repeated orders reuse that graph, but use separate stock/emitter snapshots.
- Replacement, removal, priority change and a newly craftable terminal input
  invalidate affected roots, without invalidating an unrelated root or an
  already captured order. Same-tick changes do not depend on a tick counter.
- Empty/partial/sufficient stock at 1, 2, long max, max+1 and 10^1024 has exact
  consumed/missing quantities through the native adapter.
- `NativeGraphSnapshotTest` still requires fresh unknown callback results per
  order and checks the actual optional EAEP scaled-pattern JAR.
- The pure engine checks shared topology with concurrent independent stocks.

Neither these tests nor Jar-in-Jar verification starts Minecraft. They do not
prove the reported black-screen issue resolved, gameplay startup, generic wide
execution, refunds/recovery, or the production circuit's sub-10-second target.

## 2.0.0 Acceptance Contract

These are required outcomes, not evidence that the current implementation passes.
The engine, VM dependency and packaging are not acceptance criteria. Preserve
public API behavior and exact accounting while allowing their internals to change.

| Gate | Required evidence |
| --- | --- |
| Existing consumers | Compiled API compatibility plus actual AQE/AAC calls, plan inspection, submission, completion, cancellation and recovery; descriptors alone are insufficient. |
| Exact planning | Exact consumed, missing and surplus quantities, selected pattern counts and capacity, including shared byproducts, returned containers, catalysts, fluids, NBT variants and alternate producers. |
| Wide quantities | Overflow in any intermediate input, output, pattern count, missing amount or capacity must remain exact even when the root request fits in long. |
| Stock and quantity | Empty, partial and sufficient stock for the same graph at 1, 2, Long.MAX_VALUE, MAX+1 and the final target; rebuild after stock changes without reusing stale craftable counts. |
| Physical consistency | Real output-backed completion, exact cancellation/refund, restart at transaction boundaries and concurrent orders competing for the same stock without duplicate reservations. |
| Server work | Concurrent planning and active I/O measurements covering total server work, longest server slice, MSPT, worker time, queue wait and allocations; per-job budgets alone do not prove a bounded combined load. |

The final performance fixture is this server's creative control circuit, from
raw materials through the full registered pattern tree, at about 922京. Record
the exact integer rather than substituting Long.MAX_VALUE. Literal 922京 is
9,220,000,000,000,000,000; intermediate quantities may exceed signed long.
Final acceptance requires a complete correct plan in less than 10 seconds,
not a partial shortage report, cached result lookup or preview that cannot be
submitted. Record wall time from request through the complete usable plan,
including capture, queueing, computation, validation and handoff.

Pin source revisions, JAR hashes, recipes/patterns, stock snapshots, request
quantity, hardware and concurrent load. Report cold and repeated runs separately
for all three stock cases; do not conceal a slow empty-stock case with a warm
or fully stocked result. Validate small representable cases against AE2 and
wide cases against independent exact expected quantities. A sampled subset of
the recipe tree is not evidence for the full target. Unregistered production
patterns or unperformed runtime checks stay pending.

Do not reinterpret safety fallback/rejection tests below as successful support
for required industrial recipes. Safe rejection is preferable to corrupting
stock, but remains an incomplete feature. Ordinary and giant order support,
performance and lifecycle safety must pass together before declaring 2.0.0 done.

### AQE Capacity Baseline

The 2026-09-25 capacity acceptance baseline is the configured BigInteger Quantum
Core: `10^1024 - 1 bytes` (3402 bits). Byte capacity is not an order-count limit.
Derive an admissible order from its actual exact plan cost and the CPU's current
free capacity. Test equal capacity and one byte beyond, including existing
reservations, cancellation/release, save/load and status packets. Empty/partial
stock must still produce a complete exact missing plan. Preserve intermediate
BigInteger values and larger configured/combined CPU capacities; do not hard-code
a 3402-bit global ceiling. This supplements, not replaces, the creative-circuit
performance and real physical-execution requirements above.

## Automated

### rc.7-dev.18 Planning And Recipe Intent

NativeVmDeclaredInputTest reproduces a tick wait for native substitution primary
validity before the fix (`build/issue218-native-input-before.log`). The real
`aco_vm_substitution_queries` probe primes native validation/remainders then
repeats primary, tagged and invalid inputs without advancing server ticks.
Unknown callbacks and returned-container lifecycle regressions must still pass.

IntentSearchIteratorTest checks lazy original search, fallback order, identity
dedup, direct next(), exhaustion and native failure propagation. StackIntent
identity tests require complete NBT; legacy ID-only hints cannot prove identity.
MekanismItemRecipeIntentTest checks native predicate, full NBT and exact ratios.
With Mekanism 10.4.16.80 installed only in the isolated GameTest runtime,
`aco_mekanism_intent_completion` pushes through a real provider and checks actual
typed lookup use, no unpowered/instant output, input consumption and power cost.
No result below should be treated as a production latency or all-machine claim.

`aco_gt_intent_completion` checks the actual GT 7.5.3 lookup Mixin, native furnace
recipe type, powered completion and consumption. `acoGameTestScope=machines`
requires both GT and Mekanism completion plots. Use a separate fresh test world:
AE2's debug item generator in the provider fixture exhausts memory enumerating
GT tool variants. Keep provider/API tests in their own fixture; machine-only
success is not a replacement for those tests.

GTRecipeDbView observes successful native registration, including proxy recipes,
and clear/revision changes. RecipeManager alone is not a complete GT index;
the electric furnace test failed before using this accepted DB view. Category
display entries or rejected staging recipes must never enter the bypass index.

### rc.7-dev.17 Native Execution Only

OrdinaryPatternDispatchTest and NeoEcoPackagingContractTest first reproduced
three remaining problems: the legacy external entry subtracts one from MAX_VALUE
operations, retired timing algorithms are still present, and Neo ECO budget
Mixins are still registered. Before XML: `build/issue161-native/before/`.
External operations need no native coprocessor +1 headroom; test both boundaries
independently. Preserve exact-job exclusion and coprocessor overflow tests.

SequentialInstantDispatcherTest now tests its surviving public entry, including
same-tick repeated native counts, full/blocked zero results followed by immediate
resumption, native failures and absence of per-owner state. The private probe
formula and measured-cost tests were retired with that deleted implementation;
their historical results are not proof for current behavior. AcoMixinPluginTest
now rejects both removed Neo ECO hooks even without Forge bootstrap state.
Retain pinned provider runtime tests and all exact-accounting gates.

### rc.7-dev.16 Provider Capture Work

PatternIntentExtractorTest uses real native AEProcessingPattern instances to
check immutable description reuse, identity replacement, custom/subclass refresh
and live item/fluid counters. Three assertions failed before the change; their
XML is `build/issue161-description-before.xml`. A separate initial fixture setup
failure is retained and is not counted as a reproduced product bug.

The existing provider load probe supports `-PacoProviderProfile=true`, which
runs 256 rounds with JDK Flight Recorder sampling to `provider-dispatch.jfr` in
the test-server working directory. This is test-only and adds no runtime logger
or profiler to the distributed mod. Use `java tools/ProviderJfrSummary.java
<recording>` to inspect it. Allocation weights are sample estimates, not exact
bytes; inclusive sample shares are not additive. Keep profiled timings separate
from unprofiled six-round measurements. Neither is a production performance gate.

### rc.7-dev.15 Provider Runtime Regression

Use `-PacoGameTestModsDir=<pinned-test-jars>` with the three artifacts listed in
`issues/evidence/ISSUE-161-FORGE-1.20.1-2026-09-28.md`. These are gameTestRuntimeOnly,
not release dependencies. The diagnostic InsaneAE source/JAR is intentionally
pinned; do not substitute the installed production revision without recording it.

- `-PacoGameTestScope=historical -PacoGameTestBaseline=true`: four original #102
  cases without ACO/VM. Baseline must pass before drawing comparison conclusions.
- `-PacoGameTestScope=historical`: preserve the raw old test outcome with VM. Its
  two legacy planner-counter assertions fail because VM replaces that planner;
  this is NOT a four-test pass and must not be reported as one.
- `-PacoGameTestScope=providers`: unchanged round-robin/provider-index cases plus
  separate real native/VM comparisons for cake returns and alternate chains,
  the ordinary ACO runtime suite and a real successful-provider-push load probe.
  Required named cases are enforced; absent InsaneAE fails registration.
- The load probe records six rounds of 1024 actual pushes into a chest, checks
  every delivered count, real intent-hook execution, full/blocked rejection and
  immediate resumption after clearing. This is provider-call elapsed time in a
  test world, not production MSPT, CPU scheduling or planning time.

RecipeIntentRegistryTest first failed for a 2,097,152-inspection repeated-cleanup
case and an expired tail hidden behind a longer-lived head. It now checks the
actual registry, expiry equality, coalescing, eviction, clear/backwards time and
Long.MAX_VALUE ticks. No assertion uses wall time as a correctness threshold.

### rc.7-dev.14 Ordinary Pattern Throughput

OrdinaryPatternDispatchTest loads an old config with all ordinary throttles
enabled and a one-operation cap. Native and Neo ECO dispatch counts must pass
unchanged, including repeated calls in the same tick and Integer.MAX_VALUE for
the external operation boundary. Preserve the native result (including zero),
exceptions, and +1 headroom for AE2's coprocessor count. No retry loop is added.
The source contract forbids an executeCrafting wave redirect even when the
separate transactional V2 Instant setting is enabled. Keep #125's exact-job
isolation, overflow reconstruction, API/receipt tests and real Forge GameTests.

Before evidence: build/issue161-unthrottled-before.xml (four failing tests).
Removing ACO's ordinary throttles is not proof of zero server cost, higher TPS,
or a fix for the separate world-load crash reported in the screenshot.

### rc.7-dev.13 Declared Input Semantics

NativeVmDeclaredInputTest uses the production context and real native input
implementations. Dev.12 failed both tickless repeated-query cases and spent
8192 live reads on 4096 exact validity/return pairs; the fixed wrapper spends
zero on those repeats. Before evidence: build/issue218-declared-before.xml.
Keep cancellation, NBT identity, dynamic/custom callbacks, actual non-null
returns and substitution-enabled inputs. The empty/partial/full-stock matrix
covers 1, 2, Long.MAX_VALUE, MAX+1 and 3402-bit quantities and exact bytes.

The ae2.aco_vm_declared_inputs GameTest repeats the same production wrapper
queries on the real AE2 calculation pool after obtaining a registered pattern,
while the test server deliberately advances no more ticks. Existing lifecycle,
quantity oracles and API checks remain required. Initial metadata still needs
live access, and undeclared input callbacks still synchronize. These operation
counts do not prove a production latency reduction or the final <10 s target.

### rc.7-dev.12 Continuous Arithmetic

NativeVmAccessTest checks 3402-bit arithmetic with no ticks, nonblocking server
polls, expiry without worker suspension, grant reuse, nested callback exclusion,
new live-access waits, interruption, callback failure and independent jobs.
NativeVmSchedulingTest failed on dev.11's inherited blocking simulateFor hook;
the before XML is build/issue218-continuous-before.xml. This structural failure
is not a production performance measurement.

The new ae2.aco_vm_continuous_arithmetic Forge test uses the actual AE2 pool,
TickHandler and subclass. After one protected read, it finishes more than one
normal slice's worth of 3402-bit arithmetic while the test server advances no
ticks. The existing lifecycle test still requires protected callbacks with the
server waiting. All quantity oracles continue to exercise the current context.

Diagnostics distinguish ae2WorldWaitMs from ae2ExclusiveMs and count grants and
reads. Pure computation has no tick budget; consecutive live callbacks can reuse
the current short exclusive interval. Unknown world callbacks still need that
interval and cannot be preempted in the middle. No per-input server future or
capture/retry pipeline is restored. Production timings remain unmeasured.

### rc.7-dev.11 Baseline (Superseded Scheduling)

Production uses NativeVmPlanningContext and AE2's original calculation pool and
time-slice monitor. NativeVmOracleHarness, NativeDemandAe2OracleTest,
NativeVmRequesterTest and NativeVmAqeCapacityTest now cover that production
context, not the retired capture layer. NativeVmLifecycleTest checks one stock
capture, queued cancellation, server-stop ownership and zero ingredient RPCs.
The ae2.aco_vm_ae2_lifecycle Forge test checks the real mixed-in worker name,
server monitor wait, concurrent orders and cancellation before the first slice.

NativeVmCapture/NativeVmPatternCatalog in test sources and their historical
capture/catalog tests preserve earlier evidence only. They are not runtime
fallbacks and their pass count does not prove the current scheduler. The bundle
audit rejects these classes. vmMs includes AE2 waits; vmCpuMs is worker CPU time
and vmActiveMs=-1 prevents counting wait time as active work. AE2's default
5 ms/tick calculation budget is unchanged. Production speed is still unmeasured.

The #218 missing-stock slice adds exact differential tests for zero/sub-unit
fuzzy candidates, byproduct replenishment, one-shot alternative rollback under
wide enclosing replay, and native exact inputs in revalidated outer wrappers.
The bounded diagnostics tests check category totals and pattern-type limits.
Retained before/after fixtures are under `build/issue218-missing-work/`.
Operation-count reductions in those fixtures are not production timing results.

Run:

```powershell
.\gradlew.bat test --no-daemon --rerun-tasks
.\gradlew.bat clean build --no-daemon
```

Automated tests cover:

- checked `long` and `BigInteger` planning;
- selected input alternatives;
- twenty-stage quantity-independent formula generation;
- exact escrow debit and credit;
- exact boundary mutation recovery for all-before, mixed, all-after, and
  invalid external-change states;
- parent BigInteger lease commit, rollback, and persistence;
- NBT count bounds and canonical encoding;
- transaction journal behavior;
- physical Worker formula multiplication;
- durable terminal receipt identity and explicit forget.
- nine identical signed-long input slots without merged-input rejection.
- exact NetworkStorage snapshot reuse within one tick;
- same-tick invalidation after a storage generation change;
- refusal to reuse a snapshot across ticks;
- nested NetworkStorage reuse while an outer capture remains active;
- full-grid terminal reuse of AE2's cached inventory with exact sidecars;
- preservation of add-on-specific terminal inventory paths.

## Required Live Matrix

Runtime testing must use the same ACO and AAC JAR on server and clients.

### Basic Physical Path

1. Form an AAC structure with Neo ECO Pattern Buses and Workers.
2. Encode a deterministic `9 input -> 1 output` crafting-table Pattern.
3. Order `1`, `1,000`, and a large signed-long quantity.
4. Confirm the Worker performs one physical recipe stage per Pattern, not one
   Thread per requested craft.
5. Use a tag alternative, restart, and confirm the persisted concrete key is
   revalidated rather than replaced by another tag member.
6. Confirm exact input consumption and output count.
7. Confirm normal AE2 job status remains visible.

### Twenty-Stage Chain

1. Encode a linear twenty-stage deterministic crafting-table chain.
2. Test root amounts:

```text
1
1000
Long.MAX_VALUE
10^64 - 1
10^1024 - 1
```

3. Confirm each order performs twenty dependency stages.
4. Confirm the requested amount changes arithmetic values but not step count.
5. Confirm each parent stage waits until its child's physical output receipt is
   credited.
6. Confirm no internal item is created in ME merely because it appears in the
   compiled plan.

### Independent Branches

1. Build a root with at least two independent crafting branches.
2. Provide multiple Workers.
3. Confirm both branches may run at the same time.
4. Confirm the root starts only after both branch outputs exist in escrow.

### Processing Boundary

1. Place a GTCEu or Mekanism processing Pattern between two deterministic
   crafting-table sections.
2. Confirm the upstream crafting section completes.
3. Confirm the machine receives and processes its real inputs.
4. Confirm the downstream crafting section waits for the real machine output.
5. Confirm no machine output is synthesized or skipped.

### Fallback Before Ownership

Test:

- substitutions;
- duplicate producers;
- cycles;
- changing NBT;
- durability-sensitive recipes;
- unknown remaining items;
- missing AAC Pattern ownership;
- unavailable or malformed exact storage.

Every case must leave boundary storage unchanged and use AE2's normal route or
report unsupported.

### No Fallback After Ownership

After boundary input reservation:

1. remove power;
2. unload the Worker chunk;
3. break and reform the structure;
4. change a recipe generation;
5. cancel the parent job.

Confirm the transaction waits, resumes, returns exact escrow, or quarantines.
It must not start the normal path for the same work.

### Restart Points

Stop and restart after each persisted point:

- before boundary mutation;
- after one key of a multi-key mutation;
- after all boundary input extraction;
- after Worker acceptance;
- while Thread progress is running;
- after `OUTPUT_READY`;
- after Worker terminal receipt creation;
- after escrow credit;
- after final ME insertion;
- before parent commit.

For every point, compare exact storage before and after. There must be no loss,
duplication, negative count, clamped accounting, or repeated job.

The first Worker/Thread lookup after restart may rebuild runtime UUID indexes.
Later status polling must use those indexes without scanning every Worker and
Thread again.

### Multi-Key Mutation Recovery

Use at least three distinct exact-storage keys.

1. Persist before values.
2. Apply only some keys and stop.
3. Restart.
4. Confirm after-valued keys are not replayed.
5. Confirm before-valued keys are applied once.
6. Change one key to a third value and confirm quarantine.

### Cancellation

Cancel:

- before input reservation;
- after input reservation but before Worker acceptance;
- while a Worker is running;
- after output is ready;
- after escrow credit.

Only unfinished physical work may return its reserved inputs. Completed output
must return as output, never as both output and original input.

### Capacity and Fairness

1. Submit multiple BigInteger parent jobs to the same Quantum Computer.
2. Include one small job among giant jobs.
3. Confirm per-grid start and active-stage budgets are respected.
4. Confirm the small job continues to receive scheduler opportunities.
5. Record TPS, MSPT, GC allocation, and `/aco stats`.

### Diagnostics

`/aco stats` should report:

- `Physical crafting tree` starts;
- active scheduler ticks;
- completed, cancelled, and quarantined transactions;
- start deferrals;
- receipt recovery and fingerprint revalidation.

Statistics must not iterate or print complete BigInteger inventories.

For Issue #28 profiling, `/aco stats` should additionally report:

- exact storage snapshot cache hits and misses;
- nested network scans;
- storage-generation invalidations;
- full-grid terminal snapshot reuses.

Repeat the same Spark capture with a Pattern Encoding Terminal open. Confirm
that `BigIntegerStorageSnapshotBridge.collect()` and nested
`NetworkStorage.getAvailableStacks()` no longer rebuild once per terminal plus
once per `StorageService` refresh when no intervening storage mutation occurs.

### Persisted Pattern Identity Recovery (Issue #29)

Use one deterministic physical crafting-table job with at least one accepted
Worker receipt. Record every exact input, output, task, and `waitingFor` value.

1. Unload the Pattern Provider chunk while the job is running. The transaction
   must report waiting; it must not quarantine, cancel, refund, or advance.
2. Reload the unchanged Provider. The same receipt must resume and every exact
   counter must remain equal to the recorded value.
3. Repeat across a server save and restart. Reconciliation must use the
   transaction-owned encoded Pattern definition even before the live graph has
   rediscovered the Provider.
4. Reload recipes without changing the Pattern formula. The job must revalidate
   and continue without creating another receipt.
5. Replace the encoded Pattern with a definition whose selected inputs or
   expected outputs differ. The transaction must quarantine while preserving
   escrow and receipts for administrator recovery.
6. Repeat each reconciliation twice. Planned, dispatched, introduced, and
   credited totals must be idempotent; no item may be duplicated or lost.

`PhysicalPatternAccountingSourceContractTest` runs in headless JUnit and locks
the schema, ownership-boundary capture, live-graph-independent accounting, and
retry/conflict branches. The lifecycle scenarios above remain GameTest/manual
checks because AE2 encoded items require the real mod registry lifecycle.

## Disable Checks

1. Disable the physical path before starting a new job and confirm normal AE2
   execution remains available.
2. Disable it with an existing persisted receipt and confirm recovery still
   reconciles ownership instead of deleting the receipt.
3. Disable AAC vector execution and confirm its controller delegates performance
   behavior to Neo ECO.

## Issue #103 Backport Checks

- Root Programの循環と複数Producerが別理由になること。
- 不完全Snapshotだけが一回の再構築対象になること。
- BigInteger台帳非対応CPUが`CPU_TOO_SMALL`を返さないこと。
- Forge 1.20.1とNeoForge 1.21.1の両方で全JUnitと`clean build`を通すこと。

## Not Proven by Gradle

Gradle tests do not prove:

- Forge client startup;
- dedicated-server startup;
- Arclight runtime behavior;
- multiblock formation in a live world;
- real AE power consumption;
- chunk save ordering;
- compatibility with unpinned add-on versions;
- TPS improvement on a production network.

These require the live matrix above.
