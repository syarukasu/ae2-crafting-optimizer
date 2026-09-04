# 現行実装状態

この文書は設定名ではなく、ACOに実在するruntime経路を記録します。

## 実装済み

### Pattern Provider

- `CraftingService.getCraftingFor`をAE2順のまま世代付きcacheへ保存
- Provider内容fingerprintによる世代更新
- 同一tickの重複refreshを、次のcrafting read前に必ずflush
- Pattern Provider targetはV2適格性判定のため読み取るだけで、send bufferやReceiptを書き換えない

### クラフト計画

- 同一の実行中計算Futureを共有し、待機者とcancel所有者を分離
- 不足simulationだけを既定対象とする短寿命completed-plan cache
- 一計算内の不変問い合わせmemo
- null、identity重複、構造不正だけを除く候補剪定
- Pattern/recipe世代付きcompiled graphとstrict planner
- long演算のoverflow検査と必要時だけのBigInteger再計算
- AE2結果と一致を確認するShadow Mode

### 実行

- 標準AE2 CPUごとの適応予算
- 標準AE2 gridごとの共有時間予算
- AE2本来の逐次`pushPattern`を、未計測波と計測済み波へ分けて実行
- 外部Adapterが明示登録した場合だけ使用するTransactional Batch V2 API
- V2取引のJournal、reconcile、rollback、quarantine

### exact数量

- exact在庫snapshotと、AE2へ見せる飽和long facadeの分離
- `CraftingPlan`へexact sidecarをidentity関連付け
- `BigInteger`計画、欠品、容量、進捗の公開API
- 標準AE2 CPUが所有するexact physical transaction
- quantity-independentな決定的作業台DAGのVector実行
- exact storage routeの所有権取得前preflight

### 任意連携

- AppliedE temporary patternをAE2/AppliedE Plannerへ残す境界
- GTCEuへ短寿命Recipe Intent候補を渡し、最終判定はGTCEuへ委譲
- MekanismのRecipe Cacheと機械tickには介入しない
- Circuit Cutter、Reaction Chamber、AE2 Overclock、Assembly Matrixの検証済みlookup cache
- Advanced AEとNeoECOのCPUへ、状態を所有しない実行予算だけを適用

## 削除済み

- Pattern Batch V1と内蔵Sequential Adapter
- ACO内蔵GTCEu/Mekanism Native Batch
- Fair SchedulerとそのNBT
- AQE/Advanced AE固有の親Job、子Window、取消、復旧、完了管理
- two-stage missing previewと最初の欠品だけを返すfast-fail
- 在庫量によるPattern並べ替え
- terminal、watcher、packet、scrollbarへのMixin
- Import/Export Bus、IO Port、Capability、P2P、Grid TickへのMixin
- 削除済み機能のConfig互換キー

## 完了判定

次を満たしてもMinecraft実働の証明にはなりませんが、PRへ入る最低条件です。

- `OptimizationFeature`、Config、Mixin catalog、Mixin JSONの集合が一致
- 公開BigInteger/V2/crafting-table/vector APIのsignature test成功
- 全JUnit成功
- Forge 1.20.1とNeoForge 1.21.1で`clean build`成功
- `git diff --check`成功
- Issue回帰表と責務一覧を同じPRで更新
