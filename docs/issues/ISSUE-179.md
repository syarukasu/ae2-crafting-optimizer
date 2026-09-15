# Issue #179: ACO 2.0 Planning Boundary Cleanup

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/179
- 状態: Ready
- 対象版: 2.0.0
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1

## 2026-09-15 実レシピ: 至高制御回路100個の計画停止

### 12:30 再起動後の追加観測（第一段階JARのまま）

- 12:15:05に起動完了したserver/clientのACO SHA-256は両方とも
  `44B2B7FE085B4E1030A588339CFF5BE4C603D83B748C431E85E97453F5D5662B`。
  第二段階の`70C70B...`は未配置。再起動だけでは新コードは有効になっていない。
- 今回の対象は`emextras:supreme_quantum_control_circuit`。前回の
  `mekanism_extras:supreme_control_circuit`と区別する。
  12:28:58の1個、12:29:38と12:30:42の10個は、いずれも
  `UNSUPPORTED_PATTERN / MULTIPLE_OUTPUTS`により`phase=ae2-standard`へ移行。
- 同起動中のultimate_control_circuitとglassはMULTIPLE_PRODUCERS辞退。
  異なる発注の辞退理由や、再利用されたworkerの累積CPU時間を混同しない。
- 12:32の20秒JFRにはAE Calculator実行サンプル102件。そのうち
  AECraftingPattern.isItemValid 36件、cacheFuzzy 35件、AEItemKey.toStack 32件。
  inclusiveで重複する値であり、割合の合算や速度改善率として使わない。
  workerは素材検証境界でpause、serverは通常tickを実行していた。
- この複数出力ケースは第二段階のNBT cacheだけで高速化を保証できない。
  更新後の比較対象に加え、必要なら出力を消さずに扱える計算境界を別途調査する。
- 診断のみ。在庫、レシピ、稼働JAR、計算時間予算3ms/tickは変更していない。
  証拠はdiagnosticsのsupreme-unupdated-1232.jfr、threads.txt、summary.txt。

- 状態: Implemented（2026-09-15、利用者承認後に局所試験・全buildを通過。実レシピ再計測待ち）。
- 対象: Forge 1.20.1、AE2 15.4.10、ACO 2.0.0開発版、AQE 2.2.7、
  InsaneAE 1.1.0、ExtendedAE Plus 1.5.5を含む実サーバー。
- 利用者の新規発注: `mekanism_extras:supreme_control_circuit` 100個。
- 10:53:14のcalculationId=4、10:57:41のcalculationId=9で同じ辞退を確認。
  `AMBIGUOUS_PRODUCER / MULTIPLE_PRODUCERS`から`phase=ae2-standard`へ戻る。
  `ae2-snapshot-detached`には進まない。前者は10:54:53にInterruptedExceptionで終了。
- 同時間帯に82,647ms、168,026msのserver遅延を記録した。二回のThread dumpで
  Server threadは`CraftingCalculation.simulateFor`内のmonitor待機、workerは
  `CraftingSimulationState.cacheFuzzy`内の候補構築を実行していた。
- 最終取得でもworkerはCPU時間352.625秒を消費し、RUNNABLEであった。
  これは表示だけの停止や「計算スレッドが存在しない」問題ではない。
- 新規発注開始以降のJFR実行サンプル14,313件中、`cacheFuzzy`を6,962件、
  `AEItemKey.getFuzzySearchMaxValue`を6,288件、`AECraftingPattern$Input.isValid`を
  5,152件で観測した。inclusiveで重複し、百分率を合算できない。速度改善率でもない。
- 読取診断: root候補は1つ。依存キー1,296種類の走査を完了。
  `megacells:accumulation_processor`は3候補、`printed_accumulation_processor`は2候補。
  `mekanism:energy_tablet`、`mekanism_extras:absolute_induction_cell`等は代替素材有効。
  診断出力は問題ノード先頭30件に制限し、在庫・レシピ・Patternを変更していない。
- AE2実装: `KeyCounter.getSubIndex`は既存sub-indexがあっても毎回最大耐久値を
  問い合わせる。実stackにはSkillTreeとL2 Complementsの耐久補正を観測した。
  `AECraftingPattern.getTestResult/setTestResult`はNBT付きキーをcacheしないため、
  代替素材照合でItemStack/NBT生成とrecipe検査を繰り返す。

### 承認対象の追加修正方針

11:04:05の追加結果: 同じ計算で1 tickが180秒に達し、Watchdogがサーバーを停止した。
`crash-2026-09-15_11.04.05-server.txt`の最上位原因も`simulateFor`内のmonitor待機。
以下の3番（安全なpause）の優先度を最上位とする。Watchdog無効化や長時間待機の
例外隠蔽は修正にしない。診断スクリプトはactiveディレクトリから撤去済みで、
製品JARは変更していない。サーバーは未再起動。

