# 回帰履歴

- [Issue #218](issues/ISSUE-218.md), 2026-10-06, vm-refs.8-local candidate:
  Requester CRAFT_LESS repeated feasibility trials for each quantity bit even
  on unique fixed-input graphs. Shared demand coefficients now bound funded
  quantities; integral graphs materialize directly and unshared fractional
  graphs search only rounding uncertainty. Shared fractional capacity paths,
  stocked manufactured intermediates, alternatives and dynamic rules retain
  existing ordered trials. No retry/dispatch limit or cross-order stock cache.
  A separate old-search reproduction found capacity shapes could forget visited
  descendants after a stocked-boundary summary (468 versus 460 bytes). Retain
  their capacity-only union. Quantity/capacity, native capture and performance
  evidence: [Requester evidence](issues/evidence/ISSUE-218-REQUESTER-2026-10-06.md).

- [Issue #218](issues/ISSUE-218.md), 2026-10-06, vm-refs.7-local candidate:
  The missing pass accepted the first producer using hypothetical missing inputs
  before trying funded alternatives. An unrelated true shortage could therefore
  add a false 480-item intermediate shortage despite partial stock and a gold
  producer. Try funded routes transactionally, then simulate only the remainder;
  retain producer priority, returns/byproducts and per-craft capacity semantics.
  Two fail-before regressions retain the old wrong shortages. Independent
  quantities extend through 3402 bits; real AEProcessingPattern capture also
  verifies the funded route without post-capture world access. Successful native
  AE2 oracle comparisons remain exact. Historical missing-route choices are not
  imposed on the corrected VM; fixed-input random cases additionally verify
  stock bounds and material conservation. The original live-network cause and
  subsequent false-shortage result remain unverified until re-ordered.

- [Issue #218](issues/ISSUE-218.md), 2026-10-06, vm-refs.6-local candidate:
  Known vanilla substitution rules still acquired world access for unseen NBT,
  and stocked intermediate items prevented arithmetic demand aggregation.
  Freeze audited native recipe rules, group opaque observations only for an
  encountered key, and cache demand summaries at stocked boundaries. Preserve
  the native read deadline between callbacks; no dispatch limit is added.
  Randomized differential tests caught a capacity regression (441 vs 505 bytes)
  despite equal item totals: combining fractional alternative-producer periods
  changed conceptual node visits. Keep the ordered VM for those periods.
  Reusing alternating stock shapes also fixes a slower first implementation.
  Final synthetic x4000 trials fall from 3720 to 627 with all ledgers equal;
  both AE2 variant suites and supplied-AQE capacity checks pass. Public API and
  physical execution classes are byte-identical to installed refs.5. No live
  speedup, false-shortage repair, restart or wide lifecycle acceptance is claimed.
  [Evidence](issues/evidence/ISSUE-218-INPUT-STOCK-2026-10-06.md).

- [Issue #218](issues/ISSUE-218.md), 2026-10-05, vm-refs.5-local candidate:
  The VM-only runtime still shipped a retired graph solver and maintained its
  unused provider catalog. Separate graph/SMT into comparison fixtures, remove
  catalog-update injections, and keep the scaled BigInteger coefficient reader
  in a small runtime class. The installable bundle shrinks from 2930678 to
  1359504 bytes; outer ACO classes and retained exact arithmetic are unchanged.
  Bundle gates reject fixture/solver leakage and require matching runtime source
  archives. Upstream and UELM gates pass, with bytecode dependency checks.
  Separately, two all-NUL client TOML files are backed up and restored with user
  approval. Startup, GC crash resolution and full live acceptance remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-10-05, vm-refs.4-local candidate:
  Fixed native crafting inputs rejected unmatched NBT locally, but their caller
  still acquired a live world interval and unnecessarily opened the scheduler.
  Specialize this predicate before generic dispatch, following PostgreSQL's
  expression fast paths and Truffle's stable-input partial evaluation approach.
  Real AE2 Input fixtures preserve used/missing/returned quantities and eliminate
  ten empty live boundaries across the stock/quantity matrix. Dynamic inputs,
  including peers in the same item family, still observe new returned states.
  Finite-tool regression coverage also exposed an inflated replay-search window:
  successful replay and changed key domains had doubled an unsuccessful-search
  window. Following SymPy's geometric search scheduling, grow only on exhaustion
  and reset after success/domain changes. Exact replay bounds and quantity
  equations, the public API and physical execution are unchanged. Tool periods
  32/65/257 preserve exact replacement counts through 10^1024-1 quantities.
  Production timing, original false-shortage reproduction and full acceptance
  remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-10-05, vm-refs.3-local candidate:
  Lazy process metadata had lost the archived VM's compiled-block reuse. Every
  repeated block re-entered compileIfAbsent and its output/input validation.
  Cache that code once per demanded pattern without eager unused-alternative
  compilation. The 1000-craft, partially stocked 32-use-tool fixture drops primary
  metadata reads from 170 to 2 with identical quantities and capacity charges.
  Unknown returned states remain exact, observed only when encountered. No
  execution throttles or API changes. Historical dev.20-named JAR hashes diverge
  from the recorded artifact, so archived-source comparison is not authenticated
  production dev.20 acceptance. Full live acceptance remains pending.

- [Issue #218](issues/ISSUE-218.md), 2026-10-05, vm-refs.2-local candidate:
  Snapshot acquisition omitted candidate keys produced by later dependencies.
  Collect stock/templates/all reachable outputs before observing their semantics;
  closed domains finish with post-capture world access disabled. Primary-only
  return classification also dropped alternate containers/catalysts and could
  invent a shortage despite a reusable seed. Classify captured alternatives and
  NBT-sensitive output reuse too. Reproduction checks exact consumed/missing
  quantities through 1024 digits. AE2's alternative-only return omission is not
  a correctness oracle: its missing return charge is asserted explicitly.
  Do not claim the original copper-wire report, arbitrary future NBT callbacks
  or production acceptance resolved. No provider execution changes.

- [Issue #218](issues/ISSUE-218.md), 2026-10-04, vm-refs.1-local candidate:
  Production graph.3 spent server time capturing hypothetical returned input
  states and worker time in global SMT solving. Restore the selected BigInteger
  VM arithmetic with order-local input/NBT observations and batched actual-stock
  candidates. Do not pre-expand future tool states or re-enter the old ACO
  planner from the scheduler superclass constructor. Prepared candidate fixtures
  finish with all later world reads disabled; previously repeated catalyst
  callbacks occur once per order. Unknown new opaque candidates still use the
  AE2 read boundary. Both AE2 variants and bundle gates pass; no production
  deployment, speed, wide execution or startup acceptance is claimed. See the
  [evidence record](issues/evidence/ISSUE-218-VM-REFERENCES-2026-10-04.md).

- [Issue #218](issues/ISSUE-218.md), 2026-10-04, graph.3-local startup candidate:
  MEGACells ForgePlatform selected the active caller's FML language extension
  during static initialization. Under a Kotlin caller this throws; under another
  Java caller it silently selects the wrong event bus. Resolve the registered
  MEGACells owner's context locally without modifying the thread context or
  cancelling any initializer. Keep the graph calculator and existing API.
  Forge-context and packaged-bytecode checks pass; actual launch, native JVM
  errors and Windows black-screen acceptance remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-10-03, graph.2-local candidate:
  The graph.1 path rebuilt processing definitions and topology on every order.
  A service-owned provider catalog now retains recognized processing constants
  and invalidates dependent root programs on provider add/remove/refresh.
  Frozen programs compile on the AE2 worker; stock and unknown input semantics
  remain order-local. Never infer availability from a previously solved order
  or invalidate an in-flight T0 snapshot after another player's pattern edit.
  No rollback, client/server change or runtime startup acceptance accompanies
  this candidate. General cyclic/physical/capacity-policy gaps remain explicit.

最適化経路を変更する前に、この一覧から関連Issue仕様書を読みます。詳細な症状、原因、
修正、不変条件、禁止事項、試験は`docs/issues/ISSUE-<番号>.md`を正本とします。

| Issue | 症状 | 影響版 | 修正版 | 仕様書 |
|---|---|---:|---:|---|
| [#79](https://github.com/syarukasu/ae2-crafting-optimizer/issues/79) | 一つのroot内部だけでsigned long境界を超えるexact計画が失われる | 1.5.17 | 1.5.18 | [ISSUE-79.md](issues/ISSUE-79.md) |
| [#90](https://github.com/syarukasu/ae2-crafting-optimizer/issues/90) | 無関係なProvider世代更新でBigInteger計画が提出時に失効する | 1.5.18 | 1.5.20 | [ISSUE-90.md](issues/ISSUE-90.md) |
| [#93](https://github.com/syarukasu/ae2-crafting-optimizer/issues/93) | Java 25でBigInteger在庫Sidecarの可視コピー中にJVMが終了する | 1.5.18 | 1.5.20 | [ISSUE-93.md](issues/ISSUE-93.md) |
| [#98](https://github.com/syarukasu/ae2-crafting-optimizer/issues/98) | 外部CPU登録済みでも合計bytesだけlong超過するBigCapacity計画が拒否される | 1.5.19 | 1.5.20 | [ISSUE-98.md](issues/ISSUE-98.md) |
| [#101](https://github.com/syarukasu/ae2-crafting-optimizer/issues/101) | 外部セルが正確なBigInteger在庫を公開する安定APIがない | 1.5.19 | 1.5.20 | [ISSUE-101.md](issues/ISSUE-101.md) |
| [#102](https://github.com/syarukasu/ae2-crafting-optimizer/issues/102) | 小規模ジョブが初回probeへ縮小されPattern Provider配送が遅延する | 1.5.19 | 1.5.20 | [ISSUE-102.md](issues/ISSUE-102.md) |
| [#103](https://github.com/syarukasu/ae2-crafting-optimizer/issues/103) | wide計画の実行裏付け不足がCPU容量不足と誤報される | 1.5.19 | 1.5.20 | [ISSUE-103.md](issues/ISSUE-103.md) |
| [#109](https://github.com/syarukasu/ae2-crafting-optimizer/issues/109) | BigInteger API連携が通常AE2の責務境界を越える | 1.5.20 | 1.5.21 | [ISSUE-109.md](issues/ISSUE-109.md) |
| [#140](https://github.com/syarukasu/ae2-crafting-optimizer/issues/140) | Mekanism入力探索がDedicated Serverでclient-only音声型を毎tick解決する | 1.5.25 | 1.5.26 | [ISSUE-140.md](issues/ISSUE-140.md) |
| [#148](https://github.com/syarukasu/ae2-crafting-optimizer/issues/148) | 同一キーのmounted storage合計がlong境界を超えると端末表示が消える | 1.5.27以前 | 1.5.28 | [ISSUE-148.md](issues/ISSUE-148.md) |
| [#151](https://github.com/syarukasu/ae2-crafting-optimizer/issues/151) | 版統合時にPR #127のBigInteger物理実行基準が失われる | 1.5.25-1.5.27 | 統合PR | [ISSUE-151.md](issues/ISSUE-151.md) |
| [#153](https://github.com/syarukasu/ae2-crafting-optimizer/issues/153) | 同一キーのmounted storage合計がlong境界を超えるとストレージモニターが負数化する | 1.5.28以前 | 1.5.29 | [ISSUE-153.md](issues/ISSUE-153.md) |
| [#179](https://github.com/syarukasu/ae2-crafting-optimizer/issues/179) | worker上の純粋計算中もsimulateForがtickを待たせる。固定4-thread化だけでは解決しない | 2.0.0開発版 | ローカル修正 / Snapshot適格な標準経路も分離。動的経路は対象外 | [ISSUE-179.md](issues/ISSUE-179.md) |
| [#182](https://github.com/syarukasu/ae2-crafting-optimizer/issues/182) | AQE専用実行の削除後、外部CPUから既存物理加工を呼び出す公開経路がない | 2.0.0開発版 | PR / 実機未確認 | [ISSUE-182.md](issues/ISSUE-182.md) |
| 外部コンシューマ回帰 | 実経路でsidecarが消える、またはQuantum Bulkが`maxPatterns=1`へ誤って制限される | 1.5.18系 | 作業中 | [ISSUE-BIGINT-EXTERNAL-CONSUMER.md](ISSUE-BIGINT-EXTERNAL-CONSUMER.md) |

## 2.0.0開発版の再発記録

- [Issue #218](issues/ISSUE-218.md), 2026-10-02, local dev.21 candidate:
  An unresolved leaf becomes a confirmed shortage even when its newly known
  producer list is empty. A naive demand-program reuse broke shared-DAG work
  guards. The accepted local trial preserves that transition and child shape
  generations, while recording bounded live remainder-call breakdowns without
  caching dynamic inputs. Both AE2 variants build and pass tests; production
  50/100-item timing, cancellation and physical completion remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-10-01, local rc.7-dev.19 correction:
  Eager all-craftables capture was an ACO-added deviation from AE2 demand
  planning and moved unrelated pattern work onto the server thread. Restore
  one start-stock snapshot and request-local producer/emitter memoization only
  for keys the VM actually demands. Upstream AE2 and UELM builds, contract
  suites and 16 isolated GameTests each passed. Production latency and wide
  processing completion are still unverified; this is not release acceptance.

- [Issue #161](issues/ISSUE-161.md), 2026-09-28, local rc.7-dev.17 candidate:
  Disabled ordinary limiters still retained dead timing/wave state and Neo ECO
  injections. Delete these implementations and registrations; retain stateless
  binary-compatible entry points. External MAX_VALUE operations must pass intact,
  unlike a native coprocessor count which needs +1 headroom. Preserve native
  blocking/energy/ordering and exact ownership. Both AE2 variants pass the pinned
  real-provider regressions and build/API checks; production remains on dev.13,
  with full industrial and external exact-job acceptance still pending.

- [Issue #161](issues/ISSUE-161.md), 2026-09-28, local rc.7-dev.16 candidate:
  Successful provider pushes rebuilt identical native descriptions and copied
  advisory lists per target side. Freeze once and retain only the last immutable
  native processing description per provider; custom metadata and actual counters
  must remain live. No throughput cap or inventory cache. The short fixture
  median is 32.587 -> 31.353 ms, not a proven large speedup. Production still had
  dev.13 with its ordinary limiter enabled at inspection; it was not updated.
  Build/provider checks pass; industrial/runtime acceptance remains pending.

- [Issue #161](issues/ISSUE-161.md), 2026-09-28, local rc.7-dev.15 candidate:
  Successful pushes rescanned every advisory recipe hint for expiry. Use an
  earliest-expiry bound and remove expired tail records even after TTL changes.
  Actual registry fixture inspections: 2,097,152 -> zero. Real native provider
  pushes preserve delivered counts, blocking/full rejection and resumption;
  isolated repeated-round median 67.845 -> 36.936 ms. Replay original InsaneAE
  provider regressions and separate real native/VM quantity comparisons. Retain
  old planner-counter failures as diagnostics, not passes. Extended AE Plus
  blocking, external exact-job recovery and production acceptance remain pending.

- [Issue #161](issues/ISSUE-161.md), 2026-09-28, dev.14 acceptance correction:
  Git audit of 5b4e48d, d22cd3f and 7c3ceab identifies untested historical load
  and add-on scenarios. The 813 tests / 14 GameTests did not rerun the expensive
  Extended AE Plus blocking path or the four original InsaneAE regressions.
  Retain the test results, but downgrade acceptance to Implemented/PENDING and
  require runtime evidence in the manifest. No new runtime regression is
  claimed and no source, JAR, production file or throttle policy is changed.

- [Issue #161](issues/ISSUE-161.md), 2026-09-27, local rc.7-dev.14 candidate:
  Ordinary native dispatch was capped by coprocessor, adaptive/grid budgets and
  an independent executeCrafting wave redirect. Retire the ordinary throttle
  switch even for saved true values, remove that redirect, and pass legacy wave
  callers through. Neo ECO's original operation count is preserved. Keep the
  int overflow repair, exact-job ownership and separate transactional V2 budgets.
  Four before-failures are fixed; both AE2 variants pass 813 unit/contract tests
  and 14 Forge GameTests. Production throughput, installation and publication
  remain pending. This is separate from VM planning and world-load crashes.

- [Issue #218](issues/ISSUE-218.md), 2026-09-26, local rc.7-dev.13 candidate:
  Dev.12's live supreme circuit x1 used 129864 ms of access wait in 138525 ms
  elapsed. LiveInput declared exact/no-return semantics but still synchronized
  repeated predicate/remainder queries. Consume that existing declaration
  locally, retaining live callbacks for undeclared inputs and cancellation.
  No new cache, classification, scheduler or arithmetic. Repeated-query fixture:
  8192 live reads -> zero; real AE2 pool queries finish without more ticks.
  Each AE2 variant passes 807 unit/contract cases and 14 Forge GameTests.
  Production speed, deployment and full 2.0.0 acceptance remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-09-26, local rc.7-dev.12 candidate:
  Dev.11 applied AE2's tick budget to all VM arithmetic. Keep the AE2 pool and
  TickHandler but restrict exclusive intervals to live-access demand. At expiry,
  checkpoints release the server and continue computation without another tick.
  Preserve nested callback exclusion, interruption and one-stock-snapshot semantics;
  no ingredient RPCs, new executor, revalidation/recapture or arithmetic changes.
  Both variants pass 799 unit/contract cases and 13 real Forge tests. A real
  native-pool probe finishes 3402-bit arithmetic while the test server does not
  advance ticks. Live access still needs synchronization; opaque mod callbacks
  cannot be preempted. Production latency/load and complete acceptance remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-09-26, local rc.7-dev.11 candidate:
  Detached pattern/input capture introduced per-input server futures and final
  revalidation/recapture. Restore AE2's original calculation pool and time-slice
  monitor with the BigInteger VM as the only calculator. Capture stock once;
  run actual predicates within AE2's exclusive calculation interval. Remove the
  capture/catalog layer from runtime artifacts, retaining historical test fixtures.
  Both AE2 variants pass 788 unit tests and 12 Forge GameTests, including native
  worker/monitor ownership, cancellation/concurrent orders and 3402-bit quantities.
  AE2's own slice waits remain. Production speed and complete acceptance are unverified.

- [Issue #218](issues/ISSUE-218.md), 2026-09-25, local rc.7-dev.10 candidate:
  Unusable sub-unit alternatives were guarded by exact stock equality, so
  harmless byproduct growth prevented replay. Retain their integer eligibility
  interval; preserve exact guards for partial consumption and all thresholds.
  The reproduced case goes from over 8192 requests to 3-4 with exact quantities.
  Both AE2 variants pass 783 unit tests and 11 Forge GameTests. An independent
  alternate-only returned-container gap is recorded in the Issue and remains open.
  Live latency, deployment and full acceptance remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-09-25, local rc.7-dev.9 candidate:
  Dev.8's missing supreme-circuit plan still copied visited replay ledgers and
  sent native non-substituting crafting inputs through live fuzzy checks.
  Scoped change journals retain rollback and transient minima; fixed native
  crafting retains primary remainders and revalidation without those handoffs.
  Fixtures: 64 full state copies -> zero; 156 total handoffs -> five.
  Both AE2 variants pass 781 tests and 11 GameTests on the default test JVM.
  Production elapsed time, deployment and full acceptance remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-09-25, local rc.7-dev.8 candidate:
  The fork expanded fixed shared demands once per path instead of compiling
  them into a shared program. Combine already-demanded recipe coefficients
  with exact BigInteger arithmetic, including compact conceptual node bytes.
  The same 16-stage fixture drops from 4197 requests to 18 with equal results.
  Stock-only boundaries, intermediate stock and state-dependent ordered cases
  remain covered. Upstream passes 771 tests and 10 GameTests on the default JVM.
  UELM passes those tests only in the recorded C1-only JVM trial; default-JVM
  runs exposed an unreproduced NBT assertion and repeated native JVM crashes.
  Keep these failures unresolved, not hidden by a test/persistence change.
  No production timing claim, deployment, release or full acceptance.

- [Issue #218](issues/ISSUE-218.md), 2026-09-25, local rc.7-dev.7 candidate:
  Live dev.6 order 8 took 339097 ms; JFR exposed full replay-state copies and
  recursive parent-map materialization. Flatten trial layers once, retain only
  replay-relevant before-state and update independent after-deltas in place.
  Reproduced unused candidate lookahead adding guards that prevented replay;
  defer reads until the ordered cursor reaches them. Four failures pass after
  the fix: snapshot hash work 13816->656, fixture requests 2049->3, completed
  replay copies 4->2, no copied enclosing minima/indexes. Both AE2 variants
  pass 757 tests and 10 GameTests. Exact rollback/returns/bytes remain covered;
  production speed, deployment and full 2.0.0 acceptance are still pending.

- [Issue #218](issues/ISSUE-218.md), 2026-09-25, local rc.7-dev.6 candidate:
  Input-template boundaries caused a server round trip per stocked alternative.
  Preserve ordered per-template units but batch uncached candidates across
  templates; use a captured prefix immediately when it satisfies the order.
  Unused callback failures are deferred, consulted predicates/returns remain
  revalidated, and cancellation/budgets stay active. The 384-template fixture
  drops from 383 INPUTS handoffs to 3; a stocked primary keeps zero. Both AE2
  variants pass 752 tests and 10 GameTests. Correct an existing map-order-only
  NBT test assertion without changing persistence. Production timing, deployment
  and full 2.0.0 acceptance remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-09-25, local rc.7-dev.5 candidate:
  VM limit advertisement used only the configured binary ceiling and could
  exceed the public API's exact decimal limit. Delegate to the public maximum.
  AQE's default `10^1024 - 1 byte` capacity already fits; do not turn 3402 bits
  into a global quantity ceiling. Real async-VM/API fixtures cover stock and
  byproducts near capacity, byte-only overflow, full reservations, excess
  rejection, restore, release and status packets. Both AE2 variants pass 740
  unit tests and 9 GameTests. No production deployment or runtime acceptance.

- [Issue #218](issues/ISSUE-218.md), 2026-09-24, local rc.7-dev.4 candidate:
  Depleted/sub-unit fuzzy candidates still requested live validity/return checks;
  single-execution branches allocated unusable local replay snapshots. Skip
  those observations with quantity guards, and omit only the local snapshots.
  Native exact inputs inside revalidated wrappers avoid fuzzy-family scans.
  Fixture predicate calls fall from 2048 to 2, wrapper handoffs from 91 to 5,
  and missing single-execution replay copies from 4 to 0. Both AE2 variants
  pass 735 unit tests and 9 GameTests. Add bounded category/type diagnostics,
  not more log events. Production timings, deployment and release remain pending.

- [Issue #218](issues/ISSUE-218.md), 2026-09-24, local rc.7-dev.3 candidate:
  Repeated orders reread unchanged registered metadata and native chains still
  waited on server handoffs during VM arithmetic. Reuse a bounded immutable
  provider/recipe revision while keeping stock and dynamic observations per
  order. Native 96-stage in-VM handoffs fall from 5 to 0; cold capture still costs
  10 handoffs and final validation 1. Both AE2 variants pass 728 unit tests and
  9 real GameTests, including a warmed pattern replacement/removal/readdition.
  No production speed claim, deployment, physical-execution redesign or release.

- [Issue #218](issues/ISSUE-218.md), 2026-09-24, local rc.7-dev.2 candidate:
  A 96-stage native processing chain required 112 server handoffs including
  validation. Bounded metadata lookahead reduces the fixture to 19 without
  changing quantities. Failed lookahead could leave a half-captured multi-output
  pattern; publish metadata atomically and defer unused-branch failures.
  Small physical jobs bypassed the shared time gate, and refunded reservations
  renewed its first-operation exception. Gate every later admission and yield
  between physical steps, retaining unvisited cancellation receipts. Forced-yield
  completion/cancellation tests reload after every tick. This does not establish
  a production latency improvement or complete generic processing execution.

- [Issue #215](issues/ISSUE-215.md), 2026-09-24, rc.6-rc4-vm.5:
  Production supreme quantum circuit x1 (order 248) failed at 1,048,576 captured
  slot/key observations. The eager future-return closure is independent of
  requested quantity. Replace it with demanded, bounded server-owned input
  observations; do not simply raise the cap or ignore NBT/return rules.
  Keep final revalidation and latched cancellation. Add bounded recent-order
  diagnostics so aggregate timings cannot be confused with a failed manual order.
  Runtime acceptance of the replacement remains pending.

- [Issue #208](issues/ISSUE-208.md), 2026-09-24, rc.6-rc4-vm.5:
  The native bundle now uses the fork's BigInteger compiler/bytecode executor;
  the copied rc.4 ExactBranchVM is removed. AE2 oracle failures in the upstream
  one-craft aggregation required ordered slot instructions and guarded periodic
  replay. Preserve variant units, delayed returns, failed-branch rollback and
  peak reservations. Bounded capture/validation, sparse stock trials and logging
  are verified locally on upstream AE2 and UELM. Production latency, bundled
  loading and arbitrary external CPU execution/recovery remain unverified.

- [Issue #208](issues/ISSUE-208.md), 2026-09-24, upstream migration gates:
  The original VM reused stale missing quantities after partial leaf replenishment
  and stale craft counts after intermediate stock changed. Both are reproduced
  against its real compiler/core and covered by stock-churn tests. Use the
  supplied simulation snapshot instead of querying the live IGrid from the VM.
  At that foundation stage the original core was test-only and the rc.4-derived
  engine was still bundled. This staging limitation is superseded by the entry
  above; it is not a description of the rc.6-rc4-vm.5 artifact.

- [Issue #208](issues/ISSUE-208.md), 2026-09-24: native VM logged three INFO
  messages per routine order. Default to bounded activity summaries and slow
  samples; per-order DEBUG now requires explicit opt-in, because Forge writes
  debug.log too. Failure exceptions remain visible. Eight logging tests and
  the full upstream unit suite pass; production deployment remains pending.

- [Issue #190](issues/ISSUE-190.md), 2026-09-23: supreme circuit x1 aborts at
  1,048,576 branch checkpoints. Remove the cumulative cutoff, keep cancellation
  and resident-tree bounds. Nested-branch accounting and cancellation tests
  reproduce then pass. [Issue #208](issues/ISSUE-208.md): separate thin JARs
  from the installable VM bundle and record actual routes at INFO. Not deployed.

- [Issue #190](issues/ISSUE-190.md), 2026-09-22 / rc.4 VM rebuild:
  Detached immutable planning was rejected on every live storage mutation.
  Real AE2 stock-churn tests fail before and pass after separating planning
  consistency from live reservation. Keep strict deferred exact snapshot
  validation and cache revisions. The production incident's old log does not
  identify the changed revision; new failures include per-revision diagnostics.
  This condition also exists in rc.4, not only rc.5. Production acceptance pending.

- [Issue #190](issues/ISSUE-190.md), 2026-09-19 / AQE capacity alignment:
  Byte accounting bounded amount * 8 and unreduced rational numerators before
  unit conversion; tests reproduced rejection despite an in-range final cost.
  Split whole bytes and reduced fractional remainder, ceil once, and keep real
  count/byte limits. Tests cover AQE default and maximum capacities, existing
  reservations, exact fit, one-byte overflow, NBT and physical branch cancellation.
  No AQE capacity ownership change, deployment or live performance claim.

- [Issue #190](issues/ISSUE-190.md), 2026-09-19 / receipt validation:
  Worker snapshots were trusted without checking the returned transaction ID
  and digest. After output credit, cancellation could also release a changed
  receipt. Validate identity on every read and persisted outputs/state before
  acknowledgement retry or receipt removal. Keep waiting for a same-identity
  running worker after a lagging save, without crediting it twice or forgetting
  owned work. Nine contract integration tests
  cover exact completion, unload, cancellation, NBT and faulty receipts on both
  loaders. This is injected-fault evidence, not a reproduced live-server fault.
  Build passed; no deployment or runtime acceptance.

- [Issue #190](issues/ISSUE-190.md), 2026-09-18 / lightweight follow-up:
  fixed singleton inputs used redundant server observations; Forge rebuilt exact
  accounting and persistence on idle ticks. Reuse immutable input semantics with
  adoption revalidation and the existing active-step/revision implementation.
  Also retain polled cancellation steps when another provider is unloaded, and
  share receipt accounting across external CPU reads. Tests cover 10,000 repeated
  reads (one rebuild), bounded cancellation/reload of 1,024 wide steps, missing
  providers, and item/fluid input parity with actual AE2. Runtime acceptance pending.

- [Issue #190](issues/ISSUE-190.md), 2026-09-18 / BigInteger実装:
  複数の製造方法を選んだ巨大計画を、実行時に単一製造方法へ作り直していた。
  固定入力・非循環の作業台分岐について、選択済み回数・投入素材・全余剰を保存し、
  標準AE2と外部物理APIへ同じ計画を渡す。既存のReceipt、取消・復旧の所有者は変更しない。
  Forge通常AE2/UELMは各602件中601成功・任意JAR検査1件未実施、NeoForgeは610件成功。
  未配置。実設備での完了・途中取消・再起動復旧と、加工機械/循環分岐は未確認・未対応。

- [Issue #190](issues/ISSUE-190.md), 2026-09-18: 代替素材、返却容器、独自Pattern形式を
  一律に取得対象外としていた。公開APIの構造取得とサーバー側での入力判定を分離し、
  NBT・耐久値・液体の単位・返却順をAE2に合わせて計算する。
  両版のAE2比較試験とビルドは完了。未配置、実環境の採用率と巨大分岐の物理実行は未確認。

- [Issue #190](issues/ISSUE-190.md), 2026-09-17: 複数の製造候補を一律に拒否して標準計算へ戻る。
  AE2順の候補試行、失敗の巻戻し、読取区間の証明による反復短縮を追加。
  世代変更は新しい在庫とグラフで最大3回試行し、古い在庫の標準計算へ戻さない。
  未取得Patternの実体、数量がlongを超える分岐計画の物理実行、実環境の採用率は未確認。

- [Issue #190](issues/ISSUE-190.md): Emitterで切れる循環の誤拒否、および従来Map計算で
  生成済み副産物を元在庫として数える不具合。局所DAG検証と元在庫の最大不足量へ修正。
  共有素材を持つ単一出力DAGにも順序付き会計を使い、Snapshotの再検証でEmitterを誤拒否しない。
  実レシピの全経路高速化と10秒目標は別の未達成条件として残す。

- [Issue #125](issues/ISSUE-125.md): ACO所有Jobの外側で通常BatchがTaskを削除して隔離される。
  通常実行予算の確定位置で排他化する。Taskの再生成・不一致の握り潰しは禁止。

- [Issue #218](issues/ISSUE-218.md), 2026-09-24: 必要のない製造方法の先行取得と、
  入力判定ごとのサーバー往復を削減。Requesterの接続先・在庫参照権限を保持し、
  標準加工入力のNBT完全一致を効率化。BigInteger公開APIのバイナリ契約を固定。
  通常AE2/UELMで各697件と8件のGameTest成功。巨大な加工・循環工程の実行範囲と、
  本番の完了・取消・復旧・速度確認が残るため、2.0.0の正式公開は未達成。

## 運用

- 修正前に`docs/ISSUE_WORKFLOW.md`を実行します。
- 再発しやすい局所条件だけをJavaコメントへ残します。
- 詳細な履歴はIssue仕様書へ集約し、同じ説明を複数ファイルへ複製しません。
- 新しい回帰を修正したら、この表へIssueと仕様書を追加します。
