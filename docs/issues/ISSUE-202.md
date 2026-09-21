# Issue #202: Exact wide shared-DAG demand aggregation

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/202
- Status: Implemented (local verification passed; PR review pending)
- Baseline: 2.0.0-rc.4
- Loaders: Forge 1.20.1 (upstream and UELM), NeoForge 1.21.1
- Related: #190, #185

## Problem and evidence
CompiledRootProgram sends shared fixed-input graphs to OrderedByproductPlanner.
Its explicit stack expands each dependency path; the 1,048,576-request bound can
reject small repeated diamonds. Establish a failing bounded test before the fix.
No earlier stable version or live-server incident is attributed to this case.

## Reproduction and expected result
Build n0 -> two n1 slots, n1 -> two n2 slots, ... -> raw. Each recipe produces
one unit. Order 10^64. With 28 stages, 29 unique keys represent 2^29-1 requests.
Expect exact executions, stock reservations, emissions, missing counts and CPU
bytes with O(nodes + edges) work, not per-path or per-item expansion.

## Ownership and invariants
ACO owns detached planning only. AE2 owns eligibility/inventory and external CPUs
own capacity/execution. No live state reads, persistent cache, API/NBT changes,
long saturation, material generation or custody fallback. Unproven graphs use
the existing ordered planner before any inventory ownership transfer.

## Fix
Use a calculation-local topological pass only for root quantities above signed
long, unit single-output recipes, fixed unit-template inputs and zero inventory
at produced keys. Terminal stock/emissions remain exact. Preserve logical path
multiplicities as BigInteger for CPU node overhead; expandedRequests remains
actual evaluator work. Sum rational stack charges without per-branch rounding.
Keep long trace order unchanged. Co-products, rounding, alternative inputs and
intermediate stock stay on existing ordered logic.
If a compressed demand exceeds the count limit although individual ordered
charges may fit, decline aggregation instead of rejecting the existing plan.
This includes combined terminal stock plus shortage; neither ledger is clamped.
AE2-VM commit b03afe75f2f31ff01065ba574a80bb7220a8a237 is conceptual reference;
do not import code, build scripts or dependencies.

## Owners and files
- CompiledRootProgram: select the proven evaluator for wide ordered plans.
- LinearWidePlanning: eligibility, topological demand and exact trace.
- LinearWidePlanningTest: bounded reproduction and ordered-reference comparisons.
- Existing byte counter, execution and persistence owners remain unchanged.

## Pre-implementation checklist
- [x] Read charter, regression history, class responsibilities and testing guide.
- [x] Inspected existing ordered and aggregate planners and byte traces.
- [x] Defined bounded failing reproduction, boundary tests and both loaders.
- [x] Defined ownership, forbidden changes and pre-custody fallback.

## Tests
Compare maps and exact byte counts against OrderedByproductPlanner for shallow
diamonds with terminal stock, missing items, emitters and fractional byte units.
Test deep diamonds, bit limits, cancellation, immutable inventory and exclusions.
Run existing ordered/AE2 oracle tests, full loader suites and manifest verification.
No Minecraft launch, deployment, game completion or ten-second real-pack claim.

## Results
2026-09-21:
- Before the fix, the 28-stage reproduction failed after 1,001 checkpoints.
- After the fix: 29 visited nodes, 170 checkpoints, 58 charges, preserving
  536,870,911 logical request occurrences and exact counts for an order of 10^64.
- Single JUnit samples including assertions: upstream 1.742 ms, UELM 2.648 ms,
  NeoForge 1.926 ms. These are synthetic samples, not warmed production benchmarks.
- Eight regression tests include 48 stock/emitter diamond cases with four byte
  units, 80 seeded weighted DAGs, exclusions, cancellation in both passes,
  logical path counts beyond int, and compression-only bit-limit overflow.
- The compression-only overflow test first failed against the initial optimization
  and passed after declining that optimization without changing the ordered plan.
- Forge upstream and UELM: each 631 tests, 629 passed, 2 optional Neo ECO fixture
  checks skipped, zero failures. NeoForge: 639 passed, zero skipped or failed.
- Both loader builds and verifyIssueRegressionManifest passed. Industrial audit:
  node --test tools/industrial-benchmark/audit.test.mjs: 10 passed.
- Build command: gradlew test build verifyIssueRegressionManifest --no-daemon,
  with ae2Variant=upstream/uelm on Forge, EAEP and AQE fixture paths supplied.
  JDK 17 for Forge; JDK 21 for NeoForge. No fixture JARs are bundled.
- Source and regression test contents match between loaders by SHA-256.

## Remaining scope
Co-products, rounded outputs, non-unit template quanta, intermediate stock and
root orders within signed long retain their existing handling. Broader demand
reuse needs separate proofs. This does not finish #185 or #190, make all recipes
fast, prove physical completion, or establish the real-server ten-second target.
No deployment, Minecraft startup, release or version change was performed.
