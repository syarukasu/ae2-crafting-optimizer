# Issue #156: 通常クラフト計算の高速化

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/156
- 状態: In review
- 対象版: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue: #79, #90, #103, #109, #125, #151, #153

## 要求

ACO導入時の余計な負荷を除くだけでなく、AE2の通常クラフト計算そのものを高速化する。
ただし、AE2が返すクラフト可否、使用在庫、不足数、Pattern実行回数、容量を変更しない。

## 先行修正の範囲

PR #157/#158は次のACO固有オーバーヘッドを除去した。

- 通常long注文での同期exact全mount走査
- 採用先のないBigInteger preflight
- 採用先のないShadow準備

これはACO非導入時との差を縮める修正であり、AE2計算自体の高速化ではなかった。
Issue #156の目的を満たすため、本変更で別の高速計算経路を追加する。

## AE2 15.4.10で確認した計算経路

AE2の単一Producer経路は`CraftingTreeNode`と`CraftingTreeProcess`を注文ごとに構築する。
Pattern回数は既に除算と乗算でまとめられており、注文個数そのものを一個ずつ反復してはいない。

負荷源は主に次の処理である。

- 注文ごとの再帰Tree構築
- 同じ共有中間素材が別経路に現れた場合の再訪
- `canEmitFor`、`getCraftingFor`、fuzzy候補、返却物、`IInput.isValid`の反復
- 不足計算時の通常計画とsimulation計画の再走査
- 同一Rootを再注文した場合の構造解析のやり直し

したがって、注文量だけを短絡する処理ではなく、到達した固有レシピ数に比例する
世代付きProgramを再利用することが修正境界になる。

## 実装

### Root到達範囲のコンパイル

`Ae2CompiledCraftingGraphCache`は注文Rootから到達するキーだけをBFSで収集する。
ネットワーク上の無関係なPatternは走査しない。Snapshotは次のキーで保持する。

- `ICraftingService`の同一性
- Dimension
- Root `AEKey`
- Provider Pattern世代
- recipe世代

同一世代・同一Rootの二回目以降は、コンパイル済みSnapshotと配列Programを再利用する。
Root cacheはDimensionごとに4,096件、到達Patternは一Rootあたり1,048,576件を上限とする。

### 採用できる経路

既定の`enableStrictDeterministicLongPlanner=true`では、次をすべて証明した経路だけを置換する。

- 各出力のAE2登録Producerが一つ
- 各入力slotの候補が一つ
- 出力が一種類で正数
- 入力係数と出力係数が正数かつlong演算可能
- 返却物、触媒返却、副産物がない
- 循環がない
- AE2本体所有の静的Patternである
- Emitter状態がコンパイル時と計画完了時で一致する
- live `IPatternDetails`とCompiled表現が完全一致する
- Provider Pattern世代、recipe世代、参照在庫が計画完了時まで一致する

複数Producer、タグ候補、動的Provider、外部Pattern実装、返却物、副産物、循環などは、
在庫を変更する前にACO計画を辞退し、AE2標準Plannerへ渡す。

### 数式Planner

証明済みProgramは親から子へのトポロジカル順で一巡する。

```text
demand[root] = requested

for each reachable key once:
  used = min(demand, inventory)
  deficit = demand - used
  executions = ceilDiv(deficit, outputPerPattern)
  demand[input] += executions * inputPerPattern
```

共有中間素材の需要は、そのノードを処理する前に全親から集約される。
通常量は`long`配列を使い、検査済み演算がoverflowした注文だけ既存BigInteger経路へ昇格する。

### AE2時間分割

Root探索、Pattern変換、Fingerprint、SCC解析、配列Program生成、live証明、計画本体は、
すべて`CraftingCalculation.handlePausing()`へチェックポイントを返す。
ACOのcold compileがserver tickを独占せず、AE2自身の計算時間枠とキャンセル契約を維持する。

### AE2標準経路のメモ化

高速経路を使えない注文でも、一つの`CraftingCalculation`内に限り次を再利用する。

- `canEmitFor`
- `getCraftingFor`
- fuzzy craftable候補
- 返却物
- AE2本体Patternの`IInput.isValid`

Provider Pattern世代またはrecipe世代が変わった時点でMemoは利用しない。
ThreadLocalはAE2が`finally`から呼ぶ`finish()`で破棄し、例外終了時も残さない。

### 完成済み計画Cacheの失効

同一要求Cacheのキーへ在庫世代、Provider Pattern世代、recipe世代を含める。
ME在庫の成立した`insert/extract`とAE2が検出した外部mount更新は世代だけを進める。
搬入出、Watcher通知、共有在庫一覧はRedirectしない。

## 維持する不変条件

- BigInteger正本をlongへ切り捨てない。
- 複数候補の選択順をACOで近似しない。
- 不足一覧、使用在庫、Pattern回数を推測しない。
- cold compileをmain threadへ移さない。
- stale Snapshotを新世代へ再利用しない。
- 外部BigInteger consumerの登録だけで通常Plannerを有効化しない。
- CPU、実行処理、在庫、GUI、搬入出の所有権を取得しない。

## 回帰試験

- 固定seedの非循環グラフでlong Planner、BigInteger Planner、配列Programの結果一致
- 共有中間素材の需要集約
- 全終端不足の収集
- 1個注文と`Long.MAX_VALUE`注文の訪問ノード数一致
- 1,000段・16,000桁注文が固有ノード数に比例すること
- cold Graph/Programコンパイルが時間予算へチェックポイントを返すこと
- BigInteger連携だけでは通常計画を置換しないIssue #109境界
- 新規Mixinが必須注入数を持ち、搬入出をRedirectしないこと
- 全クラス責務台帳とIssue回帰マニフェストの同期

## 性能特性

- cold単一路線: Root到達範囲を線形コンパイルし、AE2の時間枠で分割する。
- warm単一路線: 構造解析を再利用し、参照キーと固有ノードを一巡する。
- 共有DAG: 同じ中間素材を一度だけ処理する。
- 曖昧なレシピ: 入口で早期辞退し、ACO側の深い探索を重ねない。
- Patternまたはrecipe変更後: 旧Snapshotを使わず、次の注文で対象Rootだけ再構築する。

改善幅はクラフトツリー形状とcacheの暖機状態に依存する。単純な短い直列レシピでは差が小さく、
共有中間素材が多い巨大DAGと同一Rootの反復注文ほど効果が大きい。

## 検証

- Forge 1.20.1: JUnit全件成功
- NeoForge 1.21.1: JUnit全件成功予定
- `clean build`: 両版で実施
- `git diff --check`: 両版で実施
- Minecraft起動試験: 指示により実施しない
