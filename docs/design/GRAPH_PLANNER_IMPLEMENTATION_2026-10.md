# Graph Planner Implementation: graph.2-local

Date: 2026-10-03. Scope: [Issue #218](../issues/ISSUE-218.md).
Design: [BigInteger API-only reset](BIGINTEGER_PLANNER_2026-10.md).

## graph.2 Design Follow-through

Candidate: `build/distributions/2.0.0-rc.7-graph.2-local/aco2.0.0-rc.7-graph.2-local_1.20.1.jar`.
Final build target: AE2-UELM 15.5.0-uelm. Size: 2,849,420 bytes.
SHA-256: `82D2650D44944BFC7C6547D54C7177A2A6D9524F81D0533B98A625C75992F923`.

- `NativePatternCatalog` belongs to each CraftingService. Provider add/remove/
  refresh freezes recognized AE2 processing coefficients, including the known
  EAEP original-times-scale contract. It preserves AE2's producer ordering.
- Cached root programs track their dependent keys, including uncraftable raw
  inputs. A same-tick producer/priority change invalidates affected roots only.
  Previously captured orders retain their immutable definitions.
- Orders reuse processing programs, not inventory or computed answers. Exact
  stock, emitter state and unknown callback results remain order-local. A root
  containing custom/crafting callbacks still uses the order-local capture;
  recognized processing definitions within it are reused.
- A frozen processing program compiles its numeric RecipeGraph on the AE2
  worker, once. RecipeGraph retains its immutable consumer-first topology.
  There is no second worker, solver call or live read during this compilation.
- The BigInteger API, physical execution and persistence implementations are
  unchanged in this follow-through. No old calculator was restored.

Evidence: upstream and UELM each ran 883 tests (882 pass, one optional Neo ECO
20.3 contract skipped); pure-engine tests total 24. Specifically,
NativeSharedProgramTest covers six connected catalog/capture/solver behaviors,
including a 15-case quantity/stock matrix through 10^1024. NativeGraphSnapshotTest
checks fresh opaque semantics and no worker callbacks. These counts are not a
claim that all historical tests exercise the new runtime.

Both builds passed verifyExactVmBundle and verifyIssueRegressionManifest.
Archive CRCs, duplicate entries/classes, Java targets and nested hashes were
also inspected: main 529 / exact 31 / native 37 classes target Java 17; the 682
unmodified SMTInterpol classes target Java 8. No Minecraft/Forge/AE2/test-library
classes or native libraries were bundled. Exact-engine 0.1.5 and native
0.3.5-graph.2 bytes match the tested libraries; full solver sources stay outside
the runtime JAR. This is static packaging evidence, not a game launch.

No client/server file, process, configuration or public release was changed in
this follow-through. Both installed JARs are still graph.1, SHA-256
`F4E8014BBD1743D80B59F90C6D3E80C4319BE179FD6479686E0169BB523D9B7A`.
The user installed those after the historical build below and reported black
screens. This catalog change is NOT evidence that those startup failures are
fixed. Full design acceptance, capacity-policy alignment, general cyclic-model
refinement and wide physical lifecycle acceptance remain outstanding.

## Historical graph.1 Record

The following status and measurements describe its original build, before the
user's later deployment. They are not fresh measurements of graph.2.

Status: implemented and connected as a **local development candidate**.
Not deployed, not a stable release, not full ACO 2.0.0 acceptance.

Candidate built against upstream AE2:
`build/distributions/2.0.0-rc.7-graph.1-local/aco2.0.0-rc.7-graph.1-local_1.20.1.jar`

Size: 2,833,406 bytes. SHA-256:
`F4E8014BBD1743D80B59F90C6D3E80C4319BE179FD6479686E0169BB523D9B7A`.
The current public release and the server/client installations were not changed.

## What Changed

1. At order creation on the server, capture accessible exact stock, reachable
   patterns, exact keys/NBT, input alternatives, validity, remainders and owner
   revisions. Each input/key answer is evaluated once within that order.
2. Submit one callable to AE2's existing crafting pool. During graph arithmetic,
   no World, Grid, Provider, RecipeManager or input callback is called. There is
   no second executor, CraftingCalculation time-slice monitor or per-input RPC.
3. Fixed DAGs aggregate all consumers' demand before calculating a producer.
   Shared stock is spent once, lot counts round upward exactly, and all outputs
   of a recipe belong to the same execution count. Quantity does not become
   a loop over individual crafts or an expanded tree of repeated paths.
4. For coupled recipes, fix the unambiguous consumer prefix first. The remaining
   counts and slot allocations enter an exact integer model. Stock, all outputs,
   returns and competing inputs are accounted together. Feasible stock is tried
   before a missing-material model; later producers are disabled when an earlier
   producer can still satisfy that model. This is not global cost minimization.
5. Check startup resources and a legal sequence for the selected counts.
   Repeated cyclic blocks use BigInteger repetition summaries instead of one
   record per craft. A state-equation solution alone is not called executable.
6. Translate quantities through the existing BigInteger API and capacity owner.
   Planning does not reserve stock, start machines or change provider throughput.

All accounting quantities, including intermediate demand, output/return counts,
used/missing/emitted quantities and CPU bytes, remain BigInteger. Integer IDs
identify graph nodes; they do not store item counts. The ordinary AE2 result
facade uses `longValueExact` only after the existing wide-result dispatch.
Byte-only and pending-output-only overflow also select the exact bridge.

ExtendedAE Plus 1.5.5 scaled patterns are captured from the original pattern and
multiplier in BigInteger. The original scaled wrapper remains the execution
binding. Reading its already-multiplied long getters would lose positive
wrapped overflow; the installed-JAR regression reproduced this before the fix.

## Owners And Source

ACO working tree:
`C:\example\workspace\aco-rc4-vm\forge-1201`

Graph engine / native adapter working tree:
`C:\example\workspace\references\AE2-VM-aco-exact`

Both trees already contained uncommitted work before this change. Their HEADs
are baselines, not commits containing graph.1:

- ACO: `163243462a3caaa031aa5bd983abf69663c8fcdf`.
- Fork: `2b764576d6eb0a6c7884e9294cd6b78f5c70f3e6`.

| Location | Responsibility |
| --- | --- |
| `exact-engine/.../graph/RecipeGraph.java` | Immutable numeric pattern constants and indexed resources |
| `exact-engine/.../graph/ExactGraphPlanner.java` | Shared demand, fixed prefix, exact result accounting |
| `exact-engine/.../graph/IntegerRecipeSolver.java` | Coupled integer counts and input allocation |
| `exact-engine/.../graph/PlanSchedule.java` | Startup supply, legal ordering, compressed repeats |
| `native-engine/.../NativeOrderSnapshot.java` | All live reads and exact AEKey bindings at capture |
| `native-engine/.../NativeGraphCalculation.java` | Pure result conversion and byte charges |
| `native-engine/.../NativePlanSelections.java` | Weak plan-associated input/order evidence, not a stock cache |
| `native-engine/.../NativeVm.java` | One capture, one existing AE2-pool task, result delivery |
| ACO `engine/VmBigIntegerAccounting.java` | Exact stock/API boundary and captured owner revisions |

The old compiler, bytecode interpreter, NativeVmCalculation, NativeVmAccess and
NativeVmPlanningContext are excluded from the shipped native JAR. They remain
in a test-only source set for historical regressions, not as runtime fallbacks.
Public API v3 descriptors and existing persisted execution-job formats are not
regenerated or replaced.

## Dependencies And Packaging

One installable bundle contains:

- ACO `2.0.0-rc.7-graph.1-local`.
- Exact engine `0.1.4` and native bridge `0.3.4-graph.1`.
- Unmodified SMTInterpol `2.5-1388-ga5a4ab0c` via Forge Jar-in-Jar.
- Solver licenses and a versioned source notice. The full corresponding source
  archive is provided separately under `sources/` beside the installable JAR,
  not in the runtime class path or the mods directory.

Solver binary SHA-256:
`5F3277CB94546E0305A6BB1C29752F5AD30857F08BC6D82C6BA60798D1F869F4`.
Separate full source archive SHA-256:
`4599916D93FA2834B5019908890E322946F34C6D360322812CB0F043EF303EC3`.
The bundle audit checks the embedded bytes, versions, bootstrap classes,
required graph classes, legacy-class exclusion, licenses and solver sources.

## Verification

Builds use Temurin 17.0.17, not the older Oracle JVM that previously crashed
during C2 compilation. Both upstream AE2 15.4.10 and AE2-UELM 15.5.0-uelm are
compile/test targets. Neither starts Minecraft.

Targeted assertions include:

- Shared A/B/C demand, intermediate stock and lot surplus, independently
  calculated expected totals rather than the old planner as the sole oracle.
- Both outputs of a recipe requested together, with one shared execution count.
- An alternative must not consume the only stock needed by a different recipe.
- Mixed input allocation, returned containers, catalyst seed, partial returns,
  a seeded compressed cycle and a cycle without seed.
- Sixty small inventory cases checked against independent finite enumeration.
- Actual native AEProcessingPattern and AECraftingPattern inputs/returns/NBT.
- Actual EAEP scaled coefficients with positive wrapped input/output overflow.
- Worker calculation after all live accessors are changed to throw; zero such
  calls during arithmetic. Separate orders do not reuse stock/validity answers.
- Request 1, 2, long maximum, long maximum + 1, 3402-bit quantities and
  intermediates wider than 3402 bits. Public capacity admission/restore tests
  remain separate from stock sufficiency.
- Captured pattern/recipe generations are retained even when generations change
  before result delivery. Interruption does not need an AE2 tick grant.
- Public ABI descriptors unchanged. Existing physical receipt tests retained;
  these are not production runtime acceptance.

Run in the ACO tree:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
.\gradlew.bat test verifyExactVmBundle verifyIssueRegressionManifest --no-daemon --console=plain
.\gradlew.bat test verifyExactVmBundle verifyIssueRegressionManifest -Pae2Variant=uelm --no-daemon --console=plain
```

Optional actual-JAR contracts are enabled with `-PeaepPatternJar`,
`-PaqeCapacityJar` and `-PneoEco20_4Jar`. Paths used in this run:

- `C:\example\server\mods\extendedae_plus-1.5.5.jar`
- `C:\example\server\mods\aqe2.2.7_1.20.1.jar`
- `C:\example\workspace\deps\neoecoae-20.4.0\neoecoae-20.4.0.jar`

These JARs are only read by tests, not modified or launched. The Neo ECO 20.3
optional JAR contract is not supplied and remains skipped.

## Performance Scope

`exact-engine` task `graphBenchmark` writes
`build/reports/graph-planner-benchmark.csv` in the fork.
It measures pure arithmetic on three fixtures: 80 shared DAG stages, coupled
producers with a catalyst, and 80 shared stages with one producer choice.
Each uses 10 request quantities and three inventory states: none, half, full.
All 90 cases assert their expected missing/ready state before reporting time.

The sequence is partially warmed by earlier solves, not a cold-start benchmark.
It excludes server capture, queue delay, client networking and physical work.
It is **not** the actual creative control circuit graph and cannot prove the
production 10-second goal. `9,220,000,000,000,000,000` is below Long.MAX_VALUE;
long+1 and `10^1024 - 1` are tested separately.

Measured after other builds had finished, on 2026-10-03, with Temurin 17.0.17
and an AMD Ryzen 7 5700X3D:

| Fixture | Cases | Minimum | Maximum |
| --- | ---: | ---: | ---: |
| 80 shared DAG stages | 30 | 1.26 ms | 2.74 ms |
| Coupled producers and catalyst | 30 | 2.02 ms | 16.00 ms |
| 80 shared stages with one producer choice | 30 | 7.13 ms | 15.15 ms |

At `10^1024 - 1`, the shared 80-stage graph took 2.36 / 2.19 / 2.26 ms
for none / half / full stock; it visited 240 graph entries in all three cases.
With the one producer choice, the same three stock cases took
11.67 / 9.75 / 8.36 ms. These are single local samples, not statistical latency
percentiles or guarantees. The raw rows are retained in
[ISSUE-218-GRAPH-2026-10-03.csv](../issues/evidence/ISSUE-218-GRAPH-2026-10-03.csv).

Final verification counts: upstream and UELM each ran 877 tests, with 876 passed,
zero failures and one explicitly skipped Neo ECO 20.3 optional-JAR contract.
The pure engine ran 23 tests, including 16 new graph tests, with no failures or
skips. These counts include historical fixtures and do not imply all tests
exercise the new runtime entry. The tests listed above identify its coverage.

## Remaining Boundaries

- The snapshot is per-order. graph.2 adds shared definitions and programs for
  recognized processing patterns. Roots with opaque/custom or crafting input
  semantics still capture those callbacks on the server at T0; a large dynamic
  input domain can still cause a long capture. Constant processing definitions
  in those roots are reused, but the entire root program is not yet shared.
- Coupled formulas include the remaining reachable graph after fixing its
  deterministic prefix; full independent-region decomposition is not present.
- Stable remainders and finite increasing durability states can be captured.
  Unknown reusable NBT transitions without a finite declared domain raise an
  explicit unsupported error; they do not silently become shortages or loop
  forever during capture. Such patterns need an explicit adapter.
- For a selected integer model, ordering search can be expensive. If no order
  for that allocation is found, it reports an error; it does not yet refine the
  integer model across all alternate cyclic allocations. General Petri-net
  reachability and arbitrary dynamic patterns are not claimed solved.
- No global least-cost or minimum-total-missing objective is promised. Missing
  quantities are exact for the selected legal plan and include required startup
  resources. Recipe priority is preferred only when that solve remains feasible.
- Selected input/order evidence is attached to plans, but existing native/AQE
  execution consumers were not replaced to interpret a new schedule. Generic
  wide machine execution, all substitution/ordering cases and cyclic completion
  require runtime integration acceptance.
- Byte node overhead now corresponds to the shared stored graph rather than an
  exponentially expanded recursive tree. Exact public capacity arithmetic is
  retained, but this differs from the design's unchanged conceptual-node charge
  policy. Matching legacy charges or explicitly agreeing a new capacity policy
  is still required before claiming full behavioral capacity compatibility.
- Physical cancellation/refund, restart recovery, simultaneous real orders,
  AQE/AAC gameplay interoperability, server load and the real circuit benchmark
  remain unverified for this candidate. No deployment or release was performed.

This record does not replace those missing acceptances with test counts.
