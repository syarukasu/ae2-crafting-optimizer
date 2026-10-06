# Issue #218: input rules and stocked demand

Date: 2026-10-06. Local candidate only; no publication, deployment or startup.

## Artifact and scope

- ACO: `2.0.0-rc.7-vm-refs.6-local`, built on Temurin 17.0.17.
- Installable JAR: `build/distributions/2.0.0-rc.7-vm-refs.6-local/aco2.0.0-rc.7-vm-refs.6-local_1.20.1.jar`.
- SHA-256: `8029DAC8229A3578F9EED301FF1B8677E3F552AA68C807FCAAE6E9AD99016B88`.
- Size: 1,368,180 bytes, 8,676 bytes above installed refs.5 (1,359,504 bytes).
- Exact VM remains 0.1.6; native VM is 0.3.11-vm-refs.6. Only these two runtime
  JARs are nested. Graph/SMT fixtures and their sources are not distributed.
- Matching VM source archives are in the distribution's `sources` directory.
- All 531 outer ACO classes and the exact VM JAR are byte-identical to installed
  refs.5. API descriptors, physical execution and Pattern Provider dispatch are
  unchanged. Native class changes belong to NativeCraftingRule, NativeVmAccess,
  NativeVmPlanningContext, VmDemandProgram and VmInstructionExecution.
- Machine-readable comparison: `build/issue218-input-stock/artifact-audit.json`.

Installed server/client refs.5 hash, unchanged:
`A3B71D01DCCE7CD5A9D3C0033B15889F35710AFBBB6A673AEC7D47E24A12EEFB`.

## Changes

1. Freeze exact vanilla shaped/shapeless matching rules during existing order
   preparation. Copy plain Ingredient item identities and the encoded grid;
   preserve shifted/mirrored matching and overlapping shapeless alternatives.
   Default Item container returns are copied too. Do not infer custom tool or
   mod recipe behavior, and do not call recipes/items/world from the worker.
2. When the ordered VM encounters an unknown opaque key, observe relevant
   captured inputs for that key in the same existing exclusive interval. Each
   input keeps its own predicate/remainder answer; answers are not transferred
   between recipes. Stop between callbacks when AE2's existing interval expires.
   Do not enumerate future NBT states or recapture inventory.
3. Compile arithmetic summaries ending at positively stocked intermediate
   boundaries. Once a boundary runs out, expand its registered producer again.
   Retain up to 32 stock-shape summaries per pattern/order for reuse, with no
   quantity, work or throughput cap and no cross-order result cache.
4. Apply fully funded integral alternative-producer summaries in bulk, then
   reconsider the same producer. Preserve priorities, all outputs, integer
   rounding, reservation peaks and capacity charges. Fractional one-craft
   periods retain ordered VM execution and its exact replay; no second planner.

The quantity type remains BigInteger. AE2 owns the task pool and live access.
No new solver, executor, public API, rate limit or runtime dependency was added.

## Correctness evidence

- Before implementation, the encountered-key regression required 31 separate
  reads, and the stocked x4000 regression made 3,720 branch trials. Failing XML
  reports are retained in `build/issue218-input-stock/before`.
- `NativeCraftingRuleTest` compares actual AE2 native input predicates, including
  mirrored/shifted grids, overlapping ingredients and keys with Damage, name and
  enchantment NBT. A returned unknown paper key is accepted with world access
  closed. An opaque custom recipe is explicitly not specialized.
- `NativeVmReferenceBatchTest` checks 32 successive inputs observing one newly
  returned NBT key: reads fall from 31 to 1 with identical quantities, one seed
  used and 33 pattern executions. A fake slow callback clock proves grouping
  yields at the existing AE2 read interval instead of holding all peers at once.
  These read counts are deterministic fixture observations, not real wait times.
- The retained finite-tool matrix covers 32/65/257 uses and empty/partial/full
  stock through 10^1024-1 quantities, without guessing durability transitions.
- `VmDemandAggregationTest` checks 400 seeded stock/alternative/byproduct cases
  against the ordered VM, including used/missing/surplus, pattern counts and
  exact capacity. Independent equations cover 1, 2, 4,000, Long.MAX_VALUE,
  Long.MAX_VALUE+1 and 10^1024-1 with empty/half/full raw stock and stocked
  intermediates. The largest quantity is an arithmetic stress case, not a claim
  that its plan fits a CPU with that many bytes.
- Early batching preserved item totals but changed conceptual node capacity
  from 441 to 505 bytes. The final implementation requires an integral one-craft
  summary for alternative batching and retains single-producer bulk ordering.
