# Issue #161: クラフト開始後の実行負荷

## 2026-10-01: remove orphaned Neo ECO version selector

- State: Implemented; started from Ready. Related cleanup: #218, local dev.19.
- The two Neo ECO budget Mixins and their plugin selection were removed in
  dev.17. NeoEcoVersionCompatibility now has no production caller, resource
  registration, API export or reference in the installed audited AE2 add-ons.
- Delete that internal selector and its obsolete version-parser-only test.
  Retain NeoEcoPackagingContractTest, AcoMixinPluginTest and the real optional
  add-on contract tests. Add a failing runtime-class absence check first.
- Keep both supported dependency ranges, ordinary native dispatch, public
  pass-through compatibility methods, exact ownership, V2 receipts and recovery.
  Do not remove checks merely because they are uncommon or disabled by default.
- No production config/JAR changes, server restart or release is authorized.
- Both variant builds, ABI checks and retired-class checks pass. Evidence and
  the first-run historical cancellation test failure are recorded in
  [dev.19 cleanup](evidence/ISSUE-161-218-DEAD-CODE-2026-10-01.md).

## 2026-09-28: remove retired ordinary execution machinery

- State: Implemented; started from Ready. Scope: Forge 1.20.1, upstream/UELM,
  local dev.17 candidate. Full industrial/runtime acceptance remains PENDING.
- Request: ordinary insertion must be native AE2 behavior, not throughput
  throttling presented as optimization. Reviewed charter, class ownership,
  regression history, #102/#125 and the testing contract before implementation.
- Delete the retired adaptive/shared/probe/wave algorithms and their mutable
  timing state. Keep legacy public dispatch methods as stateless pass-throughs
  for binary compatibility; do not create a replacement execution loop.
- Remove Neo ECO budget Mixins from source, registration and plugin selection,
  so optional add-ons are no longer instrumented for the retired limiter.
- Keep native provider ordering, blocking, energy and successful input/output
  accounting, the coprocessor int overflow repair and #125 exact ownership.
  VM planning, BigInteger APIs and explicitly owned V2 receipt execution stay
  unchanged. Removing throughput limits does not mean bypassing a full target.
- Preserve historical evidence; replace tests of the now-deleted private wave
  formula with tests of the retained public compatibility entry points. Require
  untouched external int counts, single native calls, propagated failures,
  no timer/state/config access and no optional limiter Mixin registration.
- Build both AE2 variants and rerun the pinned real-provider regression suite.
  Production installation/restart and full industrial acceptance remain pending;
  no deployment, release or server restart is authorized by this change.
- Both builds, public API/contract and bundle checks pass. Actual provider tests
  preserve 1024 deliveries per round, native face rotation, full/blocked rejection
  and resumption; processing completion, cancellation and reservation tests pass.
  Public descriptors of both retained compatibility classes match dev.16.
- Source/JAR audit confirms the ordinary timing states/algorithms and Neo ECO
  limiter classes/registrations are absent. Old true settings cannot restore them.
- Evidence: [dev.17 native execution](evidence/ISSUE-161-NATIVE-EXECUTION-2026-09-28.md).
  Production server/client remain on dev.13; this work does not demonstrate a
  production throughput gain or authorize deployment/full 2.0.0 acceptance.

## 2026-09-28: production ordinary-limit configuration

- State: Implemented (configuration); live application is not yet confirmed.
  Started from Ready after the user's request for unlimited ordinary insertion,
  server-root/dev.13 bytecode checks and issue/charter/history/ownership review.
- Apply only the five ordinary-execution switches in the server common config:
  throttleCraftingExecution, adaptiveBudget, sharedGridBudget,
  throttleNeoEcoAeExecution and enableInstantDispatch become false.
- The installed dev.13 dispatcher has a separate Instant wave gate, so disabling
  only throttleCraftingExecution is insufficient. With both paths disabled the
  existing methods pass the native operation count without ACO time/wave caps.
- Keep a timestamped backup, validate the parsed TOML and confirm Forge reload
  in the running server log. No JAR replacement, public release, restart, client
  config change or live order/inventory mutation is required for this step.
- Native blocking, energy, representable API boundaries, exact ownership and
  separate transactional receipt/recovery settings remain unchanged. This does
  not claim zero server cost or a measured production throughput improvement.
- Changed only those five switches in
  `C:\example\server\config\ae2_crafting_optimizer-common.toml`.
  Backup: the same path plus `.before-unthrottle-20260928-105626.bak`.
  TOML parsing and a complete parsed before/after comparison confirm that every
  other value is unchanged. The installed JAR was not replaced.
