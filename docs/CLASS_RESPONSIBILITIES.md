# Class Responsibilities

This file records the NeoForge 1.21.1 ownership boundary for the BigInteger
integration. The full implementation remains in the source packages; this
document lists the classes that must not be confused with an external CPU
executor.

## ACO BigInteger boundary

| Class | Responsibility |
|---|---|
| `api.big.BigCraftingEngineApi` | Public BigInteger plan API and external CPU-consumer registration. It does not execute an external CPU. |
| `engine.Ae2CraftingPlanSidecars` | Associates an exact BigInteger plan with an AE2 `CraftingPlan` identity, including a rebuilt facade. |
| `mixin.CraftingCalculationDiagnosticsMixin` | Re-attaches the exact sidecar on the real AE2 calculation return path. |
| `mixin.CraftingCpuClusterBigCapacityGuardMixin` | Checks external-consumer registration and exact plan availability at submission. It owns no progress or execution state. |

## Ownership rule

ACO owns planning, exact accounting metadata, sidecars, bounded-window data,
and diagnostics. InsaneAE owns Quantum CPU execution, Bulk dispatch, progress,
receipts, cancellation, and completion. AQE owns its own CPU host and jobs.
ACO must not add an InsaneAE execution Mixin or take ownership of either mod's
GUI, structure, power, recipe, or persistence logic.
