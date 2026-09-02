# ACO 2.0.0 Parallel Crafting Planner

この文書はIssue #179の実装境界を定める設計正本です。ACO 1.5.33のExact Count契約とAE2のクラフト意味論を変更せず、一件の計算内だけを最大4本で並列化します。

## 目標と非目標

### 目標

- 一つの`ParallelPlanSession`が固定4本の専用Planner Threadを共同利用する。
- immutable snapshot上でGraph構築と数量伝播を実行する。
- shared intermediateを一度だけ展開し、需要集約後に在庫を一度だけ消費する。
- Thread完了順によらないcanonicalな結果を返す。
- checked long overflow時は、同じGraphとSnapshotで数量passだけをBigIntegerとして再実行する。
- Server Threadはcaptureとfinalizeだけを所有し、Planner完了を待たない。

### 非目標

- 四件の別注文を四本へ割り当てるthroughput scheduler。
- AQE、AAC、InsaneAEなど外部CPUの実行、電力、進捗、Receipt、完了の所有。
- AE2のPattern候補、候補順、入力選択、CRAFT_LESS、CPU容量判定の変更。
- ACO 1.5.33公開Exact Count APIの再設計。
- 証明できないPattern構造の近似的な高速化。

## Thread ownership

| 状態または操作 | Server Thread | Planner Thread |
|---|---:|---:|
| live Grid、Level、Storage、Provider、RecipeManager参照 | 所有 | 禁止 |
| Pattern indexのcaptureとpublication | 所有 | immutable値だけ参照 |
| Inventory snapshotのcapture | 所有 | immutable値だけ参照 |
| Graph構築、cycle検査、数量伝播 | 禁止 | 所有 |
| live Pattern bindingの再解決 | 所有 | 禁止 |
| CraftingPlanとsidecarのmaterialize | 所有 | 禁止 |
| job提出、在庫mutation、CPU予約 | AE2または外部所有者 | 禁止 |

## Session lifecycle

```text
CAPTURED -> QUEUED -> GRAPH_BUILDING -> AMOUNT_LONG
                                      -> AMOUNT_BIG
                                      -> BLUEPRINT_READY
                                      -> FINALIZING -> COMPLETED

任意の未完了状態 -> CANCELLED
任意の内部不整合 -> FAILED
revision不一致   -> STALE
```

- ACO全体でactive sessionは一件だけとする。
- 後続sessionは有限FIFOへ入れる。
- queue満杯時にServer Thread上でPlannerを実行しない。
- 通常long要求はACOが所有権を得る前ならAE2経路へ辞退できる。
- wide要求はAE2 long経路へ戻さず、既存serial exact経路を利用するか正確な理由で失敗する。
- 最後のsubscriberが離れた時だけsessionを協調cancelできる。

## Dedicated pool

- ACO専用`ForkJoinPool`のparallelismは定数`4`。
- Thread名は`ACO Tree Planner #1`から`#4`。
- `commonPool`、`parallelStream`、`CallerRunsPolicy`、要求ごとのThread生成は禁止する。
- poolへ投入するtop-level taskはactive sessionの一件だけである。
- session内では固定数のworker loopまたはchunk taskを使い、Node数に比例したFutureを生成しない。
- worker同士は`Future#get`または`join`で待ち合わない。

## Snapshot boundary

Server Threadは次を一つの`RevisionVector`に固定してからsessionを受け付けます。

- Pattern generation
- Recipe generation
- Storage revision
- Config revision
- runtime identity

captureは`revision before -> immutable capture -> revision after`の順で行い、前後が一致したものだけをpublishします。古いsnapshotへ新しいrevisionを付け直しません。Planner Threadへ渡すのは次だけです。

- `PatternDescriptor`とAE2候補順を保持した配列
- `InventorySnapshot`または`ExactInventorySnapshot`
- `RevisionVector`
- `KeyToken`、`PatternToken`
- 純粋な数量配列とGraph

live `IPatternDetails`はServer Thread側の`PatternBinding`にだけ残し、finalize時にtoken、revision、fingerprintを再検証します。

## Immutable pattern index

Pattern Providerの追加、削除、refresh、recipe reloadでindexをdirtyにします。安全なServer Thread境界で一度だけ再構築し、immutable snapshotとしてatomic publishします。注文開始時にlive `ICraftingService#getCraftingFor`を再帰探索しません。

候補配列はAE2が返した集合と順序をそのまま保存します。ACO独自のpruning、`distinct`追加、priority変更、在庫順、worker完了順による並べ替えを行いません。

初期のauthoritative対象は、次をsnapshot時に証明できる`STATIC_STRICT`だけです。

- 一意で固定された入力domain
- 動的getterなし
- remaining itemなし
- fuzzy/substitutionなし
- runtimeで変化する外部入力なし

それ以外は`STATIC_AE2_ONLY`、`DYNAMIC_AE2_ONLY`、`UNSUPPORTED`として分類し、通常longは所有権取得前にAE2へ辞退し、wideは既存serial exact経路を維持します。

## Parallel graph build

