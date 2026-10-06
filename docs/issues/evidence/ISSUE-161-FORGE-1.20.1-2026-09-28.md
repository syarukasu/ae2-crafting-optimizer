# Issue #161: pinned provider regression and intent cleanup

Status: local verification; NOT production or full 2.0.0 acceptance.

## Environment

- Forge 1.20.1 / 47.4.18, JDK Temurin 17.0.17, Windows.
- AMD Ryzen 7 5700X3D, 8 cores / 16 logical processors. Other host work was not
  controlled; timings below are an isolated fixture comparison, not a TPS claim.
- ACO checkout: `C:\example\workspace\aco-rc4-vm\forge-1201`.
- InsaneAE source: `C:\example\workspace\InsaneAE-diag-aco-1201`,
  commit `0b1fa840f115d590d1ab99f0676085aa6970361d` with the pre-existing local
  CraftingTreeNodeMixin change removing `AcoCalculationIntegration.shouldDeferCalculationBatch()`.
  That patch was NOT created or reverted in this change. No production JAR was used in its place.
- InsaneAE 1.0.4 SHA-256: `C0CA68FDA754AE0537622A9A0BB8A9A2D6D7B948958D5E0BF666F2B7CC291752`.
- MEGA Cells artifact 6175008: `19F59CC793950A3FA80DC1B29EF3BDAD587344CD5EFE25C5E010F4DB1EA4A79C`.
- Cloth Config 11.1.136: `1E895E85CF5B1E1905EF3178EC155C8BADFE22A1577B92C09143A5AA1F4CE0F2`.

These three JARs are in `.ci/runtime/issue161`, an ignored local test directory.
They are opt-in gameTestRuntimeOnly dependencies, not bundled mods. AQE, AAC,
Extended AE Plus and Thunderbolt are NOT runtime participants in this fixture.
Bytecode contract checks against their JARs do not change that fact.

## Original regressions

- Without ACO/VM, the original four #102 GameTests pass.
- With the current VM, the unchanged round-robin test and InsaneAE immediate
  provider-pattern test pass. The two original batching tests fail at their
  `CraftingCalculationBatch.batchedCrafts` counter assertions. The VM deliberately
  does not invoke that obsolete planner; preserve the failed XML rather than
  relabel it as four passes.
- New tests run the actual native CraftingCalculation and actual VM against the
  same registered graph while InsaneAE Mixins are loaded. They compare consumed,
  missing and emitted items, pattern counts and bytes, not a source-string match.
- Cake x4096: 12,288 milk buckets, 8,192 sugar, 4,096 eggs, 12,288 wheat;
  4,096 pattern executions, 57,384 bytes. Native and VM results match.
- Diamond x512 with an unavailable alternate gold producer: 4,096 iron,
  2,048 copper-pattern executions and 512 diamond-pattern executions;
  9,248 bytes. Native and VM results match.

Raw evidence: `build/issue161-historical-baseline.{log,xml}`,
`build/issue161-historical-vm.{log,xml}`, `build/issue161-providers.log`.
The provider scope additionally runs the existing real processing completion,
cancellation, competing reservation, returned-container and VM lifecycle cases.
This does not exercise external exact-job receipt recovery or new InsaneAE versions.

## Reproduced cost and fix

RecipeIntentRegistry.record/find called cleanupExpired on every successful push
and machine query. Cleanup walked all provider locations even before the earliest
expiry. Also, a shorter TTL behind a live queue head was incorrectly returned.

Maintain a conservative earliest-expiry bound, skip cleanup before it, and use
bulk removal when due. Preserve coalescing/order/index/capacity and actual dispatch.
No new cache of inventory or pattern decisions, tick budget, logger or worker.

The real registry unit fixture with 1,024 locations and 1,024 record/find pairs:
2,097,152 inspected entries before, zero afterward. The short-TTL tail fixture
fails before and passes after. Preserved XML: `build/issue161-intent-{before,after}.xml`.

Real provider test: six same-tick rounds of 1,024 successful pushes into a chest,
with 1,024 unrelated hints already registered. Every round delivered exactly
1,024 inputs; the real intent hook recorded all 6,144 successful pushes. Full and
blocking-mode targets rejected without consuming the input; clearing resumed immediately.

| Round | Before ms | After ms |
| --- | ---: | ---: |
| First | 131.705 | 61.220 |
| 1 | 94.693 | 38.101 |
| 2 | 78.660 | 36.936 |
| 3 | 67.845 | 42.783 |
| 4 | 63.785 | 25.411 |
| 5 | 63.757 | 27.219 |

Repeated-round median: 67.845 -> 36.936 ms (about 45.6% less elapsed provider work).
This is not end-to-end crafting, server MSPT, production improvement, or a bound
on every possible provider. Initial comparison used dev.14 + the narrow local
change; dev.15 names the resulting candidate. Logs/XML:
`build/issue161-provider-{before,after}.{log,xml}`.

## Replay and remaining gates

Final dev.15 verification:

- Upstream AE2 15.4.10 and UELM 15.5.0: full unit/contract suite, build, manifest
  and VM bundle audits pass. Both execute the named provider/quantity fixtures
  with the pinned add-on JARs loaded. The replay script itself was run on both;
  each native reference and current provider run passes.
- Test logs, XML and JARs: `build/issue161-runtime/{upstream,uelm}/`.
  Replay evidence: `build/issue161-replay/{upstream,uelm}/`.
- Upstream installable JAR SHA-256:
  `3FCE60395D50F8A85FFD06B8A530FCE7D9C372CB48D71E41C8CD51DE14EC43BC`.
- UELM installable JAR SHA-256:
  `D6A90EA5664AE233B7572065ECE4A034551439CA717CE7634DF34D0A93271B12`.
- Both retain exact VM 0.1.3 and native VM 0.3.3-dev.13. No planning or public API
  arithmetic change. Test classes and fixture dependencies are absent from the
  distribution. The shared distribution path is restored to the upstream JAR.
- Strict release gate (`requireRuntimeAcceptance=true`) fails for #161 PENDING,
  as required. Evidence: `build/issue161-runtime/strict-release-gate.log`.
  The ordinary prerelease gate only warns; it is NOT release approval.
- A first UELM attempt failed manifest validation because a partial evidence
  file had been placed in a PENDING row's runtime-evidence column. Restore `-`
  in that column, retain this partial evidence in the issue document, and rerun.
  No test or acceptance rule was relaxed. The failed log is retained separately.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
.\tools\run-provider-regression.ps1 -ModsDir .ci/runtime/issue161 -Variant upstream
```

The runner pins all three JAR hashes and archives both reference and provider
results. Use `-Variant uelm` for that AE2 variant. The raw legacy-counter diagnostic
is deliberately separate (`acoGameTestScope=historical`, baseline=false).

- Extended AE Plus advanced-blocking repeated target scans: bytecode confirmed,
  owner-side optimization and an actual runtime before/after remain pending.
- Thunderbolt/external exact-job isolation, huge CPU runtime and restart receipts:
  not established by this fixture.
- Production empty/partial/full-stock planning timings and creative circuit
  ~922京 under 10 seconds: not measured or claimed by this change.
- BigInteger machine/cycle execution and full lifecycle acceptance remain open.
- No production files, server restart, public release or NeoForge patch.
