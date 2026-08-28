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
- generation-keyed pattern lookup caching;
- structural candidate pruning and per-calculation memoization;
- provider refresh coalescing and generation tracking;
- long root amounts, compiled graphs, checked arithmetic, strict authoritative planning;
- Shadow comparison and bounded incomplete-snapshot retry.

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
- GTCEu and Mekanism Recipe Intent candidate lookup;
- validated lookup caches for Circuit Cutter, Reaction Chamber, AE2 Overclock, and Assembly Matrix.

Recipe Intent is a hint only. GTCEu or Mekanism performs the final recipe test and owns machine
execution. ACO does not contain a built-in native machine batch adapter.

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
