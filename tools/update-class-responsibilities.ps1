param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$sourceRoot = Join-Path $RepositoryRoot 'src/main/java'
$outputPath = Join-Path $RepositoryRoot 'docs/CLASS_RESPONSIBILITIES.md'

$packageRoles = [ordered]@{
    'com.syaru.ae2craftingoptimizer' = '起動、Forge/NeoForgeイベント接続、全体初期化。'
    'com.syaru.ae2craftingoptimizer.access' = 'Mixinが外部クラスの内部状態を型付きで公開する契約。判断ロジックは持たない。'
    'com.syaru.ae2craftingoptimizer.api.batch' = '旧Pattern Batch公開API。互換性維持を優先する。'
    'com.syaru.ae2craftingoptimizer.api.batch.v2' = '所有権、Receipt、commit、復旧を明示するTransactional Batch公開API。'
    'com.syaru.ae2craftingoptimizer.api.big' = 'BigInteger計画、Host、進捗、公開連携API。'
    'com.syaru.ae2craftingoptimizer.api.contract' = '版付きpayload、revision、Receipt、正確在庫の公開連携契約。'
    'com.syaru.ae2craftingoptimizer.api.craftingtable' = '作業台物理Batch Workerとの公開契約。'
    'com.syaru.ae2craftingoptimizer.api.execution' = 'Exact Vector実行所有者を宣言する公開契約。'
    'com.syaru.ae2craftingoptimizer.api.vector' = 'exact数量のVector計画、保存、Storage境界API。'
    'com.syaru.ae2craftingoptimizer.batch' = 'Pattern Batchの完全一致、Escrow、Receipt、再照合。'
    'com.syaru.ae2craftingoptimizer.client' = 'クライアント表示、入力、BigInteger表示用state。'
    'com.syaru.ae2craftingoptimizer.command' = '観測と安全な失効だけを行う診断コマンド。'
    'com.syaru.ae2craftingoptimizer.config' = '機能スイッチ、上限、時間予算のCommon Config。'
    'com.syaru.ae2craftingoptimizer.craftingamount' = 'long注文数をAE2 Menuへ渡すserver側境界。'
    'com.syaru.ae2craftingoptimizer.engine' = 'コンパイル済みグラフ、Planner、BigInteger会計の計算核。'
    'com.syaru.ae2craftingoptimizer.engine.craftingtable' = '所有権移転後の作業台物理クラフト取引、Escrow、復旧。'
    'com.syaru.ae2craftingoptimizer.engine.vector' = 'exact Vector計画の検証、在庫snapshot、表示投影。'
    'com.syaru.ae2craftingoptimizer.gtceu' = 'GTCEu Recipe IntentとNative Batchの任意連携。'
    'com.syaru.ae2craftingoptimizer.integration' = 'AE2、Advanced AE、任意MOD、exact storageへの版別接続。'
    'com.syaru.ae2craftingoptimizer.intent' = 'Providerが意図するrecipeを短期間伝える検索hint。'
    'com.syaru.ae2craftingoptimizer.lifecycle' = 'server起動、停止、reload、registry accessの順序管理。'
    'com.syaru.ae2craftingoptimizer.mekanism' = 'Mekanism Recipe IntentとNative Batchの任意連携。'
    'com.syaru.ae2craftingoptimizer.mixin' = '外部処理へ薄い入口を追加するMixinと適用plugin。'
    'com.syaru.ae2craftingoptimizer.network' = 'BigInteger容量と進捗を同期する版付き通信路。'
    'com.syaru.ae2craftingoptimizer.optimization' = '世代付きcache、時間予算、診断、保守的高速経路。'
    'com.syaru.ae2craftingoptimizer.scheduler' = 'ジョブとProviderへtick予算を公平配分するscheduler。'
    'com.syaru.ae2craftingoptimizer.transaction' = 'Native Batch取引、Journal、再起動復旧、収支検査。'
    'com.syaru.ae2craftingoptimizer.util' = '副作用を持たないfingerprintなどの共通処理。'
}

