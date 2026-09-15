# ACO 2.0.0 Planning Worker Contract

この文書はIssue #179の実装境界を定める正本です。

## 機能を維持した内部再構築

2026-09-14の方針: 既存の内部構造には拘束されず、必要ならゼロベースで作り直す。
これは機能削減やAPI再設計の許可ではなく、再実装の完了記録でもない。
現在の動作不良や未検証事項を、維持すべき仕様や完成済み機能として扱わない。

維持する契約:

- 通常AE2の候補集合・順序、substitution、CRAFT_LESS、Missing、容量、発注と端末操作。
- メモ化、キャッシュ、世代管理、計算重複排除とsubscriberごとの取消所有権。
- 注文・在庫・中間需要・出力・Pattern回数・bytesのどこでも正確なBigInteger数量。
- Server Threadを計算待ちにせず、live状態をworkerから触らないThread境界。
- BigInteger / BigCapacity sidecar、Exact Count、Pattern Batch、crafting-table、
  vector、physical executionの既存公開APIとcapability handshake。
- ACOが所有するexact取引のReceipt、Escrow、最終搬入、取消、保存・復旧契約。
  外部CPUのlifecycleは引き続き外部MODが所有し、ACOへ取り込まない。
- 診断ログ・統計、既存Config、保存NBT、通信形式、Forge / NeoForgeの対応。

削除対象にできるものは、役割が重なる内部経路、不要な中間層、不要と確認できた
互換処理、不整合を隠すfallbackである。既存の利用者・保存データが必要とする互換性は
維持する。入力所有権取得前の正当な通常AE2への辞退と、wide計画の明示失敗は残す。

最初の着手範囲はSnapshot / 計算 / AE2接続の境界とする。API、数量契約、
物理取引、保存形式を同時に書き換えず、個別Issueの仕様を確定してから実装する。
1.5.22の既存操作・発注挙動、1.5.33のExact Count契約、その後の必要な修正を
比較基準にする。現行コードだけを正解として、不具合を新実装へ移植しない。

受け入れは、その変更に関係する既存試験、数量・sidecar・取消結果の一致、
両ローダーのbuildとする。証拠のない速度・TPS・実クラフト完走は完了扱いにしない。
移行中も実経路の所有者は一つにし、二重計算・二重会計を製品へ残さない。
既存の未コミット変更、稼働中環境、外部MOD本体、公開先はこの方針変更では変更しない。

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

Graph構築、Topology検証、wide判定、数量伝播は、ACOの不変計算区間内で行います。
この区間へ入る時にAE2のmonitorへ制御を返し、区間中の`simulateFor`はworkerを待ちません。
checkpointは取消だけを検査します。exact在庫取得とPattern binding/sidecar確定はServer executorへ
委譲し、待つのはtickから切り離したworkerだけです。Server停止時は保留taskを取り消します。
bytesとroot-windowの計算はworkerに残します。

複数Producerや共有中間素材で数量Plannerを採用できない場合も、到達する全候補が
Snapshotへ完全captureされている通常AE2計算は、同じ非待機区間で続行します。
AE2のrequest、trial/rollback、Simulation在庫、Missing、bytes計算は変更しません。
live Grid/Providerを読むbuildChildPatternsと返却物callbackだけをServerへ渡します。
既に構築済みのnodesは再委譲しません。未capture、動的Input、substitutionの候補が
含まれる通常経路はこの拡張の対象外です。

Pattern/recipe/configが変わった場合と結果返却時は、次のAE2 pause handshakeを
取得してから外側へ戻ります。世代更新時も同じAE2標準計算を続け、最初からretryしません。
計画の最終世代検証はこの復帰後に行い、復帰待ちの間に変わった世代を見逃しません。
`run`の登録、外部Planner候補選択、finallyを飛ばしません。
Server停止は既存の保留task管理から登録workerへも割込みを通知し、finishで解除します。
未証明の通常AE2/外部Plannerのlive状態を読む経路は引き続き同期します。
この経路を含む全計算の非占有・Watchdog解消は、今回の修正の証明範囲外です。

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
- ACO不変計算区間ではServer Threadがworkerの完了やpauseを待たない
- Snapshotで不変性を証明できるAE2標準経路も、同じ非待機区間で計算する
- 直列Graphと並列Graphの二重構築が存在しない
- Forge 1.20.1とNeoForge 1.21.1で同じ契約を持つ
- 実経路全体の時間とallocationを比較するまで、高速化・軽量化を達成扱いにしない
