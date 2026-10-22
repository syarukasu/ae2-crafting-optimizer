# Issue #227: BigInteger processing-pattern requests

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/227
- Status: Ready
- Target: NeoForge 1.21.1, release/2.0.0-rc.5-1.21.1 at 2c8f919dab0bc5c0ae2adec2523dc685412cd03d
- Related: #182, #190, #164

## Problem and evidence

ExactPatternFormula.tryCreate rejects every non-assembler pattern. Physical
preflight, selected-branch validation and dispatch only admit crafting-table
targets. Batch V2 uses long counts and is not a substitute for a wide request.
An external processing machine cannot currently receive a wide exact job.

## Ownership and invariants

AE2 owns pattern definitions and providers. ACO keeps its existing exact escrow,
receipt validation, parent transaction, cancellation and persistence. The addon
owns real recipe validation, catalysts, machine energy and physical progress.
Processing workers must implement the same escrow-backed receipt semantics:
no separately delivered inputs or outputs, no results synthesized from estimates,
durable identity before acceptance and idempotent reconciliation/cancellation.
Unsupported machines decline before ownership. Never use Batch V2 as a fallback
after ownership, narrow totals, infer omitted catalysts, or replace addon execution.

## Design

- Preserve all existing public BigInteger/crafting-table/Batch V2 signatures.
- Add api.processing.ProcessingPatternBatchRequest and ProcessingPatternBatchTarget.
  Reuse validated exact request accounting and the existing receipt snapshot value
  type, not another ledger/state machine. The new target interface is independent
  of CraftingTableBatchTarget; an internal bridge adapts its lifecycle.
- Keep ExactPatternFormula.tryCreate's old crafting-table-only behavior. Add an
  explicit execution formula entry that also validates processing inputs/outputs.
- Update only exact physical admission, binding validation and dispatch to use it.
  Route processing patterns exclusively to opted-in processing targets, never to
  old crafting-table workers. Target support checks are read-only and do not
  change work already accepted.
- The deterministic vector and selected-branch builders also have table-only
  guards. Add explicit physical-execution entry points and use them only at the
  receipt-backed execution boundary. Keep legacy table-only entry points intact.
  The existing persisted step contract supports at most nine selected inputs;
  this change does not extend that schema to expanded processing patterns.
- An accepted processing target that disappears or loses its receipt is an
  uncertain outcome, not permission to refund or redispatch. Quarantine it.
- Preserve all pattern keys (including components), slot ordering, fluids,
  byproducts and exact totals. Machine slots/catalysts are target responsibilities.
- Forge is a separate follow-up port; no Forge/source, production or OAE changes
  in this task. No new transport/tick limits or configuration settings.

## Pre-implementation checklist

- [x] Read PROJECT_CHARTER, REGRESSION_HISTORY, CLASS_RESPONSIBILITIES and ISSUE_WORKFLOW.
- [x] Read #182/#190 ownership contracts and existing physical lifecycle tests.
- [x] Identified reproducible rejection at capturePatternAccounting for non-assembler patterns.
- [x] Keep old APIs and ownership boundaries; exact selected-input validation remains required.
- [x] Defined tests and platform scope before implementation.

## Verification plan

First add a non-assembler formula/accounting regression and observe failure.
Then test 1, Long.MAX_VALUE, Long.MAX_VALUE+1, 10^64 and AQE-scale quantities
through the production physical transaction with boundary doubles. Check exact
input/output maps, multiple outputs, fluid/component keys, legacy-target rejection,
mixed crafting/processing, cancelled and completed jobs, reload between transitions,
and wrong/lost receipts. Existing crafting-table lifecycle tests must still pass.
Compare public descriptors of old APIs, run tests/build/regression manifest.
OAE itself, packaged runtime and actual crash/restart acceptance remain PENDING.

## Results

## Authorized prerelease follow-up

Status for release preparation: Ready. The user authorized separate GitHub
prereleases for Forge 1.20.1 and NeoForge 1.21.1, with developer integration docs.
NeoForge version: `2.0.0-rc.5-processing.1`; tag: `v2.0.0-rc.5-processing.1-1.21.1`.
Add a release scope for #227, rebuild and rerun the automated release gate,
preserve runtime PENDING, then publish source, checksums and the installable JAR.
No production deployment, stable release or external machine certification.
This follow-up changes release metadata and documentation, not execution semantics.

## Local candidate results

Implemented locally on `feature/227-processing-bigint-1211`.

- Before: the non-assembler accounting regression failed with
  `PatternIdentityConflictException: physical crafting pattern is not deterministic`.
- After: eleven processing-boundary tests pass. The production transaction accepts
  `Long.MAX_VALUE + 1` executions as one request, not long windows. Mixed DAGs
  produce exactly the expected final count and byproducts; reload occurs after
  every transition tick. Larger cases include `10^64` and `floor((10^1024-1)/64)`.
- Native AE2 processing pattern encoding preserves fluid keys, custom components,
  sparse slots and all outputs. Cancellation returns either unused input or
  completed output, never both. Wrong/lost receipts and unconfirmed rollback
  quarantine without refund or duplicate dispatch.
- Public CPU preparation performs target and accounting preflight with zero
  inventory mutations. Non-opted-in targets are rejected before ownership.
- `build verifyIssueRegressionManifest` passes: 665 tests, 664 passed, 1 skipped.
  The skipped existing AQE bytecode-constant check needs `-PaqeCapacityJar`; no
  corresponding AQE JAR was supplied in the build dependency directory.
- `javap -public -s` comparison against baseline-compiled classes preserves all
  public declarations and descriptors from 92 existing API class files.
- Candidate: `aco2.0.0-rc.5-processing.1-local_1.21.1.jar` (1,206,355 bytes).
  SHA-256: `d3812a7fe9c1e53009e28f8360fbf06822cc4e01bfafc7d4f5bc1ed0ba3a0b66`.

Evidence is collected in `build/distributions/2.0.0-rc.5-processing.1-local/`:
build log, regression XML, before-failure XML and public API descriptor report.
No production placement, GitHub code push or release was performed at the local-candidate stage.
External OAE/AQE execution, packaged game startup and true cross-owner disk crash
recovery remain PENDING. The tested worker/storage/world boundaries are doubles,
not a claim that an unmodified processing machine can execute BigInteger jobs.

## Prerelease validation

`2.0.0-rc.5-processing.1`: build and scoped release gate passed. Runtime #227
remains PENDING. Public API descriptors: 92 baseline classes unchanged.
Installable JAR SHA-256: `897791048c86d647470c6708b8ed9e938a6d173d23e11e3ae1973d2a58393346`.
Developer guide and release notes explicitly distinguish native machine work from
ACO escrow, and contract verification from external-machine acceptance.
