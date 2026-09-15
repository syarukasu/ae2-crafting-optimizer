# Issue #196: Release ACO 2.0.0-rc.3

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/196
- Status: Implemented (local release gate passed; publication/deployment pending)
- Baseline: 2.0.0-rc.2; target: 2.0.0-rc.3 for Forge 1.20.1 and NeoForge 1.21.1.
- Approved: publish a prerelease and deploy the Forge artifact to the established server/client. No game launch.

## Scope And Evidence

Package merged Issue #190 changes from #191/#192 and #194/#195. Version metadata,
README links, changelog, bilingual release notes and release-scope evidence only.
No production Java changes. Build tooling owns versioning; no changes to AE2/AQE
ownership, exact accounting, recipes, network or persistent formats.

## Gates

- Read charter, regression history, owner map and workflow; retain all PENDING
  runtime statuses. Add this release row and a scope based on rc.2 before building.
  Synchronize the manifest test's known-issue set and sequence with #196.
- Full loader tests and verifyIssueRegressionReleaseReadiness must pass.
- Forge CI checks upstream AE2 and UELM; NeoForge CI checks its actual AE2 version.
- Merge both release PRs after successful CI. Publish the exact successful CI JARs
  together, not locally modified replacements of older release assets.
- Audit embedded version, loader metadata, class level, required classes and
  absence of tests/backups/source archives; verify uploaded bytes with SHA-256.
- Confirm target Minecraft processes are stopped before deployment, preserve old
  ACO JARs, and leave exactly one active matching Forge ACO JAR in each target.
- Do not alter JVM arguments, other mods, worlds or recipes, or start Minecraft.
- Record release/PR/CI and deployment results on #196. Close only this packaging
  issue after verification; #190 and live acceptance issues remain open.

## Verification Status

Local builds and verifyIssueRegressionReleaseReadiness passed. Forge: 562 tests;
NeoForge: 573 tests, with zero failures, errors or skips. Capture audit: 10 tests.
The manifest regression initially detected the newly added issue number; the
known-issue set and ordered rows were synchronized without weakening validation.
CI, publication and deployment are pending. Existing planner tests do not
establish full modpack-tree latency, live completion, refunds or recovery.
