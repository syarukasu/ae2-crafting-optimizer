# Issue #179: ACO 2.0 Planning Boundary Cleanup

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/179
- 状態: Ready
- 対象版: 2.0.0
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1

## 2026-09-15 第二段階: 通常recipeのTagged照合再利用（移植保留）

- 状態: Draft（実JARの意味差が判明したため、第二段階の製品差分は撤去）。
  Forge実環境の調査で通常Shaped/Shapelessが105件、Mekanism等の派生型が28件。
- ローカルAE2 19.2.17実JARのhasComponents()はstack.getComponents().isEmpty()を直接返す。
  getTestResult/setTestResultはその戻り値でcache可否を判定するため、Forgeと同じ
  tagged miss境界という前提が成立しない。8試験中5件がnull/未再利用で失敗した。
  上流の全バージョンに一般化せず、この依存JARについての確認結果として記録する。
  この挙動をACOで勝手に置き換えず、第二段階のMixin/Memo/試験追加は未適用へ戻す。
- 撤去後のclean buildに成功し、キャッシュを使わず全528 testsと回帰台帳を再実行して
  失敗0・エラー0・skip 0。未配置の再build JAR SHA-256は
  `F5C6145F6F171F541B8448372490B1D1A6641231E868CE14EC297E7AD29BC27D`。
- 通常Shaped/Shapelessと通常Ingredientの具象型完全一致だけを対象とする。
  NBTや充電等を引継ぐMekanism recipe、特殊Ingredient、未知の派生型は元処理へ委譲する。
- AECraftingPatternのgetTestResult/setTestResult境界で、既存cacheが扱わない
  components付きAEItemKeyの結果だけを計算ローカルMemoへ保存する。
  初回評価、Pattern identity、slot、完全なAEKey、true/false、世代失効、finish破棄を維持する。
- 所有クラスはCraftingCalculationMemoと薄いCraftingPatternTaggedValidationMixin。
  数量、在庫、CPU実行、候補順序、返却物は変更しない。
- 実AE2 19.2.17のprivate methodとCraftingRecipe fieldをjavapで確認済み。
- NeoForge Ingredientはfinalで内部にICustomIngredientを保持するため、具象型一致に加えて
  isCustom()も検査して除外する。Forgeの派生型検査だけを移植してはならない。
- 先行試験: Forgeは未実装時に4件失敗。両版でNBT/components、slot、Pattern、世代失効、
  計算外、別Thread、動的recipe除外、元cache保持を検証する。
- Forgeの至高制御回路は第一段階でTPS維持を観測したが計画完了は未達。
  NeoForgeの実環境試験は未実施。ローカル試験だけで高速化達成やリリース可とはしない。

## 2026-09-15 素材候補探索のcheckpointと既存索引再利用

- 状態: Implemented。利用者承認済みのForge修正と同じ意味の変更を同期した。
- Forge実環境では至高制御回路100個が複数Producer/代替素材のため標準経路へ戻り、
  cacheFuzzy/getValidItemTemplatesで計算を続けたままserverがsimulateForで待機した。
  180秒tickのWatchdog停止を実測。NeoForge実環境で同じ停止を実測したという意味ではない。
- 既存run/finishのMemoスコープ内だけ、計算専用の親Snapshot走査の各候補、素材列挙、
  IInput.isValid直前からAE2 handlePausingへ接続する。incTimeを検査可能値へ設定し、
  AE2の予算、monitor、detached世代再検証を維持する。割込み後は次tickを待たない。
- KeyCounterの既存identity索引だけを計算中に再利用し、毎回の耐久値照会を省く。
  初回の索引選択、削除後の再作成、components/NBT差、数量は元のAE2処理のままとする。
- 所有: Memoが計算スコープ、ThreadAccess/Diagnosticsがpause接続、各Mixinは入口だけ。
- 禁止: 候補の削除/並替え、レシピ変更、動的recipeの一律cache、testFrame更新途中のpause、
  live World/在庫の無検証off-thread化、Watchdog無効化、数量正本の変更。
- Forgeでfail-firstを確認したcheckpoint試験、既存索引の再利用試験、実AE2 bytecodeの
  記述子と呼出数、全JUnit/build/回帰マニフェストを検証する。
- NeoForgeへはローカル同期とビルドのみ。実ゲーム検証・PR・リリースは別の未実施事項。
- Forge再試験では20 TPS維持とcheckpointの実動作を確認したが、100個計画は中断、
  1個でも未完了。計画高速化の達成とは判定しない。NeoForge側もIssue全体は未解決。
