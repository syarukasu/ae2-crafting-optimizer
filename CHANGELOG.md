# Changelog / 変更履歴

## 2.0.6

### Fixes

- Guard unconditional diagnostic INFO logs on planning hot paths with `ACOConfig.logCraftingDecisionFlow()` and synchronize snapshot fallback log deduplication.
- Retain completed crafting plans in the deduplicator cache even when fast calculations finish before `remember()` returns, and cap completed-plan TTL to the original start time.
- Roll back prepared V2 pattern batches and release target guards on all early-exit paths before transaction commit.
- Prevent unloaded-chunk synchronous loading during batch source reconciliation and rotate non-terminal journal entries during recovery to avoid head-of-line starvation.
- Remove unverified keys from `BigKeyCounterSidecars.Accumulator` when merging an incomplete storage mount snapshot.
- Guard `BigDecimal.divide` in `NumberEntryWidgetExactCraftAmountMixin` against non-terminating decimal expansions and use hard protocol bounds when decoding S2C status/summary packets.

### Reduced work

- Avoid unnecessary `BigCraftingPlan` map widening and `BigExactCraftingByteCounter` traversal on normal `LongResult` plans.
- Eliminate repeated `KeyCounter.findFuzzy` stream allocations in `OrderedBranchingPlanner`, deduplicate compiled patterns in `Ae2ImmutablePlanningGraphCache`, and index patterns by ID in `CompiledCraftingGraph`.
- Add a full-offer fast path to `CountedItemCapacity`, cache simulated stock per key in `CountedInputPreview`, and reuse `PatternProviderRoutingCache` in `CountedCraftingDispatch`.
- Avoid NBT tag serialization in V2 batch verification, add zero-arg method fast paths to `ReflectionLookupCache`, and pre-parse GTCEu recipe intent hints once per candidate search.

### 日本語

- クラフト計画ホットパス上の無条件INFOログ出力を設定ガード配下へ修正し、高速完了した計算結果のキャッシュ漏れを修正しました。
- V2バッチ準備後の早期中断パスにおけるロールバック・ターゲット解放漏れ、未ロードチャンクの同期読込、および復旧キューのHead-of-Lineブロッキングを修正しました。
- 不完全なストレージマウント統合時の正確性フラグ管理、数量入力ウィジェットの非終端小数除算例外、およびS2Cパケットデコード時の上限設定参照バグを修正しました。
- 通常long計画での不要な`BigInteger`昇格・バイト数再計算、分岐プランナーのFuzzy検索アロケーション、容量二分探索、リフレクション検索、GTCEuレシピヒント照合などのCPU負荷とメモリアロケーションを大幅に削減しました。

## 2.0.5

### Fixes

- Guard native crafting remainder queries and keep custom item callbacks on the native path.
- Recheck counted delivery at the energy rounding boundary. Recheck stock and receiver admission before committing, and charge only accepted work.
- Preserve fractional fluid and chemical amounts, and include returned stacks in the zero-safe native batch limit.
- Validate saved BigInteger amount encodings before decoding and keep cell totals available until world saving finishes.
- Handle signed monotonic deadlines and refresh shared GTCEu buffer totals across inventory changes.

### Reduced work

- Reuse receiver slot bounds, precomputed exact input/output totals, and Pattern Provider recipe hints.
- Reduce allocations in planning, routing, wait accounting, listener cleanup, and native batch output scaling.
- Cache GTCEu reflective lookups and AE2 Overclock invocation handles; synchronize shared recipe and plan state.
- Hash the same recovery payload without creating copies of the public transaction view.

### 日本語

- 電力の丸め境界で一括搬入を再確認し、受入が確定した分だけ電力を消費します。
- 返却物・在庫枠・GTCEu共有バッファの扱いを見直し、重複する照会や計算を削減しました。
- 計画、取引復旧、BigInteger台帳の安全性を維持しながら、中間コピーを削減しました。
- 2.0.4のハング対策と、最終保存後に状態をクリアする処理を引き継いでいます。

Thanks to [@kaitu710-7878](https://github.com/kaitu710-7878) for [#252](https://github.com/syarukasu/ae2-crafting-optimizer/pull/252).
