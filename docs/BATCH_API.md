# Transactional Pattern Batch V2

ACO exposes one machine-batch contract: `api.batch.v2`.

Issue #164 removed Pattern Batch V1, its sequential adapter, and ACO's built-in
GTCEu/Mekanism adapters. A removed V1 symbol or configuration key is not a
compatibility surface and must not be reintroduced as a fallback.

## Ownership

ACO owns the source-side transaction journal and standard AE2 accounting only
after every participant has accepted the same immutable transaction identity.
The external adapter owns its target, recipe validation, machine state, power,
progress, output receipt, and restart recovery.

Before ownership transfer an adapter may decline and AE2 continues normally.
After ownership transfer neither side may retry through a legacy path or guess
whether a side effect happened.

## Public Types

- `TransactionalPatternBatchAdapter`: target preparation, exact acceptance,
  reconciliation, rollback, and receipt cleanup.
- `BatchSourceReconciler`: source inventory, energy, task-progress, and expected
  output accounting.
- `PatternBatchV2Api`: stable-ID registration and lookup.
- `BatchTransactionJournal`: persisted cross-owner state written before source
  input moves.
- `ProviderOwnedPatternBatchTarget`: optional provider-owned target boundary.

ACO registers only its standard AE2 source reconciler. It does not register a
machine adapter. When no external adapter is registered, the live hook exits
before inspecting or mutating an AE2 job.

## Transaction Contract

The successful path is:

```text
prepare target receipt
stage source receipt and journal
extract one exact complete source aggregate
commit one all-or-zero target aggregate
account energy, task progress, and expected outputs
finish the journal
remove terminal source and target receipts
```

An implementation must satisfy all of these rules:

- The transaction UUID, pattern fingerprint, exact aggregate inputs, expected
  outputs, and offered count are immutable.
- Unsupported recipes and machines decline before source ownership changes.
- `commit` accepts zero or the complete offered count. Partial acceptance is a
  protocol violation and is quarantined.
- Target acceptance has a durable receipt outside transient simulation state.
- An exception after target invocation is an unknown outcome, not rejection.
- Recovery reconciles existing evidence and never invokes the target operation
  a second time.
- Terminal evidence is removed only after both owners reach a terminal state.

The source receipt is forward-only:

```text
STAGED -> EXTRACTING -> EXTRACTED -> TARGET_ACCEPTED
       -> ENERGY_ACCOUNTING -> ENERGY_ACCOUNTED
       -> PROGRESS_ACCOUNTED -> OUTPUTS_ACCOUNTING
       -> OUTPUT_ACCOUNTING -> OUTPUTS_ACCOUNTING ... -> ACCOUNTED
```

`EXTRACTING`, `ENERGY_ACCOUNTING`, and `OUTPUT_ACCOUNTING` are uncertainty
barriers. Recovery quarantines an unresolved barrier rather than risking item
loss, duplication, or double charging.

## Registration

```java
PatternBatchV2Api.registerAdapter(MyTransactionalAdapter.INSTANCE);
PatternBatchV2Api.registerSource(MySourceReconciler.INSTANCE);
```

IDs must be unique and stable across restarts. Third-party integrations must
provide copied-world kill/restart tests and exact accounting tests. Successful
insertion simulation alone is not proof of durable acceptance.

## Configuration

```toml
[craftingExecution]
enableTransactionalBatchingV2 = true
persistTransactionJournal = true
maximumBatchExecutions = 65536
```

Disabling V2 prevents new ownership transfers. Existing non-terminal journal
records remain recoverable; disabling a feature is not permission to abandon
already-owned input.

See [Feature Ownership](FEATURE_OWNERSHIP.md),
[Project Charter](PROJECT_CHARTER.md), and [Testing](TESTING.md).