- NeoForge全528 tests、失敗0・エラー0・skip 0。clean buildと回帰マニフェストに成功。
  テスト用設定を元の状態へ復元する変更後も、全testとマニフェストを再実行して成功。
- JAR SHA-256: `49154F08429C0472C94E84D8512F87796D59A2FDDA2A81B6D051D3399916CE00`。
- Forgeは508 tests通過、同一JARをserver/clientへ配置して起動と参加を確認。
  至高制御回路100個の計画完了や高速化率は、利用者の再発注を待って実測する。

## 2026-09-15 索引公開の過剰負荷

- 状態: Verified（局所修正と両版回帰build。実サーバーの改善率は未測定）
- 証拠: Forge Server threadの二回のstackでcapturePublishedのNBT文字列ソートを観測。
  pattern_index_publishedでは2,542キー/1,468 Patternの公開に最大1,376.76msを記録した。
- 原因: addNode/removeNodeはProviderを持たないノードでもglobal世代を進め、全公開索引を失効させる。
  キー比較は比較のたびにtoTagGeneric().toString()を呼び、同じNBTを繰り返し生成する。
- 方針: クラフトProviderに関係しないadd/removeでは世代・索引を変更しない。
  capture内のソートキーはAEKeyごとに一度だけ生成し、既存の辞書順と候補順を保持する。
- 不変条件: Provider/recipe/config変更の失効、同tick発注、Snapshot世代照合は維持。
  新しい長期cache、再試行、世代付替え、live Patternのoff-threadアクセスは追加しない。
- 受け入れ: 関連既存JUnit、通常ノードとProviderの世代差、ソート順とシリアライズ回数を確認。
  main thread時間の改善率は実測なしに断定しない。
- 検証: 非Providerのadd/remove全入口が世代を変更せず、Levelにも触れないことを既存JUnitへ追加。
  Providerの従来通知経路は維持。ソートは従来と同じNBT文字列の比較であり、
  キーのSetを一度走査して文字列を固定することをコードレビューで確認した。
  シリアライズは比較回数依存からキー数依存になり、capture終了後は保持しない。
  Forge 499 / NeoForge 519 tests、失敗0・エラー0。両版clean buildと回帰マニフェスト検証に成功。
- 制限: 実Providerの変更は従来通りglobal世代を進める。Grid単位の世代再設計は今回行わない。

## 今回の追加実装: Snapshotで証明できるAE2標準計算もtickから切り離す

- 状態: Ready。対象はIssue #179の計算Thread境界であり、数量Plannerの再実装ではない。
- 目標: long / BigIntegerの既存正確計算に加え、複数Producerや共有中間素材のため
  Root Programを採用できない通常計算も、不変Pattern領域ではAE2 workerで続行する。
- AE2のNetworkCraftingSimulationStateは開始時の在庫を所有済み。以降のSimulation
  insert / extract / applyDiffは実storageではなく計算ローカル状態だけを更新する。
- Published Snapshot内の到達可能候補を全件検査する。未capture Pattern、動的Input、
  substitutionなど不変性を証明できない候補が一つでもあれば、従来の同期を維持する。
  候補数、順序、レシピ可否は変更しない。新しい数量計算器やPattern proxyは作らない。
- CraftingTreeNode.buildChildPatternsのlive Grid/ProviderアクセスだけはServerへ委譲する。
  返却物のcallbackもServerへ渡す。workerとServerが同時に計算ローカル状態を変更しない。
- 実際のPattern lookupへ渡されたServiceのruntime identityも照合する。
  Requesterが別Gridを返した場合、同じ世代番号だけで旧Snapshotの資格を流用しない。
- lookup前後と既存checkpointでPattern/recipe/config世代を確認する。変更時は計算を
  最初からretryせず、その場でAE2の元の同期へ戻して、同じ標準計算を続ける。
- Server停止では保留taskだけでなく登録中の計算workerへも割込みを通知する。
  復帰直前と停止の競合で、次tick待ちに取り残さない。AE2のfinishで登録を解除する。
- 証明済みPatternのmetadataはAE2自身の固定配列。外部MODのPattern getterを
  未検証のまま非同期化しない。外部MOD、CPU、実入出力、レシピ、mods配置は変更しない。
- wide計画を通常long計算へ戻す条件は増やさない。BigInteger正本、sidecar、
  Missing、bytes、候補順を維持する。
- 受け入れ: 関連既存JUnit、同じworkerでlongからBigIntegerへ昇格してもtickが待たない
  仮想注文、Snapshot適格性の主経路と不完全候補の辞退、両版clean buildと回帰台帳。