- JVM system properties confirm this is PID 24244's working directory; the PID
  remains running with its original start time. No ACO config-reload notification
  appears after the edit in debug.log, so automatic application is unverified.
  Asked for save/restart approval; no shutdown or restart has been performed.

## 2026-09-28: remaining provider cost follow-up

- State: Implemented; local dev.16 checks pass, full acceptance remains PENDING.
  Implementation started from Ready after reviewing issue #161, charter,
  regression history, ownership and test contract. This extends the dev.15 probe.
- Request: reduce the remaining insertion work, without reducing native dispatch
  counts. The earlier 36.936 ms measures 1024 complete provider calls in a local
  fixture, not purely ACO work, production latency or a reason to restore caps.
- First profile the actual provider fixture with opt-in, test-only JFR sampling
  and longer repetition. Keep unprofiled runs separate from diagnostic timing.
- Optimize only reproduced redundant work at its existing owner; retain native
  blocking/backpressure, round-robin, energy and exact input/output accounting.
  No inventory-result cache across mutations and no replacement executor.
- Before/after evidence must include work attribution, identical unprofiled
  pushes, exact deliveries, full/blocked rejection and resume, and the pinned
  add-on regression cases. Add focused failing cases for the identified change.
- VM/planning and physical restart/receipt acceptance stay separate and pending.
  No production deployment, restart, commit or public release in this change.
- Profile finding: 38/100 server-thread execution samples include the ACO hint
  capture; 18 include its registry and 11 its repeated list equality checks.
  The native adapter and actual insertion are separate costs. Sample shares
  are attribution, not a precise wall-clock speedup or production measurement.
- Selected change: freeze advisory input/output lists once per push, and reuse
  only the last exact native AEProcessingPattern's immutable description per
  provider. No global cache/generation system. Native pattern instances are
  immutable by API contract; subclasses/custom patterns are read on every push.
  Concrete input counters, directions, time/TTL and execution count stay live.
  Existing static capture entry points remain available without reuse.
- Tests: unchanged native descriptions reuse the same immutable value; different
  instances/content replace it; custom/subclass descriptions refresh; concrete
  items/fluids change immediately. Retain real-hook and registry acceptance.
- Both AE2 variants built and passed the pinned provider runtime suite, API and
  bundle checks. The immutable/reuse assertions first failed, then passed. The
  short unprofiled median changes only from 32.587 to 31.353 ms per 1024 pushes;
  this is not evidence for a large or statistically significant speedup.
- Production still has the dev.13 JAR and enabled ordinary 4/8 ms budget config;
  bytecode confirms that version still reads the limiter. No deployment was
  performed, and its actual contribution to a specific order remains unmeasured.
- Evidence: [provider capture follow-up](evidence/ISSUE-161-PROVIDER-CAPTURE-2026-09-28.md).

## 2026-09-28: runtime regression follow-up (dev.15)

- State: Implemented; local provider acceptance recorded below, full historical
  acceptance remains PENDING. The implementation started from Ready.
- Existing GitHub issue #161 is open; charter, history, responsibilities,
  #102/#125 specifications and testing contract have been reviewed.
- First reproduce #102's four original tests with the actual InsaneAE
  diagnostic JAR, not copied provider mocks. Record its source revision,
  existing local patch and artifact SHA-256. Runtime dependencies are opt-in
  test dependencies only and must never enter the distribution bundle.
- Keep a no-ACO reference run and a current-VM run separate. The two historical
  planning tests explicitly require InsaneAE's old planner counter to increase;
  do not claim that counter is exercised by the new VM. Retain the original
  results and add quantity-based VM acceptance where the planner differs.
- Add-on fixtures must fail if any required named test or runtime mod is absent.
  Record exact inputs/outputs and timings, rather than promoting a test total.
- Inspect actual provider blocking and intent capture costs before changing
  production algorithms. Extended AE Plus 1.5.5 repeats target inventory scans
  per candidate; that observation alone is not an ACO performance improvement.
- No throughput limits, replacement execution owners, live-stock cache across
  mutations, production deployment, restart or public release are authorized.
- Planning/execution/accounting gates beyond the reproduced scope remain open.

### Narrow production change: recipe-intent expiration

- State: Implemented. RecipeIntentRegistry owns advisory machine hints, not inventory,
  planner quantities, dispatch decisions or transaction receipts.
- Each successful push records one hint per target; record/find currently walk
  every registered location for expiry, even when no hint can have expired.
