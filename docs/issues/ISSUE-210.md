# Issue #210: ACO 2.0.0-rc.5 prerelease

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/210
- Status: Implemented (local release gates passed; CI/publication pending)
- Baseline: 2.0.0-rc.4
- Loaders: Forge 1.20.1 and NeoForge 1.21.1
- User authorization: implement the current step and publish a prerelease.

## Scope and ownership
Publish the current tested #190, #202, #207 and #209 changes together.
Include the #208 isolated source probe, but do not activate or package AE2-VM.
The existing planner, receipt, worker, inventory and CPU ownership is unchanged.
No new recipe, capacity clamp, dependency requirement or live-world operation.
Version/build metadata and release documentation are owned by this issue.
Do not include local backups, private logs, downloaded source models or JAR fixtures.

## Checks before implementation
- [x] Read AGENTS, charter, workflow, regression history and class ownership.
- [x] Inspect current branches, diffs, rc.4 assets and open #203/#204 CI.
- [x] Keep runtime acceptance PENDING instead of declaring it complete.
- [x] Separate publishing from production deployment/restart.

## Release gates
Bump both mod versions to 2.0.0-rc.5; keep Minecraft version suffix separate.
Run loader test/build and verifyIssueRegressionReleaseReadiness.
Retain normal AE2 and UELM coverage. Use separate reviewed PRs for each loader
and merge only after successful CI. Preserve the exact source/artifact relationship.
Audit JAR versions, loader metadata, absence of test/probe classes and hashes.
Publish both files in one GitHub prerelease with bilingual change descriptions.
Record remaining exact VM integration, wide machine execution and actual
creative-control-circuit stock matrix/performance verification. Do not close
those issues just because this prerelease exists.

## Expected artifacts
- aco2.0.0-rc.5_1.20.1.jar
- aco2.0.0-rc.5_1.21.1.jar
- SHA256SUMS.txt

## Runtime boundary
The one/two/Long.MAX_VALUE request by empty/partial/sufficient stock matrix,
BigInteger-wide real machine recovery, third-party wrapper console diagnostics,
server CPU and sub-ten-second full-tree target are NOT proven by local builds.
No production JAR replacement, Minecraft start/stop or automatic deployment.

## Local release gate results
2026-09-22: rc.5 test/build/release-readiness passed for Forge upstream and UELM
(647 tests each, zero failures, two optional Neo ECO fixture skips) and NeoForge
(654 tests, zero failures, one optional AQE fixture skip). Forge VM probe: five
tests passed. Historical log tool self-test and ten industrial audit tests pass.
Runtime PENDING entries are preserved. Source is submitted through separate
loader PRs; successful CI artifacts and their hashes are required before release.