- 実サーバーの180秒停止原因全体、未知の動的Pattern、実ゲームTPS改善は別の未検証事項。

### 追加実装後のローカル検証

- Forge / Java 17: 117 suites、490 tests、失敗0、エラー0、skip 2。
- NeoForge / Java 21: 124 suites、508 tests、失敗0、エラー0、skip 0。
- 両版で`clean build verifyIssueRegressionManifest --no-build-cache --no-daemon`成功。
- 仮想注文は同じGraph・在庫を使い、通常注文10個と注文Long.MAX_VALUE個を実行した。
  後者の中間必要量は`2 * Long.MAX_VALUE`となり、既存PlannerがBigIntegerへ昇格する。
  中間Pattern回数と素材不足Long.MAX_VALUE個が正確であることを確認した。
- long passと昇格後のBigInteger passをそれぞれworker上で一時停止させても、
  実Mixinのwait処理を呼ぶ模擬tickが完了することを確認した。
- native経路の資格検査は複数Producerを同じ順序で保持し、依存先に未capture候補が
  あれば辞退する。server stopでは登録workerの割込みと保留taskの取消を確認した。
- 今回追加したThread境界と資格検査は両ローダーで同じ実装。既存のNBT版差は維持する。
- この試験はMinecraftを起動せず、pure Plannerと実Mixin callbackを組み合わせたもの。
  実AE2へMixinを適用した状態の発注・物理クラフト完走・TPS改善率の証拠ではない。
- 配布物SHA-256:
  - Forge `aco2.0.0_1.20.1.jar`: `0A19F22784CBD4408B3A25E595878A2D14BD62B1646A89E650EBA1E605C62250`
  - NeoForge `aco2.0.0_1.21.1.jar`: `03F7CFA7193A8C87C04EF6E59E6B0458FA945B2AAD4E64B2F5356E488A596A32`

## 問題

### 2026-09-13 再監査: worker実行とtick非待機の混同

`CraftingCalculation.run`は計算前に`registerCraftingSimulation`へ登録し、
Server Threadの`simulateFor`はworkerがpauseするまで`Object.wait`する。
したがって従来のcheckpoint接続は非同期実行ではあってもtick非占有ではない。
Watchdogはこの待機で180秒を記録した。ただしworkerの短いstackだけでは、
AE2標準経路のMapコピーが停止した根本原因までは確定できない。

今回の最小修正:

- `run -> computePlan`の間に外部MODの候補選択とpauseがあるため、tick登録を
  飛ばす初案は採用しない。既存の登録、候補選択、run/finallyを維持する。
- `computePlan`内のACO不変計算区間に入った時だけAE2 monitorへ返し、
  その区間中の`simulateFor`はworkerを待たない。
- exact在庫取得とPattern binding/sidecar確定はServer executorへ委譲する。
  切離し区間のworkerだけが結果を待ち、Server Threadはworkerを待たない。
- 返却または通常計画の辞退時にAE2 pauseへ復帰してから外側へ戻す。
  workerがlive AE2へ戻る時にはServerと同時実行しない。
- 通常AE2の可変状態を読むfallbackからwaitを外さない。今回のクラッシュの
  fallback側原因は未確定であり、この変更で全面解決と報告しない。
- cancelとserver停止時は保留中のServer taskを取消し、後からmaterializeしない。

受け入れ条件: 切離し区間のServer task待機と取消のJUnit、両版build、
既存long/BigInteger結果の回帰試験。数量演算、公開API、CPU実行、mods配置は変更しない。
以下の古いpause方針と検証値は旧実装の記録であり、今回の非待機の証拠ではない。

追加実装前のローカル検証結果:

- Forge / Java 17: 117 suites、488 tests、失敗0、エラー0、skip 2。
- NeoForge / Java 21: 124 suites、506 tests、失敗0、エラー0。
- 両版で`clean build verifyIssueRegressionManifest --no-build-cache`成功。
- `CraftingCalculationTickHandoffTest`は実Mixinのwait処理を呼び、
  不変計算中はtickが待たず、live経路では通知を待つことを確認する。
- `PlanningServerTasksTest`はServer所有threadでの実行、失敗原因の保持、
  stop後の待機解除と遅延materialize防止を確認する。
- 復帰後の最終世代検証を維持。公開Exact Count API、数量計算器、CPU実行は変更しない。
- 稼働中Server/Clientへの配置、GitHub更新は行っていない。
- AE2標準fallbackの180秒停止は依然未確定。Issue全体や実サーバーの完了判定はしない。

初期2.0実装は「Server ThreadのTPSを守る」という目的を、1注文を固定4 threadで分割する
要件として扱っていました。その結果、次の不要な構造が入りました。