- `KeyToken`ごとにNode tableのentryを一つだけ作る。
- Node状態は`NEW`、`EXPANDING`、`EXPANDED`、`FAILED`を取る。
- 最初にclaimしたworkerだけがimmutable descriptorからNodeを展開する。
- 他workerは既存Nodeへcanonical edge descriptorを登録し、展開を重複させない。
- failureは空Nodeへ変換せず、原因付きでsessionを失敗させる。
- 探索中の内部indexを最終Node IDに使わない。
- 探索後、root、AE2候補順、input slot順、alternative順、tokenの安定表現からcanonical Node IDを確定する。
- canonical Graphへcycle検査を行い、cycle pathを診断へ残す。

## Parallel amount propagation

数量passはGraphのtopological frontier単位で進めます。同じfrontier内のNodeはchunk化して最大4 workerで処理します。

各parentは自分が所有するedge contribution slotだけへ書きます。child需要は全parent contributionが確定した次のfrontierで、canonical edge順に一度だけ合計します。その後に次を一度だけ行います。

1. total demandの確定
2. shared inventoryの消費
3. remaining demandの確定
4. Pattern実行回数とproduced/excessの計算
5. child input demandの生成
6. Missingの確定

frontier間には明示barrierを置きますが、workerが別workerのFutureを待つ構造にはしません。各workerは範囲を処理してcoordinatorへ完了を通知し、最後のworkerが次frontierをenqueueします。

## Determinism

- Pattern選択はcapture済みAE2候補順だけで行う。
- alternative選択は既存serial oracleと同じ比較規則を使う。
- contributionの加算順はcanonical edge順に固定する。
- 結果Mapはcanonical Node/Pattern順から生成する。
- worker完了順、HashMap iteration順、steal順を結果へ反映しない。
- shadow期間はparallel `PlanBlueprint`をserial oracleと全field比較し、不一致ならparallel結果を採用しない。

## long to BigInteger

通常passは`Math.addExact`、`Math.multiplyExact`、正確なceil divisionを使います。一箇所でもoverflowした場合はsessionのlong amount taskを協調cancelし、Graph、Snapshot、候補順を保持したままBigInteger amount passを最初から実行します。

再実行対象は数量passだけです。Pattern capture、Graph build、cycle検査を繰り返しません。BigInteger passには1.5.33の`ExactCountLimits`を適用し、入力、在庫、中間需要、Pattern回数、出力、Missing、bytesのどの位置も飽和または切り捨てません。

## Finalize

Plannerはlive AE2型を含まない`PlanBlueprint`を返します。Server executorへfinalizeをscheduleし、次を再検証します。

1. runtime identity
2. Pattern、recipe、config revision
3. Pattern tokenからlive bindingへの解決
4. fingerprint一致
5. cancel状態

一致した場合だけ`CraftingPlan`を生成し、BigInteger/BigCapacity sidecarを付けてFutureを完了します。在庫はAE2の計算開始時snapshot意味論を維持し、提出時の既存再検証は変更しません。

## Failure and fallback table

| 状態 | 通常long | wide / BigInteger |
|---|---|---|
| index未準備、非対応Pattern | 所有権取得前にAE2へ辞退 | 既存serial exact経路 |
| stale capture | AE2へ辞退または再要求 | 明示`STALE_SNAPSHOT` |
| queue満杯 | AE2へ辞退 | 明示`QUEUE_FULL` |
| cycle/複数Producer等でparallel証明不能 | AE2へ辞退 | 既存serial exact経路 |
| cancel | `CANCELLED` | `CANCELLED` |
| 内部invariant違反 | `INTERNAL_PLANNER_FAILURE` | `INTERNAL_PLANNER_FAILURE` |
| parallel/serial mismatch | serial/AE2の正しい結果 | serial exactの正しい結果 |

内部異常を`CPU_TOO_SMALL`、`MISSING_INGREDIENT`、`NO_COMPILED_PROGRAM`へ読み替えません。

## Bounded resources

- active session: 1
- queued session: 設定ではなく実装定数として有限値を定め、試験で上限を固定する
- planner threads: 4
- worker loop: 最大4
- task数: worker数とfrontier chunk数に比例し、Node数ごとのFutureは作らない
- Graph/root cache: 既存#167の重み付き上限を維持
- session終了時: Node table、Snapshot pin、subscriber、Future参照をactive tableから除去

## API compatibility

次の1.5.33公開境界は既存binary signatureを維持します。

- `com.syaru.ae2craftingoptimizer.api.contract`
- `ExactCountLimits`
- `CanonicalBigIntegerCodec`
- `ExactStorageAmountProvider`
- `BigCraftingEngineApi`
- BigInteger/BigCapacity plan inspection
- `Ae2CraftingPlanSidecars`
- Pattern Batch V2、crafting-table、vector API、capability handshake

Parallel Planner固有型は公開APIにせず、`engine.parallel`内部へ置きます。

## Validation gates

- pure serial oracleとparallel blueprintの全field parity
- 同一fixtureを異なるworker delay/steal順で1000回実行して一致
- single-flight展開回数、shared inventory一度消費、固定4 thread、common pool不使用
- cancel、queue full、shutdownでdeadlock/leakなし
- Issue #176の位置独立BigInteger境界
- Forge/NeoForge fixture parityと1.5.33 API signature互換
- benchmarkは絶対時間をCI gateにせず、worker利用数、展開重複0、task数上限、live access 0をgateにする
- `clean build --no-build-cache`、JUnit、回帰manifest、`git diff --check`

GameTest fixtureは実AE2境界を検証するために整備します。Minecraftの起動実行はこの作業では行いません。
