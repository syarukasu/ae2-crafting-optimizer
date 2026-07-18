# Transactional Pattern Batch APIs

ACO contains two API generations. They are intentionally separate because a
machine-facing batch optimization is safe only when ownership and restart
recovery are explicit.

## V1 Compatibility API

`PatternBatchApi`, `PatternBatchAdapter`, and
`ae2_crafting_optimizer:sequential_pattern_provider` remain available for source
and Config compatibility with ACO 1.2.0/1.2.1.

ACO 1.2.2 and the current development tree do **not** register a V1 crafting-CPU
execution Mixin. Registering a V1 adapter therefore does not redirect live AE2
crafting. The legacy Config keys are readable no-ops. This prevents the old
aggregate path from changing AE2 task progress or waiting-output accounting.

## V2 Durable API

The opt-in development API is under `api.batch.v2`:

- `TransactionalPatternBatchAdapter` owns target preparation, exact acceptance,
  target reconciliation, rollback, and terminal-receipt cleanup.
- `BatchSourceReconciler` owns CPU inventory rollback and accepted-task,
  energy, and waiting-output accounting.
- `PatternBatchV2Api` registers both sides by stable `ResourceLocation` ids.
- `BatchTransactionJournal` persists the cross-owner transaction in overworld
  `SavedData` before source inputs move.

V2 is inactive unless all of these server switches permit it:

```toml
[experimentalCraftingEngine]
enableExperimentalCraftingEngine = true
enableTransactionalBatchingV2 = true
persistBatchTransactionJournal = true
```

All switches remain false by default except the journal safety dependency.
Existing unresolved journal records continue reconciliation after a feature is
disabled.

## Adapter Contract

The normal path is:

```text
prepare target receipt
stage source receipt and journal
extract an exact complete source aggregate
commit an all-or-zero target aggregate
account energy, task progress, and expected outputs
finish and remove the journal
remove terminal source and target receipts
```

An adapter must obey these rules:

- The transaction UUID, pattern fingerprint, offered count, exact aggregate
  inputs, and expected outputs must remain unchanged through recovery.
- `commit` returns either zero or the complete offered count. Partial native
  acceptance is invalid and is quarantined.
- Target acceptance must have a durable receipt outside transient machine
  simulation state.
- A throw after target invocation is an unknown outcome, not rejection.
- `reconcileTarget` must report accepted, not accepted, retry, or quarantine
  without invoking the target operation again.
- Unsupported recipes and machines return zero before source ownership changes.
- Terminal evidence is removed only after the overworld journal is terminal.

The AE2 source receipt is forward-only:

```text
STAGED -> EXTRACTING -> EXTRACTED -> TARGET_ACCEPTED
       -> ENERGY_ACCOUNTING -> ENERGY_ACCOUNTED
       -> PROGRESS_ACCOUNTED -> OUTPUTS_ACCOUNTING
       -> OUTPUT_ACCOUNTING -> OUTPUTS_ACCOUNTING ... -> ACCOUNTED
```

`EXTRACTING`, `ENERGY_ACCOUNTING`, and `OUTPUT_ACCOUNTING` are uncertainty
barriers. Recovery quarantines those states rather than guessing whether the
side effect completed and risking loss, duplication, or a double charge.

## Built-In Native Adapters

The development tree includes typed adapters for:

- GTCEu Modern `7.5.3`;
- Mekanism `10.4.16.80` with Applied Mekanistics `1.4.3`.

They load only when their child switch is enabled and every dependency version
matches exactly. Both require an exact deterministic processing pattern, one
deterministic adjacent target, Pattern Provider Blocking Mode, healthy receipt
ledgers, and native recipe/capacity validation. Unsupported chance outputs,
container returns, substitutions, conditions, keys, or targets use AE2's normal
path.

The target receipt belongs to persisted Pattern Provider logic. GTCEu or
Mekanism still owns recipe duration, power, machine progress, and final output;
ACO performs one aggregate all-or-zero provider push and never calls a machine
tick method speculatively.

## Registration

```java
PatternBatchV2Api.registerAdapter(MyTransactionalAdapter.INSTANCE);
PatternBatchV2Api.registerSource(MySourceReconciler.INSTANCE);
```

Adapter and source ids must be unique and stable across restarts. A third-party
integration must provide its own persistent target receipt and copied-world
kill/restart tests. Passing insertion simulation alone is not proof of durable
acceptance.

See [Experimental Crafting Engine](EXPERIMENTAL_ENGINE.md) and
[Testing](TESTING.md) before enabling V2.
