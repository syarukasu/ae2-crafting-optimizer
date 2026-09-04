# ACO 2.0.0 Planning Worker Contract

この文書はIssue #179の実装境界を定める正本です。

## 目的

AE2とACO 1.5.33の正確な計画結果を維持したまま、クラフト計画の純粋計算を
Server Threadから分離し、計算時間がserver tick時間を直接消費しないようにします。

「並列化」は一つの注文を4分割する意味ではありません。AE2は既に
`CraftingService.CRAFTING_POOL`の`AE Crafting Calculator` workerで計算を実行します。
ACOは第二のexecutorを作らず、この既存境界で不変Snapshotだけを計算します。

## Thread境界

Server Threadだけが次を行います。

- live AE2 Pattern、Grid、Storageの参照
- immutable Pattern/Inventory Snapshotの取得
- exact在庫取得
- Planの提出と実在庫の変更

`AE Crafting Calculator` workerだけが次を行います。

- immutable Graphのコンパイル
- Root Programと厳密Topologyの検証
- checked long数量計算
- overflow時のBigInteger再計算
- Missing、使用在庫、Pattern回数の計算

Graph構築、Topology検証、wide判定、在庫ベクトル作成、数量伝播は、既存のnode checkpointから
AE2本来の`handlePausing()`へ制御を返します。別の間隔閾値は追加せず、実際のpause判定はAE2に任せます。
wide計画のexact在庫取得をServer Threadで待つ間も同じhandshakeを使います。純粋計算を別workerへ再投入して
`get`、`join`、busy waitする経路は持ちません。

## 正確性

- 計算器は1.5.33由来の`OverflowPromotingCraftingPlanner`を一つだけ使います。
- longとBigIntegerで別のGraphを作りません。
- overflow時は同じimmutable Snapshotから数量計算を最初からやり直します。
- Pattern候補、必要素材、Missing、bytesを近似しません。
- 証明できない通常計画は所有権取得前にAE2へ返します。
- wide計画をAE2のoverflowするlong経路へ戻しません。

## 実行制御

- ACO独自のPlanner workerとqueueは作りません。
- 1注文内のwork stealing、frontier barrier、4-thread分割は行いません。
- ノード数による推測閾値は設けません。
- ACO独自の同時注文上限や受付閾値を追加せず、AE2の計算lifecycleに従います。
- cancelとserver停止はAE2の`CraftingCalculation` Future所有権へ従います。

## ACOが行わないこと

- 外部CPUの実行速度、電力、進捗、完了を所有しない
- 外部機械のrecipe探索、cache、tick、入出力を横取りしない
- storageへの実extract/insertをworkerから行わない
- AE2のクラフト可否やPattern選択を変更しない
- timeout、retry、sleep、安全率で不整合を隠さない
- 合成ベンチだけを根拠にTPS改善を宣言しない

## 完了判定

- long計画とwide計画が既存serial oracleと完全一致する
- Planner処理がAE2の計算worker上で実行され、ACO独自executorへ再投入されない
- Server ThreadがPlanner Futureを待たない
- 直列Graphと並列Graphの二重構築が存在しない
- Forge 1.20.1とNeoForge 1.21.1で同じ契約を持つ
- 実経路全体の時間とallocationを比較するまで、高速化・軽量化を達成扱いにしない
