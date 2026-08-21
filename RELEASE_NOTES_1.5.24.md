# AE2 Crafting Optimizer 1.5.24

## English

### Fixed

- Restored the external Pattern Provider and crafting execution ownership
  boundary. ACO no longer coalesces third-party provider refreshes or applies
  a second execution budget when another add-on owns that hook.
- Kept the 1.5.21 normal AE2 boundary: ordinary inventory, GUI interaction,
  checked-long planning, CPU submission, and add-on execution remain owned by
  AE2 or the responsible add-on.
- Retained the 1.5.22 exact-inventory snapshot isolation.
- Retained the 1.5.23 standard AE2 exact physical execution, recovery-journal,
  NBT persistence, and Advanced AE integration fixes.

### Verification

- Added a machine-readable patch-release scope with 1.5.21 as the minimum
  behavior baseline.
- Added regression coverage for external provider refresh and execution-hook
  ownership.
- Full historical runtime verification remains a separate strict audit; this
  release does not relabel pending runtime evidence as verified.

## 日本語

### 修正

- 外部Pattern Providerとクラフト実行の所有権境界を復元しました。ACOは第三者Providerの
  更新通知を間引かず、他のアドオンが所有する実行フックへ二重の実行予算を適用しません。
- 1.5.21の通常AE2境界を維持しました。通常在庫、GUI操作、long範囲の計算、CPU提出、
  アドオン側の実行はAE2または担当アドオンが引き続き所有します。
- 1.5.22のexact在庫スナップショット分離を維持しました。
- 1.5.23の標準AE2 exact物理実行、復旧Journal、NBT保存、Advanced AE連携修正を維持しました。

### 検証

- 1.5.21を最低動作基準とする、機械可読なパッチ版リリース範囲を追加しました。
- 外部Provider更新と実行フック所有権の回帰試験を追加しました。
- 過去全件の実機確認は別の厳格監査として残し、未実施項目を検証済みへ変更していません。
