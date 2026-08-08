# AE2 Crafting Optimizer

<p align="center">
  <img src="docs/aco-icon.png" alt="AE2 Crafting Optimizer icon" width="192">
</p>

[English](README.md) | 日本語

AE2 Crafting Optimizer（ACO）は、Applied Energistics 2向けのNeoForge
1.21.1最適化・連携MODです。重複するクラフト計算を減らし、巨大CPUの一tick
負荷を制御し、数量に比例しない作業台クラフト取引を提供します。

通常のレシピ、Provider、クラフトJob、ストレージの正本はAE2です。深い経路は、
入力へ触る前に会計全体を証明できた場合だけ使います。

この系統の永続ブランチは `mc/1.21.1` で、`main` はNeoForge 1.21.1系です。
リリースJAR名は `aco<version>_1.21.1.jar` を使います。Forge 1.20.1系は
`mc/1.20.1` で独立管理し、platform依存コード、metadata、Mixin descriptorは
両ブランチで共有しません。

## 対象環境

- Minecraft `1.21.1`
- NeoForge `21.1.247+`
- Java `21`
- Applied Energistics 2 `19.2.17`
- Advanced AE `1.6.11-1.21.1`（任意）
- Neo ECO AE Extension `21.1.1`（任意）
- 1.21.1向けGTCEu、Mekanism、Applied Mekanistics連携（任意）
- 専用サーバー、シングルプレイ

サーバーと全クライアントへ同じJARを導入してください。共通Configは次です。

```text
config/ae2_crafting_optimizer-common.toml
```

## 主な最適化

### クラフト計算

- Provider・レシピ世代単位のCompiled Pattern Graph
- 一計算内の在庫・候補メモ化
- 厳密に証明できる材料不足の高速判定
- `add`、`multiply`、`ceilDiv`のオーバーフロー検査
- `long`優先と、overflow時だけの`BigInteger`昇格
- 世代変更時の古い計算結果破棄

代替素材、循環、動的出力、不明な返却物などを完全に証明できなければ、
ストレージへ触る前にAE2へ戻します。

### CPU実行

- CPU単位・Grid単位のPattern Push時間予算
- 巨大コプロセッサ数向けの適応上限
- AE2本来の抽出・Provider・電力・Task・出力会計を保つSequential Instant
- 対応Adapter向けの永続Transactional Batch

表示上のCPU容量やコプロセッサ数は減らしません。一tickに開始できる仕事量だけを
実測時間で制御します。

### 機械Recipe Intent

Pattern Providerが指定したレシピ意図を保持し、GTCEuやMekanismが毎tick同じ
レシピを総当たりする回数を減らします。電圧、条件、電力、Tank、出力容量などの
最終判定は各機械MODが行います。

## 物理クラフトツリー

旧来の「ツリー全体を直接最終成果物へ変換する経路」は削除しました。現在は、
厳密な作業台クラフトをレシピ一段ずつ実設備で処理します。

設計は次の長所を組み合わせています。

