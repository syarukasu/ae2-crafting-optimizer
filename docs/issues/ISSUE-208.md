# Issue #208: Pinned AE2-VM exact integration

- GitHub: https://github.com/syarukasu/ae2-crafting-optimizer/issues/208
- Status: Ready
- Target: Forge 1.20.1 first; no automatic NeoForge engine substitution
- Reference: TaoLe-si/AE2-VM 1.20.1-forge, f9e083732ee654a99a1679bcdee7f864eb90be94

## Evidence and scope
The real VM uses a BigInteger stack but narrows operands through popL, narrows
bundle results through toLongSafe, stores counters in AE2 KeyCounter and returns
ICraftingPlan with long amounts. Its public API is not an exact BigInteger API.
The power-of-two MUL shortcut also shifts without checking overflow. Public
async entry points can access live simulation state and install their own mixin
interception, so simply embedding this JAR is not an approved integration.

First establish an isolated actual-engine comparison harness with pinned source
and license provenance. It must reproduce arithmetic and accounting boundary
failures before modifying the engine. Production activation, exact API and
Jar-in-Jar packaging remain separate acceptance gates within this issue.

## Owners and invariants
VM owns compilation/calculation; ACO owns immutable capture and adoption;
AE2/add-ons retain real inventory, CPU, machine execution and receipts. No
clamping of exact authority, no worker world access, no second planner in a
production request, no post-custody fallback, no synthetic machine output.
Existing ACO work and server/client artifacts must remain untouched.

## Checks before implementation
- [x] Read charter, regression history, responsibilities, workflow and tests.
- [x] Inspect pinned VM source and current long result/stock/rollback boundaries.
- [x] Keep private logs and pack data outside the public source tree.
- [x] Require actual engine tests, not a mocked VM claimed as integration proof.

## Acceptance still required
Exact requested/used/missing/output/pattern/byte quantities; server-captured
detached snapshots; per-grid compiler/cache invalidation; cancellation; normal
AE2 quantity parity including alternative producers, fluids, byproducts and
containers; wide AQE execution and recovery; LGPL notices/source; loader-specific
packaging without duplicate interception. No runtime speed claim before measuring.

## Required creative-control-circuit matrix
Use the SAME server recipe/pattern graph and producer order throughout. For each
request of 1, 2, Long.MAX_VALUE, Long.MAX_VALUE + 1, and 10^64, compare empty,
partial and sufficient inventory. Include cases where only an intermediate count
exceeds long. Record the snapshot/graph identity, exact required/used/missing/
surplus/pattern/byte counts, cold/warm wall time, server-thread CPU/wait time,
worker CPU and allocations. Pass quantity equality before judging performance.
Registered recipe data is not proof that a live ME provider exposes that pattern.
Do not fill production inventory, place patterns or deploy a benchmark script
without a separate deliberate test setup. Do not treat synthetic timing as the
real server's creative-control-circuit acceptance.

## Stock matrix acceptance details
The core matrix has nine cases: 1, 2 and 9223372036854775807 requested, each
with empty, partial and sufficient material stock. Sufficient means ingredients
for the full production tree, not pre-stocked finished circuits; test already
stocked outputs separately. Persist each exact stock map and partial shortages.
Baseline and candidate must receive the same graph, provider order, stock,
strategy and configuration. Compare per-key required/used/missing/returned/
surplus quantities, pattern counts and bytes, not only the final output.
Overflowed native AE2 results are not an oracle: wide cases require independently
checked exact accounting plus representable small-case AE2 comparisons.
Stock revisions must invalidate cached results. Measure cold/warm runs,
capture/adoption, wall time, server-thread CPU/wait, worker CPU, allocations and
concurrent tick impact. Report repeated measurements, not just step counts.
No real creative-control-circuit case in this matrix has been completed yet.

## Local progress on 2026-09-22
Five real-interpreter arithmetic tests pass with the isolated Forge patch;
two exact assertions failed on unchanged upstream before the patch. This is not
an exact stock/result API or production VM integration. NeoForge does not run
the Forge VM probe. Activation, packaging and real matrix acceptance remain open.
