# Issue #179: ACO 2.0 Planning Boundary Cleanup

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/179
- 状態: Verified
- 対象版: 2.0.0
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1

## 問題

初期2.0実装は「Server ThreadのTPSを守る」という目的を、1注文を固定4 threadで分割する
要件として扱っていました。その結果、次の不要な構造が入りました。

- serial Root Programを構築した後にparallel Graphを再構築する二重計算
- node数1024以上だけを選ぶ推測閾値
- work stealing、frontier barrier、worker間spin
- serial oracleとは別の数量計算実装
- 純粋Plannerだけを測る合成ベンチによる過大な性能評価

これは高速化、軽量化、正確性のどれも実経路では証明しません。

## 監査結果

固定4-thread Plannerは削除済みですが、その代わりに追加した単一`ACO Planner`も正しい境界では
ありません。AE2は`CraftingService.CRAFTING_POOL`の`AE Crafting Calculator` workerで
`CraftingCalculation.run()`を実行しており、ACOの`computePlan`注入も既にServer Thread外です。

単一`ACO Planner`へ再投入すると、全Gridの計画を一列に並べ、AE2 workerは完了まで
`handlePausing()`を反復します。これはServer Thread負荷を移動せず、queue、context switch、
待機側CPUだけを追加します。

また、ACOはMekanism機械の`getRecipe(int)`を先頭で横取りし、Mekanism本来の
`RecipeCacheLookupMonitor`と各`InputRecipeCache`の手前に第二の候補cacheと反射探索を
置いています。候補順の同値性を証明できず、ACOの計画・Exact Count責務にも含まれません。

## 修正方針

- pureなGraph compileと数量計算は、AE2が用意した`AE Crafting Calculator` worker上で直接行う
- Graph構築と数量伝播の既存node checkpointからAE2の`handlePausing()`を呼び、tick予算を守る
- live Grid/Storageを必要とするexact在庫取得だけServer executorへ委譲する
- ACO独自Planner executor、queue、反復待機を削除する
- Mekanismの`getRecipe`注入、第二のrecipe index、反射入力探索を削除する
- Mekanismのrecipe選択、cache、機械tickはMekanismへ完全に返す

実行順序:

1. `CraftingCalculation`構築時にlive AE2状態をimmutable Snapshotへ固定する
2. AE2計算worker上でGraph compileと数量計算を実行し、各node checkpointでpause可能にする
3. wide計画のexact在庫取得だけServer executorへscheduleし、AE2のpause handshakeで待つ
4. `OverflowPromotingCraftingPlanner`でlong優先・overflow時BigInteger再計算を行う
5. 現在revisionを再検証してからAE2 Planとexact sidecarを作る

## 不変条件

- Server ThreadはGraph compileと数量Plannerを実行せず、Planner Futureを待たない
- workerはlive Level、Grid、Storage、Block Entityを読まない
- longとBigIntegerで同じGraphとSnapshotを使う
- 結果を`Long.MAX_VALUE`へ飽和、切り捨て、近似しない
- 対応外の通常計画は状態変更前にAE2へ返す
- wide計画はoverflowするAE2 long経路へ戻さない
- 外部CPUの実行、電力、進捗、完了へ介入しない
- 外部機械のrecipe探索、cache、tick、入出力へ介入しない

## 削除対象

- `engine.parallel`一式
- 4-thread専用Graphと数量Planner
- node数による並列化閾値
- parallel専用Graph cache
- parallel-only合成benchmark
- `AsyncPlanningExecutor`と追加Planner queue
- Mekanismの`getRecipe`横取りとCachedRecipe accessor

## 最小検証

- ACO独自Planner executorが存在せず、AE2計算workerを二重委譲しないこと
- 初回Graph compileと数量伝播の両方がAE2のpause handshakeを通ること
- Mekanismのrecipe探索へACO Mixinを適用しないこと
- 既存のlong、BigInteger、位置独立overflow試験が通ること
- exact sidecarと公開APIの既存試験が通ること
- Forge 1.20.1 / NeoForge 1.21.1のclean buildが通ること

実際のTPS改善は、実AE2発注経路全体を未導入・1.5.22・2.0で比較するまで達成扱いにしません。

## 検証結果

- Forge 1.20.1 / Java 17: 114 suites、482 tests、失敗0、エラー0、skip 2
- NeoForge 1.21.1 / Java 21: 122 suites、502 tests、失敗0、エラー0
- 両版で`clean build --no-build-cache`、回帰マニフェスト、2.0.0 release scope gateに成功
- 1,000 node / 120回の内部probeで、配列Plannerは旧Map Planner比 Forge 9.43倍、NeoForge 11.02倍、割当量は両版とも8.07分の1

上記probeはACO内部の純粋Planner比較であり、実serverのTPS改善値ではありません。

詳細なThread境界は`docs/PARALLEL_PLANNER_2_0.md`を正本とします。
