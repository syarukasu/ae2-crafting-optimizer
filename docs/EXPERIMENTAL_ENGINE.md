# ACO Core Engine

The filename is retained for existing links. The implementation described here
is the current ACO core after the Issue #164 clean break.

## Scope

ACO has five runtime domains:

1. Pattern Provider generation and bounded lookup caches.
2. Crafting planning, memoization, compiled graphs, and checked arithmetic.
3. Standard AE2 execution budgets and the public Transactional Batch V2 source.
4. Exact `long`/`BigInteger` plans and ACO-owned standard AE2 transactions.
5. Read-only optional recipe-intent and add-on lookup caches.

ACO does not own an Advanced AE, AQE, InsaneAE, NeoECO, GTCEu, or Mekanism
job. Those mods retain structure, recipe validation, machine state, power,
progress, cancellation, persistence, receipts, and completion.

## Planning Pipeline

```text
capture immutable provider/recipe/storage generations
        |
        v
generation-keyed compiled graph lookup
        |
        v
strict eligibility proof
        |
        +-- proof unavailable --> AE2 planner before ownership
        |
        v
long planner -- checked overflow --> BigInteger planner
        |
        v
generation revalidation and sidecar publication
```

The compiled graph stores immutable pattern topology. Per-request inventory,
demand, missing amounts, and chosen candidates are not cached as graph state.
Cache entries are bounded and invalidated by their owning generation.

The authoritative planner is allowed to replace an AE2 result only when every
choice and accounting rule needed by that request is proven equivalent. Shadow
mode compares results without changing AE2's answer. Unsupported dynamic or
ambiguous behavior declines before any mutation.

## Exact Counts

Ordinary values stay on checked `long` arithmetic. `add`, `multiply`, or
`ceilDiv` overflow promotes the same immutable request to `BigInteger`; it is
not retried with a saturated value.

The AE2-facing `Long.MAX_VALUE` value is only a compatibility facade for APIs
whose signature is `long`. Exact inventory, missing amounts, plan bytes,
execution remainder, progress, and receipts use the sidecar/API value.

External CPU add-ons consume the versioned `BigCraftingEngineApi`. ACO publishes
an immutable plan and never ticks, cancels, restores, or completes the external
CPU on the add-on's behalf.

## Standard AE2 Exact Execution

ACO may own an exact physical transaction only for a standard AE2 CPU and only
after deterministic topology, exact storage routes, and bounded resource limits
all pass preflight.

Before ownership, declining returns to AE2. After ownership, the only valid
outcomes are resume, exact cancellation return, or quarantine. A legacy retry
after input transfer is forbidden.

Physical crafting-table execution is quantity-independent: one real craft
proves a deterministic formula, and exact coefficients account for the accepted
batch. Final output is credited only from a durable target receipt, never from
the plan itself.

## Execution Budget

The standard AE2 hook limits work started in one tick. It does not change CPU
capacity, displayed co-processors, recipe choice, or completed-work accounting.
Sequential instant dispatch repeatedly calls AE2's original operation inside a
measured wave and stops on time budget or provider backpressure.

Advanced AE and NeoECO hooks are thin budget boundaries only. They may cap a
call count but must not introduce an ACO job ledger or replace add-on execution.

## Transactional Batch V2

V2 is the only Pattern Batch contract. ACO registers a standard AE2 source
reconciler; external mods register their own durable target adapters. If no
adapter exists, the hook exits before reading mutable job state.

See [BATCH_API.md](BATCH_API.md) for the ownership and recovery protocol.

## Optional Integration

Recipe Intent and add-on caches are hints over immutable signatures:

- candidate lists are bounded;
- the machine mod performs the final recipe validation;
- input, recipe, structure, or resource generations invalidate the hint;
- a hint miss follows the original lookup without changing machine state.

ACO contains no built-in GTCEu or Mekanism native batch implementation.

## Removed Architecture

Issue #164 removes rather than deprecates:

- external AQE/Advanced AE job, child-window, cancellation, and recovery owners;
- Pattern Batch V1 and its sequential adapter;
- built-in GTCEu/Mekanism native batch adapters;
- the independent Fair Scheduler;
- terminal, storage-watcher, packet, bus, IO Port, P2P, and Grid Tick rewrites;
- deterministic first-missing and inventory-based pattern reordering paths;
- no-op compatibility configuration keys.

These paths must not return as a fallback. Reintroduction requires a new Issue,
an explicit owner, invariant documentation, failure-injection tests, and both
loader builds.

## Validation Gates

Every change to this engine must pass:

- issue-specific unit/source tests;
- the regression manifest;
- exact arithmetic and transaction recovery tests;
- Mixin catalog/config consistency tests;
- `clean test`, `clean build`, and `git diff --check` on both supported loaders.

Build success is not gameplay proof. Client, dedicated-server, world-join, and
real crafting acceptance remain separate runtime evidence.
