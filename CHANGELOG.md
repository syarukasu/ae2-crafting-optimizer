# Changelog / 変更履歴

## [2.0.4] - 2026-11-07

### English

#### Bug Fixes
- **Crafting Execution Remainders (`CraftingExecutionRemainders`)**: Checked for overrides of the no-arg `Item#getCraftingRemainingItem()` (`m_41469_`) in `ClassValue` cache and added a fast `!item.hasCraftingRemainingItem()` guard so items overriding only the no-arg remainder method are not misclassified as vanilla non-remainder items.
- **Counted Processing Transfers (`CountedProcessingTransfers`)**: Guarded against non-positive or non-finite `perExecution` power values, and added a 1-step decrement retry when `availablePower / perExecution` hits an IEEE-754 floating-point boundary where `calculatePatternPower(payload) > availablePower + 0.01`.
- **Compact Amount Formatting (`BigAmountFormatter`)**: Fixed `formatCompact(BigDecimal)` so fractional unit amounts `< 1000` (such as `0.5` or `1.5` buckets for fluids/chemicals) format as plain decimal strings instead of falling through to scientific notation (`5e-1`, `1.5e0`).
- **Native Pattern Batch Output Scaling (`NativePatternBatchSupport`)**: Included `remainingOutputsPerExecution` in `safeExecutionLimit` to prevent `Math.multiplyExact` overflow when scaling remaining outputs.
- **Monotonic Deadline Comparisons (`PatternBatchBudget`, `Ae2AuthoritativeCraftingPlanner`)**: Made `System.nanoTime()` deadline and interval checks wrap-around safe using subtraction (`deadlineNanos - System.nanoTime() > 0L`).
- **NBT & Concurrency Safety (`BigIntegerAmountLedger`, `VmCalculatedCraftingPlan`, `MekanismItemRecipeIntent`)**: Validated empty and oversized byte arrays before `new BigInteger(encoded)` when loading NBT ledgers; made `VmCalculatedCraftingPlan.prepared` `volatile` with synchronized update; synchronized static `IdentityHashMap` access in `MekanismItemRecipeIntent`.
- **Server Lifecycle State Cleanup (`ACOServerLifecycle`, `ExactVectorGridTickBudget`, `ProfilerBenchmarkCommands`)**: Cleared `ExactVectorGridTickBudget`, `ExactBigIntegerCellConsistency`, and `ProfilerBenchmarkCommands` on server start/stop to prevent cross-world state leaks in singleplayer sessions.
- **GTCEu Buffer Cache Invalidation (`GTBufferCountedSlotMixin`)**: Invalidated cached item/fluid totals at both `HEAD` and `RETURN` of `onContentsChanged`.

#### Optimizations (Behavior-Preserving)
- **Counted Item Capacity (`CountedItemCapacity`)**: Cached per-slot counts and limits once per call, bounded binary search upper limit by `keyTotalSpace / perExecution`, and reused primitive `long` scratch arrays during binary search to eliminate per-step `BigInteger` and array allocations.
- **MethodHandle Invocation Cache (`MethodHandleInvocationCache`)**: Pre-adapted `MethodHandle`s via `asSpreader` / `asType` at lookup time and invoked via `invokeExact` instead of `invokeWithArguments`.
- **GTCEu Recipe Intent Fast Path (`GTCEuRecipeIntentFastPath`)**: Cached reflective `Method` and `Field` lookups in `ConcurrentHashMap`s and replaced hot-path stream pipelines with direct loops.
- **Pattern Target Query Scope (`PatternTargetQueryScope`)**: Stored up to 2 `AEKeyType` references inline before allocating an overflow `IdentityHashMap`.
- **Exact Pattern Formula & Batch Fingerprinting (`ExactPatternFormula`, `BatchPayloadFingerprint`, `BatchTransactionRecord`)**: Pre-aggregated per-execution input/output key totals in `ExactPatternFormula`; computed batch payload digests directly without allocating defensive `toPublicView()` copies and deep-cloned `CompoundTag`s.
- **Planner & Routing Cache Allocations (`ExactPlanPatternRevalidator`, `ExactCraftingJobLedger`, `BigCraftingInventory`, `Ae2ImmutablePlanningGraphCache`, `Ae2BranchingInputRules`, `PatternProviderRoutingCache`, `PhysicalCraftingTreeTransaction`, `RevisionWakeupApi`)**: Eliminated redundant intermediate collection copies on hot paths and optimized `BigAmountFormatter.abbreviatesTooltip(BigInteger)` using `TEN_POW_24` comparison.

---

### 日本語 (Japanese)

