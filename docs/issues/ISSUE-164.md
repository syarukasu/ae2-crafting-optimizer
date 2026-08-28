# Issue #164: ACOの実行所有権とレガシー互換層を整理する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/164
- 状態: Ready
- 対象版: 1.5.x
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: #109、#115、#125、#129、#145、#156、#161

## 問題

現行ACOは計画最適化、BigInteger API、標準AE2実行制御に加え、外部CPUの親Job、
子Window、予約、取消、復旧、旧Batch API、廃止済みConfig、危険なI/O・端末hookまで
保持している。`PROJECT_CHARTER`と`FEATURE_OWNERSHIP`が定める責務より実装範囲が広く、
同じ状態をACO、AE2、外部MODが重複所有できる。

この重複により、通常AE2のGUI・在庫・クラフト提出を壊したIssue #109、外部CPUの
停止・二重会計を切り分けにくくしたIssue #115/#125、Configだけ残る退役機能を生んだ
Issue #129/#145と同じ構造が残っている。

## 再現と証拠

- `ACOServerLifecycle`が`AqeBigCraftingExecutionManager`と
  `OptionalAqeBigCraftingExecution`を毎server tick実行する。
- `AqeBigCraftingExecutionManager`がAdvanced AE CPUの親Job、子Window、予約、取消、
  復旧、完了を所有する一方、`FEATURE_OWNERSHIP.md`は外部CPU実行を禁止している。
- `PatternBatchApi`と`PatternBatchV2Api`が同時登録され、旧Sequential V1経路と
  transaction/receiptを持つV2経路が併存する。
- `ACOConfig`に`RETIRED_COMPATIBILITY_KEY`として常に拒否される設定が残る。
- Config既定OFFで原子性を証明できないGTCEu/Mekanism native batch実装が
  本番コード、起動報告、cache lifecycleを占有する。
- Mixin台帳に、通常在庫、端末、Import/Export Bus、IO Port、P2Pの正本状態または
  実行順へ介入する旧hookが残る。

## 期待結果

ACOの本番骨格を次の五領域へ限定する。

1. AE2クラフト計画とPattern/recipe/inventory世代管理
2. long/BigInteger正確量、plan sidecar、公開API
3. 標準AE2へ適用する公平な実行予算
4. 外部Worker自身が実装するV2/crafting-table/vector契約
5. cache、計画辞退、実行停滞の診断

## 現在結果

外部CPU実行、V1/V2、稼働機能/退役Config、安全なhint/状態変更hookが同じMOD内で混在し、
ライフサイクル、設定、Mixin、診断の正本が一つに定まっていない。

## 所有権

- AE2が所有する状態: 通常レシピ適格性、Provider、通常CPU Job、リンク、通常在庫、
  GUI、通常Pattern配送
- ACOが所有する状態: bounded cache、世代、exact計画とsidecar、標準AE2 exact経路で
  明示的に取得したtransaction/escrow、診断
- 任意アドオンが所有する状態: 固有CPU/Worker、構造、電力、進捗、取消、Receipt、完了
- fallback可能な境界: ACOが入力またはJob実行の所有権を取得する前だけ

## 維持する不変条件

- 通常AE2のクラフト可否、結果、欠品、在庫、進捗、取消を変えない。
- exact正本をlongへクランプ、飽和、切り捨てして判定しない。
- 外部CPUへは版管理されたAPIと不変データだけを提供する。
- AACが利用するV2/crafting-table/vector APIの公開シグネチャを維持する。
- Issue #156/#161の計画高速化と標準AE2実行予算の骨格を維持する。
- 所有権移転後は通常AE2へfallbackしない。
- 不明なMixinはfail-closedで適用しない。

## やってはいけないこと

- ACOからAQE/Advanced AEの親Job、子Window、取消、復旧、完了をtickする。
- 廃止機能をConfigだけ残して、稼働機能のように報告する。
- V1とV2のBatch実行を同じJobへ競合させる。
- 在庫、端末、Bus、IO Port、P2Pの正本をcache結果で置換する。
- 外部MODのprivate fieldを恒久的な実行契約にする。
- clean-break後に互換fallbackやlegacy pathを追加して削除責務を復活させる。

## 修正方針

1. `ACOServerLifecycle`から外部AQE実行tickとclearを除去する。
2. Advanced AE固有のJob/transaction/child/recovery MixinとManagerを削除する。
   性能上限だけを返す状態非所有のexecution-budget hookは維持する。
3. AQE専用submit/status/cancelコマンドを削除する。
4. 旧Pattern Batch V1の組み込みSequential経路を削除し、V2を唯一のBatch契約にする。
5. 退役Configキーと、既定OFFのGTCEu/Mekanism native batch本体を削除する。
6. 通常在庫、端末、Bus、IO Port、P2Pの正本または実行順へ介入する旧hookを削除する。
7. `OptimizationFeature`、Mixin台帳、Mixin JSON、Config、Startup Reportを同じ実装集合へ揃える。
8. 削除境界と公開APIを静的JUnitで固定する。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] 関連クラスと既存試験を読んだ
- [x] 再現条件を試験へ変換した
- [x] 所有権とfallback境界を確定した
- [x] 禁止事項を明記した
- [x] Forge/NeoForgeの適用範囲を確定した

## 試験計画

- 単体試験: 外部CPU実行非所有、V1非登録、退役Config非存在、Mixin三者一致
- 境界試験: 公開BigInteger/V2 APIシグネチャ、通常long、Long.MAX_VALUE、wide sidecar
- 故障・取消・復旧試験: 既存の標準AE2 exact transaction試験を維持
- ビルド: 両版`clean test`、`clean build`、issue regression manifest
- GameTestまたはユーザー側確認: 本依頼では起動試験を行わない

## 実装結果

実装後に記録する。

## 検証結果

実装後に記録する。

## 完了

- PR:
- マージコミット:
- 修正版:
- リリース:
