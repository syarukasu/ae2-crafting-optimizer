# Changelog

All notable changes to this project are documented here.

## [Unreleased]

### Added

- Added exact wide-aggregate planning for deterministic recipes whose distinct
  item, fluid, or chemical counters each fit signed `long` while their combined
  amount exceeds it.
- Added AQE BigInteger capacity-sidecar submission for plans whose exact AE2 CPU
  byte cost exceeds `Long.MAX_VALUE`, including atomic reservation promotion,
  restart persistence, standard-CPU rejection, and fail-fast Mixin validation.
- Added generation-cached `CompiledRootProgram` arrays. Deterministic roots now
  evaluate each reachable recipe once with primitive `long[]` demand counters,
  then restart the same program with `BigInteger[]` only after checked overflow.
- Added complete missing-leaf aggregation, referenced-key-only inventory capture
  and revalidation, shared-intermediate DAG support, and conservative fallback
  for alternatives, fuzzy inputs, returns, byproducts, emitters, and cycles.
- Added per-generation Shadow qualification. The complete Pattern, inventory,
  emitter, missing, output, and simulation accounts must match AE2 64 times by
  default; one mismatch rejects that root until its generation changes.
- Added an exact BigInteger implementation ceiling of `10^16384 - 1`.

### Changed

- Restored deterministic missing fast-fail to its documented opt-in default.
  The shortcut intentionally returns the first proven blocker and ends AE2's
  full missing-item calculation, so normal player-facing requests now keep
  AE2's complete calculation unless the option is explicitly enabled.
- Replaced full ME inventory Map snapshots and end-of-calculation full rescans
  in compiled planning with indexed reads of only the keys used by that root.

## [1.3.2] - 2026-07-18

### Fixed

- Added a client-side safety check that stops AE2 scrollbar Page Up/Down repeat
  after the physical left mouse button has been released. This prevents Pattern
  Access Terminal scrolling from continuing when another screen mod consumes
  the mouse-up event.
- Kept normal click-and-hold repetition, dragging, mouse-wheel scrolling,
  crafting logic, item transfer, and server-side accounting unchanged.

### Verification

- Clean Forge build and the complete automated test suite pass on Java 17.
- The same `1.3.2` jar is required on the server and every client.

## [1.3.1] - 2026-07-18

This is the P0-P8 runtime-qualification candidate. Automated tests and clean
builds are complete; startup, recovery, multiplayer, and live-world testing are
P9 and remain the pack operator's responsibility. Experimental behavior-changing
paths remain disabled by default.

### Changed

- Added exact source-extraction receipts and fail-closed recovery for persistent
  batch transactions.
- Hardened Pattern Provider escrow preconditions and exact ownership accounting.
- Hardened GTCEu and Mekanism native-batch verification with bounded exact
  matching and conservative fallback to AE2's standard path.
- Expanded planner, receipt, conservation, overflow, and typed-input tests.
- Coordinated optional BigInteger host API v3 compatibility with AQE `2.0.1`;
  neither mod is made mandatory by this integration.

### Fixed

- Made deterministic missing-item fast-fail fall back to AE2 whenever a Pattern
  contains a reusable tool, container return, or catalyst. GTCEu wrenches and
  files are no longer multiplied as permanently consumed ingredients.
- Preserved AE2's final crafting-provider refresh notification after same-tick
  coalescing. Pattern Access Terminal slot layouts now remain synchronized for
  large providers such as Crazy Pattern Providers.
- Expanded the automated suite to 153 passing tests with a reusable-tool
  fallback regression case.

## [1.3.0] - 2026-07-18

The release artifact was clean-built and qualified through Forge client bootstrap
and Arclight dedicated-server startup on the documented dependency stack. Deep
planner and native-batch rewrites remain opt-in and disabled by default.

### Added

- Added a disabled next-generation crafting-engine master section.
- Added checked-long arithmetic, an immutable generation-aware crafting graph,
  and a symbolic long planner with comparison-only Shadow Mode.