- Maintain a conservative earliest-expiry bound, updated on insertion and reset
  on clear. Before that deadline cleanup is O(1); at the deadline scan retained
  records and rebuild the bound. Eviction/coalescing may leave an earlier bound,
  causing only an extra scan, never delayed expiry. No per-tick work limit.
- Scan all records when expiry is due: reducing configured TTL can put a shorter
  expiry behind a longer-lived head. The former head-only removal leaks expired
  hints into queries. Preserve surviving order, spatial index and entry count.
- Before-failure tests: instrument actual registry deques to count inspections
  for 1024 locations and 1024 same-tick pushes/queries; assert expired tail hints
  disappear. Check deadline equality, coalescing, eviction, clear and backwards
  clock changes. Add an in-game successful provider-push load probe with exact
  delivered quantities, full destination/backpressure and no throughput cap.

### Verified scope and open work

- The real registry reproduces two before-failures: redundant expiry walks and
  an expired tail behind a longer-lived head. Both are fixed. Same-tick fixture
  inspections fall from 2,097,152 to zero, without dropping records or pushes.
- With the real InsaneAE diagnostic JAR loaded, actual provider calls deliver
  exactly 1024 items in each of six rounds. Full/blocked targets reject without
  consumption and resume when cleared. Median repeated-round elapsed time in
  the initial local pair falls from 67.845 to 36.936 ms. Not production MSPT.
- The original four #102 cases pass without ACO/VM. On the current VM, the two
  provider cases pass; two obsolete planner-counter assertions fail as expected.
  Their failed results are preserved, NOT counted as passes. Separate actual
  native/VM result comparisons pass for returns and alternate producers.
- A pinned-JAR replay runner and required test-name gates prevent accidental
  validation without the add-on or the relevant cases.
- Evidence and exact fixture versions:
  [ISSUE-161-FORGE-1.20.1-2026-09-28.md](evidence/ISSUE-161-FORGE-1.20.1-2026-09-28.md).
- Still open: Extended AE Plus's own repeated blocking scans; huge external CPU
  runtime and exact-job receipt isolation/restart; production planning latency
  and generic wide machine/cycle execution. Neither a source patch nor this
  provider fixture claims those tasks are complete. No deployment or release.

## 2026-09-27: ordinary dispatch must not be throttled

- State: Implemented (historical regression acceptance PENDING; not release-approved)
- Scope: Forge 1.20.1, upstream AE2 and UELM, rc.7-dev.14 candidate.
- Request: remove ACO-imposed processing-pattern insertion limits. The supplied
  CurseForge comment does not establish the commenter's precise setup or crash
  cause; world-load crashes and VM planning latency are outside this change.
- Reproduction: a legacy config with `throttleCraftingExecution=true` caps native
  coprocessors, while the independently enabled Sequential Instant redirect
  further shrinks executeCrafting calls to measured CPU/grid time windows.
- Expected: ordinary AE2 keeps its own dispatch count, provider order,
  backpressure, energy use, used-operations history and input/output accounting.
  No ACO wave, adaptive or shared budget may lower that native dispatch count.
- Implementation: retire the ordinary-throttle switch (keep the old key readable
  for config compatibility, but ignore even existing true values). Remove the
  executeCrafting wave redirect. Existing Neo ECO budget hooks become no-ops
  through the same retired switch; do not replace an add-on's executor.
- Preserve the coprocessor overflow repair at the int API boundary, including
  AE2's +1 headroom, and Issue #125's exclusion of already-owned exact jobs.
  Public BigInteger APIs, quantities, receipts, recovery and VM are unchanged.
- ACO-owned transactional V2/exact physical work still has its separate admission
  rules. Those must not be confused with ordinary processing dispatch or removed
  in order to permit duplicate work. No unbounded replacement loop is added.
- Test first: legacy-enabled limits must not cap 65,537/1,048,576 coprocessors;
  spent CPU/grid budgets must not affect ordinary or Neo ECO dispatch; maximum
  external int operation counts must pass unchanged. A source contract must
  reject reintroduction of the executeCrafting wave redirect, including when
  Instant V2 is enabled. Keep overflow, ownership and provider GameTests.
- Required: before/after JUnit evidence, both AE2 variant builds and existing
  Forge GameTests, regression manifests, bundle/API checks and JAR hashes.
  Historical Extended AE Plus load and InsaneAE regressions below must also be
  retested. Production throughput/MSPT and the commenter's reproduction remain unverified.
- No production deployment, restart, public release or NeoForge change in this
  slice. NeoForge synchronization requires its separate reviewed change.
