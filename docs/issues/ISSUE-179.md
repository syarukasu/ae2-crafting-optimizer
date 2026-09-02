# ACO 2.0.0 Parallel Crafting Planner

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/179
- 状態: Ready
- 対象版: 2.0.0
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 基準: v1.5.33、#167、#168、#169、#176、#177、#178

## 目的

AE2のクラフト結果とACO 1.5.33のExact Count契約を変更せず、一件の自動クラフト計算に含まれるGraph構築と数量伝播を、ACO専用の固定4 Planner Threadで決定論的に共同実行する。

4本のThreadを4件の別注文へ割り当てる設計ではない。一つの`ParallelPlanSession`だけをactiveにし、後続注文は有限FIFOへ置く。

## 基準

- Forge 1.20.1: `test/issue176-bigint-position-boundaries-1.20.1`（PR #177）のHEADを開始点とする。
- NeoForge 1.21.1: `test/issue176-bigint-position-boundaries-1.21.1`（PR #178）のHEADを開始点とする。
- v1.5.33 / PR #168・#169のSnapshot、世代、cache不変条件を維持する。
- Issue #176の入力・中間・出力・bytes・Pattern回数の位置独立BigInteger境界を維持する。

## 現行経路で確認した制約

1. `CraftingService.beginCraftingCalculation`は`CraftingCalculation`をServer Threadで構築し、AE2のcached poolへ一件単位でsubmitする。
2. `Ae2ImmutablePlanningGraphCache.captureRoot`は発注時にrootから`ICraftingService#getCraftingFor`を同期走査する。
3. `RootCapture.compile`と`CompiledRootProgram.compile`はcold graphを単一workerで構築する。
4. `CompiledRootProgram.planLong` / `planBig`は親から子のトポロジカル順を単一workerで一巡する。
5. wide判定後のexact在庫取得は、planner workerがServer executorへ処理を投げて`Future#get`で待つ。

## 不変条件

- AE2の候補集合、候補順、具体入力選択、Pattern回数、使用在庫、余剰、Missing、simulation、CRAFT_LESS、CPU bytesを変えない。
- workerはlive `Level`、`IGrid`、Crafting/Storage service、BlockEntity、Provider、RecipeManager、在庫へ触れない。
- Server ThreadはPlanner完了を`get`、`join`、busy waitしない。
- graph nodeはsingle-flightで一度だけ展開する。
- shared intermediateの需要は全親から集約してから在庫を一度だけ消費する。
- Thread完了順を候補選択、Node ID、Map順へ使わない。
- checked longが一箇所でもoverflowしたらGraphを再構築せず、同じSnapshot上で数量passだけBigIntegerとして最初から実行する。
- wide計画をAE2標準long経路へfallbackしない。
- 公開Exact Count / BigInteger / BigCapacity / Pattern Batch APIの既存バイナリシグネチャを変更しない。

## Thread model

- ACO専用`ForkJoinPool`、parallelism固定4。
- Thread名は`ACO Tree Planner #1`から`#4`。
- active Sessionは原則一件。後続は有限FIFO。
- queue満杯時にServer ThreadでPlannerを実行しない。
- NodeごとのFuture生成、worker間の`Future#get` / `join`、common pool、`parallelStream`、`CallerRunsPolicy`は禁止する。

## 導入段階

1. 設計、不変条件、pure model、serial oracle、Golden fixture。
2. 世代付きimmutable Pattern indexとSnapshot publication。
3. fixed-4 parallel graph builder、single-flight node、cycle検査、canonical node order。
4. edge-local contributionを使うparallel long amount passとBigInteger再実行。
5. bounded queue、subscriber cancel、AE2 calculation workerでのfinalize、materialize。
6. Shadow比較、診断、benchmark、loader parity、GameTest fixture。

各段階はForgeとNeoForgeで別のstacked Draft PRにし、既存PR #168、#169、#177、#178を上書きしない。

## Fail closed / fallback

