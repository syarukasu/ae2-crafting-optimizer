# Issue #156: 通常クラフト発注時の不要なexact計算

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/156
- 状態: In review
- 対象版: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue: #109, #125, #151, #153

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
AE2本体は`CraftingCalculation`を生成した後に専用計算executorへsubmitしており、
ACO、AQE、InsaneAEの確認済み利用箇所はFutureをtick間でpollするため、server threadを
同期`get()`で塞ぐ経路にはならない。

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
また、Futureをserver thread上で同期的に待つ外部アドオンはAE2の非同期契約にも反するため、
今回確認した利用箇所以外がその呼出し方をする場合は別途adapterが必要になる。
