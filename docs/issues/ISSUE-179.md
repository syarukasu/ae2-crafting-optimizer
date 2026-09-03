# Issue #179: ACO 2.0 Async Crafting Planner

## 問題

初期2.0実装は「Server ThreadのTPSを守る」という目的を、1注文を固定4 threadで分割する
要件として扱っていました。その結果、次の不要な構造が入りました。

- serial Root Programを構築した後にparallel Graphを再構築する二重計算
- node数1024以上だけを選ぶ推測閾値
- work stealing、frontier barrier、worker間spin
- serial oracleとは別の数量計算実装
- 純粋Plannerだけを測る合成ベンチによる過大な性能評価

これは高速化、軽量化、正確性のどれも実経路では証明しません。

## 修正方針

固定4-thread Plannerを削除し、既存の正確なserial計算器を`ACO Planner`という専用workerで
実行します。目的は計算内並列性ではなく、Server Threadから重い純粋計算を分離することです。

実行順序:

1. Server Threadでlive AE2状態をimmutable Snapshotへ固定する
2. AE2計算workerからGraph compileと数量計算を専用workerへ渡す
3. AE2計算workerは`handlePausing()`でserver tickへ制御を返す
4. 専用workerは`OverflowPromotingCraftingPlanner`でlong優先・overflow時BigInteger再計算を行う
5. 現在revisionを再検証してからAE2 Planとexact sidecarを作る

## 不変条件

- Server ThreadはPlannerを実行せず、Planner Futureを待たない
- workerはlive Level、Grid、Storage、Block Entityを読まない
- longとBigIntegerで同じGraphとSnapshotを使う
- 結果を`Long.MAX_VALUE`へ飽和、切り捨て、近似しない
- 対応外の通常計画は状態変更前にAE2へ返す
- wide計画はoverflowするAE2 long経路へ戻さない
- 外部CPUの実行、電力、進捗、完了へ介入しない

## 削除対象

- `engine.parallel`一式
- 4-thread専用Graphと数量Planner
- node数による並列化閾値
- parallel専用Graph cache
- parallel-only合成benchmark

## 最小検証

- 専用workerが呼出thread以外で動作すること
- 既存のlong、BigInteger、位置独立overflow試験が通ること
- exact sidecarと公開APIの既存試験が通ること
- Forge 1.20.1 / NeoForge 1.21.1のclean buildが通ること

実際のTPS改善は、実AE2発注経路全体を未導入・1.5.22・2.0で比較するまで達成扱いにしません。

詳細なThread境界は`docs/PARALLEL_PLANNER_2_0.md`を正本とします。