- Added a bounded deterministic missing-input proof for item, fluid, and chemical
  crafting keys. It short-circuits only when every recipe and alternative path
  is proven impossible; ambiguous graphs fall back to AE2 unchanged.
- Added a persistent V2 prepare/accept/account/reconcile journal with AE2 source
  receipts and GTCEu/Mekanism target receipts.
- Added exact all-or-zero native Pattern Provider batch adapters for GTCEu and
  Mekanism item, fluid, and supported chemical keys.
- Added a persistent deficit-round-robin scheduler for standard AE2 and Advanced
  AE CPU jobs, bounded by per-grid operation and elapsed-time budgets.
- Added JUnit coverage for arithmetic, graph/planner behavior, NBT, transaction
  phases, receipt state, scheduler budgets, fairness, and starvation prevention.
- Added stable SHA-256 graph/task fingerprints and exact aggregate validation at
  the native adapter boundary.
- Added checked overflow promotion from the deterministic long planner to a
  BigInteger planner, while ambiguous results remain Shadow-only fallbacks.
- Added API v3 for explicitly integrated BigInteger CPU hosts: atomic capacity
  reservation, multiple fair jobs, bounded execution windows, versioned NBT,
  long-job migration, persisted-lease recovery, cancellation quarantine, and
  output completion.
- Added a shared physical-capacity host that reconciles standard signed-long CPU
  reservations and native BigInteger jobs without allowing either path to
  oversubscribe the other.
- Added optional AQE 2.0.0 integration through a weak owner registry; ACO is not
  a required dependency of AQE and has no dependency on AQE.
- Applied the configured runtime count-byte budget to externally reconciled
  standard-job reservations, preventing oversized host NBT from allocating an
  unbounded collection of BigInteger magnitudes.
- Added a separate Forge channel protocol `2` with status payload protocol `1`, immutable paging,
  strict key/count decoding, a `1 MiB` packet cap, and persistent runtime ids.
- Added configurable BigInteger magnitude, execution-window, status-page, and
  aggregate count-memory bounds.
- Added a bounded multi-transaction instant dispatcher and generation-aware
  Pattern Provider routing cache for V2.
- Added exact-version optional bridge loading for GTCEu `7.5.3`, Mekanism
  `10.4.16.80`, and Applied Mekanistics `1.4.3`.
- Expanded automated coverage with randomized planner properties, 64/128/1024
  digit NBT values, status protocol rejection tests, runtime memory limits,
  output completion, and a 1,000-pattern/1024-digit benchmark.

### Fixed

- Recorded the actual modulated source extraction before validating its amount,
  so a simulation/modulation race cannot omit a partial extraction from rollback.
- Added an `ENERGY_ACCOUNTING` receipt barrier. An interrupted or failed charge
  is quarantined instead of being replayed and potentially charged twice.
- Replaced transaction-path field-name reflection with typed Accessor Mixin
  contracts and startup transformation audits.
- Deduplicated identical logical compiled patterns, built graphs outside the
  global cache lock, and revalidated provider generation before publication.
- Removed full remaining-task map copies from each BigInteger scheduler pass and
  made status totals and encoded-count memory accounting incremental.
- Closed the BigInteger host API around defensive job copies and immutable,
  runtime-bound execution leases so callers cannot bypass capacity accounting.
- Added byte-aware status-page shrinking so extreme configured magnitudes cannot
  overflow the strict packet cap merely because the entry-count limit was met.
- Added `EXTRACTING` and per-output `OUTPUT_ACCOUNTING` receipt barriers so a
  stop inside a source/output side effect is quarantined instead of guessed and
  replayed.
- Removed resolved source/target receipts only after the terminal journal record
  is complete, preventing the 256-entry ledgers from stalling normal throughput.
- Enforced the configured BigInteger bit limit during planner intermediates,
  added configured API planner/runtime restore factories, and made runtime
  encoded-count accounting incremental.
- Distributed a BigInteger runtime tick budget across selected runnable jobs and
  preserved the rotating cursor when the budget cannot reach every job.
