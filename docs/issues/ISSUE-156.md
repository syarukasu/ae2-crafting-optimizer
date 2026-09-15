# Issue #156: 通常クラフト発注時の不要なexact計算

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/156
- 状態: Verified
- 対象版: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue: #109, #125, #151, #153

## 2026-09-14: 発注時の在庫固定・exact集計の追加最適化

目標は発注前の同期準備とwide在庫集計の重複処理を減らすこと。
実storageのinsert/extract、外部CPU実行、数量契約、公開APIシグネチャは変更しない。
World/MEのmutationをworkerへ移すことも非目標とする。

確認した原因:

- constructorのcaptureReferencedへ渡すRootCapture.referencedKeysは、注文の依存キーでは
  なく公開index全体のキーを返していた。小さい注文でも全Pattern規模の照会になる。
- PlanningExactInventorySnapshotはmountごとにBigKeyCounterSidecars.mergeを呼び、
  それまでの全合計Map/Setを複製・凍結する。異なるキーが増える構成で累積コピーが二次的に増える。

最小修正:

- 同じPublishedCaptureにcached Root Programがある場合だけ、その不変キー列を参照する。
  cold、失敗、eviction時は従来の全参照キーを保持し、同期graph構築は追加しない。
  在庫値自体は毎回新たに固定し、古い在庫やMissingを再利用しない。
- 一回のexact captureだけが所有するAccumulatorでmount寄与を合計する。
  キー別exact判定は既存mergeと共通化し、最後に一度だけ不変Snapshotを公開する。
  各mountの読み取り、可視キー、negative/incomplete判定、重複mount除外は維持する。
- 通常のNetworkStorage/GUI/busの経路や実insert/extractへ新しい注入を追加しない。

受け入れ:

- 関連既存JUnitを先に実行する。
- warm/cold rootの参照範囲と、immutable在庫が元Counterの変更を受けないことを確認する。
- 既存逐次mergeと一括captureのlong Facade、BigInteger量、キー別exact判定を比較する。
- 既存のBigInteger位置独立、sidecar/API、世代、取消試験を維持する。
- 両版のclean build、回帰マニフェスト、git diff --checkを通す。
- 実際のcaptureメソッドの仮想mount fixtureを同一JVM内で比較し、発注全体/TPSの倍率とは区別する。
- GitHub公開、稼働環境への配置は行わない。

### 在庫固定・集計変更の結果

今回の製品変更は5ファイルに限定した。

- Ae2ImmutablePlanningGraphCache: 同じPublishedCaptureのcached Root Programだけから参照キーを得る。
- CompiledRootProgram: 構築済み不変キー列をpackage-privateで返す。
- BigKeyCounterSidecars: キー別exact判定を共有するcapture-local Accumulator。
- BigIntegerStorageSnapshotBridge: 通常の逐次公開を維持し、Planningだけローカル合計へ加算する。
- PlanningExactInventorySnapshot: 重複mount除外とFacadeを維持し、最終exact Sidecarを一度だけ公開する。

追加試験は既存2クラスへ計3件。
Ae2PlanningInventorySnapshotTestでwarm/cold/失敗時の選択、同期compile不在、在庫の取り直しを確認。
BigIntegerStorageSnapshotBridgeTestで実captureメソッドと逐次集計を比較し、
incomplete Provider、負数Facade、キー別exact判定も一致することを確認した。
重複mount、10^64在庫、通常long加算、公開APIの計画参照は既存試験も維持している。

同一JVM、同一258 mount fixture、暖機10回後に20回ずつ経路を交互に計測。
256種類の独立キーに、10^64を返す公開ExactStorageAmountProviderと同じキーのlong寄与7を追加した。
「逐次」は従来方式のmountごとmerge、「一括」は本番のcaptureMountedStorages入口である。
入力fixture生成と結果比較は計測外。各行は20回合計で、API呼出し前の集計区間だけの値。

