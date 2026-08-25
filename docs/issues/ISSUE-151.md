# Issue #151: PR #127を安定基準に1.5.25-1.5.27を再統合する

## 状態

Implemented

## 症状

公開版へ単純に切り替えると、`long`を超えるBigIntegerクラフトが受理された後、
完成品を搬入せずに停止する構成が再発した。一方、PR #127のHEAD `df01aae`は、
Issue #125向けの汎用target解決と物理実行状態遷移を保持している。

## 原因

PR #127と1.5.25以降の変更は、同じ基点から別々に実装された。1.5.25を基準に
後からPR #127を混ぜると、capacity gate、exact境界route、物理実行所有権の
統合順序が不明瞭になる。版番号だけでは、PR #127の実行責務を保持した成果物か
判定できなかった。

## 修正

1. Forge 1.20.1のPR #127 HEAD `df01aae`を実行基準にする。
2. 1.5.25のwide容量gateとexact境界routeを追加する。
3. 1.5.26のDedicated Server向けMekanism音声型解決修正を追加する。
4. 1.5.27の最適化domain、安全gate、IO/端末境界整理を追加する。
5. PR #127の実行3クラスと回帰試験は内容を変更しない。
6. 基準ファイルのSHA-256をJUnitで固定する。

## 不変条件

- `PhysicalCraftingTreeTransaction`は、target探索から最終exact搬入までを所有する。
- `CraftingTableBatchTargetResolver`は、NeoECO固有型ではなくACO汎用APIでtargetを解決する。
- `Ae2BigCraftingExecutionManager`は、待機理由を隠さず、裏付けのないwide jobを保持し続けない。
- 通常long計画はACOが所有せず、AE2標準経路へ返す。
- capacity gateはwide計画の提出を許可するだけで、物理実行状態を置換しない。
- 端末、Import/Export Bus、在庫正本へ広域exact hookを追加しない。

## 禁止事項

- PR #127の実行クラスを1.5.25版で上書きする。
- 版ブランチを丸ごとmergeして競合解決をGitへ委ねる。
- `long`へクランプした値をBigInteger会計の正本にする。
- 実機未確認を静的試験だけで正常動作保証と表現する。

## 自動試験

- `Pr127StableBaselineSourceTest`
- `Issue125RegressionSourceTest`
- wide容量gate関連試験
- Issue #129最適化domain関連試験
- Issue #140 Dedicated Server安全試験
- 全JUnitと`clean build`

## GitHub

- Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/151
