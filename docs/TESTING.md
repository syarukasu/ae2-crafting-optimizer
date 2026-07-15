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
22. Place several AE2 Import Buses and Export Buses with speed-card upgrades on a large ME network.
23. Confirm item movement still respects filters, redstone mode, craft-only mode, and scheduling mode.
24. Leave `enableGridTickBudget = false` first and confirm Import Buses, Export Buses, IO Ports, and the ExtendedAE Circuit Cutter behave exactly like AE2/addons normally do.
25. Only for opt-in stress testing, set `enableGridTickBudget = true`, `deferHeavyGridTickables = true`, and `logGridTickBudget = true`, keep `maxIoBusOperationsPerTick = 4096`, and confirm bus operation caps, idle backoff, or slow tickable logs appear only when those devices are active.
26. Configure an Export Bus with a crafting card for an item that cannot currently be crafted and confirm repeated failed requests are cooled down without blocking a later valid craft after stock/patterns change.
27. Test ExtendedAE Ex Import Bus, Ex Export Bus, and Precise Export Bus if ExtendedAE is installed.
28. Test an AE2 IO Port moving high-capacity cells and confirm cells still move to the correct slots and contents are not lost.
29. Test the ExtendedAE Circuit Cutter with normal recipes and auto-export enabled. Confirm recipes still complete and outputs still leave the machine when possible.

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
- No grid tick budget deferral.
- No IO bus operations cap.
- No export-bus-style craft request throttle.
- AE2 behaves as if this mod were absent.