1. まず計算中の既存KeyCounter sub-index再利用時だけ不要な耐久判定を省く。
   AE2の初回の型選択、候補、数量、NBT、既存sub-indexを保持する。
   ItemStack全体の耐久値固定や外部MODの耐久ルール変更はしない。
2. 代替素材の照合結果は、同一計算・同一Pattern入力・完全なNBTキー・同一世代で、
   レシピの読取専用性を証明できる範囲だけ再利用する。未知の動的レシピは元処理へ委譲。
   既存のAE2 cacheと二重化せず、NBT付き候補の繰返しを局所的に扱う。
3. `cacheFuzzy`等の長い一処理内にも安全なpause地点を置き、live経路が長時間
   serverを待たせないようにする。可変World/Patternを無検証でoff-thread化しない。
4. 複数Producerは消去・統合・並べ替えず、通常計画経路自体の重複作業を減らす。
   この実測だけを根拠に、全代替レシピを数式Plannerで置換しない。

### 承認後の実装境界

- `CraftingCalculationMemo`の既存run/finishスコープを使用する。CPU実行、server委譲処理、
  計算外にはcheckpointを接続しない。
- `CraftingSimulationState.cacheFuzzy`の各候補の`simulateExtractParent`直前、
  `CraftingCpuHelper.getValidItemTemplates`の候補列挙と`IInput.isValid`直前にcheckpointを置く。
  前者の走査対象は計算専用の親Snapshotであり、実ME在庫のiteratorではない。
  `AECraftingPattern.testFrame`を一時変更しているrecipe callbackの途中ではpauseしない。
- 時間判定と待機はAE2の`handlePausing`を再利用する。候補内ではincTimeを検査可能値へ
  設定し、既存のtick予算を毎回確認する。独自の待機解除、時間上限、executorは追加しない。
  割込みは次のtick待ちに入る前に伝播する。detached経路の世代照合も従来通り実行する。
- `KeyCounter.getSubIndex`の先頭で既存のidentity索引を返すMixinだけを追加する。
  初回は元処理へ委譲し、既存索引の型、NBT別キー、数量、消去後の初回再判定を維持する。
  追加cacheは持たず、計算中かつ既存のmemo機能が有効な場合に限る。
- 所有クラス: Memoは計算スコープ、ThreadAccess/Diagnosticsは既存pause接続、
  Simulation/HelperのMixinは安全な呼出地点、KeyCounterのMixinは既存索引の再利用だけ。
- NBT付き動的recipeの検証結果cacheは今回の第一段階では追加しない。副作用やWorld依存を
  否定できないため。まず上記2点を実測し、残るボトルネックは別途証拠を取る。
- fail-first回帰: checkpointの存在と接続地点、計算外の無操作、割込み伝播、既存索引再利用、
  未登録キー/削除済み索引の元処理への委譲、feature台帳登録。

### 必要な検証

- 優先候補、NBT違い、代替素材、欠品、副産物、液体/Chemical数量を変更前と比較する。
- lookup回数と一回の計算内の失効、取消、server停止の待機解除を局所試験にする。
- 同じ実レシピ100個で計画完了時間とServer thread待機を再計測する。
- 今回通過済みの20段階exact注文（ACO側10個・AQE側完了）も回帰確認する。
- 純粋内部benchmarkや上記BigInteger完走だけでは、本件の高速化達成としない。
- Forge/NeoForgeの意味が同じ修正は別PRで同期し、実環境未試験の版を明記する。

ログ/JFR/候補JSONはserverの`diagnostics/aco-aqe-20260915/`へ保存。
診断スクリプトのAPI読取エラーは別件であり、上記計算例外へ混同しない。
上記の調査時点では製品を変更していない。承認後の変更と配置は以下の通り。

### 第二段階: 通常recipeのNBT照合再利用

- 状態: Implemented（ローカルのみ、未配置）。承認済み方針2のうち通常recipeに限定する。
- 実graphを再走査: 1,296 keys、切捨てなし。505 AEProcessingPattern、133 AECraftingPattern、
  31 AdvancedAE ProcessingPattern。作業台の内訳は通常Shaped 92、Shapeless 13、
  MekanismShaped 10、Cucumber 16、GTCEu StrictShaped 2。
- 通常Shaped/Shapelessの具象型完全一致、通常Ingredient具象型完全一致だけを対象とする。
  MekanismShapedはassemble時に充電等のRecipeUpgradeDataを合成することを実JARで確認し除外。
  その他の派生型、特殊Ingredientも除外。Schrodingers IngredientのMixinはTag参照による
  追加判定であることを実JARで確認。具象型だけでは全MODによる改変の純粋性までは保証しない。
  未知のrecipeを一般化して対象にしない。導入環境での比較検証は引き続き必要。