$overrides = @{
    'com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer' = 'MOD entrypoint。Config、network、lifecycle、optional integrationを登録し、個別計算は各層へ委譲する。'
    'com.syaru.ae2craftingoptimizer.config.ACOConfig' = '全Common Config key、既定値、範囲、説明を登録する唯一のConfig正本。'
    'com.syaru.ae2craftingoptimizer.engine.Ae2AuthoritativeCraftingPlanner' = 'Shadow一致済みの決定的rootだけをACO計画へ昇格し、証明不能なら採用を辞退する。'
    'com.syaru.ae2craftingoptimizer.engine.Ae2BigCraftingPlanFactory' = 'AE2 Pattern木からexact BigInteger計画とsimulation不足計画を構築する。'
    'com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache' = 'Pattern世代ごとのコンパイル済みグラフsnapshotを保持し、世代変更で失効する。'
    'com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars' = '純正AE2 CraftingPlanへexact真値をidentity関連付けし、外部型互換を維持する。'
    'com.syaru.ae2craftingoptimizer.engine.Ae2StrictCraftingTopology' = '代替、循環、返却物などを検査し、数式計画へ安全に変換できるPattern DAGだけを認定する。'
    'com.syaru.ae2craftingoptimizer.engine.BigCraftingJob' = 'BigInteger注文の永続状態とlong実行Windowの貸出・回収を管理する。'
    'com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram' = '一つのrootから到達する決定的DAGを配列プログラムへ変換し、数量に依存しない計算を行う。'
    'com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobState' = 'Advanced AE実Jobへ付随するexact task、waiting、output、Receiptのsidecar正本。'
    'com.syaru.ae2craftingoptimizer.engine.OverflowPromotingCraftingPlanner' = 'checked long Plannerを先に試し、overflowした注文だけ最初からBigIntegerで再計算する。'
    'com.syaru.ae2craftingoptimizer.engine.craftingtable.ExactCountMap' = '正のBigInteger数量Mapの検証、順序付き複製、包含判定、exact加算を行う副作用なし共通部品。'
    'com.syaru.ae2craftingoptimizer.engine.craftingtable.ExactCraftingEscrow' = '一注文が所有する境界素材、中間素材、最終成果物をBigIntegerのまま原子的に増減する。'
    'com.syaru.ae2craftingoptimizer.engine.craftingtable.PhysicalCraftingTreeTransaction' = '作業台ツリーの所有権移転後state machine。予約、Worker Receipt、取消、返却、隔離、NBT復元を統括する。'
    'com.syaru.ae2craftingoptimizer.integration.AqeBigCraftingExecutionManager' = 'Advanced AE CPU HostとACO exact計画を接続し、予約、実行、取消、復元を調停する外部境界。'
    'com.syaru.ae2craftingoptimizer.integration.ExactNetworkStorageBridge' = 'ME storageへのexact snapshot、reserve、insert、rollbackをAE2権限境界内で行う。'
    'com.syaru.ae2craftingoptimizer.integration.TerminalDisplaySnapshotProjection' = 'ME端末へ送る一時在庫Snapshotだけをmount単位で飽和加算し、long超過キーをLong.MAX_VALUE表示へ投影する。'
    'com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator' = '同一世代・同一注文の計算Futureを共有し、待機者と取消所有権を分離する。'
    'com.syaru.ae2craftingoptimizer.optimization.TransactionalCraftingExecutorV2' = 'Batch V2のprepare、transfer、commit、rollbackをReceipt契約に従って進める。'
    'com.syaru.ae2craftingoptimizer.mixin.MEStorageMenuDisplaySaturationMixin' = 'Issue #148の表示投影をMEStorageMenu#broadcastChangesのSnapshot取得だけへ接続する。'
    'com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi' = 'AQE、InsaneAEなどへexact計画、容量、台帳を公開する版付きFacade。'
    'com.syaru.ae2craftingoptimizer.api.big.BigCraftingRuntime' = 'BigInteger計画sidecarとruntime jobの登録、照会、寿命管理を行う。'
    'com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRuntime' = '明示登録された外部CPU Hostのexact容量予約、snapshot、復旧を管理する。'
    'com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatchCodec' = 'exact Vector計画を上限付きNBTへ符号化・復号し、schemaを検証する。'
    'com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchCommit' = 'prepare済みBatchの所有権証明、受理数、Receiptをまとめ、commit可否を表す。'
    'com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch' = 'Adapterへ渡す前にidentity、payload、要求回数を固定した不変Batch。'
    'com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusInbox' = '分割受信したBigInteger進捗pageをHost世代ごとに集約するclient側受信箱。'
    'com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPage' = 'BigInteger容量・使用量・Job進捗の一部分を運ぶ版付きpage。'
    'com.syaru.ae2craftingoptimizer.api.big.BigIntegerCraftingPlanView' = '外部MODがACO計画のexact bytes、要求量、不足量を切り捨てず読むための公開view。'
    'com.syaru.ae2craftingoptimizer.api.contract.ExactStorageAmountProvider' = '外部ストレージがAEKey別の正確なBigInteger在庫SnapshotをACOへ公開する安定契約。'
    'com.syaru.ae2craftingoptimizer.client.AsyncTerminalView' = '端末検索・sortの非同期結果を世代付きで保持し、古い結果の反映を拒否する。'
    'com.syaru.ae2craftingoptimizer.command.ACOIntentCommands' = 'Recipe Intent、cache、Batch、計算統計を表示・安全に失効するserver commandを登録する。'
    'com.syaru.ae2craftingoptimizer.engine.BigCountMath' = 'BigInteger数量Mapの加算・乗算・ceilDivを正確に行う副作用なし算術。'
    'com.syaru.ae2craftingoptimizer.engine.BigCraftingInventory' = '計画中の在庫使用量と不足量をAEKey別BigIntegerで保持する仮想在庫。'
    'com.syaru.ae2craftingoptimizer.engine.BigCraftingTaskProgress' = 'Pattern taskの総数、完了数、待機数をBigIntegerで追跡する。'
    'com.syaru.ae2craftingoptimizer.engine.BigExecutionWindow' = 'BigInteger残量からlegacy実行へ貸し出す、Long.MAX_VALUE以下の一時Window。'
    'com.syaru.ae2craftingoptimizer.engine.CheckedLongMath' = '通常計画のlong演算をexact検査し、overflow時は昇格用例外を返す。'
    'com.syaru.ae2craftingoptimizer.engine.CompiledCraftingGraph' = '世代内で再利用するPattern nodeと入出力edgeの不変グラフ。'
    'com.syaru.ae2craftingoptimizer.engine.CompiledPattern' = '一つのPatternをnode ID、exact係数、候補情報へ正規化した不変値。'
    'com.syaru.ae2craftingoptimizer.engine.CompiledPlanningSession' = '一計算で共有するgraph snapshot、在庫snapshot、取消token、世代検証を束ねる。'
    'com.syaru.ae2craftingoptimizer.engine.CraftingPlanShadowComparator' = 'ACO計画とAE2標準計画の結果・不足・bytesを比較し、不一致なら採用を拒否する。'
    'com.syaru.ae2craftingoptimizer.engine.PlanningCancellationToken' = '共有計算を直接cancelせず、呼出者ごとの取消要求を協調的に伝えるtoken。'
    'com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath' = 'Provider Intentに一致するGT recipe候補だけを優先し、GTCEu本来の成立判定へ渡す。'
    'com.syaru.ae2craftingoptimizer.integration.AdvancedAePatternProviderAccess' = 'Advanced AE Pattern Providerの実体と世代情報を版差を吸収して取得する。'
    'com.syaru.ae2craftingoptimizer.integration.Ae2UelmCompatibility' = 'Forge 1.20.1でAE2 UELMを検出し、対応済みdescriptorと機能差を検証する。'
    'com.syaru.ae2craftingoptimizer.intent.InputIntent' = 'Providerが機械へ渡した具体的なItem、Fluid、Chemical入力を不変値で表す。'
    'com.syaru.ae2craftingoptimizer.intent.IntentLocation' = 'Intentの送信元Provider、Level、位置、方向を特定する。'
    'com.syaru.ae2craftingoptimizer.intent.PatternIntentCapture' = 'Pattern push直前のrecipe intentをthread-local境界へ短期間保存する。'
    'com.syaru.ae2craftingoptimizer.intent.PatternIntentExtractor' = 'AE2 Patternから機械検索用の入力、出力、recipe種別hintを抽出する。'
    'com.syaru.ae2craftingoptimizer.intent.RecipeIntent' = '一回のPattern pushが作ろうとするrecipeとexact入出力の読取専用表現。'
    'com.syaru.ae2craftingoptimizer.intent.StackIntent' = '一つのItem、Fluid、Chemicalと要求量をrecipe intent内で表す。'
    'com.syaru.ae2craftingoptimizer.mekanism.MekanismRecipeIntentFastPath' = 'Provider Intentに一致するMekanism recipe候補だけを優先し、CachedRecipeの成立判定へ渡す。'
    'com.syaru.ae2craftingoptimizer.optimization.ConfigInventoryGenerationAccess' = 'BusやProviderの設定Inventoryに変更世代を付与する最小Access契約。'
    'com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo' = '一回のクラフト計算内で在庫照会、候補、返却物などの純粋結果を共有する。'
    'com.syaru.ae2craftingoptimizer.optimization.DeepRangeUpdateHelper' = '端末の巨大な差分範囲を上限付きchunkへ分け、順序を保って同期する。'
    'com.syaru.ae2craftingoptimizer.optimization.P2PNotificationDeduplicator' = '同一tick・同一topology世代の重複P2P通知を一度へまとめる。'
    'com.syaru.ae2craftingoptimizer.optimization.PatternAvailabilitySorter' = '直前成功や利用可能性をhintに候補順だけを変え、適格性判定は変更しない。'
    'com.syaru.ae2craftingoptimizer.optimization.PatternCandidatePruner' = '証明済みの非候補だけを計算前に除外し、曖昧なら元候補集合を維持する。'
    'com.syaru.ae2craftingoptimizer.optimization.PatternProviderBatchEligibility' = 'Pattern ProviderをBatch対象にできるか、所有権・入力・target能力から保守的に判定する。'
    'com.syaru.ae2craftingoptimizer.optimization.StorageWatcherUpdateBuffer' = 'client可視のstorage差分を上限付きで集約し、screenやtopology変更時に即flushする。'
    'com.syaru.ae2craftingoptimizer.optimization.FallbackBoundary' = 'ACOがAE2標準経路へ安全に戻せる最終地点を、ownership取得の前後で分類する。'
    'com.syaru.ae2craftingoptimizer.optimization.OptimizationDomain' = '最適化をnetwork、storage IO、provider、client sync、planning、execution、BigInteger、任意連携へ分割する。'
    'com.syaru.ae2craftingoptimizer.optimization.OptimizationFeature' = '各最適化の安定ID、実装状態、domain、risk、状態所有者、失効条件、fallback境界、関連回帰Issueを宣言する正本。'
    'com.syaru.ae2craftingoptimizer.optimization.OptimizationFeatureGate' = 'master、domain、個別設定、実装状態を順に評価し、無効または互換No-opの機能がAE2状態へ触れる前に停止する。'
    'com.syaru.ae2craftingoptimizer.optimization.OptimizationImplementationStatus' = '実行経路が有効な機能と、既存TOML互換のためキーだけ残すNo-opを区別する。'
    'com.syaru.ae2craftingoptimizer.optimization.OptimizationInvalidation' = 'cacheとtransactionを破棄するstorage、provider、topology、reload、lifecycle等の正本イベントを列挙する。'
    'com.syaru.ae2craftingoptimizer.optimization.OptimizationRisk' = '最適化の影響範囲をLOW、MEDIUM、HIGHへ分類し、必要な回帰証拠を決める。'
    'com.syaru.ae2craftingoptimizer.optimization.StateOwnership' = '最適化中の正本がAE2、ACO cache、ACO transaction、外部アドオンのどれかを示す。'
    'com.syaru.ae2craftingoptimizer.mixin.MixinFeatureCatalog' = '全Mixinを監査済みOptimizationFeatureへ対応付け、未登録Mixinをfail-closedにする正本。'
    'com.syaru.ae2craftingoptimizer.api.contract.BatchTargetRevision' = 'Batch targetの内容世代と有効性を一つの単調revisionとして表す。'
    'com.syaru.ae2craftingoptimizer.api.contract.PayloadKind' = 'exact payloadがItem、Fluid、Chemicalなど何を表すかを列挙する。'
    'com.syaru.ae2craftingoptimizer.api.contract.ReceiptReservation' = 'Receiptへ対応する所有量、期限、target revisionを固定した予約値。'
    'com.syaru.ae2craftingoptimizer.api.contract.ReceiptReservationProtocol' = 'Receipt予約のprepare、commit、cancel、復旧順序を外部連携へ公開する契約。'
    'com.syaru.ae2craftingoptimizer.api.contract.RevisionWakeupListener' = 'target revision変更時に待機中処理を再評価させる通知callback。'
    'com.syaru.ae2craftingoptimizer.api.contract.SupportedFeature' = 'ACO連携先が明示的に保証するexact機能を列挙する。'
    'com.syaru.ae2craftingoptimizer.optimization.FallbackReasonCode' = '高速経路を辞退した理由を安定した診断codeとして列挙する。'
    'com.syaru.ae2craftingoptimizer.optimization.SharedCalculationFuture' = '一つの計算Futureを複数呼出者へ共有し、各待機者の取消を所有Futureの取消と分離する。'
}

