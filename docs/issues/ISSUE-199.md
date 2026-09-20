# Issue #199: ACO 2.0.0-rc.4 prerelease

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/199
- Status: Ready
- Baseline: 2.0.0-rc.3
- Scope: package the current #190 work for Forge 1.20.1 and NeoForge 1.21.1.
- Runtime acceptance: PENDING in #190, not a completion condition of this packaging issue.

## Invariants
Keep exact counts, capacity ownership, receipts, recipes and physical IO unchanged
during packaging. Preserve all current #190 implementation and tests. Never mark
runtime tests passed merely because unit tests or builds pass. Do not deploy or
restart production. Keep backups, captures and machine-specific artifacts local.

## Plan and verification
1. Bump the mod version to 2.0.0-rc.4 on both loaders, with one PR per Minecraft branch.
2. Declare the release scope and run test/build/release-readiness checks.
3. Run GitHub CI, merge only after success, and verify merged trees match tested sources.
4. Verify embedded version/loader and artifact hashes; publish one prerelease with
   aco2.0.0-rc.4_1.20.1.jar and aco2.0.0-rc.4_1.21.1.jar plus SHA256SUMS.txt.
5. Publish bilingual improvements, explicit limitations and remaining tasks.
6. Keep #190 open. Record PRs, CI and release links in the release issue.

## Remaining acceptance
Live AQE/AAC completion, cancellation, restart recovery and concurrent reservations;
general processing-machine/interleaved wide execution; unsupported/dynamic input
coverage; real creative-control-circuit full-tree latency under ten seconds;
server tick, memory and transfer-cost measurements.
