# Issue #182: External physical crafting API for AQE

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/182
- Related: syarukasu/advanced-quantum-engineering#33
- Status: Ready
- Scope: Forge 1.20.1 / ACO 2.0 development branch. NeoForge port is not part of this PR.

## Evidence

Commit 4633028 removed the AQE submit/execute hooks. AQE 2.2.7 registers a
consumer but only manages capacity. The reported twenty-stage order reaches
the crafting status UI but stops; planning completion is not execution proof.
Installed JAR inspection confirms the missing AQE handoff and its separate
zero-job-count display defect.

## Ownership and Minimal Change

Expose the existing PhysicalCraftingTreeTransaction through a versioned,
CPU-independent API. Prepare from the attached exact plan after pattern,
storage and worker preflight. Reuse the existing transaction, receipt, escrow,
recovery and grid budget code; do not introduce another executor.
AQE owns submission, ticking, capacity, link completion, UI, persistence and
cancellation of its CPU. ACO never references Advanced AE or AQE classes.
Ordinary AE2 jobs and their existing manager are unchanged.

Before input transfer a bad plan is rejected. After transfer retain the
transaction, resume or cancel with receipt-backed return; never fall back to
legacy dispatch. No generated outputs based solely on planned amounts.

## Acceptance and Tests

- Public state retains exact amounts and persisted receipt progress.
- Completion is reported only after actual output return.
- Existing receipt/accounting tests run first; add only one API persistence
  and receipt-accounting regression, plus unknown-schema rejection.
- Build and regression manifest validation. No startup, deployment or world changes.
- Live acceptance remains PENDING; do not close as gameplay verified.

## Pre-Implementation Check

- [x] Read PROJECT_CHARTER, REGRESSION_HISTORY, CLASS_RESPONSIBILITIES and ISSUE_WORKFLOW.
- [x] Read physical transaction, previous removed AQE integration and current public APIs.
- [x] Confirmed optional dependency, ownership and pre-transfer rejection boundaries.
- [x] No new framework, configuration layer or unrelated tests.

## Results

- Added public API version 1 over the existing physical transaction. CPU classes
  remain outside ACO. Disabled physical execution is rejected before ownership;
  transient snapshot-generation races wait without becoming accounting conflicts.
- Focused JUnit: 10 tests passed, no failures or skips. This includes a real
  AEItemKey/NBT round trip at 10^40, pending-output accounting, cancellation-state
  persistence, unknown-schema rejection, existing escrow/accounting and the manifest.
  Only registries are initialized in the codec fixture; no game or Forge network startup.
- Forge build and reobfuscation passed. Ordinary AE2 execution code was not changed.
- Runtime acceptance remains PENDING. No launch, deployment, cancellation of existing
  orders or release was performed. This API does not reconstruct orders previously
  accepted through the incomplete legacy handoff.

## Integration Limits

- Count-wide physical plans require deterministic crafting-table patterns, loaded
  receipt-backed workers and audited exact storage for boundary inputs AND outputs.
  Registering an external consumer does not prove these routes exist.
- Capacity-only plans can keep their native long executor after exact validation.
- AQE needs the companion change in advanced-quantum-engineering#33. The original
  ACO 2.0 development artifact does not contain this new API.
- The ACO PR is stacked on Forge PR #180; it also carries the existing #125
  receipt/accounting regression commit from its parent development branch.