- `AECraftingPattern.getTestResult/setTestResult`へ薄いMixinを接続し、元cacheが扱わない
  NBT付きAEItemKeyだけをMemoへ渡す。キーはPattern identity、slot、完全AEKey。
  初回はrecipe.matches/assembleを必ず実行し、true/falseを同じ計算内だけ再利用する。
  元の入力優先順位、通常NBTなしcache、返却物、数量は変更しない。
- recipe/Pattern世代変更時とfinish時に破棄。テストは異なるNBT/slot/Pattern、false、
  計算外、機能OFF、世代失効、除外recipe型、実メソッド記述子を対象にする。
- これは通常recipeの重複検証削減であり、未実証の全体高速化を主張しない。

第二段階のローカル結果:

- 未実装時に先行4試験が失敗。実装後は8試験と実AE2メソッド契約試験が通過。
  同一候補1,000回の試験では初回評価1回。NBT違い、slot、Pattern、false、特殊Ingredient、
  派生recipe除外、世代失効、終了、別Thread、既存AE2 cache保持を検証した。
- Forge 1.20.1: clean build、全517 tests、失敗0・エラー0・skip 0、
  verifyIssueRegressionManifestを実行して成功。台帳のruntime_statusはPENDINGのまま。
- JAR SHA-256: `70C70B7529582FF2E0556A665012FA826D9646AFE71E9A23325099AF48AB11C2`。
- 製品差分: CraftingCalculationMemo、CraftingPatternTaggedValidationMixin、feature台帳と
  Mixin登録のみ。レシピ、在庫、CPU実行、BigInteger API、AQEは変更していない。
- NeoForge第二段階は実JARのhasComponentsとcache境界に意味差が見つかり移植保留。
  意味差を無視した実装は撤去済み。第一段階の変更は維持する。
- Server/Clientはいずれも第一段階JARのまま。利用者のクライアント終了確認後に配置する。
  一個/100個の計画完了時間、出力と欠品の正しさを未確認のため、Issueは未解決。

### 第一段階の検証・配置

実機再試験の判定: **未達**。11:32:40の100個注文は11:35:09にInterruptedExceptionで
終了し、完了結果なし。11:35:14の1個注文も同じMULTIPLE_PRODUCERS辞退後に長時間計算。
11:33:14のserver計測は20 TPS / 平均28.186msで、新checkpoint内でworkerがwaitし、
serverが通常tickを実行するstackを取得した。180秒のserver占有はこの採取では再発していない。
しかし計画自体の完了高速化は未達。ネイティブ予算は設定通り3ms/tickであり、
pause追加を計算量削減と混同しない。複数Producer削除や時間予算の引上げでは対処しない。
1個注文区間のJFRでもAECraftingPattern.isItemValidを含む素材照合を観測した。
次段階は実recipe実装の識別と、照合結果を安全に再利用できる境界の調査から行う。

- checkpoint試験3件は修正前に失敗を確認。修正後は通過。
- 計算内の既存索引200回再利用では、最大耐久値照会は初回1回のみ。
  初回のFuzzy/Unordered選択、identityキー、別secondaryキー、削除後の再作成も試験した。
- 実AE2クラスのbytecodeでpause注入先の呼出数、KeyCounterのprivate field型と戻り型を検証。
- Forge: 全508 tests、失敗0、エラー0、skip 0。clean buildと回帰マニフェスト成功。
- NeoForge: 全528 tests、失敗0、エラー0、skip 0。同じ修正を同期してclean build成功。
  NeoForgeはテスト用設定の復元修正後も全testと回帰マニフェストを再実行して成功。
- Forge ACO SHA-256: `44B2B7FE085B4E1030A588339CFF5BE4C603D83B748C431E85E97453F5D5662B`。
  指定server/clientに同じJARを配置した。AQE、KubeJSレシピ、Watchdog設定は変更していない。
- 停止中のworld 1,875ファイルを別途backupし、全SHA-256を照合。11:24:43にserver Done、
  11:28:14にclient参加を確認。新規Mixinの適用もdebug.logで確認。
- 新規診断先: `diagnostics/aco-issue179-ingredient-search-20260915/`。
  この段階では至高制御回路100個の計画完了・取消・20段階exact再完走は未確認。
  起動成功やJUnit件数を計画速度改善の証拠にしてはいけない。

## 2026-09-15 索引公開の過剰負荷

- 状態: Verified（局所修正と両版回帰build。実サーバーの改善率は未測定）
- 証拠: Server threadの二回のstackでcapturePublishedのNBT文字列ソートを観測。
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