#### バグ修正
- **クラフト実行時の残留アイテム判定 (`CraftingExecutionRemainders`)**: 引数なしの `Item#getCraftingRemainingItem()` (`m_41469_`) のオーバーライド判定を `ClassValue` キャッシュに追加し、`!item.hasCraftingRemainingItem()` による早期ガードを導入することで、引数なしメソッドのみをオーバーライドする MOD アイテムの残留アイテムが消失する不具合を修正しました。
- **加工パターン転送時の電力計算 (`CountedProcessingTransfers`)**: `perExecution` が `0` 以下または非有限値の場合のガードを追加し、`availablePower / perExecution` の IEEE-754 浮動小数点丸め境界によって `calculatePatternPower(payload) > availablePower + 0.01` となりバッチ全体が拒否されるケースで `offered - 1` にフォールバックする処理を追加しました。
- **数量フォーマット表示 (`BigAmountFormatter`)**: `formatCompact(BigDecimal)` において `1000` 未満の小数単位量（液体やケミカルの `0.5` B や `1.5` B など）が指数表記（`5e-1`、`1.5e0`）になる問題を修正し、通常の小数表記で表示されるようにしました。
- **ネイティブパターンバッチの残り出力スケーリング (`NativePatternBatchSupport`)**: `safeExecutionLimit` の上限計算に `remainingOutputsPerExecution` を含め、残り出力のスケーリング時に `Math.multiplyExact` がオーバーフローする不具合を修正しました。
- **ナノ秒タイマーのラップアラウンド対策 (`PatternBatchBudget`, `Ae2AuthoritativeCraftingPlanner`)**: `System.nanoTime()` の期限・間隔比較を減算ベース（`deadlineNanos - System.nanoTime() > 0L`）に統一し、`long` オーバーフローに対して安全にしました。
- **NBT 読み込み・スレッド安全性の強化 (`BigIntegerAmountLedger`, `VmCalculatedCraftingPlan`, `MekanismItemRecipeIntent`)**: `BigIntegerAmountLedger.load` での空配列および最大バイト数超過の事前検証、`VmCalculatedCraftingPlan.prepared` の `volatile` 化と同期化、`MekanismItemRecipeIntent` の静的 `IdentityHashMap` アクセスの同期化を行いました。
- **サーバーライフサイクル時の状態クリア (`ACOServerLifecycle`, `ExactVectorGridTickBudget`, `ProfilerBenchmarkCommands`)**: サーバー開始・停止時に `ExactVectorGridTickBudget`、`ExactBigIntegerCellConsistency`、`ProfilerBenchmarkCommands` のキャッシュと状態をクリアし、シングルプレイでのワールド切り替え時の状態リークを防止しました。
- **GTCEu バッファキャッシュの無効化 (`GTBufferCountedSlotMixin`)**: `onContentsChanged` の `HEAD` と `RETURN` の両方でキャッシュ無効化を行うように修正しました。

#### コード最適化（挙動維持）
- **アイテム容量計算の高速化 (`CountedItemCapacity`)**: スロットごとの個数・上限を最初に1回だけ取得してキャッシュし、二分探索の上限を `keyTotalSpace / perExecution` で絞り込み、探索ループ内での `BigInteger` および配列のアロケーションを排除してプリミティブ `long` 演算に最適化しました。
- **MethodHandle 呼び出しの最適化 (`MethodHandleInvocationCache`)**: `unreflect` 時に `asSpreader` / `asType` で事前変換し、呼び出し時の `ArrayList` 生成と `invokeWithArguments` を排除して `invokeExact` で実行するように最適化しました。
- **GTCEu レシピインテントのリフレクションキャッシュ (`GTCEuRecipeIntentFastPath`)**: `Method` および `Field` のリフレクション検索結果を `ConcurrentHashMap` にキャッシュし、ホットパスの Stream パイプラインをループ処理に置き換えました。
- **ターゲットスコープの軽量化 (`PatternTargetQueryScope`)**: 2種類までの `AEKeyType` をフィールドでインライン保持し、3種類以上になった場合のみ `IdentityHashMap` を確保するように最適化しました。
- **パターン計算・バッチダイジェスト計算の軽量化 (`ExactPatternFormula`, `BatchPayloadFingerprint`, `BatchTransactionRecord`)**: `ExactPatternFormula` で入出力のキー別合計を事前集計し、バッチペイロードの SHA-256 ダイジェスト計算時に `toPublicView()` や `CompoundTag` のディープコピーを生成しないように最適化しました。
- **プランナー・ルーティングキャッシュ等のコレクションコピー削減 (`ExactPlanPatternRevalidator`, `ExactCraftingJobLedger`, `BigCraftingInventory`, `Ae2ImmutablePlanningGraphCache`, `Ae2BranchingInputRules`, `PatternProviderRoutingCache`, `PhysicalCraftingTreeTransaction`, `RevisionWakeupApi`)**: ホットパス上の不要な中間コレクションコピーを削減し、`BigAmountFormatter.abbreviatesTooltip(BigInteger)` を `TEN_POW_24` との定数時間比較に最適化しました。