$reviewNotes = @{
    'PhysicalCraftingTreeTransaction' = '高。state machineと永続Codecが同居。Issue #87では数量Mapだけ分離し、Receipt/Codec分割は専用回帰試験を伴う別Issueにする。'
    'ACOConfig' = '中。長大だがConfig IDと既定値の正本として凝集している。key互換を固定する試験なしに分割しない。'
    'AqeBigCraftingExecutionManager' = '高。外部CPU所有権と復旧境界。起動・再起動・取消試験を用意してから段階分割する。'
    'CompiledRootProgram' = '中。計算核として大きいが副作用は限定的。コンパイルと評価の分離候補。'
    'BigCraftingJob' = '高。永続状態とWindow貸出を所有。NBT Codec分離はschema回帰試験と同時に行う。'
    'Ae2AuthoritativeCraftingPlanner' = '中。採用判定と計画生成の境界を維持し、fallback条件を別クラスへ散らさない。'
    'TransactionalCraftingExecutorV2' = '高。所有権移転後の処理。見た目の短縮目的では分割せず、phase単位の試験を先に増やす。'
    'ExactNetworkStorageBridge' = '高。実在庫境界。snapshotとmutationの分離候補だが原子性試験が先。'
    'BigCraftingRuntime' = '中。公開API側のruntime registry。Host runtimeとの責務重複を監視する。'
    'BigCraftingHostRuntime' = '高。外部Host容量と予約を所有。複数Job仕様を勝手に導入しない。'
}

