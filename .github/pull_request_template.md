## Summary

Describe the behavior and performance problem being addressed.

## Safety Boundary

- [ ] AE2 remains authoritative for craft validity and storage mutation.
- [ ] Optional-mod paths retain an original fallback.
- [ ] New caches are bounded and have documented invalidation.
- [ ] Timing, ordering, or batching changes have a config switch.

## Verification

- [ ] `./gradlew clean build`
- [ ] Relevant checks in `docs/TESTING.md`
- [ ] Tested once with `enableOptimizer=false`
- [ ] Included before/after performance evidence where applicable