- Pre-implementation: charter, regression history, class ownership, related
  #102/#125 specs, tests and workflow read; no source edits before Ready.

This request supersedes the earlier ordinary-wave budget policy below; those
sections describe the historical implementation, not the new native behavior.

### Local verification

- Before: all four original OrdinaryPatternDispatchTest cases failed against
  dev.13. Preserved XML/log: `build/issue161-unthrottled/issue161-unthrottled-before.*`.
- After: six focused regression cases pass, including the legacy wave entry,
  unchanged zero/success results and propagation of native failures without retry.
- Both upstream AE2 and UELM: 162 suites / 813 unit and contract tests; zero
  failures, errors or skips. Each also passes 14 real Forge GameTests.
- `build`, `verifyIssueRegressionManifest`, `verifyIssueRegressionReleaseReadiness`,
  `verifyExactVmBundle` and `runGameTestServer` pass. Bundled VM remains dev.13.
- Artifact/test evidence: `build/issue161-unthrottled/README.md` and variant folders.
- No production installation, server restart, push or prerelease publication.
  No production throughput/MSPT claim, world-crash fix, or full 2.0.0 acceptance.

### 2026-09-28 Git history audit: acceptance correction

The original local test results remain valid, but they do not prove the removal
preserves the conditions that motivated the old fixes. The earlier Verified
label and UNIT/NOT_REQUIRED manifest entry were too strong. Do not publish or
deploy dev.14 on that evidence. No newly reproduced runtime failure is claimed.

- `5b4e48d82c655fb43bee0c79993e231a78c89f89`: restored overflowed coprocessor
  totals and reduced cold waves after profiling costly Extended AE Plus
  `eap$redirectBlockingContains`/`containsPatternInput` work. The count repair
  remains, but dev.14 bypasses the pacing. The expensive provider path was not
  optimized or remeasured by this change. Test the actual legacy blocking case
  with cold/steady dispatch, full and blocked targets, and concurrent CPUs;
  record completed pushes, exact accounting and maximum server tick/MSPT cost.
- `d22cd3f8205d016a2fc76a993147a312fa7c0307`: fixes InsaneAE ownership and
  provider refresh regressions. Priority 900 is retained and the outer wave
  hook is removed, but the 14 current GameTests do NOT include the original
  `ae2.pattern_provider_faces_round_robin`, `ae2.insaneae_pattern_provider_patterns`,
  `ae2.insaneae_crafting_batch` or `ae2.insaneae_crafting_batch_chain` cases.
  Rerun those with pinned add-on revisions; the old #102 evidence is historical,
  not proof about dev.14.
- `7c3ceab41d74f075e431eb1236b1885364c6a8cc`: #125 isolates receipt-owned exact
  jobs before outer native batches can modify tasks. That guard is unchanged;
  its source/unit tests are not an external-batch runtime reproduction. Keep
  exact/native ownership, cancellation and recovery checks in the impact set.

Do not make this gate green by deleting the old cases, counting unreachable
wave-helper tests as current runtime coverage, or silently restoring throughput
caps. Retain the no-throttle request and address reproduced redundant work at
its owner boundary. The manifest now requires runtime evidence; use
`verifyIssueRegressionReleaseReadiness -PrequireRuntimeAcceptance=true` for
acceptance, not only the prerelease task's warning-only mode.

## 目的

クラフト計算完了後の実行時間とサーバー負荷を分離して計測し、AE2の結果と会計を変えずにACO固有の不要処理を除去する。

## 確定した第一の無駄

`enableTransactionalBatchingV2=true`でも、V2 Adapterが一件も登録されていない環境では、V2が成功する可能性はない。従来はその状態でも`CraftingCpuLogic.executeCrafting`の各呼出からJob、Task、Inventory、Provider候補の探索へ進み得た。

## 修正

- Adapter登録が0件なら、Job状態へ触れる前に`NOT_HANDLED`を返す。
- `/aco stats`へSequential Instantの平均実時間を追加する。
- V2 probe、Adapter 0件bypass、Task走査、route成立、標準fallbackを個別に数える。

## 第二段: Adapter登録環境の静的metadata再利用

`PatternProviderRoutingCache`は既にProvider候補一覧をPattern世代単位で再利用していた。一方、`TransactionalCraftingExecutorV2`は各waveで全Taskを先頭から調べ、作業台Patternの入力候補配列、係数乗算、出力集約を毎回作り直していた。加工Patternも、V2対象外と分かる前にtask fingerprintへ到達していた。