function Get-ClassRole {
    param(
        [string]$FullyQualifiedName,
        [string]$PackageName,
        [string]$ClassName,
        [string]$Source
    )

    # 重要クラスは名前推測を使わず、レビュー済みの説明を返す。
    if ($overrides.ContainsKey($FullyQualifiedName)) {
        return $overrides[$FullyQualifiedName]
    }

    $escapedClassName = [regex]::Escape($ClassName)
    $javadocPattern = '(?s)/\*\*(?<doc>.*?)\*/\s*(?:public\s+)?(?:(?:final|abstract|sealed|non-sealed)\s+)*(?:class|record|interface|enum)\s+' + $escapedClassName
    $javadocMatch = [regex]::Match($Source, $javadocPattern)
    # 日本語Javadocがある型は、名前推測より実装者が書いた先頭説明を優先する。
    if ($javadocMatch.Success) {
        $documentedRole = $javadocMatch.Groups['doc'].Value
        $documentedRole = $documentedRole -replace '(?m)^\s*\*\s?', ''
        $documentedRole = $documentedRole -replace '\{@(?:link|code|literal)\s+([^}]+)\}', '$1'
        $documentedRole = $documentedRole -replace '<[^>]+>', ' '
        $documentedRole = ($documentedRole -split '(?m)^\s*@')[0]
        $documentedRole = ($documentedRole -replace '\s+', ' ').Trim()
        # 日本語の最初の一文だけを責務欄へ使い、詳細はsource Javadocへ残す。
        if ($documentedRole -match '[ぁ-んァ-ン一-龯]' -and $documentedRole -match '^(.+?。)') {
            return $Matches[1]
        }
    }

    # Access層は外部状態の型付き窓口だけを担当し、判断を持たない。
    if ($PackageName -eq 'com.syaru.ae2craftingoptimizer.access') {
        return "${ClassName}が示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。"
    }

    # Mixin pluginは対象MOD、版、Configに応じて注入可否だけを決める。
    if ($PackageName -eq 'com.syaru.ae2craftingoptimizer.mixin' -and $ClassName.EndsWith('Plugin')) {
        return "${ClassName}が担当するMixin群の適用可否を、対象MODと対応版から決定する。"
    }

    # Accessor Mixinは非公開状態をAccess契約へ公開するだけに留める。
    if ($PackageName -eq 'com.syaru.ae2craftingoptimizer.mixin' -and $ClassName.Contains('Accessor')) {
        return "${ClassName}の対象となる非公開状態を型付きAccessorとして公開するMixin。"
    }

    # 通常Mixinは名前に示す一点だけを既存処理へ接続する薄い境界とする。
    if ($PackageName -eq 'com.syaru.ae2craftingoptimizer.mixin') {
        return "${ClassName}が示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。"
    }

    # 例外型は失敗理由を型で伝え、値の丸めや握り潰しを防ぐ。
    if ($ClassName.EndsWith('Exception')) {
        return "${ClassName}が示す失敗を呼出側へ型付きで通知する。"
    }

    # Codecは値の意味を変えず、保存または通信表現だけを変換する。
    if ($ClassName.EndsWith('Codec')) {
        return "${ClassName}が示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。"
    }

    # Plannerは入力snapshotから副作用なしの計画を構築する。
    if ($ClassName.EndsWith('Planner')) {
        return "${ClassName}が示す入力から、副作用なしでクラフト計画を構築する。"
    }

    # Planは計画結果を表す不変値で、実在庫を直接変更しない。
    if ($ClassName.EndsWith('Plan') -or $ClassName.EndsWith('Program')) {
        return "${ClassName}が示すクラフト計画またはコンパイル済みプログラムを不変値として保持する。"
    }

    # Cacheは正解を生成せず、世代やrevision失効まで既知結果を再利用する。
    if ($ClassName.EndsWith('Cache')) {
        return "${ClassName}が示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。"
    }

    # LedgerとJournalは推測せず、所有量と収支の事実を保存する。
    if ($ClassName.EndsWith('Ledger') -or $ClassName.EndsWith('Journal')) {
        return "${ClassName}が示す所有量、Receipt、収支をexact値で記録・検証する。"
    }

    # SnapshotとStateは特定時点の状態を表し、外部操作を直接行わない。
    if ($ClassName.EndsWith('Snapshot') -or $ClassName.EndsWith('State')) {
        return "${ClassName}が示す時点の状態を、検証可能な値として保持する。"
    }

    # Registryは登録と検索だけを所有し、登録対象の実行を奪わない。
    if ($ClassName.EndsWith('Registry')) {
        return "${ClassName}が示す実装またはHostの登録、解除、検索を管理する。"
    }

    # BridgeとAdapterは二つの所有者間で値を変換し、最終判定を元MODへ残す。
    if ($ClassName.EndsWith('Bridge') -or $ClassName.EndsWith('Adapter') -or $ClassName.EndsWith('Support')) {
        return "${ClassName}が示す二つのAPI境界を接続し、対応不能時は元の所有者へ判断を戻す。"
    }

    # Factoryは検証済み入力から対象値を組み立てる。
    if ($ClassName.EndsWith('Factory')) {
        return "${ClassName}が示す値を、検証済み入力から生成する。"
    }

    # RuntimeとManagerは寿命と状態遷移を管理するが、外部所有物は奪わない。
    if ($ClassName.EndsWith('Runtime') -or $ClassName.EndsWith('Manager') -or $ClassName.EndsWith('Coordinator')) {
        return "${ClassName}が示す機能の登録、寿命、状態遷移を管理する。"
    }

    # FormatterとParserは表示または入力境界だけを担当する。
    if ($ClassName.EndsWith('Formatter') -or $ClassName.EndsWith('Parser')) {
        return "${ClassName}が示す表示または入力文字列を、意味を変えずに変換する。"
    }

    # 公開APIは実装所有権を奪わず、登録・照会・要求の入口だけを提供する。
    if ($ClassName.EndsWith('Api')) {
        return "${ClassName}が示す機能を外部MODへ公開する安定Facade。"
    }

    # ModeとPhaseは状態や方針の有限な選択肢を表す。
    if ($ClassName.EndsWith('Mode') -or $ClassName.EndsWith('Phase')) {
        return "${ClassName}が表す実行方針または状態遷移段階を列挙する。"
    }

    # RequestとContextは一回の呼出しに必要な検証済み入力を運ぶ。
    if ($ClassName.EndsWith('Request') -or $ClassName.EndsWith('Context')) {
        return "${ClassName}が示す一回の要求に必要な入力、所有者、実行条件を保持する。"
    }

    # ResultとOutcomeは実行結果を不変値として返す。
    if ($ClassName.EndsWith('Result') -or $ClassName.EndsWith('Outcome')) {
        return "${ClassName}が示す処理結果、受理量、次状態を不変値として返す。"
    }

    # ReceiptとProofは実際に所有または完了した事実だけを表す。
    if ($ClassName.EndsWith('Receipt') -or $ClassName.EndsWith('Proof')) {
        return "${ClassName}が示す所有権移転または完了事実を、検証可能な証跡として保持する。"
    }

    # Storeは永続またはruntime上の記録を保存・検索する。
    if ($ClassName.EndsWith('Store')) {
        return "${ClassName}が示す記録を保存、検索、削除する。"
    }

    # ReconcilerとResolverは二つの事実を照合して一意な結果を選ぶ。
    if ($ClassName.EndsWith('Reconciler') -or $ClassName.EndsWith('Resolver')) {
        return "${ClassName}が示す計画、Receipt、実状態を照合し、一意に証明できる結果だけを返す。"
    }

    # Fingerprint、Signature、Identityは再起動やcacheで使う安定識別子を表す。
    if ($ClassName.EndsWith('Fingerprint') -or $ClassName.EndsWith('Signature') -or $ClassName.EndsWith('Identity')) {
        return "${ClassName}が示す対象を、順序と内容から安定して識別する。"
    }

    # Budget、Policy、Rules、Limitsは副作用なしの制約判断を担当する。
    if ($ClassName.EndsWith('Budget') -or $ClassName.EndsWith('Policy') -or $ClassName.EndsWith('Rules') -or $ClassName.EndsWith('Limits')) {
        return "${ClassName}が示す上限、時間予算、適格条件を副作用なしで判定する。"
    }

    # TargetとOwnerは実行を所有する外部実装の契約を表す。
    if ($ClassName.EndsWith('Target') -or $ClassName.EndsWith('Owner')) {
        return "${ClassName}が示す処理を所有・受理できる実装の契約を定義する。"
    }

    # Serviceは関連操作をまとめた境界で、内部実装を外部へ漏らさない。
    if ($ClassName.EndsWith('Service')) {
        return "${ClassName}が示す関連操作を一つのサービス境界として提供する。"
    }

    # Step、Slot、Stack、Payload、Recordはexact計画または取引の一要素を表す値型。
    if ($ClassName.EndsWith('Step') -or $ClassName.EndsWith('Slot') -or $ClassName.EndsWith('Stack') -or $ClassName.EndsWith('Payload') -or $ClassName.EndsWith('Record')) {
        return "${ClassName}が示す計画または取引の一要素を、不変のexact値として保持する。"
    }

    # DiagnosticsとReportは観測値を集約し、結果や在庫を変更しない。
    if ($ClassName.EndsWith('Diagnostics') -or $ClassName.EndsWith('Report') -or $ClassName.EndsWith('Metrics')) {
        return "${ClassName}が示す診断理由と観測値を集約する。ゲーム結果は変更しない。"
    }

    # Validator、Guard、Matcher、Preflightは採用前の証明だけを行う。
    if ($ClassName.EndsWith('Validator') -or $ClassName.EndsWith('Guard') -or $ClassName.EndsWith('Matcher') -or $ClassName.EndsWith('Preflight') -or $ClassName.EndsWith('Consistency')) {
        return "${ClassName}が示す入力や互換条件を検証し、証明不能な高速経路を拒否する。"
    }

    # Scheduler、Dispatcher、Throttleは予算を配分し、一つの仕事による独占を防ぐ。
    if ($ClassName.EndsWith('Scheduler') -or $ClassName.EndsWith('Dispatcher') -or $ClassName.EndsWith('Throttle')) {
        return "${ClassName}が示す仕事へtick予算を配分し、公平性とbackpressureを維持する。"
    }

    # TrackerとClockは世代または時刻を観測する単一責務に留める。
    if ($ClassName.EndsWith('Tracker') -or $ClassName.EndsWith('Clock')) {
        return "${ClassName}が示す世代、進捗、tick時刻を単調に追跡する。"
    }

    # ProjectionとCounterは正本を変更せず、表示値または合計値を導出する。
    if ($ClassName.EndsWith('Projection') -or $ClassName.EndsWith('Counter')) {
        return "${ClassName}が示す表示値または合計値を、exact正本から副作用なしで導出する。"
    }

    # RegistrationとCapabilitiesは任意連携が提供する能力と登録寿命を表す。
    if ($ClassName.EndsWith('Registration') -or $ClassName.EndsWith('Capabilities')) {
        return "${ClassName}が示す任意連携の能力または登録寿命を表す。"
    }

    # 未分類型は意図的にplaceholderを出し、JUnitで失敗させて責務overrideの追加を要求する。
    return "${ClassName}は、$($packageRoles[$PackageName])に属するクラス名どおりの単一処理または値を担当する。"
}

