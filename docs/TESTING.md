# Testing

## Automated

Run:

```powershell
.\gradlew.bat test --no-daemon --rerun-tasks
.\gradlew.bat clean build --no-daemon
```

Automated tests cover:

- Mixin configuration contract boundaries: required core accounting, separated fail-closed integrations, and fail-open performance-only paths;
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
- distinct compiled root-program failure reasons for cycles, multiple
  producers, byproduct patterns, and incomplete graph snapshots;
- retrying only snapshot-shaped root-program failures;
- refusing a wide plan without claiming that the crafting CPU is too small.
- exact NetworkStorage snapshot reuse within one tick;
- same-tick invalidation after a storage generation change;
- refusal to reuse a snapshot across ticks;
- nested NetworkStorage reuse while an outer capture remains active;
- full-grid terminal reuse of AE2's cached inventory with exact sidecars;
- preservation of add-on-specific terminal inventory paths.
- 1,000 explicit Big Crafting Host register/close cycles returning the registry
  to zero without relying on GC;
- replacement-generation safety where an old handle cannot close a newer host;
- atomic host snapshots, clamped overcommit availability, and close-time
  admission rejection.
- canonical exact-count payload round trips and rejection of non-canonical
  encodings;
- capability snapshot initialization, receipt reservation idempotence, and
  revisioned snapshot/wakeup behavior.
- pure-Java canonical fixture parity for shaped/shapeless semantics, repeated
  keys, remaining containers, concrete tag alternatives, ambiguous producers,
  cycles, missing inputs, partial inventory, signed-long boundaries, deep/wide/
  shared dependency graphs, and generation changes;
- reviewable Golden JSON output that is updated only by the explicit
  `updateGoldenFixtures` task;
- crash-window conservation and idempotent Receipt reprocessing for the exact
  ownership boundaries listed in the parity issue.

To deliberately regenerate the reviewable fixture output, run:

```powershell
.\gradlew.bat updateGoldenFixtures --no-daemon
```

The ordinary `test` task never writes Golden files.

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

### Wide Plan Submission Refusal

Covers issue #103, problem 1.

1. Build a two-stage chain whose total executions exceed `Long.MAX_VALUE`
   (for example `1 log -> 4 planks` and `1 plank -> 1 button`, then order
   `Long.MAX_VALUE` buttons). Two stages put the execution total at roughly
   1.25x the order, which no longer fits a signed long.
2. Give the network a standard AE2 crafting CPU with `Long.MAX_VALUE` free
   bytes, so the required bytes and the free bytes print as the same number.
3. Submit the job and confirm the refusal does **not** say the CPU is too
   small. The plan is declined because no signed-long CPU ledger can hold it.
4. Confirm `latest.log` contains exactly one `ACO refused to submit a plan for
   ... to a standard AE2 crafting CPU` warning per output, carrying the exact
   byte count and the free bytes the CPU reports.
5. Add crafting storage and resubmit. The refusal must be unchanged; the
   message must not have sent the player to add storage in the first place.
6. Repeat with an Advanced AE crafting CPU that has no registered BigInteger
   host and confirm the same non-capacity refusal.
7. Set `logWidePlanSubmissionDeclines = false` and confirm the refusal itself
   still happens with no new warning line.

### Incomplete Graph Snapshot Fallback

Covers issue #103, problem 2.

1. Order the same two-stage chain while patterns are being added or removed so
   the pattern generation moves during the calculation.
2. When ACO returns the calculation to AE2, confirm `/aco stats` and the slow
   calculation line report `INCOMPLETE_GRAPH_SNAPSHOT`, never
   `AMBIGUOUS_PRODUCER`, for a root that has exactly one pattern.
3. Confirm `latest.log` carries one `compiled crafting graph snapshot was
   incomplete` warning per output and generation pair, including
   `patternGeneration` and `recipeGeneration`.
4. Encode a genuinely ambiguous root (two patterns for the same output) and
   confirm it still reports `AMBIGUOUS_PRODUCER`, and that no graph rebuild
   is triggered for it.
5. Install a mod whose patterns ACO cannot compile (AppliedE is the reference
   case) and order repeatedly through a root that touches one. Confirm with
   Spark that the graph is rebuilt at most once per pattern generation, not
   once per calculation.
6. Set `retryIncompleteCraftingGraphSnapshot = false` and confirm the same
   reason code is reported with no second graph build.

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

Physical Exact Vector transactions are revision-driven. A waiting tick does not
reserialize the transaction or call the parent `markDirty()` path. Active
steps are indexed by scheduling lane, and dependency waiters are requeued only
when the corresponding escrow key is credited. The runtime diagnostics expose
queue scans/processed steps, accounting snapshot rebuilds, and avoided dirty
calls in the existing optimization summary.

Repeat the same Spark capture with a Pattern Encoding Terminal open. Confirm
that `BigIntegerStorageSnapshotBridge.collect()` and nested
`NetworkStorage.getAvailableStacks()` no longer rebuild once per terminal plus
once per `StorageService` refresh when no intervening storage mutation occurs.

## Disable Checks

1. Disable the physical path before starting a new job and confirm normal AE2
   execution remains available.
2. Disable it with an existing persisted receipt and confirm recovery still
   reconciles ownership instead of deleting the receipt.
3. Disable AAC vector execution and confirm its controller delegates performance
   behavior to Neo ECO.

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
