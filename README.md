# AE2 Crafting Optimizer

<p align="center">
  <img src="docs/aco-icon.png" alt="AE2 Crafting Optimizer icon" width="192">
</p>

[![Build](https://github.com/syarukasu/ae2-crafting-optimizer/actions/workflows/build.yml/badge.svg)](https://github.com/syarukasu/ae2-crafting-optimizer/actions/workflows/build.yml)
[![License: LGPL-3.0-only](https://img.shields.io/badge/License-LGPL--3.0--only-blue.svg)](LICENSE)

English | [日本語](README_ja.md)

AE2 Crafting Optimizer (ACO) is a Forge 1.20.1 optimization and integration
layer for Applied Energistics 2. It reduces repeated crafting calculations,
paces very large CPU execution bursts, and provides an exact transaction model
for quantity-independent crafting-table batches.

AE2 remains authoritative for normal recipes, providers, crafting jobs, and
storage. Deep paths are used only when ACO can prove their complete accounting
contract before moving any input.

The persistent branch for this line is `mc/1.20.1`. Its next release artifact
uses `aco<version>_1.20.1.jar`; the NeoForge line is maintained separately on
`mc/1.21.1` and never shares platform-dependent source or metadata.

## Target Environment

- Minecraft `1.20.1`
- Forge `47.4.18+`
- Java `17`
- Applied Energistics 2 `15.4.10`
- Optional Advanced AE `1.3.5-1.20.1`
- Optional Neo ECO AE Extension `20.3.x`
- Optional GTCEu Modern `7.5.3`
- Optional Mekanism `10.4.16`
- Optional Applied Mekanistics `1.4.3`
- Dedicated server, singleplayer, and Arclight as a normal Forge mod

Install the same ACO JAR on the server and every client. The common config is:

```text
config/ae2_crafting_optimizer-common.toml
```

## Core Optimizations

### Crafting Calculation

- Generation-keyed compiled Pattern graphs.
- Calculation-local inventory and candidate memoization.
- Deterministic missing-material fast paths with conservative fallback.
- Checked `add`, `multiply`, and `ceilDiv` arithmetic.
- `long` fast paths and bounded `BigInteger` promotion after overflow.
- Cancellation and stale-result rejection when provider or recipe generations
  change.
- Immediate compiled planning for ordinary `long` requests only after the
  current AE2 Pattern API, input candidates, inventory, and generation all
  pass the strict proof. Shadow history remains available as an additional
  qualification path.

Ambiguous substitutions, cycles, dynamic outputs, unsupported container
returns, and other unproven behavior return to AE2 before storage mutation.

### CPU Execution

- Per-CPU and per-grid wall-clock budgets for Pattern pushes.
- Adaptive limits for giant co-processor counts.
- Sequential Instant waves that keep AE2's original extraction, provider,
  energy, task-progress, and output accounting.
- Optional durable transactional batching for compatible adapters.

The displayed CPU capacity and co-processor count are not reduced. ACO limits
how much work may be started in one server tick.

### Machine Intent

ACO can retain the Pattern Provider's recipe intent so compatible GTCEu and
Mekanism machines do not rediscover the same recipe from all candidates every
tick. The machine mod still validates its live inputs, voltage, conditions,
energy, tanks, and outputs.

## Physical Crafting Tree

The former direct whole-tree conversion path has been removed. ACO now uses a
physical recipe-by-recipe transaction for strictly deterministic crafting-table
trees.

The design combines two proven ideas:

- [InsaneAE](https://github.com/taikun24/InsaneAE): perform one real assemble,
  then multiply the verified formula by the exact execution coefficient.
- Neo ECO AE Extension: Pattern Bus, Worker, Thread, real progress, real power,
  structure ownership, and NBT recovery remain physical and visible.

### Execution Flow

1. ACO compiles the reachable deterministic Pattern DAG once for the current
   provider and recipe generation.
2. It computes exact boundary inputs, selected slot inputs, per-step execution
   coefficients, final outputs, and fixed remaining outputs.
3. Every boundary input is atomically reserved from exact ME storage into an
   ACO-owned escrow.
4. A recipe step starts only when all of its exact inputs exist in that escrow.
5. AAC asks a real Neo ECO Worker to assemble the encoded crafting recipe once.
6. The verified one-craft input/output formula is multiplied by the exact
   `BigInteger` coefficient without iterating by quantity.
7. The Worker's durable receipt credits those actual outputs to escrow.
8. Only then may dependent recipe steps start.
9. Final output is inserted from escrow. It is never synthesized from the
   compiled plan.
10. The parent CPU job is committed only after the final ME insertion is
    proven complete.

A linear twenty-recipe chain therefore performs twenty physical recipe stages
for an order of `1`, `Long.MAX_VALUE`, or a supported `BigInteger` amount.
Independent branches may occupy separate Workers at the same time. Runtime
cost follows distinct recipe nodes and boundary keys, not requested quantity.

### Ownership and Recovery

- ACO owns the plan, escrow, exact storage mutations, parent-job accounting,
  cancellation, and recovery decisions.
- AAC owns only the Neo ECO physical adapter and Worker receipts.
- A pending ME mutation stores exact before/after amounts.
- After a shutdown, keys already at `after` are not replayed; only keys still at
  `before` are retried.
- A Worker writes its terminal output receipt before releasing its Thread.
- ACO credits each receipt once, then explicitly asks the Worker to forget it.
- Tag alternatives persist the concrete key chosen by the planner and
  revalidate that same choice after restart.
- Resolved one-craft formulas are reused until the provider or recipe
  generation changes.
- AAC indexes transaction UUIDs to Workers and Threads and rebuilds those
  indexes once after restart.
- Any value that matches neither saved state is quarantined instead of guessed.

Fallback is allowed only before input ownership moves. After ownership transfer,
the transaction must resume, cancel with exact return, or quarantine.

### Supported Recipes

The physical path requires:

- an AE2 molecular-assembler-compatible crafting Pattern;
- deterministic selected inputs for every slot;
- one real assembled result matching the encoded Pattern output;
- exact fixed remaining items;
- an acyclic, generation-stable producer graph;
- a formed AAC/Neo ECO physical target for every included step.

Processing Patterns are boundaries. GTCEu, Mekanism, fluids, and chemicals keep
their original machine execution. A downstream crafting island waits for the
real machine output before it becomes runnable.

## BigInteger Boundary

ACO exposes a versioned optional host API. It does not convert ordinary AE2
CPUs into BigInteger CPUs.

- Counts use `long` while exact arithmetic fits.
- Overflow promotes the calculation to `BigInteger`.
- The implementation limit is `10^16384 - 1`.
- NBT stores canonical byte arrays, never decimal strings.
- Standard AE2 APIs receive a saturated `Long.MAX_VALUE` facade only where
  their signature requires `long`.
- Exact inventory, missing amounts, task progress, escrow, and receipts are
  never derived from that facade.

AQE is an optional current host integration. AAC is an optional physical
executor. ACO itself requires neither mod.

## Long Root Orders

With `enableLongRootCraftAmounts = true`:

- `1..Integer.MAX_VALUE` keeps AE2's original confirmation packet.
- `Integer.MAX_VALUE + 1..Long.MAX_VALUE` uses ACO's versioned long-order
  packet and server-side menu validation.
- Values outside signed `long` use only an explicitly integrated BigInteger
  host path.

## Safety Rules

ACO intentionally does not:

- generate final output directly from a plan;
- iterate one Java operation per requested craft;
- silently clamp exact inventory or task accounting to `long`;
- replay an uncertain external mutation;
- fall back after another owner has accepted inputs;
- replace GTCEu or Mekanism recipe validity;
- modify recipes or Quantum Computer structure rules;
- use Bukkit, Paper, Spigot, or Arclight APIs.

## Important Configuration

The generated TOML is authoritative. The principal physical-tree settings are:

```toml
[exactVectorCrafting]
enabled = true
enableAqeBigIntegerParents = true
maximumPatternNodes = 1024
maximumUniqueInputKeys = 128
maximumUniqueOutputKeys = 128
maximumStartsPerGridPerTick = 1
maximumActiveStagesPerGridPerTick = 256
maximumActiveTransactionsPerGrid = 4
gridTimeBudgetMillis = 2
logVectorDiagnostics = false
```

`exactVectorCrafting` is retained as the config section name for migration.
Its implementation is the physical crafting tree described above; the deleted
direct Vector executor cannot be re-enabled.

The soft time budget begins when Exact Vector first runs on a grid during that
server tick. Trees with at most 64 physical recipe nodes are fully scanned
within the count limit, and dependency-blocked nodes do not consume active-stage
capacity.

See [Configuration](docs/CONFIGURATION.md),
[Feature ownership](docs/FEATURE_OWNERSHIP.md),
[Implementation](docs/IMPLEMENTATION.md), and
[Testing](docs/TESTING.md).

## Build

```powershell
.\gradlew.bat clean build --no-daemon
```

The output JAR is written to `build/libs`.

## Design References

ACO is not a port of either project. It reuses architectural ideas after
researching the actual implementation boundaries:

- [AE2-UEL](https://github.com/AE2-UEL/Applied-Energistics-2): generation-based
  caches and reduced repeated work.
- [InsaneAE](https://github.com/taikun24/InsaneAE): one real craft plus exact
  coefficient accounting.
- Neo ECO AE Extension: persistent physical crafting Workers and Threads.

No dependency source code is redistributed.

## License

ACO is licensed under `LGPL-3.0-only`.

Report ACO problems to this project first. Do not report an ACO-only failure to
AE2 or another dependency until it reproduces without ACO.
