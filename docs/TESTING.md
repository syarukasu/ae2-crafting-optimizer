# Testing

## Build

```bat
gradlew.bat clean build
```

Expected:

- Build succeeds.
- Jar is created under `build/libs/`.
- No generated `build/` or `.gradle/` directories are committed.

## Manual Runtime Checks

1. Start a Forge 1.20.1 client or dedicated server with AE2 15.4.10.
2. Confirm `ae2_crafting_optimizer-server.toml` is generated.
3. Request a small craft, such as a normal furnace-pattern output.
4. Confirm the craft confirmation screen leaves `Calculating` normally and shows AE2's standard result.
5. Request a large craft that cannot complete due to missing ingredients.
6. Confirm AE2's normal missing-item result appears without any preliminary preview behavior.
7. Enable `logCraftingCalculationDeduplication = true`, request the exact same large craft twice before the first calculation finishes, and confirm the second request logs an active calculation reuse.
8. Change a pattern provider or crafting node and confirm no stale calculation is reused afterward.
9. With `cacheCompletedCraftingPlans = true` and `cacheSuccessfulCompletedCraftingPlans = false`, repeat the same impossible request and confirm the missing/simulation result can be reused without allowing a craft to start.
10. Add/remove storage or change pattern providers and confirm the completed-plan cache is cleared.
11. Enable `fastFailMissingCrafts = true`, request an impossible large deterministic one-path craft, and confirm the result is a normal missing-items plan rather than a stuck calculating screen.
12. Repeat with a craft that has substitutions, tags, multiple possible patterns, or can complete successfully, and confirm AE2 performs its normal calculation.
13. Enable `logPatternLookupCache = true`, perform repeated large craft calculations, and confirm pattern or craftable-set cache hits appear without stale recipes after provider changes.
14. Push an AE2 processing pattern into a GTCEu item-output machine and confirm the recipe still starts normally.
15. Push an AE2 processing pattern into a GTCEu fluid-output machine and confirm the recipe still starts normally.
16. Push an AE2 processing pattern into a Mekanism item-output machine and confirm the recipe still starts normally.
17. Push an AE2 processing pattern into a Mekanism fluid-output or chemical-output machine and confirm the recipe still starts normally.
18. Run `/aco intents list 10` shortly after a Pattern Provider push and confirm target position and outputs are recorded.
19. Run `/aco intents clear` and confirm captured intents and machine output indexes are cleared.
20. With `throttleCraftingExecution = true`, start a large craft on a high-co-processor CPU and confirm the craft progresses without one CPU monopolizing server tick time.
21. With `adaptiveCraftingExecutionBudget = true`, keep `targetCraftingExecutionMillis = 4` and confirm repeated heavy crafts do not create sustained MSPT spikes.
22. Start large jobs on at least three standard AE2 crafting CPUs attached to one ME grid, leave `sharedCraftingExecutionBudget = true`, and confirm every job progresses while combined CPU execution remains near the configured grid target.
23. Run `/aco stats` and confirm shared-budget limits/deferred operations increase during that test.
24. Repeatedly process the same Pattern Provider recipe through GTCEu and Mekanism, then confirm `/aco stats` reports machine cache hits.
25. Run `/reload`, repeat one GTCEu and one Mekanism processing recipe, and confirm the machines rebuild their indexes and still select the correct recipes.
26. Place several AE2 Import Buses and Export Buses with speed-card upgrades on a large ME network.
27. Confirm item movement still respects filters, redstone mode, craft-only mode, and scheduling mode.
28. Leave `enableGridTickBudget = false` first and confirm Import Buses, Export Buses, IO Ports, and the ExtendedAE Circuit Cutter behave exactly like AE2/addons normally do.
29. Only for opt-in stress testing, set `enableGridTickBudget = true`, `deferHeavyGridTickables = true`, and `logGridTickBudget = true`, keep `maxIoBusOperationsPerTick = 4096`, and confirm bus operation caps, idle backoff, or slow tickable logs appear only when those devices are active.
30. Configure an Export Bus with a crafting card for an item that cannot currently be crafted and confirm repeated failed requests are cooled down without blocking a later valid craft after stock/patterns change.
31. Test ExtendedAE Ex Import Bus, Ex Export Bus, and Precise Export Bus if ExtendedAE is installed.
32. Test an AE2 IO Port moving high-capacity cells and confirm cells still move to the correct slots and contents are not lost.
33. Test the ExtendedAE Circuit Cutter with normal recipes and auto-export enabled. Confirm recipes still complete and outputs still leave the machine when possible.
34. Process several recipes in an AdvancedAE Reaction Chamber, including item and fluid outputs, and confirm input consumption, energy use, output, and auto-export remain exact.
35. Install/remove AE2 Overclock overclock and parallel cards in a Reaction Chamber and Circuit Cutter. Confirm the new count applies no later than the next server tick and the configured speed/parallel behavior remains correct.
36. With several Import and Export Buses placed before and after expensive grid tickables, run them for at least ten minutes and confirm every bus continues moving items under load.
37. Fill an Export Bus target, observe failed simulations, then free one slot and confirm transfer resumes on the next tick rather than remaining negatively cached.
38. Change or break the adjacent inventory during active transfer and confirm the capability cache follows the replacement Block Entity no later than the same/next server tick.
39. Add/remove Pattern Providers and immediately request their recipes. Confirm provider changes are visible and no stale craftable recipe remains.
40. Open a large ME terminal while storage changes rapidly. Confirm the final view matches storage after each client tick and extraction remains live.
41. Run two ExtendedAE Circuit Cutters with different item/fluid inputs. Confirm each selects the correct recipe, a blocked output remains blocked, and changing input resumes the correct recipe without restart.
42. Repeat a no-recipe Circuit Cutter input on two machines, add the missing datapack recipe, run `/reload`, and confirm both machines immediately discover it.
43. Put valid importable items near the end of a large external inventory, move them between slots, and confirm the Import Bus first-hit hint never prevents the fallback full scan.
44. Change Export Bus filters repeatedly while it is active and confirm each new key takes effect immediately.
45. Fill all six IO Port input slots, use `ioPortCellSlotsPerTick = 2`, and confirm the cursor eventually services and ejects every cell without item loss.
46. With `cacheAdjacentCapabilitiesAcrossTicks = true`, replace and invalidate adjacent item/fluid handlers and confirm buses bind to the new capability.
47. With `asyncTerminalSearchSort = true`, search a terminal above the threshold using plain, `@`, `#`, `$`, and `*` terms; change the query rapidly and confirm only the newest generation appears.
36. Form an ExtendedAE Assembly Matrix containing several crafter blocks, pattern blocks, and speed blocks. Start enough jobs to use multiple internal threads and confirm every job completes.
37. If ExtendedAE Plus is installed, include its 32-thread crafter core and confirm the matrix busy count and scheduling still use all supported threads.
38. Break and reform that matrix, reload its chunks, and restart the server. Confirm formation, patterns, busy state, names, and stored jobs recover normally.
39. Run `/aco stats` after the add-on tests and confirm runtime-helper reflection, upgrade-count, Reaction Chamber, Circuit Cutter, or Assembly Matrix counters increase for the installed integrations. Direct redirects into machine methods merged by AE2 Overclock are compatibility-disabled in 1.1.0.
40. Disable each `[addonMachineOptimizations]` sub-option separately, restart, and confirm its counter stops increasing while the machine remains functional through the original add-on path.

