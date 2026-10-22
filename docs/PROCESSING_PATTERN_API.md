# BigInteger加工パターン連携API

Issue [#227](https://github.com/syarukasu/ae2-crafting-optimizer/issues/227) / API version 1 / **開発者向けプレリリース**

## 対応版

| 環境 | このAPIを含むACO | Java |
| --- | --- | --- |
| Forge 1.20.1 | `2.0.0-rc.7-beta.2-processing.1` | 17 |
| NeoForge 1.21.1 | `2.0.0-rc.5-processing.1` | 21 |

両版の追加APIは同じ形です。JARはMinecraft・ローダー別です。混用しないでください。
旧版にはこのインターフェースがないため、連携コードを旧ACOでロードしないでください。
加工機MODの開発環境・テスト環境の両方に、対応するACOとAE2を入れてください。
ACOのAPIクラスを加工機MODへコピー・shade・Jar-in-Jar同梱するのではなく、
対応版のACOを依存MODとして読み込みます。任意連携なら連携クラスのロードも分離します。

## どのAPIを使うか

| 用途 | API |
| --- | --- |
| long数量のバッチ契約 | `api.batch.v2`。今回のBigInteger加工依頼とは別 |
| 数量の符号化・共有会計 | `api.contract`。これだけでは加工機へ依頼は届かない |
| 従来の作業台クラフト | `CraftingTableBatchTarget / CraftingTableBatchRequest`。既存契約は変更なし |
| **加工パターンのBigInteger依頼** | **`api.processing.ProcessingPatternBatchTarget / ProcessingPatternBatchRequest`** |
| CPU側の計画・実行連携 | 既存の `api.big.BigCraftingPhysicalExecution` 等 |

**加工機作者が実装するのは新しい `ProcessingPatternBatchTarget` です。**
作業台用インターフェースを加工機向けに読み替える必要はありません。
ACOを更新するだけで、未対応の加工機が巨大注文を処理できるようになるわけではありません。

## 接続手順

1. 加工機の **BlockEntity** に `ProcessingPatternBatchTarget` を実装します。
2. AE2 Pattern Providerの出力方向に隣接させます。
   Provider自身が処理する設備なら、既存の
   `ProviderOwnedPatternBatchTarget.aco$getProviderOwnedBatchTarget()` で実体を公開します。
   同じLevelに存在し、ロード済みのBlockEntityが必要です。
3. 通常どおり加工パターンをProviderへ登録します。独自の全ワールド検索や
   `pushPattern` の差し替えは不要です。
4. BigInteger計画・容量・物理実行に対応したCPU/利用側から注文します。
   ACOは所有権取得前に各工程の対応先を確認し、加工依頼をこのAPIへ渡します。
   **通常のAE2 CPUやlong専用ストレージまで、このAPIだけでBigInteger化するわけではありません。**
5. 加工機側で実レシピ、触媒、エネルギー、進捗を扱い、検証済み出力をReceiptで返します。

ACOはパターンから設備固有の上下スロットや省略された触媒を推測しません。
刻印機などの曖昧なパターンは、設備側の触媒・設定・ネイティブレシピで確定できる場合のみ受理してください。

## 実装する7メソッド

すべてサーバースレッドの呼出しを想定します。巨大注文の全処理をコールバック中に回して
サーバースレッドを占有せず、受理と進捗取得は短時間で返します。

| メソッド | 必須の動作 |
| --- | --- |
| `aco$supportsProcessingPattern(pattern)` | 読み取り専用の対応可否。予約・消費・処理開始は禁止 |
| `aco$acceptProcessingPatternBatch(request)` | 全量処理可能性を検証し、仕事を所有してtrue。falseなら所有・副作用なし |
| `aco$ownsProcessingPatternBatch(id, digest)` | **IDとdigestの両方**で所有を照合。同じIDの別内容を同一仕事としない |
| `aco$processingPatternBatchSnapshot(id, digest)` | 保存済みの実進捗・状態・実出力を返す。不明な仕事は `Optional.empty()` |
| `aco$acknowledgeProcessingPatternBatch(id, digest)` | 出力を保持したまま `ACKNOWLEDGED` へ。重複呼出しでも再製造しない |
| `aco$forgetProcessingPatternBatch(id, digest)` | 承認済み仕事の解放。未完了・未承認の仕事は破棄しない |
| `aco$cancelProcessingPatternBatch(id, digest)` | 全入力の返却が安全な取消だけtrue。取消できない仕事はfalse |

同一依頼の再送、acknowledge、forget、cancelは冪等にしてください。
受理済みの依頼は後から対応可否が変わっても所有を失わせず、完了・取消まで追跡します。
別digestや未知のIDに対して、実行・取消成功を推測して返してはいけません。

## Requestの数量

```java
import com.syaru.ae2craftingoptimizer.api.processing.ProcessingPatternBatchRequest;

// Called inside your ProcessingPatternBatchTarget implementation:
BigInteger times = request.executions();                  // number of recipe executions
Map<AEKey, BigInteger> inputs = request.aggregateInputTotals();
Map<AEKey, BigInteger> expected = request.aggregateExpectedOutputs();
IPatternDetails pattern = request.pattern();
UUID id = request.transactionId();
String digest = request.payloadDigest();
```

上記は読取り例で、仕事を受理・実行する完成コードではありません。

- `executions()` は製造回数です。完成品数とは限りません。
- `aggregateInputTotals()` は全回数分の入力総量です。
- `aggregateExpectedOutputs()` は全出力・返却品を含む**予定総量**です。実出力ではありません。
- `aggregateSlotInputs()` は入力枠別の総量です。
- `inputsPerExecution()` / `outputsPerExecution()` / `remainingPerExecution()` は1回分の係数です。
  係数がlongでも、総量はBigIntegerのままです。
- `ownerTransactionId()` / `craftingJobId()` / `stageIndex()` は親取引・注文・工程の識別に使えます。
- アイテムIDだけでなく完全なAEKeyを保持します。1.20.1はNBT、1.21.1はデータコンポーネントも区別します。
  液体などもAEKeyの種類を消さずに扱います。

例えば1回で入力3個・出力2個、依頼回数が `9223372036854775808` なら、
入力 `27670116110564327424`、出力 `18446744073709551616` です。
`longValue()`、`Long.MAX_VALUE` への切詰め、double経由の計算は禁止です。
機械内部で有限サイズに分ける場合でも、親の残量・消費・出力会計はBigIntegerで保持します。

## 二重消費・二重出力を防ぐ

**入力総量はACOのEscrowで予約・保持済みです。**

- 加工機が同じ入力をMEからもう一度取り出してはいけません。
- 通常の `pushPattern` で入力が重ねて配送される前提にしないでください。
- 加工機は受理した仕事に対して実処理を行いますが、出力をMEや外部在庫へ独自に搬出しません。
- 出力はReceiptを通してACOへ引き渡します。ACOが数量を照合して一度だけ会計します。
- 予定値をコピーして「完成」にする実装は禁止です。既存機械に加工した証拠・実行結果が必要です。

## 完了通知

型名は既存の `CraftingTableBatchSnapshot` を共有しています。加工にも使うReceipt値型です。

```java
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;

// Illustrative only: nativeJob must be your persisted, actually executed job.
return Optional.of(new CraftingTableBatchSnapshot(
        nativeJob.transactionId(),
        nativeJob.payloadDigest(),
        CraftingTableBatchSnapshot.State.OUTPUT_READY,
        1, 1,
        nativeJob.verifiedActualOutputs(),  // Map<AEKey, BigInteger>, NOT planned outputs
        "complete"));
```

`nativeJob` はACOが用意するクラスではありません。加工機側の保存済み仕事を表す疑似コードです。

| 状態 | Receiptの扱い |
| --- | --- |
| `RUNNING` | 途中。出力会計なし。出力Mapは空にする |
| `OUTPUT_READY` | 全依頼を実行済み。全キー・全数量の検証済み実出力を提示 |
| `ACKNOWLEDGED` | ACOが承認済み。同じ実出力Receiptをforgetまで保持 |
| `CANCELLED` | 安全な取消を確認済み。再送にも同じ取消結果を返せるよう識別を保持 |
| `QUARANTINED` | 不整合や結果不明。正常完了・返金に読み替えない |

`progress / maximumProgress` はintの表示用値です。最大値は正数、進捗は0以上最大値以下。
巨大な製造回数をここへキャストする必要はありません。数量会計とは分けてください。

ACOは実出力のMapが予定と一致するか確認してから会計します。
出力不足・余分な出力・別NBT/コンポーネント・別digestは成功扱いになりません。
全量の出力が確定する前に部分出力を `OUTPUT_READY` として返す用途は未対応です。

## 取消と保存

- cancelでtrueを返すのは、その仕事から今後出力が流出せず、
  入力を全量返しても二重取得にならないことを保証できる場合だけです。
- 取り消せない途中加工、不可逆な触媒の変更、完成済み出力を「未使用入力」として返さないでください。
  完了Receiptまで進めるか、結果不明として隔離します。
- 受理済み仕事のID・digest・BigInteger数量・完全なキー・ネイティブ処理状態・実出力・承認状態を保存します。
- 入力を予約してから受理するまで、完了、acknowledge、forget、cancelの各境界で、
  再ロードしても二重実行しないようにしてください。単にBlockEntityをdirtyにするだけでは
  複数MOD間のディスク書込みの原子性は保証されません。
- 受理済み仕事の所有・Receiptが失われたら、ACOは加工を隔離します。
  勝手な返金、別機械への再送、long経路へのfallbackは行いません。
- チャンク未ロード時は待機し、強制ロードしません。

## 対応範囲と検証状態

対応するのは、入力と全出力が正確に固定できる処理です。複数出力、
液体、NBT/コンポーネント付きキー、作業台と加工の混合工程を扱います。
数量は既存の設定済みBigInteger上限内で正確に保持します。

現在の保存契約では選択入力は1～9枠です。拡張入力枠、動的に変わるバッチ結果、
循環する物理工程、パターンに現れず設備側でも確定できない触媒は今回の追加対象外です。
未対応の機械・工程をlongに丸めて受理しません。
搬入出速度、CPU容量、機械の加工速度を変更するAPIではありません。

ACO本体の取引・数量検証・Receipt・保存Codecを通す契約テストで、
`Long.MAX_VALUE + 1` 以上の加工依頼、完了、取消、副産物、再ロードを確認しています。
ワールド・ストレージ・加工機の境界はテスト用実装です。
**OAE/AQE等を含む実機、同時注文、MOD間のクラッシュ復旧は未受入です。**
このプレリリースを安定版や、未改造加工機の対応済み証明として扱わないでください。
