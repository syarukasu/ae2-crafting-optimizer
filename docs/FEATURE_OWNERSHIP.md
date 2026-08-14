# Feature Ownership

This document is the ownership boundary for the BigInteger integration. It is
also a guard against reintroducing the execution ownership bugs recorded in
`ISSUE-BIGINT-EXTERNAL-CONSUMER.md`.

## ACO Owns

- AE2 recipe/provider generation tracking and deterministic planning.
- The exact `long`/`BigInteger` calculation result, including missing amounts.
- The public BigInteger plan API and plan sidecar association.
- Exact plan inspection and diagnostics for a plan that AE2 represents with a
  saturated `long` facade.
- Lossless conversion of a BigInteger remainder into one bounded physical
  execution window.
- Its own generic AE2 optimization paths and their fallback rules.

ACO does **not** own an external CPU's crafting loop, Pattern dispatch,
`waitingFor`, progress, cancellation, output receipt, power accounting, or
job completion. ACO never creates a second execution ledger for an external
CPU and never treats a saturated AE2 value as an exact amount.

## AE2 Owns

- Encoded Pattern identity and recipe eligibility.
- The standard calculation service and normal `CraftingPlan` lifecycle.
- Standard CPU selection, job submission, inventories, links, and normal
  provider routing.
- Standard insertion/extraction when no external exact consumer accepts the
  plan.

## InsaneAE Owns

- Quantum CPU structure, CPU selection, and one-CPU/one-job rules.
- Quantum CPU execution, Bulk/Task Fusion dispatch, provider backpressure,
  power use, progress, `waitingFor`, receipts, persistence, cancellation, and
  completion.
- Registration as an external ACO BigInteger plan consumer.
- Its own bounded `long` execution windows and exact BigInteger remainder.

ACO may expose the plan and API to InsaneAE, but must not add an InsaneAE
execution Mixin, inspect InsaneAE internals, or submit work on its behalf.

## AQE Owns

- Advanced Quantum Engineering CPU structures, hosts, capacity calculation,
  CPU selection, active jobs, progress, persistence, and completion.
- Its optional reflective ACO adapter. The adapter only registers AQE as an
  external plan consumer and reads the public ACO contract.

AQE and ACO remain optional dependencies. ACO must still load without AQE;
AQE must still load without ACO and use its native exact backend.

## External Consumer Contract

An external consumer must:

1. register through `BigCraftingEngineApi`;
2. read the exact plan/sidecar rather than the saturated AE2 `long` field;
3. compare the exact requested capacity with its own exact CPU capacity;
4. execute only bounded physical windows;
5. retain the BigInteger remainder and reconcile the actual accepted amount.

If any of these proofs is unavailable, the consumer declines the plan and AE2
handles the normal path. Declining must not throw from the AE2 calculation
thread and must not create a `+1` fallback, duplicate output, or lost input.

## Forbidden Changes

- ACO-side Quantum CPU execution, Task Fusion, or progress replacement.
- ACO-side InsaneAE/AQE structure, GUI, texture, recipe, power, or NBT
  ownership.
- Converting the exact plan to `long` before choosing a physical window.
- Returning a saturated plan without preserving its exact sidecar.
- Running the same plan once through an external consumer and again through
  AE2 because the external consumer declined it.