- [InsaneAE](https://github.com/taikun24/InsaneAE):
  一回だけ実クラフトし、証明済み式へ正確な係数を掛ける
- Neo ECO AE Extension:
  Pattern Bus、Worker、Thread、実進捗、実電力、構造、NBT復旧

### 実行順

1. 現在のProvider・レシピ世代から決定的なPattern DAGを一度だけコンパイル
2. 境界入力、slot入力、各段の実行係数、最終出力、固定返却物を正確に計算
3. MEの全境界入力を一括でACO所有Escrowへ予約
4. Escrowに全入力があるレシピ段だけ実行可能
5. AACが実NeoECO Workerでエンコード済みレシピを一回assemble
6. 一回分の実入出力へ`BigInteger`係数を掛ける
7. Workerの永続Receiptから実出力をEscrowへ追加
8. その後だけ依存する次段を解禁
9. 最終成果物もEscrowからMEへ返却
10. ME挿入完了後だけ親CPU Jobを完了

20段直列レシピは、注文が`1`でも`Long.MAX_VALUE`でも対応範囲の
`BigInteger`でも20物理段です。独立した枝は複数Workerへ並列配置できます。
計算量は注文数量ではなく固有レシピ数と境界キー数に比例します。

### 所有権と復旧

- ACO: Plan、Escrow、正確なME変更、親Job会計、取消、復旧判断
- AAC: NeoECO物理AdapterとWorker Receiptのみ
- ME変更前にキーごとの正確なbefore/afterを保存
- 再起動後、after済みキーは再実行せずbeforeのキーだけ再試行
- WorkerはThread解放より先に終端Receiptを同じBlock Entity NBTへ保存
- ACOは実出力を一度だけ会計し、後からReceipt削除を明示要求
- タグ・代替素材はPlannerが選んだ具体キーを保存し、再起動後も同じ選択だけを再検証
- 解決済み一回分レシピ式はProvider・Recipe世代が変わるまで配列から再利用
- AACはTransaction UUIDからWorker・Threadを直接引き、再起動後だけ一度索引を再構築
- before/afterのどちらにも一致しない値は推測せず隔離

Fallbackできるのは入力所有権の移動前だけです。移動後は再開、正確な取消返却、
または隔離のいずれかになります。

### 対象レシピ

- AE2 Molecular Assembler対応Pattern
- slotごとの決定済み入力
- Pattern宣言と一致する一回の実assemble出力
- 固定返却物
- 循環がなく世代が一致するProducer Graph
- 各段を所有する形成済みAAC/NeoECO設備

Processing Patternは境界です。GTCEu、Mekanism、液体、Chemicalは本来の機械で
処理され、下流作業台クラフトは実際の機械出力が戻るまで待ちます。

## BigInteger境界

ACOは任意連携用の版付きHost APIを提供します。通常AE2 CPUを勝手に
BigInteger化しません。

- 正確に収まる間は`long`
- overflowした計算だけ`BigInteger`
- 実装上限は`10^16384 - 1`
- NBTは10進文字列ではなく正規byte array
- `long`しか受け取れないAE2 APIに限り表示・互換Facadeを`Long.MAX_VALUE`へ飽和
- 在庫、材料不足、Task進捗、Escrow、ReceiptはFacadeから逆算しない
- 完成品1個分だけで中間要求が`long`を超える場合、対応AQE HostではExact Vector
  専用親Jobとして保持する。通常AE2の子Windowへ縮小せず、適合する物理Executorが
  なければ安全に待機する

AQEは任意のHost連携、AACは任意の物理Executorです。ACO本体の必須依存では
ありません。

## 安全規則

ACOは次を行いません。

- Planから最終成果物を直接生成
- 注文個数ぶんJava処理を反復
- 正確な在庫・Task会計を暗黙に`long`へクランプ
- 成否不明な外部変更を再実行
- 入力受理後に通常経路へFallback
- GTCEu/Mekanismのレシピ成立条件を書き換え
- レシピやQuantum Computer構造ルールを変更
- Bukkit、Paper、Spigot、Arclight APIを使用

## 主な設定

```toml
[exactVectorCrafting]
enabled = true
enableAqeBigIntegerParents = true
maximumPatternNodes = 1024
maximumUniqueInputKeys = 128
maximumUniqueOutputKeys = 128
maximumStartsPerGridPerTick = 1
maximumActiveStagesPerGridPerTick = 256
maximumActiveTransactionsPerGrid = 4
gridTimeBudgetMillis = 2
logVectorDiagnostics = false
```

`exactVectorCrafting`という節名はConfig移行のため維持しています。実装は本項の
物理クラフトツリーであり、削除済み直接Vector Executorを再有効化する設定では
ありません。

詳細は[Configuration](docs/CONFIGURATION.md)、
[Feature ownership](docs/FEATURE_OWNERSHIP.md)、
[Implementation](docs/IMPLEMENTATION.md)、
[Testing](docs/TESTING.md)を参照してください。

## ビルド

```powershell
.\gradlew.bat clean build --no-daemon
```

生成JARは`build/libs`へ出力されます。

## ライセンス

ACO 1.6.xはNeoForge 1.21.1向けです。従来の1.5.x成果物はForge 1.20.1
向けとして残り、この移植で上書きされません。

`LGPL-3.0-only`

ACO固有の問題はまず本プロジェクトへ報告してください。ACOなしで再現していない
問題をAE2や依存MODの作者へ直接報告しないでください。
