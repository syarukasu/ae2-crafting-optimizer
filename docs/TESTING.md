# Testing

## Automated

Run:

```powershell
.\gradlew.bat test --no-daemon --rerun-tasks
.\gradlew.bat clean build --no-daemon
```

Automated tests cover:

- checked `long` and `BigInteger` planning;
- selected input alternatives;
- twenty-stage quantity-independent formula generation;
- exact escrow debit and credit;
- exact boundary mutation recovery for all-before, mixed, all-after, and
  invalid external-change states;
- parent BigInteger lease commit, rollback, and persistence;
- NBT count bounds and canonical encoding;
- transaction journal behavior;
- physical Worker formula multiplication;
- durable terminal receipt identity and explicit forget.
- nine identical signed-long input slots without merged-input rejection.
- exact NetworkStorage snapshot reuse within one tick;
- same-tick invalidation after a storage generation change;
- refusal to reuse a snapshot across ticks;
- nested NetworkStorage reuse while an outer capture remains active;
- full-grid terminal reuse of AE2's cached inventory with exact sidecars;
- preservation of add-on-specific terminal inventory paths.

## Required Live Matrix

Runtime testing must use the same ACO and AAC JAR on server and clients.

### Basic Physical Path

1. Form an AAC structure with Neo ECO Pattern Buses and Workers.
2. Encode a deterministic `9 input -> 1 output` crafting-table Pattern.
3. Order `1`, `1,000`, and a large signed-long quantity.
4. Confirm the Worker performs one physical recipe stage per Pattern, not one
   Thread per requested craft.
5. Use a tag alternative, restart, and confirm the persisted concrete key is
   revalidated rather than replaced by another tag member.
6. Confirm exact input consumption and output count.
7. Confirm normal AE2 job status remains visible.

### Twenty-Stage Chain

1. Encode a linear twenty-stage deterministic crafting-table chain.
2. Test root amounts:

```text
1
1000
Long.MAX_VALUE
10^64 - 1
10^1024 - 1
```

3. Confirm each order performs twenty dependency stages.
4. Confirm the requested amount changes arithmetic values but not step count.
5. Confirm each parent stage waits until its child's physical output receipt is
   credited.
6. Confirm no internal item is created in ME merely because it appears in the
   compiled plan.

### Independent Branches

1. Build a root with at least two independent crafting branches.
2. Provide multiple Workers.
3. Confirm both branches may run at the same time.
4. Confirm the root starts only after both branch outputs exist in escrow.

### Processing Boundary

1. Place a GTCEu or Mekanism processing Pattern between two deterministic
   crafting-table sections.
2. Confirm the upstream crafting section completes.
3. Confirm the machine receives and processes its real inputs.
4. Confirm the downstream crafting section waits for the real machine output.
5. Confirm no machine output is synthesized or skipped.

### Fallback Before Ownership

Test:

- substitutions;
- duplicate producers;
- cycles;
- changing NBT;
- durability-sensitive recipes;
- unknown remaining items;
- missing AAC Pattern ownership;
- unavailable or malformed exact storage.

Every case must leave boundary storage unchanged and use AE2's normal route or
report unsupported.

### No Fallback After Ownership

After boundary input reservation:

1. remove power;
2. unload the Worker chunk;
3. break and reform the structure;
4. change a recipe generation;
5. cancel the parent job.

Confirm the transaction waits, resumes, returns exact escrow, or quarantines.
It must not start the normal path for the same work.

### Restart Points

Stop and restart after each persisted point:

- before boundary mutation;
- after one key of a multi-key mutation;
- after all boundary input extraction;
- after Worker acceptance;
- while Thread progress is running;
- after `OUTPUT_READY`;
- after Worker terminal receipt creation;
- after escrow credit;
- after final ME insertion;
- before parent commit.

For every point, compare exact storage before and after. There must be no loss,
duplication, negative count, clamped accounting, or repeated job.

The first Worker/Thread lookup after restart may rebuild runtime UUID indexes.
Later status polling must use those indexes without scanning every Worker and
Thread again.