- 通常long計画は、ACOが所有権を取る前で、Snapshotまたは構造を証明できない場合だけAE2標準計算へ戻す。
- wide計画は既存serial BigInteger経路を維持する。正確な計算を継続できなければ具体的なfailure codeで失敗し、longへ落とさない。
- timeout、cancel、stale、内部不整合をMissingや`CPU_TOO_SMALL`へ読み替えない。
- Parallelとserial/AE2の結果が一致しない場合、Parallel結果を採用せず有限診断を残す。

## 必須証拠

- fixed poolが4本を超えず、一件の広いTreeで複数workerを使用する。
- 深い一本道では余計な分割をせず、並列性が低いことを記録する。
- single-flight、shared inventory一度消費、canonical result、cancel、queue満杯、shutdownでdeadlock/leakがない。
- longとBigIntegerの各位置境界がserial oracleと一致する。
- Forge 1.20.1 / NeoForge 1.21.1の結果と公開API signatureが一致する。
- clean build、JUnit、回帰manifest、`git diff --check`。
- GameTest fixtureを整備する。実ゲーム起動はこのIssueの自動検証範囲外とする。

## 非目標

- 外部CPUの実行、電力、進捗、Receipt、完了、GUI、構造をACOが所有すること。
- レシピ、クラフト成立条件、Pattern優先順位を変更すること。
- 4件の注文を4workerへ割り当てるthroughput最適化。
- 1.5.33 Exact Count APIの再設計。

## 完了条件

AE2の意味論と1.5.33のExact Count契約を維持し、一つの自動クラフトツリーの構築と正確な数量計算を、固定4本の専用Planner Threadで決定論的に並列実行できる。検証できない構造では既存の正しい経路を選び、途中結果を成功扱いしない。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] `docs/CLASS_RESPONSIBILITIES.md`で既存Planner、Snapshot、世代、APIの責務を確認した
- [x] Issue #167 / #176とPR #168 / #169 / #177 / #178を確認した
- [x] v1.5.33と両`mc/*`のHEADおよびstacked branch関係を確認した
- [x] 現行AE2 15.4.10の`CraftingService`、`CraftingCalculation`、`CraftingTreeNode`を確認した
- [x] 所有権、fallback、Exact Count互換、Thread境界を確定した
- [x] 既存Draft PRを変更しない新規stacked branchを用意した

## 正常時の状態遷移

1. Server Threadがrevisionを固定してPattern/Inventory snapshotをpinする。
2. 一件のsessionを有限FIFOへ渡し、呼出元へFutureを直ちに返す。
3. 固定4本のPlanner Threadが同じsessionのGraphをsingle-flightで展開する。
4. canonical Graphのcycle検査後、frontier単位で数量を伝播する。
5. overflow時は同じGraph上の数量passだけをBigIntegerで再実行する。
6. pure `PlanBlueprint`をAE2 calculation workerへ戻し、固定済みbindingとrevisionを再検証する。
7. 一致した時だけ同worker上でCraftingPlanと1.5.33 sidecarをmaterializeする。live AE2 getterやworld状態は参照しない。

## 失敗時の状態遷移

- snapshot、binding、revisionが一致しない結果はmaterializeしない。
- 通常longかつ所有権取得前だけAE2標準経路へ辞退する。
- wide計画はAE2 long経路へ落とさず、既存serial exact経路または明示failureを使う。
- queue満杯、cancel、shutdown、内部invariant違反をMissingやCPU不足へ変換しない。

## 試験計画

- pure planner: chain、wide tree、diamond、shared intermediate、cycle、missing、checked-long overflow、位置独立BigInteger。
- concurrency: single-flight、完了順撹乱1000回、固定4 thread、queue full、cancel、shutdown、参照解放。
- parity: serial oracle、Forge/NeoForge fixture、1.5.33公開API signature。
- AE2境界: immutable capture、stale finalize拒否、通常long辞退、wide serial exact維持。
- performance: server受付、snapshot pin、queue wait、graph/amount wall time、worker利用数、重複展開防止数、allocation。
- build: 両版`clean build --no-build-cache`、回帰manifest、`git diff --check`。
- GameTest: fixtureを整備する。Minecraft起動は行わない。

詳細設計は`docs/PARALLEL_PLANNER_2_0.md`を正本とする。