## Pattern Micro-Batch Checks

1. Keep `enablePatternMicroBatching = false`, run a repeated GTCEu and Mekanism processing craft, and record output counts and `/aco stats` as a baseline.
2. Enable it with `maxPatternExecutionsPerMicroBatch = 64` and a Pattern Provider exposing exactly one target side.
3. Request at least 256 identical executions and confirm inputs consumed, AE energy charged, outputs received, and CPU task progress exactly match the baseline.
4. Confirm `/aco stats` reports aggregate pushes and the represented execution count.
5. Repeat with item, fluid, and chemical-containing processing patterns supported by the target machine.
6. Test a full target inventory. The aggregate must be rejected and AE2's original single-execution path must continue without duplication or loss.
7. Test a crafting pattern/Molecular Assembler, a pattern with a returned container, a blocking Pattern Provider, lock-until-result mode, multiple provider sides, and an Advanced AE directional pattern. Every case must remain on the original path.
8. Place the Provider against a GT input bus or fluid hatch whose controller is up to 16 blocks away. Confirm `/aco intents list` shows the execution count and GT starts the intended recipe while still rejecting invalid live inputs.
9. Test a standard AE2 CPU and an Advanced AE Quantum Computer separately. Both must progress; the Advanced AE menu must open without a class-loading or Mixin error.
10. Disable `enablePatternMicroBatching` again and confirm the same saved jobs continue through AE2's original execution path after restart.

Optional disabled-by-default checks:

1. Open an ME terminal on a large network with `throttleTerminalInventorySnapshots = true` and confirm visible amounts update every few ticks while extraction/insertion still uses live AE2 storage.
2. Enable `throttleStorageWatcherUpdates = true`, use `storageWatcherUpdateIntervalTicks = 4`, and confirm terminal visible amounts update with small delay but no item loss or insertion/extraction change.

## Deep Rewrite Checks

1. Enable the deep master switch and all six feature switches, then repeat one normal item craft and one normal fluid processing craft.
2. Give one output two valid patterns, toggle `patternSelectionByAvailability`, and confirm only the attempted order changes while AE2's success/missing result remains the same.
3. Set `networkUpdateIntervalTicks = 2`, mutate storage continuously, and confirm monitors/terminals converge within two ticks while direct insert/extract amounts remain exact.
4. Open a terminal containing more than `terminalRangeEntriesPerTick` keys and confirm entries arrive over multiple ticks, searching works after synchronization, and no entry remains missing.
5. Add/remove and retune P2P tunnels several times in one tick. Confirm every structural change applies, then toggle grid power and confirm duplicate wake sweeps do not alter the final input/output topology.
6. Use a fuzzy-card Export Bus while variants enter and leave storage. Confirm valid variants still export and newly added variants are visible after at most `busFuzzySearchCacheTicks`.
7. Craft with one exact fluid input and then with fluid substitutions. Confirm the exact case uses the fast path and substitutions fall back to AE2.
8. Disable each deep sub-switch separately and repeat its test to confirm behavior returns to the original AE2 path.

## Disable Checks

Set:

```toml
[general]
enableOptimizer = false
```

Expected:

- No preliminary missing preview behavior.
- No storage watcher throttling.
- No active calculation de-duplication.
- No completed-plan cache.
- No deterministic missing fast-fail.
- No pattern lookup cache.
- No craftable-set cache.
- No terminal snapshot pacing.
- No crafting execution budget cap.
- No adaptive crafting execution pacing.
- No shared per-grid crafting execution pacing.
- No grid tick budget deferral.
- No IO bus operations cap.
- No AdvancedAE/ExtendedAE/AE2 Overclock machine caches.
- No export-bus-style craft request throttle.
- No pattern micro-batching or Advanced AE Pattern Provider intent capture.
- AE2 behaves as if this mod were absent.