次の範囲だけを`TransactionalExactPatternCache`へ分離した。

- 作業台Patternであること
- 入力slot順、候補順、入力係数と候補一個あたりのchecked long量
- 出力の一実行あたり集約
- 静的に対象外であるPattern

Cacheは`ProviderPatternGenerationTracker`の世代に結び付ける。compile中または取得直後に世代が変化した結果は使わず、そのwaveだけ現在のPatternを直接読む。直接読取中にも世代が進んだ場合はV2が所有権を取らず、AE2標準実行へ残す。

次はキャッシュしない。

- Task進捗と`waitingFor`
- 在庫量と代替入力の選択
- `IInput.isValid`と返却物
- Providerの順序、busy、target、chunk load
- Adapterの`supports`、Receipt健全性、backpressure
- 電力、抽出、成果物、取引状態

これにより実行結果と選択順を維持したまま、同じPatternをwaveごとに再解析するallocationと、非作業台Taskの不要なfingerprint生成を除去する。

## 第三段: int並列数の桁あふれと未計測wave

最新のprofilerでは、ACOのAE2実行境界の内側でExtended AE Plus
`PatternProviderLogicAdvancedMixin#eap$redirectBlockingContains`が支配的だった。
同MixinはAdvanced Blocking時にPattern入力slotと候補を走査し、候補ごとに
`PatternProviderTarget.containsPatternInput`を呼ぶ。ACOがこの判定結果を置き換えることはできないため、
一回の未計測waveへ65,536操作を渡す従来値では最初のtickだけで時間予算を大きく超え得た。

同時に、AE2の`CraftingCPUCluster.accelerator`と
`CraftingCpuLogic#tickCraftingLogic`は次の計算をすべて`int`で行う。

```text
accelerator += unitThreads
remaining = getCoProcessors() + 1 - (usedOps[0] + usedOps[1] + usedOps[2])
```

Extended AE Plusを含む16スレッド上限拡張Mixinは、巨大ユニットをintフィールドへ直接加算する。
合計が`Integer.MAX_VALUE`を越えると、ACO従来実装も負数を
`Math.min(negative, configuredCap)`のまま返していたため、AE2の`remaining > 0`へ入れず
例外なしでクラフトが停止した。合計が2^32を越えて正値へ再ラップする場合は、負数判定だけでも検出できない。

修正は次のとおり。

- 標準AE2クラスタの構成ユニットを形成後の最初の実行時に一度だけ走査し、各
  `getAcceleratorThreads()`をlongで合計する。
- クラスタ変更時はAE2が新しいクラスタインスタンスを作るため、Weak cacheは旧クラスタと共に破棄する。
- ACOの1 tick実行窓へ渡す時だけ`Integer.MAX_VALUE - 1`以下へ投影する。
  正確な合計や表示をクランプする処理ではなく、直後のAE2 `+ 1`を安全にするint API境界である。
- 説明できない負数は0操作へ潰さず、明示的な例外としてfail closedする。
- 既存Configに65,536 probeが残っていても、新しい未計測wave上限の初期値1,024を先に適用する。
- 64操作以下のcold jobは分割せず、Issue #74/#102のProvider面ラウンドロビンを維持する。
- 一度計測した後は、要求が最大wave以下でも実測時間から次waveを計算する。
- 完了0件でも高価だったProvider探索時間を記録し、次tickで同じcold-start大波を繰り返さない。
- 専門アドオンが高優先度Redirectを所有する場合にACOが二重予算を重ねない規則は変更しない。

## 不変条件

- `NOT_HANDLED`後は同じ呼出内でAE2標準`executeCrafting`が実行される。
- Task、waitingFor、電力、入力抽出、Provider順序、成果物を変更しない。
- Adapterが一件以上ある場合のV2選択と取引処理は変更しない。
- Cache hit後も在庫、候補有効性、返却物、Provider、Adapterを毎回再検証する。
- 世代変更中のmetadataをCacheへ公開しない。
- 専門アドオンが実行Redirectを所有する場合は、従来どおりACOの予算処理を重ねない。

## 残る実環境確認

JUnitは桁あふれ前のlong復元、正値への再ラップ、AE2 `+ 1`境界、cold probe、
計測済み小wave、完了0件の費用記録を固定する。実環境では同じ注文で
`/aco stats`の`Wide co-processor execution count`とSequential Instant最大wave時間を確認し、
Extended AE Plus側の一操作自体が時間予算を越える場合だけ、同MOD側のアルゴリズム改善を別件として扱う。
