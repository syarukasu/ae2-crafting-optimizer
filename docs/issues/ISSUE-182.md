# Issue #182: External Physical Crafting API Loader Parity

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/182
- Related Forge implementation: commit `61e9655406683493f26ab27e07b9a3353c356399`, PR #183
- Status: Verified (automated acceptance); external CPU runtime acceptance remains PENDING.
- Scope: NeoForge 1.21.1 / ACO 2.0 development branch, same public contract as Forge.

## Evidence and Root Cause

Forge exposes `BigCraftingPhysicalExecution.API_VERSION = 1`, but the NeoForge
branch has neither that facade nor its persisted accounting accessor. External
consumers cannot use that contract on NeoForge. The underlying receipt-backed
transaction, escrow and vector API already exist on both loaders.

## Minimal Fix and Ownership

Port the Forge facade, not a second execution engine. Reuse the existing physical
transaction and the NeoForge AEKey/registry codec. Keep all public signatures and
the version-1 persisted wrapper unchanged.

The consumer owns its CPU, ticking, link lifecycle, capacity, persistence and
cancellation. ACO does not reference AQE, AAC or InsaneAE implementation classes.
Before custody, reject missing workers or exact storage routes. After custody,
retain receipts and escrow through completion, cancellation or quarantine; never
switch to legacy dispatch or generate outputs from planned quantities.

## Acceptance

- Same public signatures as Forge; no new configuration or executor.
- Exact wide pending counts and cancellation state survive save/restore.
- Unknown persisted schema fails explicitly.
- Existing virtual-order, escrow and accounting tests remain green.
- Build and regression manifest pass. External CPU gameplay completion is not
  inferred from these tests and remains PENDING.

## Pre-Implementation Check

- [x] Read charter, regression history, class responsibilities and workflow.
- [x] Compared Forge API with NeoForge transaction and registry codec.
- [x] Existing escrow, accounting, Issue #125 and overflow tests passed before editing.
- [x] No external project, mods, config, world, GUI or native storage mutation changes.
- [x] Only port the existing main persistence test and unknown-schema rejection.

## Results

- API version 1 public signatures match in both built JARs (`javap -public`).
- The real AEItemKey/NBT test preserves 10^40 pending quantities and cancellation state
  without treating unreceived outputs as inventory. Unknown-schema rejection passes.
- NeoForge's registry/config fixture was adapted to the actual loader API; no product
  fallback, new executor, configuration layer or testing framework was introduced.
- Forge: 117 suites / 496 tests / 2 skipped. NeoForge: 125 suites / 516 tests.
  Zero failures and errors; both `clean build verifyIssueRegressionManifest --no-build-cache` pass.
- Existing position-independent wide virtual-order and receipt/escrow recovery tests pass.
- Standard CPU snapshot-capture staleness is fixed separately under Issue #125.
- Actual external CPU completion and runtime performance remain PENDING. No release,
  deployment or external project edit was performed.