- An early one-entry stock-shape cache was slower due to recompilation. The
  final bounded per-pattern cache reuses alternating shapes; no failing or
  slower first-attempt result is presented as an accepted artifact.

## Synthetic performance

Stocked, multi-route x4000 graph. The reference is ordered VM arithmetic with
aggregation disabled, not a production refs.5 end-to-end run. Fixture creation
is outside the timing. Eight alternating rounds; discard first two and report
the remaining six medians. Every round compares all quantities and capacity.

| Upstream AE2 15.4.10 | Ordered reference | Candidate |
| --- | ---: | ---: |
| VM arithmetic wall time, median | 230.4873 ms | 39.6791 ms |
| Thread CPU time, median | 218.75 ms | 39.0625 ms |
| Branch trials | 3,720 | 627 |
| Work counter | 11,491 | 964 |

The synthetic arithmetic time is about 5.8 times faster. This excludes actual
server scheduling, mod callbacks, order capture and terminal updates. It does
not predict the supreme-circuit latency or establish the final <10 s target.

Raw measurements: `build/issue218-input-stock/upstream/benchmark.json` and
`build/issue218-input-stock/uelm/benchmark.json`.

## Commands and retained results

From the ACO Forge 1.20.1 directory with JAVA_HOME set to Temurin 17:

```powershell
.\gradlew.bat test verifyIssueRegressionManifest verifyExactVmBundle vmNativeSourcesJar --no-daemon -Pae2Variant=uelm '-PeaepPatternJar=C:\example\server\mods\extendedae_plus-1.5.5.jar' '-PmegaCellsJar=C:\example\server\mods\megacells-forge-2.4.6-1.20.1.jar'
.\gradlew.bat test verifyIssueRegressionManifest verifyExactVmBundle vmNativeSourcesJar --no-daemon -Pae2Variant=upstream '-PeaepPatternJar=C:\example\server\mods\extendedae_plus-1.5.5.jar' '-PmegaCellsJar=C:\example\server\mods\megacells-forge-2.4.6-1.20.1.jar'
.\gradlew.bat test --no-daemon -Pae2Variant=upstream '-PaqeCapacityJar=C:\example\server\mods\aqe2.2.7_1.20.1.jar' --tests '*NativeVmAqeCapacityTest' --tests '*AqeCpuCapacityContractTest'
```

Each full variant run: 923 tests, zero failures/errors, three optional skips.
The AQE actual-JAR skip was subsequently run explicitly: seven capacity/API
contract tests passed with no skips. Neo ECO 20.3/20.4 optional binary checks
remain skipped. Logs, XML and per-variant JARs are retained in
`build/issue218-input-stock/{upstream,uelm,aqe-contract}`. The final installable
distribution uses the upstream build. No Minecraft or GameTest launch occurred.

A final `verifyIssueRegressionManifest verifyExactVmBundle` invocation also
reran the suite without optional external-JAR properties: 923 tests, zero
failures/errors, eight optional skips. Its XML is kept separately in
`build/issue218-input-stock/final-gates`; it does not replace the supplied-JAR
variant evidence above. The rebuilt installable SHA-256 remained unchanged.

## Acceptance boundaries

- Known rule freezing is not support for freezing arbitrary mod callbacks.
  Unknown NBT/custom input validity may still need exclusive AE2 access. No
  assertion that all INPUT_VALIDITY waits are gone is made.
- Production supreme-circuit and original fine-copper-wire false-shortage cases
  have not been re-run with this candidate. Test agreement does not settle them.
- Wide execution, cancellation/refund, restart recovery, concurrent-order
  ownership and the creative-circuit <10 s goal remain pending.
- The separate live AdvancedAE/Thunderbolt extraction-failure/reinjection tick
  stall was not changed or declared fixed by these planning changes.
- No stable 2.0.0 acceptance, startup success or GC/black-screen fix is claimed.

## Primary references

- [AE2 AECraftingPattern](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/pattern/AECraftingPattern.java): native substitution and return ownership.
- [GTCEu IGTTool](https://github.com/GregTechCEu/GregTech-Modern/blob/v7.5.3/src/main/java/com/gregtechceu/gtceu/api/item/IGTTool.java): custom crafting remainder/durability hooks must not be replaced with a sampled formula.
- [LLVM loop terminology](https://github.com/llvm/llvm-project/blob/main/llvm/docs/LoopTerminology.rst): loop-invariant rule specialization as a reference, not a new dependency or a proof of recipe equivalence.
