# Changelog / 変更履歴

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
