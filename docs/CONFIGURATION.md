# Configuration

ACO uses one Common Config:

```text
config/ae2_crafting_optimizer-common.toml
```

Issue #164 intentionally removes obsolete compatibility keys. A key shown in an older ACO config
but absent from this document has no runtime implementation and is no longer part of the schema.

## General and domains

```toml
[general]
enableOptimizer = true

[optimizationDomains]
patternProvider = true
craftingPlanning = true
craftingExecution = true
bigInteger = true
optionalIntegration = true
```

The master switch is evaluated first, then the domain, then the individual feature. Disabling a
domain prevents that domain from touching AE2 or an add-on before ownership is acquired.

## Crafting planning

The active planning settings control:

- running-calculation deduplication;
- short-lived completed simulation-plan caching;
- root-reachable immutable graph capture and generation-keyed compiled programs;
- per-calculation memoization that preserves AE2's complete candidate list and order;
- provider refresh coalescing and generation tracking;
- long root amounts, compiled graphs, checked arithmetic, strict authoritative planning;
- Shadow comparison against AE2's authoritative result.

ACO does not prune, reorder, or replay AE2's global Pattern candidate list. Issue #167 removed the
old global Pattern lookup cache and structural candidate pruning because they could publish a stale
list under a new generation or alter AE2 recipe selection. A stale ordinary request returns to AE2
before ACO owns the plan; an exact wide request fails with its original diagnostic instead of
silently entering an overflowing long path.

The in-flight calculation index is bounded per AE2 crafting service by
`activeCalculationMaximumEntries` (default `4096`). Eviction removes only ACO's lookup entry; it
does not cancel the AE2 calculation or any caller-owned Future.

Successful completed-plan caching remains disabled by default. It must never reuse a plan after a
storage or provider generation change.

## Crafting execution

The execution section controls only:

- per-CPU and per-grid standard AE2 execution budgets;
- measured sequential AE2 dispatch waves;
- the public Transactional Batch V2 protocol and its persistent journal;
- a thin NeoECO execution-budget hook that does not own NeoECO jobs.

`maxEffectiveCoprocessorsPerCpu` changes the amount ACO lets one CPU spend in a tick. It does not
change CPU capacity, displayed co-processors, recipes, or completed-work accounting.

## BigInteger and exact vector

The BigInteger section controls exact plan/API availability, exact inventory snapshots, standard
AE2 exact execution, maximum magnitude, execution-window size, and memory accounting.

The exact-vector section controls only ACO-owned standard AE2 physical transactions. Before taking
ownership, ACO verifies deterministic topology, bounded key/node counts, and exact storage routes.
After ownership, it does not fall back to AE2.

External CPUs such as AQE or InsaneAE are not configured here. They register through the public
ACO API and retain their own execution, progress, power, cancellation, persistence, and completion.

## Optional integrations

Optional integration settings cover:

- AppliedE temporary-pattern ownership boundaries;
- GTCEu Recipe Intent candidate lookup;
- validated lookup caches for Circuit Cutter, Reaction Chamber, AE2 Overclock, and Assembly Matrix.

Recipe Intent is a hint only. GTCEu performs the final recipe test and owns machine execution.
Mekanism recipe lookup remains entirely Mekanism-owned. ACO does not contain a built-in native
machine batch adapter.

## Diagnostics

```toml
[diagnostics]
logCraftingDecisionFlow = true
```

`logCraftingDecisionFlow` records bounded `ACO-DIAG event=...` lines in `debug.log` for
planning decisions, compiled-graph rebuilds, and ACO-owned standard AE2 exact execution
lifecycle transitions. It does not log every successful tick, entire inventory or pattern
collections, or every decimal digit of huge `BigInteger` values. Disabling it changes only
diagnostic output and never changes planning, ownership, accounting, or fallback decisions.
The former `logBigIntegerPlanDeclines` key was removed; its narrower output is covered by this
single structured diagnostic contract.

`/aco stats` reports immutable capture time separately from worker-side authoritative Planner time.
When decision-flow logging is disabled, ACO does not allocate correlation IDs for each calculation;
metrics remain bounded counters and do not serialize inventories or complete BigInteger values.

## Removed settings

The following families were removed instead of being kept as no-op compatibility switches:

- terminal, storage watcher, packet, visible-range, and scrollbar rewrites;
- Import/Export Bus, IO Port, capability, transfer-simulation, P2P, and Grid Tick rewrites;
- two-stage missing preview and deterministic first-missing fast-fail;
- inventory-availability Pattern reordering;
- Pattern Batch V1, built-in GTCEu/Mekanism native batching, and Fair Scheduler;
- AQE-specific or generic external-CPU execution profiles inside ACO.

Reintroducing one of these requires a separate Issue, explicit ownership contract, and automated
failure/recovery tests. It must not be smuggled back as a legacy fallback.