| 計測 | 逐次 ms | 一括 ms | 逐次 MiB | 一括 MiB |
|---|---:|---:|---:|---:|
| Forge / Java 17 関連試験 | 137.387 | 44.655 | 250.437 | 104.927 |
| Forge / Java 17 clean build | 146.225 | 47.833 | 250.489 | 104.995 |
| NeoForge / Java 21 clean build | 147.692 | 53.978 | 251.057 | 105.286 |
| Forge / Java 17 #182 parity後の再検証 | 171.652 | 61.983 | 250.456 | 104.909 |
| NeoForge / Java 21 #182 parity後の再検証 | 168.633 | 53.575 | 250.937 | 105.126 |

このfixtureの集計区間は約2.7-3.1倍、割り当て量は約58%減。
全クラフト発注時間、Main Server Thread時間、TPS、実セルI/Oの倍率は未計測。
実サーバーのstorage読み取りは引き続きserver上で実行される。
実insert/extractを非同期へ移したり、CPUやEscrow/Receiptの所有権を変更したりしていない。
AQE/InsaneAEが使う公開APIシグネチャ、sidecar、数量Plannerにも変更はない。
外部MODとの実稼働相互運用はこの仮想試験だけでは保証しない。

- Forge: 117 suites / 495 tests / failures 0 / errors 0 / skipped 2。
- NeoForge: 124 suites / 513 tests / failures 0 / errors 0 / skipped 0。
- 両版でclean build verifyIssueRegressionManifest --no-build-cache --no-daemon成功。
- 両版でgit diff --check成功。新ロジックは同じ意味で実装した。
  loader差分は既存のRegistryAccess呼出しとコメントだけである。
- Forge JAR SHA-256: 3D39B8189E5FDB7F85A2B7A9FA191DADC96FEAF11DC663A3F655E25F78934B1B
- NeoForge JAR SHA-256: 248CD35159A60328E630B3605FC1DF44D3B45B7E6F3592C5951F5CD2EF49F01D

これはIssue #156全体の完了ではなく、現在の発注経路で確認した二つの重複処理の修正記録。
cold rootは全参照キーの開始時在庫を保持し、未知のPatternや実在庫をworkerへ渡さない。
GitHubのIssue/PR投稿、リリース、サーバー・クライアントへの配置は行っていない。

## 2026-09-14: クラフトGraph構築の追加最適化

目標は構築そのものの時間と割り当て量を減らすこと。数量計算、CPU実行、
公開API、保存形式、Serverとの待機境界は今回変更しない。

実コードで確認した構築経路:

- 公開済みPattern SnapshotをworkerのSnapshot.compileがCompiledCraftingGraphへ変換する。
- Graphは出力索引と依存辺を作り、循環検査で逆向きの依存Graphをもう一つ構築する。
  二度の探索に加え、逆辺用のMap/Setを各ノードへ割り当てている。
- Snapshot.compileのcandidatesByOutputは全候補のListを作るが、返却Snapshotにも
  Graphにも渡されず、以降の処理から一度も参照されない。

修正方針:

- 循環検査を再帰しないTarjan法へ置換し、逆Graphと二巡目をなくす。
  Pattern索引・候補集合・候補順は変更しない。共有依存と別SCCを混同しない。
- 未使用候補Map/Listだけを除去し、候補数・未capture・出力量の検証は維持する。
- 世代付きcacheのkey、失効、publication、long/BigInteger計算は変更しない。
- 元の実装と変更後で、同一fixtureのGraphとRoot Program構築を測る。
  数量Plannerの倍率をツリー構築やTPSの倍率として報告しない。

受け入れ:

- 先に既存Graph/Root Program試験を実行する。
- 既存の20,000段chainと循環検査を維持し、別SCCへの片方向辺・自己循環・候補順を
  一つの追加fixtureで確認する。新しい試験基盤は作らない。
- 既存性能probeへ共有中間素材を持つ構築計測だけを追加する。絶対時間は合否条件にしない。
- 両版の関連JUnit、clean build、回帰マニフェスト、diff検査を通す。
- 不変条件は結果、Missing、bytes、循環判定と候補順の一致。
  稼働環境への配置、起動、GitHub公開はこの変更に含めない。

