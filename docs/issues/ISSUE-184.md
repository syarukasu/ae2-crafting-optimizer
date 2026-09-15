# Issue #184: Publish ACO 2.0.0-rc.1

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/184
- Status: Ready
- Scope: Forge 1.20.1 and NeoForge 1.21.1 release packaging, CI and documentation.
- Baseline: published 1.5.33.
- Related: #125, #156, #161, #164, #167, #176, #179, #182.

## Evidence and Acceptance

The user approved a GitHub prerelease before stable 2.0.0. Current local
changes passed Forge 531 and NeoForge 542 tests, but the new artifacts have
not passed the full live completion/cancellation/restart matrix.
PR #183's existing CI failure is a distribution-JAR mapping problem already
corrected locally under #182; rerun against pinned dependencies.

Publish exactly two named mod artifacts in one prerelease:
- aco2.0.0-rc.1_1.20.1.jar
- aco2.0.0-rc.1_1.21.1.jar

The mod version is 2.0.0-rc.1 in both filenames, Gradle and JAR metadata.
Minecraft and loader versions remain separate. Record both source commits
and SHA-256 checksums. Keep stable 1.5.33 as Latest.

## Ownership and Prohibitions

No recipe, storage, world, server/client deployment or runtime behavior changes
are part of this packaging step. Preserve previous fixes and loader APIs.
Do not bundle local backups, raw logs, external mod JARs or credentials.
Do not force-push existing branches, overwrite stable tags, mark live
acceptance complete or close unresolved functional issues.

## Implementation

1. Update version metadata and add the complete RC release-scope manifest.
2. Add bilingual release notes with migration guidance and known limitations.
3. Add this issue to the regression index; run the release gate in CI.
4. Commit accumulated local fixes on separate loader release branches.
5. Push and review release PRs, require their current CI checks to succeed.
6. Merge accepted PRs, create immutable source tag(s), publish one prerelease.
7. Download published assets and verify their SHA-256 checksums.

## Checklist

- [x] Read project charter, regression history, responsibility catalog and issue workflow.
- [x] Identify existing local fixes and pinned CI failure.
- [x] Define packaging tests and preserve runtime PENDING status.
- [x] Define separate loader PRs and shared release naming.
- [x] Local RC builds and release-scope gate (Forge 531 / NeoForge 542 tests, no failures or skips).
- [ ] Current PR CI and source review.
- [ ] Published asset audit.
- [ ] Release URL and final source commits recorded.

Runtime acceptance for the underlying functional issues remains PENDING.
