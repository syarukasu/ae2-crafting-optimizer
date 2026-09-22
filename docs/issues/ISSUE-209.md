# Issue #209: Visible, evidence-based planning diagnostics

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/209
- Status: Implemented
- Scope: both loaders; historical audit uses private Forge server logs

## Evidence
Mekanism Optimizer 1.5.0 emits one INFO statistics line per minute in the actual
server latest.log. ACO logs detailed planning at DEBUG, slow results at INFO,
and aggregate counters only on stop when logCacheStatistics is enabled.
The user retains archived logs to compare healthy behavior and regressions.
Audit all files in the authoritative server logs directory, including gzip and
debug archives, before selecting new metrics. Overlapping debug/latest/stdout
logs are separate evidence streams, not independent executions.

## Owners and implementation
Offline tooling owns read-only historical extraction. Runtime diagnostics owns
bounded observations only; no recipes, quantities, storage, execution ownership
or deployment changes. Reuse OptimizationMetrics and server lifecycle. Emit a
bounded INFO summary periodically while activity changed, with elapsed duration
and route counts. Keep per-order detail in debug.log. Do not label unavailable
reasons, exact amounts, cache work or VM usage as known. A VM that is not wired
into production must never be reported as active.

## Safety and tests
No per-tick log spam, unbounded IDs/strings/maps, decimal expansion of extreme
BigIntegers, raw player details or private log publication. Original logs are
never modified. Test interval gating, idle suppression, reset, concurrent updates
and consistent route labels. Verify normal tests/build/manifest on both loaders;
runtime console validation remains pending until a deliberate deployment.

## Before implementation
- [x] Read project charter, regression history, responsibility and testing docs.
- [x] Inspect existing diagnostic, metric, lifecycle and reference-log paths.
- [x] Establish upstream issue and exact safety/verification scope.

## Historical findings and refined implementation
Read all 263 files in logs, including gzip and debug archives, with zero read
errors. They contain 185943 matching ACO lines (not unique jobs). Slow legacy
examples: ultimate_control_circuit 27451 ms on August 3; experimental_quantum_core
9826 ms on August 8. Do not compare different orders as a speedup measurement.
The current September 18 startup identifies ACO rc.3, not the local rc.4 tree.
In debug.log, ACO records starts and declines but no planning_complete; matching
Thunderbolt records finished. Therefore absent ACO completion is not proof of a
stalled job. A third-party cancellable run RETURN can bypass another observer;
observe logCraftingJob HEAD, before such wrappers, while retaining sidecar alias
at the existing RETURN. Route describes the calculation before outer wrappers.
finish records an unobserved end as aborted (cancel/error, not falsely success).
The summary owns constant-size counters and a reset epoch, never job references.
Two source regressions fail on the original code before these edits.

## Implementation and verification on 2026-09-22
PlanningLogSummary owns bounded epoch-scoped counters, covered by seven tests.
CraftingCalculationDiagnostics, OptimizationMetrics, BigIntegerPlanDiagnostics,
ACOConfig and ACOServerLifecycle expose/reset/gate periodic normal-log output.
Cache-only activity is visible without inventing completed calculations.
CraftingCalculationDiagnosticsMixin observes logCraftingJob; its original
sidecar/shadow-validation boundary is preserved. Two source tests cover the hook.
Forge tools/diagnostics supplies the read-only gzip/plain historical audit.

Forge upstream/UELM: 647 tests each, no failures/errors, two optional fixture
skips. NeoForge: 654 tests, no failures/errors, one optional fixture skip.
Builds and regression manifests passed. Four isolated Forge/AE2 GameTests
passed: processing, cancellation, concurrent reservation and returned containers.
Their debug log contains planning_complete from the new observer.
The audit self-test passes, including unchanged input hashes.

No production deployment, restart or release was performed. Real Arclight
wrappers and 60-second console output remain runtime gates; periodic summary
state/format has only unit-test coverage. This is not the creative-circuit matrix.