### 追加変更の検証結果

今回の変更はGraph構築の局所最適化。Issue全体の完了、実AE2の発注時間やTPS改善、
通常AE2再帰ツリー全体の置換を意味しない。

- Forge / Java 17: 117 suites、492 tests、失敗0、エラー0、skip 2。
- NeoForge / Java 21: 124 suites、510 tests、失敗0、エラー0、skip 0。
- 両版の`clean build verifyIssueRegressionManifest --no-build-cache --no-daemon`成功。
- 今回のGraph実装と追加試験は両ローダーで同一。数量Planner、public API、Mixinは変更なし。
- 追加した機能試験は別SCC・自己循環・候補順を確認する1件。性能probeは既存クラスへ追加した。

1,000枝が共有中間素材へ合流する1,003ノードを、40回暖機後に120回構築した記録。
Graphは候補索引と循環検査、TreeはそのGraphにRoot Programの構築を加えた範囲。
fixture生成、live Pattern capture、数量計算、ゲーム内発注は計測外。
割り当て量は計測threadの合計であり、常駐heap量ではない。

| 実行 | Graph ms | Tree ms | Graph MiB | Tree MiB |
|---|---:|---:|---:|---:|
| Forge 変更前・対象試験のみ | 122.328 | 290.617 | 175.242 | 323.256 |
| Forge 変更後・対象試験のみ | 74.320 | 216.057 | 96.052 | 246.529 |
| Forge 変更後・全体試験内 | 73.107 | 300.430 | 96.049 | 246.524 |
| NeoForge 変更前・対象試験のみ | 118.008 | 222.462 | 188.271 | 329.343 |
| NeoForge 変更後・対象試験のみ | 86.351 | 228.698 | 107.956 | 258.871 |
| NeoForge 変更後・全体試験内 | 141.252 | 373.159 | 107.956 | 246.948 |

Graphの割り当て量は約43-45%減少した。一方、wall timeは実行間で変動が大きく、
Graphを含めて固定の高速化倍率は未確定。Root Program込みの全体高速化も断定しない。
未使用候補Map除去はlive Snapshot側の変更であり、このpure probeの倍率へ加算しない。

変更ファイル:

- `engine/CompiledCraftingGraph.java`: 逆Graphを作らない非再帰SCC検査。
- `engine/Ae2ImmutablePlanningGraphCache.java`: 未使用候補Map/Listの除去。
- `engine/CompiledCraftingGraphTest.java`: SCCと候補順の保護。
- `engine/PlannerPerformanceProbeTest.java`: 構築範囲を分離した計測。
- 本仕様書、回帰マニフェスト、クラス責務一覧とその生成script。

ビルド済みJARのSHA-256:

- Forge: `720FDEDF421F3E83AFBD5CC0BE0374970FCF9D769C1C9DA771564A7AB5070AE2`
- NeoForge: `01B298DE38C5FE93D971EFE5980A7373670FACF141BC1CA879ED6E75D6F94406`

GitHubのIssue/PR更新、リリース、稼働環境への配置は行っていない。

## 症状

通常のint/long範囲の注文でも、発注直後の応答が非常に遅くなる。
ACOが通常AE2計画を置換しない既定構成でも再現する。

## 確定したRoot Cause

次の四つが重なっていた。

1. `enableAtomicBigCapacityPlans`が有効な場合、通常long置換が無効でも
   `Ae2AuthoritativeCraftingPlanner.capture`が全注文で起動する。
2. Captureは毎回`PlanningExactInventorySnapshot.capture(grid)`を実行し、
   全mounted storageからexact在庫を収集する。
3. 非同期Plannerは毎回、空在庫のBigInteger計画を最後まで展開してwide判定を行う。
4. `CraftingCalculation`生成側で上記Captureを行うため、全mount走査は
   AE2の`CRAFTING_POOL`へsubmitされる前の呼出スレッドで実行される。

加えてShadow Modeは、通常long置換もwide資格要求も無効な場合まで
Compiled Root Programと参照在庫を準備していた。このShadow結果には採用先がなく、
既定構成では純粋な追加負荷だった。

