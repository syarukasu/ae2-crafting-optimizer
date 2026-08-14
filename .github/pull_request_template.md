## Issue Specification

- GitHub Issue: #
- Specification: `docs/issues/ISSUE-N.md`

- [ ] Read `docs/PROJECT_CHARTER.md` and `docs/REGRESSION_HISTORY.md`.
- [ ] The Issue specification was `Ready` before implementation began.
- [ ] Updated the specification with the actual implementation and verification.
- [ ] Documented ownership, fallback boundary, and forbidden changes.

## Summary

Describe the behavior and performance problem being addressed.

## Safety Boundary

- [ ] AE2 remains authoritative for craft validity and storage mutation.
- [ ] Optional-mod paths retain an original fallback.
- [ ] New caches are bounded and have documented invalidation.
- [ ] Timing, ordering, or batching changes have a config switch.
- [ ] Exact values are never clamped or silently reduced to a narrower type.
- [ ] No fallback occurs after ACO takes ownership of inputs or progress.
- [ ] ACO does not replace an optional add-on's CPU, structure, GUI, or execution logic.

## Verification

- [ ] `./gradlew clean build`
- [ ] Relevant checks in `docs/TESTING.md`
- [ ] Tested once with `enableOptimizer=false`
- [ ] Included before/after performance evidence where applicable
- [ ] Marked GameTest, startup, and in-game checks as passed or explicitly not run
