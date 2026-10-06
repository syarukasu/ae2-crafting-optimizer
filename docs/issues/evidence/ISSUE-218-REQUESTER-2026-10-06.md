# ISSUE-218: Requester quantity-search optimization

## Ownership and implementation

The installed ME Requester 1.1.5 RequestState calls beginCraftingCalculation
with CRAFT_LESS; PlanState awaits its Future and MissingState can request again.
Current logs do not identify callers and do not prove requester-induced load.
No requester, retry, scheduling, provider or physical execution code was edited.

The native VM already shares initial stock, pattern definitions, input
observations and process metadata within an order. The removed redundancy is
quantity-bit feasibility work, not an additional snapshot cache.

VmInstructionExecution.directCraftLessPlan uses existing VmDemandProgram
coefficients. For eligible single-producer graphs, available raw stock divided
by combined material coefficients bounds the root manufacturing count. Complete
program periods supply funded counts with exact batch rounding. Integral graphs
materialize directly; unshared fractional graphs test the remaining interval
using ordinary VM attempts. Shared fractional paths, manufactured intermediate
stock that must be replenished, alternatives, reused outputs, cycles and opaque
tools retain the existing route. Nothing is truncated to long or timed out.

CapacityShape preserves the union of conceptual nodes already visited by
aggregate blocks. Previously a later stocked-boundary shape could overwrite
deeper capacity history. It contains no inventory, reservation or recipe choice.
Shape conversion is memoized within an order; union traversal memoizes shared
pairs and immediately returns unchanged shapes.

## Verification

The pre-change fixed shared-DAG requester test returned correct quantities but
performed 266 VM work units. The new path uses one. Its requested count is
Long.MAX_VALUE, with raw stock for one third of the requested output. All used,
missing, emitted, pattern, output and capacity values match the ordered VM.
Retained failing report: `build/issue218-requester/before/`.

Independent equations cover empty/partial/full supply through 10^1024-1.
An additional fractional-batch case at that magnitude completes in fewer than
100 VM work units, instead of searching all 3402 quantity bits. A seeded
500-case matrix compares every quantity ledger and capacity with ordered
CRAFT_LESS, including intermediate stock, output rounding and alternatives.
Returned-tool accounting also retains the ordered result.

The 500-case matrix caught an existing 468-versus-460-byte discrepancy. It was
reproduced with the new search disabled before the capacity union fix. Both
reports remain in `build/issue218-requester/capacity-investigation/`.
The explicit regression now requires output 60 and capacity 468 for that graph.
No capacity assertion or native oracle was weakened for this optimization.

A real AEProcessingPattern capture runs with world access closed afterward.
Successive independent orders with full, empty, partial and tiny stock validate
that a prior successful quantity is not reused against a different stock view.

The arithmetic benchmark compares fresh order executors, not live server ticks:
two warmups and seven samples, Long.MAX_VALUE with partially funded shared demand.
The ordered executor has optimizations disabled; it is not the installed refs.7
timing baseline. Raw results are printed in the retained test XML reports.

Final arithmetic medians: upstream 45.330 ms ordered / 1.965 ms optimized;
UELM 45.877 ms ordered / 2.023 ms optimized. These exclude Minecraft tick,
snapshot-capture and requester retry time. Work counts are 2085 ordered,
266 pre-change optimized and 1 new optimized for this one synthetic graph.

Both full suites completed with 937 tests, zero failures/errors and two optional
Neo ECO contract skips. Actual supplied EAEP 1.5.5, MEGACells 2.4.6 and AQE 2.2.7
jar contracts passed. Reports, build logs and variant JARs are retained under
`build/issue218-requester/uelm/` and `upstream/`.

Executed with Temurin 17.0.17.10:

```powershell
.\gradlew.bat test verifyIssueRegressionManifest verifyExactVmBundle --no-daemon -Pae2Variant=uelm '-PeaepPatternJar=C:\example\server\mods\extendedae_plus-1.5.5.jar' '-PmegaCellsJar=C:\example\server\mods\megacells-forge-2.4.6-1.20.1.jar' '-PaqeCapacityJar=C:\example\server\mods\aqe2.2.7_1.20.1.jar'
```

Repeated with `-Pae2Variant=upstream` for the final distribution artifact.

## Candidate

- Version: `2.0.0-rc.7-vm-refs.8-local`.
- JAR: `build/distributions/2.0.0-rc.7-vm-refs.8-local/aco2.0.0-rc.7-vm-refs.8-local_1.20.1.jar`.
- SHA-256: `DCD10CB901E10C25BB412EC9CFDEE454925179C8005B16C42A92BFB1DD0F36E5`.
- Size: 1372395 bytes, 4008 bytes above refs.7.
- Matching source archives: the same distribution's `sources/` directory.
- Nested versions: exact VM 0.1.6 and native VM 0.3.12-vm-refs.8.

All 531 outer ACO class files are byte-identical to refs.7. Native class changes
are limited to VmInstructionExecution and its inner types, including the new
capacity shape bookkeeping. No class was removed. The bundle gate verifies
matching nested bytes, sources, versions, bootstrap classes and licenses.
Full audit: `build/issue218-requester/artifact-audit.json`.

## Acceptance boundary

This is a local candidate, not full ACO acceptance. No production installation,
restart, launch, release or user-order performance measurement was performed.
During work the installed server/client files were observed at refs.7 with
SHA-256 `35AB053B5E0255007D23E735896437E432D9D73F00B03BAAA475E9F499B97326`;
this task did not replace them. Wide execution, cancellation/refund, restart
recovery, concurrent-order acceptance and the live sub-10-second target remain
pending. The screenshot's exact original shortage still needs live verification.