## 前の未検証修正で不十分だった点

前差分は保守的なwide閾値を追加したが、次の理由で採用しなかった。

- CraftingCalculation生成側でGraph、Root Program、Topology、閾値を新規構築していた。
- 閾値生成はRootごとに最大63回DAGを走査し、cold pathをメインスレッドへ移していた。
- 同一slotの代替候補をすべて合計した上界を、実際のwide判定として扱っていた。
- 旧Capture経路を無条件wideとして扱い、従来の意味を変えていた。
- 世代更新後も旧閾値判定を再利用でき、stale proofを新世代へ移せた。

## 最終修正

### Capture境界

`Ae2CompiledCraftingGraphCache.currentSnapshot`は、現在世代の既存Snapshotだけを返す。
Graph、Root Program、Topologyを新規構築しない。

Captureは次の三状態を持つ。

- `UNASSESSED`: cold cacheまたは未証明。exact在庫は持たず、非同期側で構造を判定する。
- `PROVEN_LONG_SAFE`: 同一世代のcached safety certificateが成立し、exact在庫を持たない。
- `EXACT_AVAILABLE`: wideが正確に確定した後、server executor上で取得したexact在庫を持つ。

`CraftingCalculation`生成時は既存cacheを参照するだけで、Graph、Root Program、Topology、
exact在庫のいずれも新規構築しない。cold通常注文は非同期側で一度だけ安全証明を作り、
wideでなければexact在庫を走査せずAE2標準計画へ戻る。

wideが確定した注文だけ、AE2計算Futureのworkerからserver executorへexact取得を委譲する。
計画後の一致再検証に使うexact在庫もserver executorで再取得し、計算workerから
mounted storageを直接走査しない。往復後にはPattern/recipe世代も再確認する。
AE2 15.4.10本体は`CraftingCalculation`を生成した後に専用`CRAFTING_POOL`へsubmitしており、
ACO、AQE、InsaneAEの確認済み利用箇所はFutureをtick間でpollするため、server threadを
同期`get()`で塞ぐ経路にはならない、という当初の判断は不完全だった。

2.0.0で、呼出側がFutureをpollしていてもこの往復が停止することが判明した。
AE2自身が`TickHandler.simulateCraftingJobs()`から`CraftingCalculation.simulateFor()`を呼び、
Server Thread上で計算workerのpauseを同期的に待つためである。workerがその区間でserver
executorへexact取得を投入して`Future.get()`すると、両threadが相互待機になる。
ACOはexact取得の完了待ち中に、AE2本来の`handlePausing()` handshakeへ協調yieldする。
これにより全注文のeager exact走査へ戻さず、Server Threadがexact取得を実行できる。
2.0.0の並列Session待ちにも同じhandshakeを使い、別poolの完了待ちでServer Threadを
塞がない。

### wide判定

`LongSafetyCertificate`はwideを確定しない。
現在注文を一巡して代替候補をすべて含む保守的上界がlongに収まる場合だけ、安全と証明する。
証明済みの最大注文量以下は同じ世代中O(1)で再利用する。

安全を証明できない注文は、従来の`program.planBig`による正確なpreflightへ進む。
このため保守的上界のfalse positiveは性能だけに影響し、結果や診断をwideへ変えない。

### 世代変更

`UNASSESSED`または`EXACT_AVAILABLE`だけ、一度だけ最新Pattern/recipe世代へ再評価できる。
`PROVEN_LONG_SAFE`は旧証明を新世代へ移さず、ACO結果を採用せずAE2へ戻る。
Graph、Topology、証明器は世代付きSnapshotに所属し、新世代へコピーしない。

### Shadow Mode

Shadow計算は次のどちらかに該当する場合だけ有効になる。

- 実験的な通常long置換が有効
- wide計画へShadow資格を要求する設定が有効

採用先が無い既定構成では通常注文へShadow計算を重ねない。

### hot pathの割り当て削減

数式Plannerの結果は変えず、注文ごとの一時処理を次のように削減する。