- Aligned the journal Config maximum with its `16,384`-record load safety bound.
- Added exact-version fail-fast checks for the experimental AE2 and Advanced AE
  integration paths.

### Safety

- The experimental master, compiled graph, V2 batching, both native adapters,
  and fair scheduler all default to disabled.
- Empty receipt and scheduler compounds are not written while the experimental
  paths are unused.
- Malformed receipts and unknown journal schemas fail closed and remain
  inspectable. Stale terminal receipts are retained as bounded replay evidence
  only if post-journal cleanup cannot remove them.
- Existing journal records continue recovery after experimental feature flags
  are disabled, while unresolved source receipts pause that CPU's normal pushes.
- BigInteger support is an explicit-host sidecar API only. The API switch is
  enabled by default, but it does not patch standard AE2/Advanced AE jobs and
  has no gameplay effect without a compatible CPU add-on.
- Shipping defaults keep the experimental master, compiled planner, V2 native
  batching, and fair scheduler disabled. The explicit-host BigInteger API is
  enabled but inert unless a compatible add-on registers a host.

## [1.2.2] - 2026-07-18

### Fixed

- Unregistered every custom Import Bus, Export Bus, IO Port, and shared I/O tick-budget Mixin. AE2 now exclusively owns live storage insertion, extraction, rollback, and cell transfer.
- Unregistered standard and Advanced AE transactional batch execution Mixins so crafting inputs are transferred only by AE2's original execution path.
- Unregistered terminal craftable-set caching to keep zero-stock terminal entries synchronized with AE2's current repository generation.

### Safety

- Removed the custom Import Bus implementation that could extract first and fail to return a remainder when network insertion changed between simulation and modulation.
- Changed `cacheCraftableSets` and `cacheImportBusLastSuccessfulSlot` defaults to `false`; the latter is now a compatibility no-op.
- Recipe lookup, crafting calculation, execution-budget, machine-intent, and non-mutating compatibility optimizations remain enabled.

### Documentation

- Added the team development specification, safety invariants, acceptance criteria, and design-reference map.
- Aligned compatibility-path documentation with the 1.3.0 runtime Mixin list,
  release defaults, and qualified dependency versions.
- Added the experimental engine architecture, recovery model, NBT keys, and
  deferred runtime qualification matrix.
- Documented the BigInteger API/packet boundary and corrected native target
  receipt ownership to the persisted Pattern Provider logic.

## [1.2.2] - 2026-07-18

### Fixed

- Unregistered every custom Import Bus, Export Bus, IO Port, and shared I/O
  tick-budget Mixin. AE2 now exclusively owns live storage insertion,
  extraction, rollback, and cell transfer.
- Unregistered standard and Advanced AE legacy transactional batch execution
  Mixins so crafting inputs use AE2's original execution path.
- Unregistered terminal craftable-set caching to keep zero-stock entries tied
  to AE2's current repository generation.

### Safety

- Removed the custom Import Bus implementation that could lose a remainder when
  network insertion changed between simulation and modulation.
- Changed `cacheCraftableSets` and `cacheImportBusLastSuccessfulSlot` defaults
  to `false`; the latter is now a compatibility no-op.
- Recipe lookup, crafting calculation, execution-budget, machine-intent, and
  non-mutating compatibility optimizations remain enabled.

## [1.2.1] - 2026-07-17

### Fixed

- Unregistered terminal inventory snapshot, terminal range packet, client repository coalescing, storage watcher pacing, and aggregate storage refresh Mixins after stale terminal generations could conflict with live insertion.

### Changed

- Changed `throttleStorageWatcherUpdates` and `networkForceUpdateCoalescing` source defaults to `false`.
- Retained the affected Config keys as compatibility no-ops for existing world TOML files.

## [1.2.0] - 2026-07-17

### Added

