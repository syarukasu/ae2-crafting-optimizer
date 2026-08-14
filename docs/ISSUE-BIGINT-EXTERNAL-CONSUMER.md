# BigInteger external-consumer regression record

## Symptom

An order above `Long.MAX_VALUE` could be calculated through the direct ACO
API, but the real AE2 `CraftingService.beginCraftingCalculation` path could
return a saturated `CraftingPlan` with no exact sidecar. An external CPU then
saw `Long.MAX_VALUE`, rejected a valid order as `CPU_TOO_SMALL`, or lost the
exact remainder. A second symptom was a Quantum CPU receiving
`maxPatterns = 1` and therefore processing only one physical recipe operation
per tick even though its Bulk provider could accept a much larger bounded
window.

## Root causes

- AE2 can rebuild or wrap the returned `CraftingPlan`; the sidecar was attached
  only to the authoritative calculation object.
- The exact Quantum path incorrectly used AE2's logical operation budget as
  the physical Bulk capacity.
- ACO had an InsaneAE-specific execution branch, which blurred ownership and
  made the two mods compete for the same execution state.

## Required fix

- ACO keeps the exact BigInteger plan in a sidecar and aliases that sidecar to
  a rebuilt AE2 plan returned from `CraftingCalculation.run`.
- ACO exposes only the public plan/sidecar API and an explicit external-consumer
  registration method.
- InsaneAE registers itself, reads the exact sidecar, and owns Quantum CPU
  execution. Its `BulkExecutionWindow` converts only one physical window to a
  lossless `long`; the BigInteger remainder stays exact.
- AQE registers itself through its optional adapter and keeps its own CPU host
  and job processing.
- If no external consumer is registered, a wide plan is declined without an
  exception or fake saturated execution; AE2 owns the normal fallback.

## Invariants

- Exact plan, missing amounts, capacity, and remainder are never clamped to
  `long` as the source of truth.
- A physical window is positive, bounded by provider capacity, and accounted
  for exactly once.
- A declined external plan is not submitted a second time by the external
  consumer.
- Normal small AE2 orders retain the original AE2 result and execution path.
- ACO contains no InsaneAE-specific execution Mixin or Quantum CPU loop.

## Verification

The independent tests cover exact window selection, `Long.MAX_VALUE` windows,
BigInteger remainder conservation, external registration, sidecar aliasing,
and the ownership boundary. Forge 1.20.1 and NeoForge 1.21.1 are built and
tested separately; game startup and in-world GameTest execution remain an
operator-side step.

## GitHub tracking

- ACO Issue [#44](https://github.com/syarukasu/ae2-crafting-optimizer/issues/44): long overflow must not silently return to the saturated AE2 path.
- ACO Issue [#55](https://github.com/syarukasu/ae2-crafting-optimizer/issues/55): NeoForge duplicate-calculation cancellation must not cancel a shared owner.
- InsaneAE Issue [#6](https://github.com/syarukasu/InsaneAE/issues/6): external ACO plan handoff and Quantum CPU execution boundary.
- AQE Issue [#23](https://github.com/syarukasu/advanced-quantum-engineering/issues/23): optional ACO BigInteger host integration.
