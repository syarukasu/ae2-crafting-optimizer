# Issue #161: Native ordinary dispatch, dev.17

## Change

- Delete adaptive CPU/grid budgets, cold probes, wave prediction and their maps.
- Delete both Neo ECO limiter Mixins, their helper and bootstrap selection.
- Preserve legacy public descriptors as stateless pass-through/no-op methods.
- Preserve native provider order, full/blocked rejection, energy and accounting.
- Keep native int overflow reconstruction and #125's exact-job exclusion.
- Do not change VM arithmetic, BigInteger APIs or owned V2 receipt transactions.
- Keep old config keys readable, labelled retired; saved true values have no effect.
  Startup reports native dispatch separately from owned transactional execution.

## Reproduction and checks

`build/issue161-native/before/` preserves three expected before-failures:
external MAX_VALUE operations lost one operation, timing implementation remained,
and the two optional limiter Mixins were registered. The independent coprocessor
boundary still needs +1 headroom, but external operation counts do not.

The first post-change source-contract check incorrectly matched the retained
method name `clearAdaptiveState`. The corrected check rejects state declarations
and verifies zero fields, without deleting that binary-compatible method.
The failed result remains as `initial-test-contract-failure.xml` and `.log`.

Tests of deleted private wave formulas were replaced with public dispatch checks:
one native call with the complete count, same-tick repetition, immediate retry
after native backpressure is cleared, propagated native failures without an ACO
retry loop, and no per-owner timing/config state. Historical results remain in
the earlier issue evidence; they are not counted as current wave coverage.

Both `upstream` AE2 and `uelm` were built under Temurin 17.0.17 with:

```text
build verifyIssueRegressionManifest verifyIssueRegressionReleaseReadiness
verifyExactVmBundle runGameTestServer --no-daemon --console=plain
```

The test-only runtime uses the same pinned InsaneAE diagnostic, Mega Cells and
Cloth Config JARs recorded in `ISSUE-161-FORGE-1.20.1-2026-09-28.md`, with scope
`providers`. These are not production mod versions or shipped dependencies.

For each variant: 820 unit/contract tests, no failures/errors/skips, and 19 named
GameTests pass. Relevant real-engine cases include:

- `ae2.pattern_provider_faces_round_robin`
- `ae2.insaneae_pattern_provider_patterns`
- `ae2.aco_provider_dispatch_load`: six rounds, exactly 1024 delivered each;
  checks full/blocked target rejection without consumption and cleared-target resume.
- `ae2.aco_processing_completion`, `ae2.aco_cancel_before_delivery`,
  `ae2.aco_competing_reservations`, `ae2.aco_returned_containers`
- Existing wide quantity/API, VM lifecycle and native/VM result comparisons.

Logs/XML/JARs are in `build/issue161-native/`. Public javap descriptors for
CraftingExecutionBudget and SequentialInstantDispatcher match the archived
dev.16 upstream artifact. Bundle audit rejects removed limiter classes, state
classes, Neo ECO budget registrations and test-only classes. Only exact VM
0.1.3 and native VM 0.3.3-dev.13 are bundled.

## Artifacts

| AE2 profile | SHA-256 |
| --- | --- |
| upstream | BF2A8712C33D49D3531E06E509861F3140958E632E70A5AE45846119639C3AF2 |
| uelm | 1BA8638A5E2800E0AF570762887097B569AB022E48A5763CFF05B0FE209FD387 |

Both files are named `aco2.0.0-rc.7-dev.17_1.20.1.jar` in separate variant folders.
The shared `build/distributions/2.0.0-rc.7-dev.17/` copy is restored to upstream,
matching the current production AE2 family. Do not install both variants.

## Not established

No production installation, restart, order mutation, public release or commit.
Server PID 24244 retains its 2026-09-27 start; server/client JARs remain dev.13.
The previous production config edit is distinct from this new implementation.
No production speedup, final 10-second target, or zero server cost is claimed.
Extended AE Plus blocking load, external exact-job recovery, production and
NeoForge acceptance remain pending. The required runtime release gate is not
approved by this limited fixture, regardless of the aggregate test count.