- A public internal pattern-batch adapter API with explicit accepted-execution-count receipts.
- A conservative built-in Pattern Provider adapter for standard AE2 and Advanced AE CPU execution.
- Independent transactional batching limits, target namespaces, and adapter controls in server config.
- Defensive API rules for zero-mutation rejection, exact ownership transfer, deterministic target routing, and adapter result validation.
- Time-bounded instant dispatch across multiple ready tasks and adapter transactions in one CPU call.
- `PatternBatchBudget` for shared operation and wall-clock boundaries inside iterative or native adapters.

### Changed

- Reimplemented CPU batching around exact one-execution input templates. Only exact processing patterns without substitutions or container remainders are eligible.
- Batch extraction and AE2 accounting may be combined, but the built-in adapter preserves one original `pushPattern` call per accepted execution and stops immediately when the provider becomes busy.
- Expected outputs, task progress, energy, and metrics are now updated from the adapter's actual accepted count rather than the requested batch size.

### Safety

- The unsafe 1.1.0 aggregate-inventory path remains permanently disabled and its legacy config keys remain no-ops.
- Unsupported providers, targets, patterns, and adapters fall back to AE2's original `executeCrafting` path before inputs are transferred.
- A native future adapter may accept multiple executions in one call only when it can durably own exactly the returned count; partial insertion simulation is explicitly not sufficient.
- This source build is intentionally not deployed over the currently installed 1.1.1 jars until an in-game test is requested.

## [1.1.1] - 2026-07-17

### Added

- Optional Neo ECO AE Extension 20.3.x integration for its custom ECO crafting CPU.
- A dedicated `[compatibility.neoEcoAe].throttleNeoEcoAeExecution` server-config switch, enabled by default when ACO execution pacing is enabled.
- Compile-only signature validation against Neo ECO 20.3.0 without bundling or requiring Neo ECO at runtime.

### Changed

- Neo ECO's existing normal and FastPath tick limits now pass through ACO's adaptive per-CPU and shared per-grid execution budgets when the optional integration is active.
- Standard AE2 and Neo ECO execution wrappers now use ACO's common server tick identifier, ensuring they debit one coherent `CraftingService` budget in the same tick.

### Fixed

- Changed terminal inventory snapshot reuse, terminal craftable caching, client view coalescing, and visible range splitting to default OFF. This prevents stale zero-stock terminal generations from conflicting with interactive insertion slots in heavily modified clients.
- Compatibility-disabled aggregate processing-pattern micro-batching. External machine input acceptance cannot guarantee every represented recipe completion, which could leave AE2 waiting forever for multiplied outputs that were never produced.

### Safety

- Neo ECO's recipe logic, scheduler, batch/aggressive FastPath, storage, displayed CPU values, energy accounting, status batching, job persistence, and crafting results remain authoritative and unchanged.
- The optional Pseudo Mixin is version-bounded to Neo ECO 20.3.x and is skipped when the mod is absent.
- This release was clean-built and checked against the published Neo ECO 20.3.0 bytecode signatures. In-game Neo ECO runtime testing remains pending.
- Both standard AE2 and Advanced AE micro-batch Mixins are unregistered. Legacy config keys remain readable, but a configured `true` value is ignored with a startup warning.

## [1.1.0] - 2026-07-17

### Added

- A per-ME-grid shared real-time budget for standard AE2 crafting CPU pattern pushes.
- Crafting-job-local memoization for invariant emit, pattern, fuzzy-candidate, and container-return queries.
- Provider pattern generations, Assembly Matrix crafter routing, bounded IO Port cell cursors, Import Bus last-success hints, and Export Bus configured-key generations.
- Short-lived resolved recipe-intent caches for GTCEu candidate lists and Mekanism validated recipes.
- Cached Mekanism input-field access plans and recipe-test method lists to remove repeated reflection scans.
- Chunk-bucketed GTCEu multiblock intent lookup with concrete-input candidate prioritization.
- Advanced AE Pattern Provider intent capture and Quantum Computer effective co-processor pacing.
- Optional CrazyAE-style processing-pattern micro-batching with exact AE2 input, energy, waiting-output, and task accounting.
- Configurable AdvancedAE Reaction Chamber reuse of the recipe already resolved after an input change.
- Exact ExtendedAE Circuit Cutter positive/negative recipe-result sharing with live recipe revalidation.
- AE2 Overclock runtime-helper reflection metadata and MethodHandle caches plus one-tick upgrade-count caches.
- One-tick used-thread and busy-thread caches, crafter routing, and same-tick status-update coalescing for ExtendedAE Assembly Matrices.
- Optional cross-tick adjacent capability reuse tied to Forge `LazyOptional` invalidation.
- Optional generation-checked asynchronous terminal search and sorting over client-thread-created immutable projections.
- `/aco stats` and `/aco stats reset` diagnostics for execution pacing and machine cache hit rates.
- Recipe-intent/index invalidation after server datapack recipe reloads.