- serial Root Programを構築した後にparallel Graphを再構築する二重計算
- node数1024以上だけを選ぶ推測閾値
- work stealing、frontier barrier、worker間spin
- serial oracleとは別の数量計算実装
- 純粋Plannerだけを測る合成ベンチによる過大な性能評価

これは高速化、軽量化、正確性のどれも実経路では証明しません。

## 2026-09-15: 工業用の複数出力計画 (Ready)

- 実測: 12:30:42、`emextras:supreme_quantum_control_circuit` x10 は
  `root program unavailable: MULTIPLE_OUTPUTS` で標準計算へ戻った。
- 現行コードは副産物を一律に拒否する。NBT付き素材照合の局所改善とは別の問題。
- 今回の実装範囲: 要求グラフへ戻らない副産物を持つ決定的Patternを配列経路で扱う。
  入力を全て探索した後、要求キー以外の出力が到達集合に含まれないことを確認する。
  元のPatternと全出力を保持し、生成予定量を初期在庫へ加えない。
- 副産物を別の枝で消費する場合は、独立出力の証明とは分ける。
  `COUPLED_OUTPUTS` と診断し、標準探索を継続する。この段階で工業計画全般の完成とはしない。
- 全出力の積と合計をchecked long / BigIntegerで検査する。副産物だけのoverflowも
  preflight・通常Planへの変換・子Jobのwindow判定で見落とさない。
- 作業台専用Exact経路へ複数出力を誤投入しない。CPU実行、Receipt、在庫、取消は変更しない。
- Forge/NeoForgeで同じ純粋計算変更を同期する。NBT Memoは以前記録した版ごとの差を維持。
- 先行試験: 独立副産物の受付、注文端数・一部在庫・欠品、主副の入替、再利用の拒否、
  副産物だけのlong超過、出力合計超過、最大bit幅、window幅、指紋差、取消。
- Ready確認: 所有クラス・既存試験・AE2 CraftingTreeNode/Processを読了。
  Issue #167の共有DAG/CPU bytes証明とexact input domainは維持する。
- 稼働中Server/Clientへの配置と実注文の短縮時間は未検証。

### 上記範囲のローカル検証 (2026-09-15)

- 状態: Implemented (local only)。Issue全体は未完了、runtime証拠はPENDING。
- 新規試験は変更前に10件中9件失敗を確認し、その後に実装した。
- 独立副産物12試験、strict topology採用試験、Forgeの実AEItemKeyによる耐久/NBT分離試験を追加。
- Forge: clean build + verifyIssueRegressionManifest、531 tests / 0 failures / 0 errors / 0 skipped。
- NeoForge: clean compile後にテストのResourceLocation API差を修正し、build + manifest成功。
  542 tests / 0 failures / 0 errors / 0 skipped。既存Forgeの8*2^60境界試験も同期。
- Forge candidate SHA-256: `EBEED7C17C1EB57C3B817FB07FBA899F6FDC5CD43826EF9CCDB64AEDB601BD79`。
- NeoForge candidate SHA-256: `CB663DF5C2CE00AB59048B64662C15EA4E259DB3BE18B5400356AFA863E5EB09`。
- 変更: CompiledRootProgram / RootProgramFailure / Ae2StrictCraftingTopology /
  WideArithmeticPreflight / Ae2AuthoritativeCraftingPlanner / Ae2BigCraftingPlanFactory。
- 独立副産物の未使用出力は初期在庫へ入れず、元Patternのまま実行へ渡す。
  副産物変更をProgram fingerprintへ含める。既存単一出力の指紋は維持。
- 副産物再利用、複数候補からの分岐選択、共有DAGのAE2 CPU bytes再現は今回未解決。
  標準計算を削除したり、全工業レシピの高速化完了と報告したりしない。
- Server/Clientは未配置。実注文の所要時間、完成、取消、復旧は未確認。

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

同じ端末で各版を一回ずつ別Gradle test JVMから測った補助値では、1.5.33から2.0への
Compiled Planner時間はForgeが`40.830 ms -> 35.329 ms`、NeoForgeが
`31.480 ms -> 26.887 ms`だった。割当量はForgeが両版`15.262 MiB`、NeoForgeが
両版`15.252 MiB`で変化しなかった。単発のmicroprobeなので速度差を合否条件や
実経路改善率には使わない。ACO未導入、Server Thread時間、MSPT、GC、TPSの比較は
実環境計測まで未証明のままとする。

詳細なThread境界は`docs/PARALLEL_PLANNER_2_0.md`を正本とします。