$javaFiles = Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter '*.java' |
    Where-Object { $_.Name -ne 'package-info.java' } |
    Sort-Object FullName

$rows = foreach ($file in $javaFiles) {
    $raw = Get-Content -LiteralPath $file.FullName -Raw
    $packageMatch = [regex]::Match($raw, '(?m)^package\s+([A-Za-z0-9_.]+);')
    # package宣言がない本番Javaは責務と依存方向を分類できないため生成を止める。
    if (-not $packageMatch.Success) {
        throw "Missing package declaration: $($file.FullName)"
    }
    $packageName = $packageMatch.Groups[1].Value
    $className = $file.BaseName
    $topLevelTypePattern = '(?m)^\s*(?:public\s+)?(?:(?:final|abstract|sealed|non-sealed)\s+)*(?:class|record|interface|enum|@interface)\s+' + [regex]::Escape($className) + '\b'
    # Javaファイル名と所有するトップレベル型が一致しなければ、誤ったFQCNを文書化せず生成を止める。
    if (-not [regex]::IsMatch($raw, $topLevelTypePattern)) {
        throw "Missing matching top-level type $className in $($file.FullName)"
    }
    $fqcn = "$packageName.$className"
    $lineCount = (Get-Content -LiteralPath $file.FullName).Count
    [pscustomobject]@{
        Package = $packageName
        Class = $className
        Fqcn = $fqcn
        Lines = $lineCount
        Role = Get-ClassRole -FullyQualifiedName $fqcn -PackageName $packageName -ClassName $className -Source $raw
    }
}

