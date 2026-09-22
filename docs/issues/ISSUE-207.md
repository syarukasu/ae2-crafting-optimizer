# Issue #207: Fast ordinary industrial plans with exact shortage reports

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/207
- Status: Implemented (local verification; live acceptance pending)
- Baseline: local 2.0.0-rc.4 development, after #202
- Loaders: Forge 1.20.1 upstream/UELM and NeoForge 1.21.1
- Related: #190, #185

## Problem and evidence
The user requires fast creative-control-circuit planning with both insufficient
and sufficient stock. Source inspection found two explicit 4096-repeat gates in
OrderedBranchingPlanner.repeat. Even nested ordinary orders ignore proven stock
read intervals and execute every repeat. This is a source-confirmed opportunity,
not a measured explanation of a live creative-circuit stall. Latest production
log contains incomplete snapshots for other roots, but no creative-circuit sample.
The server recipe has alternative manufacturing routes and fluid inputs; do not
hardcode an item or recipe route, or assume all registered recipes are ME patterns.

## Reproduction and expectations
First add failing bounded-work tests for a one-item root needing 1000 intermediates,
with two producers, in full-stock and missing-stock cases. Also cover partial stock
that changes the selected producer and reusable catalysts. Retain exact outputs,
pattern counts, missing and reserved stock, producer priority and CPU bytes.
Compare actual AE2 at 1, 2, 100, 4095, 4096, 4097, with fluid fractions and CRAFT_LESS.

## Ownership and safety
ACO owns detached planning, not live inventory, machines or recipe progression.
AE2 retains provider eligibility, reservations and execution. This modifies no
snapshot generations, custody, physical dispatch, NBT or public API. Failures and
cancellation keep their existing propagation; fallback remains pre-custody only.
No clamping, fabricated outputs, guessed missing entries, stale plan cache or
background world reads. Do not reduce input observations unless existing interval
and membership proofs already permit it.

## Implementation
Owner: OrderedBranchingPlanner.repeat and existing State/ByteRepeat helpers.
Remove the quantity-only barriers to stock-interval-proven batching. Reuse the
existing calculation-local block and exact IEEE-754 addition replay rather than
multiplying rounded CPU costs. Keep partial last batches, failed candidate reads,
key membership guards and the 256-observation block bound. No new production type.
If oracle comparison exposes a flaw, update this specification before extending
the proof. Other graph optimizations and live capture completeness stay in #190.

## Pre-implementation checklist
- [x] Read charter, regression history, class responsibilities and testing guide.
- [x] Read ordered planner, interval proof, byte replay and actual AE2 oracle tests.
- [x] Defined failing bounded-work cases and exact acceptance comparisons.
- [x] Defined ownership, cancellation, forbidden changes and both loader scope.

## Validation
Fail the new bounded tests before code edits, then run them and the existing
branching/byproduct/input-semantics suites. Full tests, build and issue manifest
on both loaders; record skipped fixtures. Actual creative-circuit latency requires
the live ME graph and current stock; do not claim instant UI from synthetic tests.
No deployment, server restart or release is part of this change.

## Additional proof obligation
A targeted actual-AE2 oracle found a nested failed reusable process lost its read
intervals when its block threw Unavailable without its own transaction. An outer
candidate could then batch across a stock change that makes this process viable.
The 100-order reproducer expected first=99, second=1, b=198, root=100, but produced
second=100, root=100. Preserve the failed block's read intervals in its parent
before propagating the failure; never apply its failed stock/craft mutations.
Add actual-AE2 comparison with a newly produced catalyst enabling the first route.
The expanded test covers 2/100/5000 orders with no/partial/full fuel, plus exact
10^64 accounting. The latter uses 2836 work steps: the transient first catalyst
fills the existing 256-observation block, so its bound is 8192, not the initial
overly restrictive 500. All ledger assertions passed before adjusting this newly
added performance assertion. No algorithmic work bound was raised.

## Results
- Before the fix: all four new tests failed their work bound, with 3006, 3015,
  3009 and 4002 steps. All exact expected maps already matched.
- Initial optimization: 9, 18, 15 and 6 steps, with 999/999/998/999 skipped repeats.
- Targeted oracle tests exposed an independent headless-fixture prerequisite:
  BranchingInputSemanticsTest did not initialize TestKeyTypes and relied on other
  test classes running first. Add the existing helper in its BeforeAll; no runtime
  registry behavior changes. Preserve single-repeat direct handling to avoid an
  extra transaction layer when no repetition can be skipped.
- Final Forge upstream and UELM: each 638 tests, 636 passed, zero failures,
  two optional Neo ECO JAR checks skipped. AQE 2.2.7 and EAEP 1.5.5 contract
  fixtures were explicitly supplied. JDK 17.
- Final NeoForge: 645 tests, 644 passed, zero failures, one optional AQE JAR
  capacity-constant check skipped (fixture not supplied). JDK 21.
- All three configurations passed test, build and verifyIssueRegressionManifest.
  Commands: gradlew test build verifyIssueRegressionManifest --no-daemon;
  Forge also uses -Pae2Variant=upstream/uelm, -PaqeCapacityJar and -PeaepPatternJar.
- Added four bounded-work tests and two AE2 oracle tests (36 fluid/quantity/strategy
  cases, nine nested catalyst cases and one exact 10^64 assertion set).
  Existing randomized branching, byproduct and changing-input tests also pass.
- Production planner source matches between loaders by SHA-256:
  C5DBF5EB28ADAD0F3B35D27453D90ED3E7460F956CC828F2E03ED0AF1E9DE49B.
- Evidence is retained outside Git under artifacts/issue207-before.xml,
  issue207-nested-read-before.xml and issue207-final-*-tests.
- No persistent cache, recipe change, deployment, server restart, version change,
  commit, PR or release. Existing unrelated working changes remain intact.
  The public issue stays open: actual creative-circuit pattern capture completeness
  and full missing/sufficient-stock UI latency remain unmeasured. These operation
  counts are synthetic work reductions, not whole-mod speed multipliers.