### Changed

- Expanded independent server config switches for calculation, execution, grid tick, bus, terminal, intent, add-on machine, and deep AE2 optimization paths.
- Import/Export Buses and Circuit Cutters are exempt from hard Grid Tick deferral and idle/slow backoff to prevent transfer starvation.
- Documentation now distinguishes active AE2 Overclock runtime-helper caches from compatibility-disabled redirects into Mixin-merged machine handlers.

### Fixed

- Removed registration of the AE2 Overclock merged-method reflection/MethodHandle redirect Mixin. Forge Mixin cannot safely redirect handlers added by AE2 Overclock's own higher-priority Mixins, and the attempted integration produced startup injection warnings on Reaction Chambers and Circuit Cutters.
- Kept AE2 Overclock's original reflection path authoritative in its Mixin-merged machine handlers while retaining runtime-helper reflection/MethodHandle and one-tick upgrade-count caches.

### Safety

- Every active standard AE2 CPU retains a configurable minimum progress allowance after the shared grid budget is consumed.
- Mekanism cache hits still run Mekanism's live recipe test before returning a recipe.
- GTCEu cache hits only reuse a candidate prefix; GTCEu's original matching and setup path remains authoritative.
- Bus simulation caching never skips a real transfer, and Circuit Cutter cache hits always pass ExtendedAE's live recipe test.
- Add-on machine optimizations do not change recipes, machine throughput, process waves, energy use, inventories, or matrix structure rules.
- Pattern micro-batching defaults off and rejects dedicated crafting machines, multiple targets by default, blocking/locked providers, container returns, directional Advanced AE patterns, unsupported namespaces, and non-atomic target capacity.
- Unsupported or disabled optimization paths always fall back to AE2 or the owning machine mod.

## [1.0.0] - 2026-07-16

### Added

- Active AE2 crafting-calculation single-flight and bounded completed-plan caching.
- Pattern lookup and craftable-set caches with grid/provider invalidation.
- Configurable giant crafting CPU execution budgets and adaptive pacing.
- Terminal snapshot, visible range, storage watcher, and P2P notification pacing.
- Optional grid-tick, IO-bus, and failed automatic craft-request controls.
- Pattern Provider recipe-intent capture.
- Optional GTCEu Modern and Mekanism output-indexed recipe-intent fast paths for item, fluid, and supported chemical processing.
- Server diagnostics and `/aco intents` maintenance commands.
- Original ACO icon for Forge mod listings and repository documentation.
- Bilingual English/Japanese README and CurseForge publishing copy.

### Safety

- AE2 remains authoritative for craft planning, job submission, storage mutation, recipes, and network topology.
- Deterministic fast-fail, grid tick deferral, IO-bus caps, fuzzy bus caching, and successful-plan reuse are disabled by default.
- Advanced AE Quantum Computer execution logic is not mixed into by the conservative 1.0.0 build.

[Unreleased]: https://github.com/syarukasu/ae2-crafting-optimizer/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/syarukasu/ae2-crafting-optimizer/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/syarukasu/ae2-crafting-optimizer/compare/beta_1.0.0...v1.1.0
[1.0.0]: https://github.com/syarukasu/ae2-crafting-optimizer/releases/tag/beta_1.0.0
