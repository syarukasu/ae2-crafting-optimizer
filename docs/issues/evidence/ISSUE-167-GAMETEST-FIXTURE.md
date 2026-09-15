# Issue #167 GameTest fixture

## Status

`PENDING`: the repository has no `runGameTestServer` task. This document fixes the runtime
fixture and assertions without claiming that Minecraft was started.

## Common setup

- One AE2 grid with one Crafting Storage, one Pattern Provider, one ME terminal, and storage for
  inputs and outputs.
- A deterministic chain: oak log to planks to buttons.
- A second output with two valid encoded Patterns registered in a known order.
- A test hook that can add or remove one Provider Pattern and mutate one referenced inventory key
  between capture and worker completion.
- ACO decision-flow diagnostics enabled only while collecting the evidence log.

## Required scenarios

1. Submit the same output, amount, strategy, requester reference, action-source reference, and
   storage/pattern/recipe revisions from two callers. Exactly one delegate calculation runs; both
   subscribers receive equivalent independently materialized plans.
2. Cancel one subscriber while the other remains. The shared delegate continues. Cancelling the
   final subscriber cancels the delegate once.
3. Add or remove a Pattern after immutable capture and before worker completion. The stale plan is
   never adopted or cached. An ordinary long request proceeds through AE2's authoritative path.
4. Mutate a referenced inventory amount after capture. A completed simulation plan from the old
   storage revision is not reused.
5. Keep two valid Patterns for one output. ACO preserves AE2's candidate count and order and never
   removes a candidate before `CraftingTreeNode` evaluates it.
6. Repeat the deterministic request until Shadow qualification is reached, then change provider or
   recipe generation during comparison. The mixed-generation comparison does not increment the
   qualification count.
7. Submit a supported exact-wide request and mutate the exact storage mount before its deferred
   snapshot. The old inventory is not relabelled with the new revision and no saturated long plan
   is submitted.

## Pass conditions

- AE2 and ACO report identical `finalOutput`, `simulation`, `multiplePaths`, Pattern counts,
  `usedItems`, `emittedItems`, and `missingItems` for every ordinary request.
- No stale capture is published under a newer revision.
- No background thread reads a live `IGrid`, `Level`, BlockEntity, or mutable AE2 service.
- No internal error is reported as `CPU_TOO_SMALL` or `NO_COMPILED_PROGRAM`.
- No cancellation of one subscriber cancels another subscriber's valid calculation.
- `/aco stats` shows bounded capture, compile, dedup, stale-rejection, and planner timing counters.