$builder = [System.Text.StringBuilder]::new()
[void]$builder.AppendLine('# ACOクラス責務一覧')
[void]$builder.AppendLine()
[void]$builder.AppendLine('この文書は、各本番トップレベル型が所有する仕事と依存境界を確認する正本です。')
[void]$builder.AppendLine('クラス追加・削除・責務変更時は、コードより先にIssue仕様書を更新し、本一覧も更新します。')
[void]$builder.AppendLine('一覧は`tools/update-class-responsibilities.ps1`で生成し、重要クラスの説明は同scriptのoverrideで管理します。')
[void]$builder.AppendLine('入れ子型は所有元の実装詳細です。独立した所有権を持たせる場合はトップレベル型へ抽出して本一覧へ載せます。')
[void]$builder.AppendLine()
[void]$builder.AppendLine('## ACOの目的')
[void]$builder.AppendLine()
[void]$builder.AppendLine('ACOはAE2を置き換えるMODではなく、結果・在庫・欠品・容量・進捗・取消・復旧を変えずに、')
[void]$builder.AppendLine('巨大自動クラフトの計算と実行負荷を減らす最適化・連携レイヤーです。速度より正確さと所有権を優先します。')
[void]$builder.AppendLine()
[void]$builder.AppendLine('## 依存方向')
[void]$builder.AppendLine()
[void]$builder.AppendLine('```text')
[void]$builder.AppendLine('AE2 / optional add-ons')
[void]$builder.AppendLine('          |')
[void]$builder.AppendLine('          v')
[void]$builder.AppendLine('mixin + access  ->  integration  ->  optimization / scheduler')
[void]$builder.AppendLine('                                           |')
[void]$builder.AppendLine('                                           v')
[void]$builder.AppendLine('                     engine / batch / transaction / craftingtable')
[void]$builder.AppendLine('                                           |')
[void]$builder.AppendLine('                                           v')
[void]$builder.AppendLine('                                  public api value contracts')
[void]$builder.AppendLine('```')
[void]$builder.AppendLine()
[void]$builder.AppendLine('- `mixin`は入口、`access`は型付き窓口であり、計算・会計を所有しません。')
[void]$builder.AppendLine('- `engine`以下から`mixin`を参照しません。Common初期化から`client`を直接ロードしません。')
[void]$builder.AppendLine('- 外部CPUアドオンは構造、実行、GUI、電力、進捗を所有し、ACOは計画と公開APIだけを提供します。')
[void]$builder.AppendLine('- 所有権移転前だけ安全なfallbackを許可し、移転後は再開、正確な取消返却、隔離のいずれかにします。')
[void]$builder.AppendLine()
[void]$builder.AppendLine('## 禁止事項')
[void]$builder.AppendLine()
[void]$builder.AppendLine('- BigInteger正本を`long`へクランプ、飽和、切り捨てして判定する。')
[void]$builder.AppendLine('- 所有権移転後に通常AE2へfallbackし、同じ入力を二重実行する。')
[void]$builder.AppendLine('- 計画値から、実クラフトまたは検証済みReceiptなしで成果物を生成する。')
[void]$builder.AppendLine('- 非同期スレッドからWorld、Grid、Block Entity、実在庫へ直接触る。')
[void]$builder.AppendLine('- MixinへPlanner、会計、外部CPU実行ロジックを実装する。')
[void]$builder.AppendLine('- 外部MODの構造、GUI、レシピ、テクスチャをACOの所有物として変更する。')
[void]$builder.AppendLine()
[void]$builder.AppendLine('## 大規模クラスのレビュー')
[void]$builder.AppendLine()
[void]$builder.AppendLine('| クラス | 行数 | 判断 |')
[void]$builder.AppendLine('|---|---:|---|')
$largestClassReviewCount = 10 # 行数上位10件だけを人手レビュー対象として先頭へ表示する。
$largestRows = $rows | Sort-Object Lines -Descending | Select-Object -First $largestClassReviewCount
# 行数上位だけをレビュー表へ出し、全クラスの役割は後続一覧へ分離する。
foreach ($row in $largestRows) {
    $note = if ($reviewNotes.ContainsKey($row.Class)) {
        $reviewNotes[$row.Class]
    } else {
        '中。責務一覧を基準に、挙動固定試験を追加してから分割可否を別Issueで判断する。'
    }
    [void]$builder.AppendLine("| ``$($row.Class)`` | $($row.Lines) | $note |")
}
[void]$builder.AppendLine()
[void]$builder.AppendLine('## パッケージ責務')
[void]$builder.AppendLine()
[void]$builder.AppendLine('| パッケージ | 責務 |')
[void]$builder.AppendLine('|---|---|')
# 現在のsourceに存在するパッケージだけを安定順で掲載する。
foreach ($packageName in ($rows.Package | Sort-Object -Unique)) {
    $role = if ($packageRoles.Contains($packageName)) {
        $packageRoles[$packageName]
    } else {
        'この版固有の連携契約。呼出元と所有権を個別クラス行で確認する。'
    }
    [void]$builder.AppendLine("| ``$packageName`` | $role |")
}
[void]$builder.AppendLine()
[void]$builder.AppendLine('## 全トップレベル型一覧')
[void]$builder.AppendLine()
[void]$builder.AppendLine("本版の本番トップレベル型: **$($rows.Count)件**")
[void]$builder.AppendLine()

# パッケージ単位に全トップレベル型を一度だけ掲載し、更新漏れをJUnitで検出する。
foreach ($group in ($rows | Group-Object Package | Sort-Object Name)) {
    [void]$builder.AppendLine("### ``$($group.Name)``")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('| クラス | 仕事 |')
    [void]$builder.AppendLine('|---|---|')
    foreach ($row in ($group.Group | Sort-Object Class)) {
        [void]$builder.AppendLine("| ``$($row.Fqcn)`` | $($row.Role) |")
    }
    [void]$builder.AppendLine()
}

$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
$document = ($builder.ToString() -replace "`r`n", "`n").TrimEnd("`n") + "`n"
[System.IO.File]::WriteAllText($outputPath, $document, $utf8WithoutBom)
Write-Output "Updated $outputPath with $($rows.Count) production top-level types."
