# Final Engine Research

This document records the exact upstream code used for ACO's experimental engine work. It intentionally separates source-level verification from runtime qualification.

## Fixed target versions

| Project | Version | Source tag | Relevant artifact |
| --- | --- | --- | --- |
| Applied Energistics 2 | 15.4.10 | `forge/v15.4.10` | `appeng:appliedenergistics2-forge:15.4.10` |
| Advanced AE | 1.3.5-1.20.1 | `1.3.5-1.20.1-forge` | CurseForge project 1084104, file 7939754 |
| GregTech CEu Modern | 7.5.3 | `v7.5.3-1.20.1` | `com.gregtechceu.gtceu:gtceu-1.20.1:7.5.3` |
| Mekanism | 10.4.16.80 | `v1.20.1-10.4.16.80` | `mekanism:Mekanism:1.20.1-10.4.16.80` |

ACO declares the three add-ons as optional. Their integration classes must not be initialized unless the matching mod and supported version are present.

## AE2 15.4.10

- `appeng.crafting.CraftingCalculation` constructs the standard `CraftingTreeNode` planner.
- `appeng.crafting.CraftingTreeNode` and `CraftingTreeProcess` use `long` counts and contain unchecked multiplication/addition in the original implementation.
- `appeng.crafting.execution.ExecutingCraftingJob` stores pattern task counts as `long` values and persists them as NBT longs.
- `appeng.crafting.execution.CraftingCpuLogic` dispatches one pattern execution at a time and accounts expected outputs in `waitingFor`.
- `appeng.helpers.patternprovider.PatternProviderLogic.pushPattern` may place a remainder in the provider send buffer. A `true` result therefore means that the provider owns the complete input set, not that the adjacent machine inserted every unit immediately.
- Standard AE2 remains authoritative unless an ACO path can prove exact pattern semantics, durable ownership, and exact accounting.

## Advanced AE 1.3.5

- Quantum Computer execution uses `net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic` and `AdvCraftingCPU`.
- A Quantum Computer cluster exposes multiple active CPUs through `getActiveCPUs()`.
- Advanced AE's executing job still stores task and waiting counts as `long`.
- ACO must keep BigInteger capacity and job state in a versioned sidecar and expose bounded execution windows to the original long-based executor.
- Capacity reservation must be atomic across all active CPUs in one Quantum Computer cluster.

## GTCEu 7.5.3

- Native parallel calculation is `com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic`.
- `ParallelLogic.getParallelAmount(MetaMachine, GTRecipe, int)` checks input capacity and output merging.
- `ParallelLogic.getMaxByInput(...)` and `limitByOutputMerging(...)` are public components of that calculation.
- Recipe lifecycle is owned by `com.gregtechceu.gtceu.api.machine.trait.RecipeLogic`.
- `RecipeLogic.findAndHandleRecipe`, `checkRecipe`, `setupRecipe`, and `handleRecipeIO` must remain authoritative for conditions, voltage, cleanroom state, capability IO, and output ownership.
- `GTRecipe.parallels`, `subtickParallels`, and `batchParallels` are `int`; larger ACO jobs must be split into bounded windows.
- Recipes with probabilistic outputs or unverifiable capabilities are not eligible for the exact native fast path.

## Mekanism 10.4.16.80

- Processing is owned by `mekanism.api.recipes.cache.CachedRecipe`.
- `CachedRecipe.process()` builds an `OperationTracker` from `baselineMaxOperations`, validates energy/input/output constraints, and only then finishes operations.
- Machines and factories use `RecipeCacheLookupMonitor` or `FactoryRecipeCacheLookupMonitor`; factories have one cache per process.
- ACO must not invoke `process()` speculatively and must not duplicate `OperationTracker` logic.
- Native eligibility requires an exact recipe match and a supported cache monitor. Item, fluid, gas, infusion, pigment, and slurry keys must stay in their native medium.
- Unsupported recipes, inaccessible monitors, and ambiguous factory routing fall back to Mekanism and AE2's original paths.

## Safety boundary

The previous prototype adapters multiplied AE2 inputs and called `pushPattern` without consulting GTCEu `ParallelLogic` or Mekanism `CachedRecipe`. Those adapters are not considered native integration and must not be registered. The 1.3.1 V2 adapters replace that prototype boundary: they require an exact typed recipe match, query the verified native operation/output limit, persist an all-or-zero receipt on Pattern Provider logic, and fall back before source mutation when proof is unavailable.

A completed adapter must satisfy all of the following:

1. It resolves one exact machine recipe from the AE2 pattern.
2. It rejects alternatives, container returns, chance outputs, unsupported conditions, and ambiguous targets unless explicitly supported.
3. It obtains a bounded operation limit from the target mod's real execution model.
4. It transfers ownership durably before decrementing AE2 tasks.
5. It records accepted executions exactly once and survives a save between every transaction phase.
6. It falls back without mutation when any proof is unavailable.

The Pattern Provider's persisted send buffer is the durable owner after a
successful aggregate `pushPattern`. A receipt on the machine alone would be
incorrect because the provider may retain a remainder that the adjacent machine
cannot accept immediately. V2 therefore stores target acceptance evidence on
the exact standard or Advanced AE Pattern Provider logic instance.

BigInteger counts cannot be inserted into AE2's long-based job structures.
ACO's implementation keeps them in a versioned sidecar runtime and exposes only
bounded execution leases. Standard AE2 remains untouched; an integrating CPU
add-on must explicitly own the runtime, persistence, and UI entry point.

## Runtime qualification

`clean test` and `clean build` verify compilation and pure-Java invariants. They do not prove Forge Mixin compatibility, machine capability behavior, chunk unload recovery, or multiplayer packet compatibility. Those require the manual matrix in `docs/TESTING.md` and are not to be enabled or deployed before that matrix passes.