### Multi-Key Mutation Recovery

Use at least three distinct exact-storage keys.

1. Persist before values.
2. Apply only some keys and stop.
3. Restart.
4. Confirm after-valued keys are not replayed.
5. Confirm before-valued keys are applied once.
6. Change one key to a third value and confirm quarantine.

### Cancellation

Cancel:

- before input reservation;
- after input reservation but before Worker acceptance;
- while a Worker is running;
- after output is ready;
- after escrow credit.

Only unfinished physical work may return its reserved inputs. Completed output
must return as output, never as both output and original input.

### Capacity and Fairness

1. Submit multiple BigInteger parent jobs to the same Quantum Computer.
2. Include one small job among giant jobs.
3. Confirm per-grid start and active-stage budgets are respected.
4. Confirm the small job continues to receive scheduler opportunities.
5. Record TPS, MSPT, GC allocation, and `/aco stats`.

### Diagnostics

`/aco stats` should report:

- `Physical crafting tree` starts;
- active scheduler ticks;
- completed, cancelled, and quarantined transactions;
- start deferrals;
- receipt recovery and fingerprint revalidation.

Statistics must not iterate or print complete BigInteger inventories.

For Issue #28 profiling, `/aco stats` should additionally report:

- exact storage snapshot cache hits and misses;
- nested network scans;
- storage-generation invalidations;
- full-grid terminal snapshot reuses.

Repeat the same Spark capture with a Pattern Encoding Terminal open. Confirm
that `BigIntegerStorageSnapshotBridge.collect()` and nested
`NetworkStorage.getAvailableStacks()` no longer rebuild once per terminal plus
once per `StorageService` refresh when no intervening storage mutation occurs.

### Persisted Pattern Identity Recovery (Issue #29)

Use one deterministic physical crafting-table job with at least one accepted
Worker receipt. Record every exact input, output, task, and `waitingFor` value.

1. Unload the Pattern Provider chunk while the job is running. The transaction
   must report waiting; it must not quarantine, cancel, refund, or advance.
2. Reload the unchanged Provider. The same receipt must resume and every exact
   counter must remain equal to the recorded value.
3. Repeat across a server save and restart. Reconciliation must use the
   transaction-owned encoded Pattern definition even before the live graph has
   rediscovered the Provider.
4. Reload recipes without changing the Pattern formula. The job must revalidate
   and continue without creating another receipt.
5. Replace the encoded Pattern with a definition whose selected inputs or
   expected outputs differ. The transaction must quarantine while preserving
   escrow and receipts for administrator recovery.
6. Repeat each reconciliation twice. Planned, dispatched, introduced, and
   credited totals must be idempotent; no item may be duplicated or lost.

`PhysicalPatternAccountingSourceContractTest` runs in headless JUnit and locks
the schema, ownership-boundary capture, live-graph-independent accounting, and
retry/conflict branches. The lifecycle scenarios above remain GameTest/manual
checks because AE2 encoded items require the real mod registry lifecycle.

## Disable Checks

1. Disable the physical path before starting a new job and confirm normal AE2
   execution remains available.
2. Disable it with an existing persisted receipt and confirm recovery still
   reconciles ownership instead of deleting the receipt.
3. Disable AAC vector execution and confirm its controller delegates performance
   behavior to Neo ECO.

## Issue #103 Backport Checks

- Root Programの循環と複数Producerが別理由になること。
- 不完全Snapshotだけが一回の再構築対象になること。
- BigInteger台帳非対応CPUが`CPU_TOO_SMALL`を返さないこと。
- Forge 1.20.1とNeoForge 1.21.1の両方で全JUnitと`clean build`を通すこと。

## Not Proven by Gradle

Gradle tests do not prove:

- Forge client startup;
- dedicated-server startup;
- Arclight runtime behavior;
- multiblock formation in a live world;
- real AE power consumption;
- chunk save ordering;
- compatibility with unpinned add-on versions;
- TPS improvement on a production network.

These require the live matrix above.