- 単一入力候補では候補順位計算を省略する
- 通常`long`候補順位で`BigInteger`を生成しない
- checked演算のノード位置文字列は、失敗時だけ生成する
- 在庫Snapshotとコンパイル配列は、private生成元から所有権を移して重複cloneしない
- `used`、`emitted`、`missing`用の五配列を三配列へ集約する
- 最終結果Mapを四回走査せず、一回のノード走査で同時に構築する

配列とSnapshotは外部へ公開されず、計画結果は従来どおり不変Mapへcopyされる。

## 維持する不変条件

- BigInteger正本をlongへ切り捨てない。
- 保守的上界だけでwide計画と診断しない。
- cold pathのGraph/Topology構築をCraftingCalculation生成側へ移さない。
- Provider/recipe世代が変わった安全証明を再利用しない。
- 通常long置換が無効なwarm注文はAE2標準計画へ戻す。
- AE2の在庫、CPU job、GUI、搬入出、クラフト可否をACO側で変更しない。
- AQE、InsaneAE、AAC、NeoECOの実行ロジックへ介入しない。

## 回帰試験

- 通常多段レシピのlong safety certificate
- long注文ではdeferred exact取得を開始しないCapture policy
- Long.MAX_VALUE境界
- 合流DAG
- 非先頭の代替候補を含む保守的上界
- 保守的上界がwideでも、正確な実選択がlongならwideへ変えない
- 固定seedで200個の多段DAGを生成し、安全証明のfalse negativeが無いこと
- exactを持たない証明を新世代へ再利用しないこと
- Shadow計算の採用先が無い場合に無効となること
- Issue回帰マニフェストの同期

## 性能への影響

- cold long root: exact captureを行わず、現在注文の安全証明を非同期側で一巡する。
- cold wide root: 正確なwide判定後にだけ、server threadで計画前後のexact在庫を取得する。
- warm long-safe root: O(1)のcache参照だけを行い、全mount exact走査とBigInteger preflightを省略する。
- 世代変更: Snapshotごと証明を破棄し、次のcold rootで再構築する。
- 通常AE2結果を守るため、wall-clock時間による打ち切りや無条件retryは追加しない。

### 同一JVM内の性能プローブ

`PlannerPerformanceProbeTest`で、1,000段の一意な直列DAGを40回暖機後に120回計算した。
これはMinecraft全体の発注時間ではなく、Planner hot pathだけの相対値である。

| 環境 | 配列Planner | 旧Map型Planner | 相対高速化 | 配列割当 | 旧Map割当 | 割当削減 |
|---|---:|---:|---:|---:|---:|---:|
| Forge / Java 17 | 29.286 ms | 205.103 ms | 7.00倍 | 15.262 MiB | 123.048 MiB | 8.06倍 |
| NeoForge / Java 21 | 30.645 ms | 208.420 ms | 6.80倍 | 15.251 MiB | 123.032 MiB | 8.07倍 |

Forgeで割り当て削減前の配列Plannerは55.602 msだったため、今回のhot path修正だけで
同プローブを約47%短縮した。絶対時間と割当量はJIT、CPU、GCで変動するため、回帰判定には
速度の固定閾値を使わず、出力一致と固有ノード数比例を必須条件とする。

## 検証

- Forge 1.20.1: 全JUnit成功
- NeoForge 1.21.1: 全JUnit成功
- 対象境界テスト: 両版成功
- `git diff --check`: 両版成功
- 専用GameTestタスク: 両版のGradleプロジェクトに未定義
- Minecraft起動試験: 対象外

## 残存リスク

今回の修正は通常注文に対するCapture時のexact全走査、毎回のBigInteger preflight、
unused Shadow計算を対象とする。wide注文では正確性のためserver thread上の全mount走査を
一度行うため、その費用自体は残る。

Authoritative plan採用前のlive inventory/topology再検証には既存のGrid参照が残る。
外部アドオンが返却FutureをServer Thread上で直接`get()`する独自経路は、AE2の通常契約外で
あるため対象外とする。AE2自身の`simulateFor()` pause handshakeは今回の修正で維持する。
