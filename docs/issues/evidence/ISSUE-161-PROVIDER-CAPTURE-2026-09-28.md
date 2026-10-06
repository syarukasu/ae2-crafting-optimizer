# Issue #161: Remaining Provider Capture Cost

Local rc.7-dev.16 candidate, based on dev.15. No production deployment, restart
or public release. Runtime acceptance for issue #161 remains PENDING.

## Reproduction and Change

Use the same six-round fixture and pinned diagnostic InsaneAE/MEGA Cells/Cloth
JARs documented in `ISSUE-161-FORGE-1.20.1-2026-09-28.md`. Actual AE2 provider
calls push one iron ingot per execution to a chest with 1024 other hint locations
present. Full/blocked rejection and resumption are also checked. This is not
a complete CPU-driven industrial machine or a production MSPT measurement.

An opt-in 256-round JFR profile attributed 38 of 100 server-thread samples to
ACO hint capture (18 to the registry, 11 to list equality). Capture was rebuilding
unchanged native pattern metadata on every push and copying lists per side.
Dev.16 freezes those lists once and retains only the last exact native processing
pattern description in each provider's existing hook. Custom/subclass patterns
are refreshed each time, and input counters, execution amounts, targets and TTL
are never cached. The VM and physical execution owner are unchanged.

The focused pre-change test failed three intended assertions: native description
reuse and immutable/shared outer lists and concrete inputs. Two identity/custom
behavior checks already passed. Evidence: `build/issue161-description-before.xml`.
The initial fixture's missing AE2 key registry was corrected using the existing
TestKeyTypes helper; its separate setup failure is not a product regression.
After the change all five focused checks pass, including separate exact item and
fluid amounts. Existing registry expiry/coalescing checks remain unchanged.

## Timing Boundaries

Identical unprofiled fixture, upstream AE2, 1024 pushes per round, milliseconds:

| Run | First round | Repeated rounds 1-5 | Repeated median |
| --- | ---: | --- | ---: |
| Before | 52.565 | 34.921, 32.587, 35.791, 31.022, 21.260 | 32.587 |
| After | 49.303 | 32.106, 31.353, 29.905, 29.369, 57.122 | 31.353 |

This small short-run difference and the slow after-round do not establish a
large or statistically significant speedup. JIT, concurrent host workload and
allocation effects are uncontrolled. All six rounds delivered exactly 1024.
Logs/XML: `build/issue161-description-runtime-before.*`,
`build/issue161-description-after.log`, `build/issue161-description-runtime-after.xml`.

JFR is diagnostic only. After-profile capture appears in 25/80 server samples;
sample counts/shares are not deterministic timings. `*.jfr` and summaries are
`build/issue161-provider-profile-{before,after}*`. Reported allocation weights
are sampling estimates and must not be treated as exact bytes saved. Both
profiled runs delivered 262144 inputs, then passed rejection/resumption checks.

## Installed Server (Read-Only Check)

At inspection the production mods directory still contained
`aco2.0.0-rc.7-dev.13_1.20.1.jar`. Its bytecode still reads the ordinary throttle
config, unlike the dev.14+ source. The production config sets:

- `throttleCraftingExecution=true`, `adaptiveBudget=true`, `targetExecutionMillis=4`.
- `sharedGridBudget=true`, `sharedGridBudgetMillis=8`.
- `enableInstantDispatch=true`, `instantDispatchBudgetMillis=4`.

Thus the unthrottled candidate has not been installed there. This confirms an
old configured limiter, not its precise contribution to a particular order's
delay. No production configuration or JAR was changed. The actual slow provider
and target machine still need to be identified and reproduced.

## Remaining Acceptance

- Upstream AE2 and UELM builds, unit/contract checks, bundle/ABI checks and the
  pinned provider runtime suite pass. Evidence is archived under
  `build/issue161-capture/<variant>/`. The first upstream command reached the
  release-scope task before the dev.16 manifest existed; that failure is retained
  in `upstream/missing-scope-first-attempt.log`. The completed run uses the real
  scope manifest without weakening the runtime-pending gate.
- Nested artifacts remain exact VM 0.1.3/native VM 0.3.3-dev.13. The VM source
  worktree was not modified. The distribution contains neither test mods nor
  GameTest/profiler classes. The common distribution path contains upstream AE2.
- JAR SHA-256, `aco2.0.0-rc.7-dev.16_1.20.1.jar`:
  - upstream: `63C376557363A74945B3A2CA12AE4CED05F7D303ABC95D58DAD33BEF696E82E7`
  - UELM: `2B3F1737C1F48885944F5165CD2F21DA9BDD40DC76CDFBBDC91C0117090F3D19`
- Real Extended AE Plus blocking/large-machine load, AdvancedAE runtime coverage,
  external exact-job receipt isolation/restart, production latency and NeoForge
  remain unverified. Do not substitute short chest timings or a unit-test total.
