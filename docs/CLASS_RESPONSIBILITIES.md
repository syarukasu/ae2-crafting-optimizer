# ACOクラス責務一覧

この文書は、各本番トップレベル型が所有する仕事と依存境界を確認する正本です。
クラス追加・削除・責務変更時は、コードより先にIssue仕様書を更新し、本一覧も更新します。
一覧は`tools/update-class-responsibilities.ps1`で生成し、重要クラスの説明は同scriptのoverrideで管理します。
入れ子型は所有元の実装詳細です。独立した所有権を持たせる場合はトップレベル型へ抽出して本一覧へ載せます。

## ACOの目的

ACOはAE2を置き換えるMODではなく、結果・在庫・欠品・容量・進捗・取消・復旧を変えずに、
巨大自動クラフトの計算と実行負荷を減らす最適化・連携レイヤーです。速度より正確さと所有権を優先します。

## 依存方向

```text
AE2 / optional add-ons
          |
          v
mixin + access  ->  integration  ->  optimization / scheduler
                                           |
                                           v
                     engine / batch / transaction / craftingtable
                                           |
                                           v
                                  public api value contracts
```

- `mixin`は入口、`access`は型付き窓口であり、計算・会計を所有しません。
- `engine`以下から`mixin`を参照しません。Common初期化から`client`を直接ロードしません。
- 外部CPUアドオンは構造、実行、GUI、電力、進捗を所有し、ACOは計画と公開APIだけを提供します。
- 所有権移転前だけ安全なfallbackを許可し、移転後は再開、正確な取消返却、隔離のいずれかにします。

## 禁止事項

- BigInteger正本を`long`へクランプ、飽和、切り捨てして判定する。
- 所有権移転後に通常AE2へfallbackし、同じ入力を二重実行する。
- 計画値から、実クラフトまたは検証済みReceiptなしで成果物を生成する。
- 非同期スレッドからWorld、Grid、Block Entity、実在庫へ直接触る。
- MixinへPlanner、会計、外部CPU実行ロジックを実装する。
- 外部MODの構造、GUI、レシピ、テクスチャをACOの所有物として変更する。

## 大規模クラスのレビュー

| クラス | 行数 | 判断 |
|---|---:|---|
| `PhysicalCraftingTreeTransaction` | 3521 | 高。state machineと永続Codecが同居。Issue #87では数量Mapだけ分離し、Receipt/Codec分割は専用回帰試験を伴う別Issueにする。 |
| `ACOConfig` | 2047 | 中。長大だがConfig IDと既定値の正本として凝集している。key互換を固定する試験なしに分割しない。 |
| `AqeBigCraftingExecutionManager` | 1631 | 高。外部CPU所有権と復旧境界。起動・再起動・取消試験を用意してから段階分割する。 |
| `CompiledRootProgram` | 1332 | 中。計算核として大きいが副作用は限定的。コンパイルと評価の分離候補。 |
| `BigCraftingJob` | 1215 | 高。永続状態とWindow貸出を所有。NBT Codec分離はschema回帰試験と同時に行う。 |
| `Ae2AuthoritativeCraftingPlanner` | 1042 | 中。採用判定と計画生成の境界を維持し、fallback条件を別クラスへ散らさない。 |
| `TransactionalCraftingExecutorV2` | 933 | 高。所有権移転後の処理。見た目の短縮目的では分割せず、phase単位の試験を先に増やす。 |
| `ExactNetworkStorageBridge` | 906 | 高。実在庫境界。snapshotとmutationの分離候補だが原子性試験が先。 |
| `BigCraftingRuntime` | 873 | 中。公開API側のruntime registry。Host runtimeとの責務重複を監視する。 |
| `BigCraftingHostRuntime` | 861 | 高。外部Host容量と予約を所有。複数Job仕様を勝手に導入しない。 |

## パッケージ責務

| パッケージ | 責務 |
|---|---|
| `com.syaru.ae2craftingoptimizer` | 起動、Forge/NeoForgeイベント接続、全体初期化。 |
| `com.syaru.ae2craftingoptimizer.access` | Mixinが外部クラスの内部状態を型付きで公開する契約。判断ロジックは持たない。 |
| `com.syaru.ae2craftingoptimizer.api.batch` | 旧Pattern Batch公開API。互換性維持を優先する。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2` | 所有権、Receipt、commit、復旧を明示するTransactional Batch公開API。 |
| `com.syaru.ae2craftingoptimizer.api.big` | BigInteger計画、Host、進捗、公開連携API。 |
| `com.syaru.ae2craftingoptimizer.api.contract` | 版付きpayload、revision、Receipt、正確在庫の公開連携契約。 |
| `com.syaru.ae2craftingoptimizer.api.craftingtable` | 作業台物理Batch Workerとの公開契約。 |
| `com.syaru.ae2craftingoptimizer.api.execution` | Exact Vector実行所有者を宣言する公開契約。 |
| `com.syaru.ae2craftingoptimizer.api.vector` | exact数量のVector計画、保存、Storage境界API。 |
| `com.syaru.ae2craftingoptimizer.batch` | Pattern Batchの完全一致、Escrow、Receipt、再照合。 |
| `com.syaru.ae2craftingoptimizer.client` | クライアント表示、入力、BigInteger表示用state。 |
| `com.syaru.ae2craftingoptimizer.command` | 観測と安全な失効だけを行う診断コマンド。 |
| `com.syaru.ae2craftingoptimizer.config` | 機能スイッチ、上限、時間予算のCommon Config。 |
| `com.syaru.ae2craftingoptimizer.craftingamount` | long注文数をAE2 Menuへ渡すserver側境界。 |
| `com.syaru.ae2craftingoptimizer.engine` | コンパイル済みグラフ、Planner、BigInteger会計の計算核。 |
| `com.syaru.ae2craftingoptimizer.engine.craftingtable` | 所有権移転後の作業台物理クラフト取引、Escrow、復旧。 |
| `com.syaru.ae2craftingoptimizer.engine.vector` | exact Vector計画の検証、在庫snapshot、表示投影。 |
| `com.syaru.ae2craftingoptimizer.gtceu` | GTCEu Recipe IntentとNative Batchの任意連携。 |
| `com.syaru.ae2craftingoptimizer.integration` | AE2、Advanced AE、任意MOD、exact storageへの版別接続。 |
| `com.syaru.ae2craftingoptimizer.intent` | Providerが意図するrecipeを短期間伝える検索hint。 |
| `com.syaru.ae2craftingoptimizer.lifecycle` | server起動、停止、reload、registry accessの順序管理。 |
| `com.syaru.ae2craftingoptimizer.mekanism` | Mekanism Recipe IntentとNative Batchの任意連携。 |
| `com.syaru.ae2craftingoptimizer.mixin` | 外部処理へ薄い入口を追加するMixinと適用plugin。 |
| `com.syaru.ae2craftingoptimizer.network` | BigInteger容量と進捗を同期する版付き通信路。 |
| `com.syaru.ae2craftingoptimizer.optimization` | 世代付きcache、時間予算、診断、保守的高速経路。 |
| `com.syaru.ae2craftingoptimizer.scheduler` | ジョブとProviderへtick予算を公平配分するscheduler。 |
| `com.syaru.ae2craftingoptimizer.transaction` | Native Batch取引、Journal、再起動復旧、収支検査。 |
| `com.syaru.ae2craftingoptimizer.util` | 副作用を持たないfingerprintなどの共通処理。 |

## 全トップレベル型一覧

本版の本番トップレベル型: **367件**

### `com.syaru.ae2craftingoptimizer`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer` | MOD entrypoint。Config、network、lifecycle、optional integrationを登録し、個別計算は各層へ委譲する。 |

### `com.syaru.ae2craftingoptimizer.access`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.access.AdvancedAeClusterExecutionAccess` | AdvancedAeClusterExecutionAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingJobAccess` | Advanced AE実JobへBigInteger正本を設置・同期するためのversion-pinned契約。 |
| `com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingLogicAccess` | Advanced AE標準の完了・取消通知経路へExact Jobを戻すための最小Invoker契約。 |
| `com.syaru.ae2craftingoptimizer.access.BigCapacityPlanBoundaryAccess` | Big容量計画を受理または拒否する専用Mixinが対象CPUへ適用済みであることを示す。 |
| `com.syaru.ae2craftingoptimizer.access.CheckedCraftingArithmeticHookAccess` | AE2クラフト計算のlong境界検査Mixinが適用済みであることを示す印。 |
| `com.syaru.ae2craftingoptimizer.access.CraftingClusterHostTransactionAccess` | CraftingClusterHostTransactionAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.CraftingClusterRecoveryAccess` | CraftingClusterRecoveryAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess` | CraftingJobTransactionAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess` | CraftingLogicTransactionAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess` | CraftingOwnerTransactionAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.CraftingServiceCalculationHookAccess` | CraftingServiceの計算共有・事前判定Mixinが適用済みであることを示す印。 |
| `com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess` | CraftingTaskProgressAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.DelegatingMEInventoryAccess` | DelegatingMEInventoryAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.ExactBigIntegerInventoryHookAccess` | 正確なBigInteger在庫Snapshotの生成・伝播・失効Mixinが適用済みであることを示す印。 |
| `com.syaru.ae2craftingoptimizer.access.ExactCraftingInventoryAccess` | AE2のListCraftingInventoryへBigInteger数量を保持させる拡張契約。 |
| `com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess` | AE2系の実ExecutingCraftingJobへBigInteger正本を設置・同期する共通契約。 |
| `com.syaru.ae2craftingoptimizer.access.ExactCraftingLogicAccess` | AE2系CraftingCpuLogicを本来の完了通知順序で閉じる共通契約。 |
| `com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess` | ExtendedAePlusBigIntegerCellInventoryAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.MekanismCachedRecipeAccess` | MekanismCachedRecipeAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess` | NetworkStorageMountsAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.NumberEntryWidgetAccess` | NumberEntryWidgetAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |
| `com.syaru.ae2craftingoptimizer.access.PatternProviderTransactionAccess` | PatternProviderTransactionAccessが示す外部状態を型付きで公開するMixin用Access契約。判断や会計は持たない。 |

### `com.syaru.ae2craftingoptimizer.api.batch`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.api.batch.ExactPatternFormula` | 一意な作業台Patternを、入力slotと出力係数だけの不変式へ変換する。 |
| `com.syaru.ae2craftingoptimizer.api.batch.PatternBatchAdapter` | 完全なProcessing Patternを一回以上、機械側の所有物として受理するAdapter契約。 |
| `com.syaru.ae2craftingoptimizer.api.batch.PatternBatchApi` | PatternBatchApiが示す機能を外部MODへ公開する安定Facade。 |
| `com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget` | 一回のAdapter commitに対する不変の操作数・実時間境界。 |
| `com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext` | PatternBatchContextが示す一回の要求に必要な入力、所有者、実行条件を保持する。 |
| `com.syaru.ae2craftingoptimizer.api.batch.PatternBatchResult` | PatternBatchResultが示す処理結果、受理量、次状態を不変値として返す。 |

### `com.syaru.ae2craftingoptimizer.api.batch.v2`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.api.batch.v2.BatchCpuAccountingMode` | Batch一件をCrafting CPUのtick予算へどう数えるか。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.BatchEnergyAccountingMode` | Transactional Batchの電力を送信元と実機のどちらが所有するか。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.BatchOwnershipProof` | Batch入力の所有権が送信先へ移ったことを示す証明。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.BatchPayloadFingerprint` | 取引IDとは独立した、入力・期待出力・実行数の決定的Fingerprint。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.BatchRecoveryResult` | BatchRecoveryResultが示す処理結果、受理量、次状態を不変値として返す。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceipt` | BatchSourceReceiptが示す所有権移転または完了事実を、検証可能な証跡として保持する。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceiptStore` | BatchSourceReceiptStoreが示す記録を保存、検索、削除する。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReconciler` | BatchSourceReconcilerが示す計画、Receipt、実状態を照合し、一意に証明できる結果だけを返す。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord` | BatchTransactionRecordが示す計画または取引の一要素を、不変のexact値として保持する。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt` | NativeBatchReceiptが示す所有権移転または完了事実を、検証可能な証跡として保持する。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore` | NativeBatchReceiptStoreが示す記録を保存、検索、削除する。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchCommit` | prepare済みBatchの所有権証明、受理数、Receiptをまとめ、commit可否を表す。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchIdentity` | ACO Journalと外部Adapterが共有する、Pattern Batchの正規識別API。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api` | PatternBatchV2Apiが示す機能を外部MODへ公開する安定Facade。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch` | Adapterへ渡す前にidentity、payload、要求回数を固定した不変Batch。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget` | 外部Inventoryではなく、Pattern Provider自身がBatchの永続所有者になることを示す。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.SourceRecoveryResult` | SourceRecoveryResultが示す処理結果、受理量、次状態を不変値として返す。 |
| `com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter` | TransactionalPatternBatchAdapterが示す二つのAPI境界を接続し、対応不能時は元の所有者へ判断を戻す。 |

### `com.syaru.ae2craftingoptimizer.api.big`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.api.big.AeKeyBigCraftingCodec` | AeKeyBigCraftingCodecが示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。 |
| `com.syaru.ae2craftingoptimizer.api.big.AeKeyBigCraftingPacketCodec` | AeKeyBigCraftingPacketCodecが示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi` | AQE、InsaneAEなどへexact計画、容量、台帳を公開する版付きFacade。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostBackendState` | BigInteger Hostの原子的な状態表示に使う安定ラベル。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRegistration` | 一つのCPU所有者とBigCraftingHostRuntimeを結ぶ世代付き登録ハンドル。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRegistry` | CPU所有者ごとのBigInteger Hostを、GCではなく明示的なライフサイクルで管理する。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRuntime` | 明示登録された外部CPU Hostのexact容量予約、snapshot、復旧を管理する。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostSnapshot` | 一回の会計世代から取得した、分割読み取りを避ける不変Snapshot。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingPacketKeyCodec` | BigCraftingPacketKeyCodecが示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingRuntime` | BigInteger計画sidecarとruntime jobの登録、照会、寿命管理を行う。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusInbox` | 分割受信したBigInteger進捗pageをHost世代ごとに集約するclient側受信箱。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPage` | BigInteger容量・使用量・Job進捗の一部分を運ぶ版付きpage。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPageCodec` | BigCraftingStatusPageCodecが示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigIntegerAmountLedger` | Add-on向けの正確な量会計。 |
| `com.syaru.ae2craftingoptimizer.api.big.BigIntegerCraftingPlanView` | 外部MODがACO計画のexact bytes、要求量、不足量を切り捨てず読むための公開view。 |

### `com.syaru.ae2craftingoptimizer.api.contract`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.api.contract.BatchTargetRevision` | Batch targetの内容世代と有効性を一つの単調revisionとして表す。 |
| `com.syaru.ae2craftingoptimizer.api.contract.CanonicalBigIntegerCodec` | CanonicalBigIntegerCodecが示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.CraftingTableBatchSnapshot` | CraftingTableBatchSnapshotが示す時点の状態を、検証可能な値として保持する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.ExactCountLimits` | ExactCountLimitsが示す上限、時間予算、適格条件を副作用なしで判定する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.ExactCountPayload` | ExactCountPayloadが示す計画または取引の一要素を、不変のexact値として保持する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.ExactCountPayloadCodec` | ExactCountPayloadCodecが示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.ExactStorageAmountProvider` | 外部ストレージがAEKey別の正確なBigInteger在庫SnapshotをACOへ公開する安定契約。 |
| `com.syaru.ae2craftingoptimizer.api.contract.IntegrationCapabilities` | IntegrationCapabilitiesが示す任意連携の能力または登録寿命を表す。 |
| `com.syaru.ae2craftingoptimizer.api.contract.IntegrationCapabilitiesRegistry` | IntegrationCapabilitiesRegistryが示す実装またはHostの登録、解除、検索を管理する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.LiveTransactionProof` | LiveTransactionProofが示す所有権移転または完了事実を、検証可能な証跡として保持する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.LiveTransactionState` | LiveTransactionStateが示す時点の状態を、検証可能な値として保持する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.PayloadKind` | exact payloadがItem、Fluid、Chemicalなど何を表すかを列挙する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.ReceiptOrphanPolicy` | ReceiptOrphanPolicyが示す上限、時間予算、適格条件を副作用なしで判定する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.ReceiptReservation` | Receiptへ対応する所有量、期限、target revisionを固定した予約値。 |
| `com.syaru.ae2craftingoptimizer.api.contract.ReceiptReservationProtocol` | Receipt予約のprepare、commit、cancel、復旧順序を外部連携へ公開する契約。 |
| `com.syaru.ae2craftingoptimizer.api.contract.ReceiptReservationState` | ReceiptReservationStateが示す時点の状態を、検証可能な値として保持する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.RevisionWakeupApi` | RevisionWakeupApiが示す機能を外部MODへ公開する安定Facade。 |
| `com.syaru.ae2craftingoptimizer.api.contract.RevisionWakeupListener` | target revision変更時に待機中処理を再評価させる通知callback。 |
| `com.syaru.ae2craftingoptimizer.api.contract.RevisionWakeupRegistration` | RevisionWakeupRegistrationが示す任意連携の能力または登録寿命を表す。 |
| `com.syaru.ae2craftingoptimizer.api.contract.SnapshotRevisionTracker` | SnapshotRevisionTrackerが示す世代、進捗、tick時刻を単調に追跡する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.SnapshotState` | SnapshotStateが示す時点の状態を、検証可能な値として保持する。 |
| `com.syaru.ae2craftingoptimizer.api.contract.SupportedFeature` | ACO連携先が明示的に保証するexact機能を列挙する。 |

### `com.syaru.ae2craftingoptimizer.api.craftingtable`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode` | 一括作業台仕事の数量会計を誰が所有するか。 |
| `com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest` | 一つの確定作業台Patternを一つの物理Worker仕事へ渡す不変Request。 |
| `com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot` | 物理Workerの小さな進捗Receipt。 |
| `com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget` | Pattern Provider配下の物理作業台設備が実装する最小契約。 |

### `com.syaru.ae2craftingoptimizer.api.execution`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.api.execution.VectorBatchExecutionOwner` | 外部クラフトエンジンが、大量の論理クラフトを一つの定数時間Batchとして処理できることをACOへ伝える。 |

### `com.syaru.ae2craftingoptimizer.api.vector`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.api.vector.ExactCraftingInputSlot` | 一つの作業台Pattern slotでPlannerが選んだ、一回実行当たりの具体入力。 |
| `com.syaru.ae2craftingoptimizer.api.vector.ExactCraftingStep` | 決定的な作業台Pattern一種類を、実Workerが一度組み立てて係数展開する実行Step。 |
| `com.syaru.ae2craftingoptimizer.api.vector.ExactStack` | AEKey一種類と、その正確な非負longを超えられる数量。 |
| `com.syaru.ae2craftingoptimizer.api.vector.ExactStorageMutationResult` | 一AEKeyの正確在庫操作結果。 |
| `com.syaru.ae2craftingoptimizer.api.vector.ExactVectorCraftingApi` | ACO Exact Vector Craftingの、既存BigInteger APIとは独立した公開契約版。 |
| `com.syaru.ae2craftingoptimizer.api.vector.ExactVectorDiagnostics` | 外部Exact Vector設備が、数量非依存の低コスト統計をACOへ通知するAPI。 |
| `com.syaru.ae2craftingoptimizer.api.vector.ExactVectorExecutionBudget` | Exact Vector Executorが、同じME Gridの空き時間から論理段をまとめて取得する公開境界。 |
| `com.syaru.ae2craftingoptimizer.api.vector.ExactVectorStoragePolicy` | Infinity BigInteger Cellの派生実装が、ACOの直接BigInteger挿入へ追加制約を伝える契約。 |
| `com.syaru.ae2craftingoptimizer.api.vector.ExactVectorStorageService` | Exact Vector Executorが使用する、数量分割を行わないBigInteger在庫境界。 |
| `com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch` | 計算スレッドで完成し、実行中に再びグラフ探索しない不変Vector計画。 |
| `com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatchCodec` | exact Vector計画を上限付きNBTへ符号化・復号し、schemaを検証する。 |
| `com.syaru.ae2craftingoptimizer.api.vector.VectorResourceMode` | Exact Vector Transactionで、境界入出力を所有・配送する側を明示する。 |

### `com.syaru.ae2craftingoptimizer.batch`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.batch.Ae2BatchSourceReconciler` | Ae2BatchSourceReconcilerが示す計画、Receipt、実状態を照合し、一意に証明できる結果だけを返す。 |
| `com.syaru.ae2craftingoptimizer.batch.BatchSourceReceiptLedger` | BatchSourceReceiptLedgerが示す所有量、Receipt、収支をexact値で記録・検証する。 |
| `com.syaru.ae2craftingoptimizer.batch.ExactMultisetMatcher` | ExactMultisetMatcherが示す入力や互換条件を検証し、証明不能な高速経路を拒否する。 |
| `com.syaru.ae2craftingoptimizer.batch.ExactPatternSnapshot` | ExactPatternSnapshotが示す時点の状態を、検証可能な値として保持する。 |
| `com.syaru.ae2craftingoptimizer.batch.NativeBatchReceiptLedger` | NativeBatchReceiptLedgerが示す所有量、Receipt、収支をexact値で記録・検証する。 |
| `com.syaru.ae2craftingoptimizer.batch.NativePatternBatchSupport` | NativePatternBatchSupportが示す二つのAPI境界を接続し、対応不能時は元の所有者へ判断を戻す。 |
| `com.syaru.ae2craftingoptimizer.batch.PatternProviderBatchEscrow` | Pattern Providerの永続send bufferをV2取引のEscrowとして使う共通処理。 |
| `com.syaru.ae2craftingoptimizer.batch.PatternProviderReceiptResolver` | PatternProviderReceiptResolverが示す計画、Receipt、実状態を照合し、一意に証明できる結果だけを返す。 |
| `com.syaru.ae2craftingoptimizer.batch.PatternTaskFingerprint` | PatternTaskFingerprintが示す対象を、順序と内容から安定して識別する。 |
| `com.syaru.ae2craftingoptimizer.batch.SequentialPatternProviderBatchAdapter` | 保守的な標準Adapter。 |

### `com.syaru.ae2craftingoptimizer.client`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.client.AsyncTerminalView` | 端末検索・sortの非同期結果を世代付きで保持し、古い結果の反映を拒否する。 |
| `com.syaru.ae2craftingoptimizer.client.BigAmountFormatter` | AE2のlong表示範囲を越える数量を、丸めによる上限張り付きなしで表示する。 |
| `com.syaru.ae2craftingoptimizer.client.BigCraftingPlanClientStore` | 現在開いているCraftConfirmMenuだけへ、BigInteger表示用Sidecarを提供する。 |
| `com.syaru.ae2craftingoptimizer.client.ClientRepoUpdateScheduler` | ClientRepoUpdateSchedulerが示す仕事へtick予算を配分し、公平性とbackpressureを維持する。 |
| `com.syaru.ae2craftingoptimizer.client.LongCraftAmountClientHandler` | 戻る操作で再作成されたCraftAmountMenuへ、サーバーのlong初期値を同期する。 |
| `com.syaru.ae2craftingoptimizer.client.LongCraftAmountClientParser` | NumberEntryWidgetのlongValue()が範囲外で折り返さないよう、 CraftAmountScreenの入力だけをBigDecimalから厳密変換する。 |

### `com.syaru.ae2craftingoptimizer.command`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.command.ACOIntentCommands` | Recipe Intent、cache、Batch、計算統計を表示・安全に失効するserver commandを登録する。 |

### `com.syaru.ae2craftingoptimizer.config`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.config.ACOConfig` | 全Common Config key、既定値、範囲、説明を登録する唯一のConfig正本。 |

### `com.syaru.ae2craftingoptimizer.craftingamount`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountMenuBridge` | CraftAmountMenuへ追加するlong専用の狭い境界。 |
| `com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountRules` | AE2本来のint注文と、ACOが追加するlong注文の境界を一か所で管理する。 |
| `com.syaru.ae2craftingoptimizer.craftingamount.LongCraftConfirmMenuBridge` | CraftConfirmMenuのintフィールドを変更せず、longルート量をSidecarで保持する。 |

### `com.syaru.ae2craftingoptimizer.engine`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.engine.Ae2AuthoritativeCraftingPlanner` | Shadow一致済みの決定的rootだけをACO計画へ昇格し、証明不能なら採用を辞退する。 |
| `com.syaru.ae2craftingoptimizer.engine.Ae2BigCraftingPlanFactory` | AE2 Pattern木からexact BigInteger計画とsimulation不足計画を構築する。 |
| `com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache` | Pattern世代ごとのコンパイル済みグラフsnapshotを保持し、世代変更で失効する。 |
| `com.syaru.ae2craftingoptimizer.engine.Ae2CompiledPatternFactory` | Ae2CompiledPatternFactoryが示す値を、検証済み入力から生成する。 |
| `com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars` | 純正AE2 CraftingPlanへexact真値をidentity関連付けし、外部型互換を維持する。 |
| `com.syaru.ae2craftingoptimizer.engine.Ae2CraftingShadowValidator` | AE2標準計画を正としてRoot Programの全会計を比較し、同一世代Programの採用実績を蓄積する。 |
| `com.syaru.ae2craftingoptimizer.engine.Ae2ReferencedInventory` | AE2の全在庫をMap化せず、Compiled Root Programが参照するキーだけを取得する。 |
| `com.syaru.ae2craftingoptimizer.engine.Ae2StrictCraftingTopology` | 代替、循環、返却物などを検査し、数式計画へ安全に変換できるPattern DAGだけを認定する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan` | 各AEKey量とPattern回数はlongに収まるが、合計CPU容量だけがlongを超える厳密計画。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCountMath` | BigInteger数量Mapの加算・乗算・ceilDivを正確に行う副作用なし算術。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCraftingCapacityLedger` | BigCraftingCapacityLedgerが示す所有量、Receipt、収支をexact値で記録・検証する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCraftingCpuLedger` | BigCraftingCpuLedgerが示す所有量、Receipt、収支をexact値で記録・検証する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCraftingInventory` | 計画中の在庫使用量と不足量をAEKey別BigIntegerで保持する仮想在庫。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCraftingJob` | BigInteger注文の永続状態とlong実行Windowの貸出・回収を管理する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec` | BigCraftingKeyCodecが示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCraftingPlan` | BigCraftingPlanが示すクラフト計画またはコンパイル済みプログラムを不変値として保持する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCraftingPlanner` | BigCraftingPlannerが示す入力から、副作用なしでクラフト計画を構築する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCraftingPlanSummary` | CraftConfirm画面へ送る、容量と素材別数量のBigInteger正本。 |
| `com.syaru.ae2craftingoptimizer.engine.BigCraftingTaskProgress` | Pattern taskの総数、完了数、待機数をBigIntegerで追跡する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigExactCraftingByteCounter` | AE2 15.4.10のCPU bytes式をBigIntegerの有理数として計算する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigExecutionWindow` | BigInteger残量からlegacy実行へ貸し出す、Long.MAX_VALUE以下の一時Window。 |
| `com.syaru.ae2craftingoptimizer.engine.BigIntegerBufferCodec` | BigIntegerBufferCodecが示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan` | Pattern回数または個別AEKey量がsigned longを超える、AQE専用のBigInteger親計画。 |
| `com.syaru.ae2craftingoptimizer.engine.BigIntegerNbtCodec` | BigIntegerNbtCodecが示す値を、上限とschemaを検証しながら保存・通信形式へ相互変換する。 |
| `com.syaru.ae2craftingoptimizer.engine.BigIntegerPlanProjection` | BigInteger正本を変更せず、AE2のlong固定表示境界へ投影する共通処理。 |
| `com.syaru.ae2craftingoptimizer.engine.BigIntegerSimulationPlan` | long計算へ戻さずに返す、BigInteger正本の不足simulation計画。 |
| `com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars` | AE2のlong KeyCounterへ対応する、正確なBigInteger在庫Snapshot。 |
| `com.syaru.ae2craftingoptimizer.engine.CheckedLongMath` | 通常計画のlong演算をexact検査し、overflow時は昇格用例外を返す。 |
| `com.syaru.ae2craftingoptimizer.engine.CompiledCraftingGraph` | 世代内で再利用するPattern nodeと入出力edgeの不変グラフ。 |
| `com.syaru.ae2craftingoptimizer.engine.CompiledPattern` | 一つのPatternをnode ID、exact係数、候補情報へ正規化した不変値。 |
| `com.syaru.ae2craftingoptimizer.engine.CompiledPlanningSession` | 一計算で共有するgraph snapshot、在庫snapshot、取消token、世代検証を束ねる。 |
| `com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram` | 一つのrootから到達する決定的DAGを配列プログラムへ変換し、数量に依存しない計算を行う。 |
| `com.syaru.ae2craftingoptimizer.engine.CompiledRootQualificationRegistry` | AE2標準計画とのShadow一致実績を、世代付きRoot Program単位で記録する。 |
| `com.syaru.ae2craftingoptimizer.engine.CountOverflowException` | CountOverflowExceptionが示す失敗を呼出側へ型付きで通知する。 |
| `com.syaru.ae2craftingoptimizer.engine.CraftingPlanShadowComparator` | ACO計画とAE2標準計画の結果・不足・bytesを比較し、不一致なら採用を拒否する。 |
| `com.syaru.ae2craftingoptimizer.engine.ExactCraftingByteCounter` | AE2 15.4.10の線形CraftingTreeと同じ順番でCPU bytesを再計算する。 |
| `com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobLedger` | AE2実JobのBigIntegerカウンタを再起動後も検証する永続Journal。 |
| `com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobState` | Advanced AE実Jobへ付随するexact task、waiting、output、Receiptのsidecar正本。 |
| `com.syaru.ae2craftingoptimizer.engine.ExactPlanPatternRevalidator` | Exact計画が参照するPatternだけを、CPU提出直前のCraftingServiceへ再照合する。 |
| `com.syaru.ae2craftingoptimizer.engine.GenerationAwareGraphCache` | GenerationAwareGraphCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.engine.LongCraftingPlan` | LongCraftingPlanが示すクラフト計画またはコンパイル済みプログラムを不変値として保持する。 |
| `com.syaru.ae2craftingoptimizer.engine.LongCraftingPlanner` | 通常規模の注文をchecked long演算で展開するPlanner。 |
| `com.syaru.ae2craftingoptimizer.engine.OverflowPromotingCraftingPlanner` | checked long Plannerを先に試し、overflowした注文だけ最初からBigIntegerで再計算する。 |
| `com.syaru.ae2craftingoptimizer.engine.PlanningCancellationToken` | 共有計算を直接cancelせず、呼出者ごとの取消要求を協調的に伝えるtoken。 |
| `com.syaru.ae2craftingoptimizer.engine.PlanningCancelledException` | PlanningCancelledExceptionが示す失敗を呼出側へ型付きで通知する。 |
| `com.syaru.ae2craftingoptimizer.engine.PlanningGenerationSnapshot` | PlanningGenerationSnapshotが示す時点の状態を、検証可能な値として保持する。 |
| `com.syaru.ae2craftingoptimizer.engine.PlanningGuard` | PlanningGuardが示す入力や互換条件を検証し、証明不能な高速経路を拒否する。 |
| `com.syaru.ae2craftingoptimizer.engine.PlanningRuntimeEpoch` | 現在のサーバープロセスを識別する一時ID。 |
| `com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker` | RecipeGenerationTrackerが示す世代、進捗、tick時刻を単調に追跡する。 |
| `com.syaru.ae2craftingoptimizer.engine.RootProgramFailure` | Root Programをコンパイルできなかった正確な理由。 |
| `com.syaru.ae2craftingoptimizer.engine.StalePlanningSnapshotException` | StalePlanningSnapshotExceptionが示す失敗を呼出側へ型付きで通知する。 |
| `com.syaru.ae2craftingoptimizer.engine.SymbolicCraftingPlanner` | 決定的なPattern DAGを CompiledRootProgram へ変換し、数式一巡で計画する公開Facade。 |
| `com.syaru.ae2craftingoptimizer.engine.WideArithmeticPreflight` | 通常計画へBigInteger Plannerを重ねる前に、全量クラフト時の安全な上限だけを調べる。 |
| `com.syaru.ae2craftingoptimizer.engine.WideCraftingPlan` | AE2のsigned long APIだけでは表現できない真値を持つACO内部計画。 |
| `com.syaru.ae2craftingoptimizer.engine.WidePlanSubmissionGuard` | BigInteger台帳非対応CPUの拒否を、容量不足と誤報しないための共通境界。 |
| `com.syaru.ae2craftingoptimizer.engine.WidePlanUnavailableException` | wide計画を正確に作れず、AE2のoverflowするlong計算へ戻してはいけないことを示す例外。 |

### `com.syaru.ae2craftingoptimizer.engine.craftingtable`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.engine.craftingtable.CraftingTableBatchTargetResolver` | ACOの汎用CraftingTableBatchTargetをAE2 Pattern Providerから解決する正本。 |
| `com.syaru.ae2craftingoptimizer.engine.craftingtable.ExactCountMap` | 正のBigInteger数量Mapの検証、順序付き複製、包含判定、exact加算を行う副作用なし共通部品。 |
| `com.syaru.ae2craftingoptimizer.engine.craftingtable.ExactCraftingEscrow` | 一注文が所有する境界素材、中間素材、最終成果物をBigIntegerのまま原子的に増減する。 |
| `com.syaru.ae2craftingoptimizer.engine.craftingtable.ExactMutationReconciler` | 保存済みbefore/afterと現在値を照合し、まだ適用していないキーだけを返す。 |
| `com.syaru.ae2craftingoptimizer.engine.craftingtable.PhysicalCraftingTreeTransaction` | 作業台ツリーの所有権移転後state machine。予約、Worker Receipt、取消、返却、隔離、NBT復元を統括する。 |

### `com.syaru.ae2craftingoptimizer.engine.vector`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.engine.vector.LongClampedProgressProjection` | Exact Vectorの正確な数量を変更せず、AE2のlong表示へ進捗を投影する。 |
| `com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanner` | Compiled Root Programを、要求数量に依存しない一つのExact Vector式へ変換する。 |
| `com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanValidator` | Config上限とBigInteger桁数を、Executor選択より前に一か所で検査する。 |
| `com.syaru.ae2craftingoptimizer.engine.vector.VectorInventorySnapshot` | Vector計画が参照したキーだけを保持する不変の正確在庫Snapshot。 |
| `com.syaru.ae2craftingoptimizer.engine.vector.VectorPlanFingerprint` | Programと正確な境界量から、保存Receiptを取り違えない安定SHA-256を作る。 |

### `com.syaru.ae2craftingoptimizer.gtceu`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.gtceu.GTCEuNativeBatchBridge` | GTCEuNativeBatchBridgeが示す二つのAPI境界を接続し、対応不能時は元の所有者へ判断を戻す。 |
| `com.syaru.ae2craftingoptimizer.gtceu.GTCEuNativePatternBatchAdapter` | GTCEuのItem・Fluid入力を完全一致でまとめて受理させるAdapter。 |
| `com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath` | Provider Intentに一致するGT recipe候補だけを優先し、GTCEu本来の成立判定へ渡す。 |

### `com.syaru.ae2craftingoptimizer.integration`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.integration.AdvancedAePatternProviderAccess` | Advanced AE Pattern Providerの実体と世代情報を版差を吸収して取得する。 |
| `com.syaru.ae2craftingoptimizer.integration.Ae2BigCraftingExecutionManager` | Issue #115: 標準AE2 CraftingCPUCluster上のexact Jobを物理Receipt経路で進める。 |
| `com.syaru.ae2craftingoptimizer.integration.Ae2CraftingTreeCompatibility` | AE2 Crafting TreeがCraftingPlanSummaryへ追加するRecipeHelperを任意連携で初期化する。 |
| `com.syaru.ae2craftingoptimizer.integration.Ae2UelmCompatibility` | Forge 1.20.1でAE2 UELMを検出し、対応済みdescriptorと機能差を検証する。 |
| `com.syaru.ae2craftingoptimizer.integration.AppliedECompatibility` | AppliedE本家とTPS Fix forkに共通する動的パターン境界を扱う。 |
| `com.syaru.ae2craftingoptimizer.integration.AqeBigCraftingExecutionContext` | 標準容量判定へ、現在投入中のBig子Job一件分だけを一時的に貸し出すサーバースレッド文脈。 |
| `com.syaru.ae2craftingoptimizer.integration.AqeBigCraftingExecutionManager` | Advanced AE CPU HostとACO exact計画を接続し、予約、実行、取消、復元を調停する外部境界。 |
| `com.syaru.ae2craftingoptimizer.integration.BigIntegerStorageSnapshotBridge` | NetworkStorageが各mountを集計する境界で、AE2用long FacadeとBigInteger正本を分離する。 |
| `com.syaru.ae2craftingoptimizer.integration.ExactBigIntegerCellConsistency` | ACOが直接更新したExtendedAE Plus在庫Mapと、同MODの保存用総量を結ぶ弱Sidecar。 |
| `com.syaru.ae2craftingoptimizer.integration.ExactNetworkStorageBridge` | ME storageへのexact snapshot、reserve、insert、rollbackをAE2権限境界内で行う。 |
| `com.syaru.ae2craftingoptimizer.integration.ExactNetworkStorageSnapshotCache` | 同一server tick内で完成済みのNetworkStorage在庫集計を再利用する。 |
| `com.syaru.ae2craftingoptimizer.integration.ExactVectorGridTickBudget` | BigInteger親Jobと標準AQE Jobが共有する、Grid単位のExact Vector tick予算。 |
| `com.syaru.ae2craftingoptimizer.integration.ExperimentalCompatibilityValidator` | 有効化された実験Mixinの対象クラス、Accessor、内部契約を起動時に監査する。 |
| `com.syaru.ae2craftingoptimizer.integration.GridStorageSnapshotBridge` | 通常MEネットワーク端末へ、AE2 StorageServiceが管理する同じ在庫Snapshotを複製する。 |
| `com.syaru.ae2craftingoptimizer.integration.NeoEcoVersionCompatibility` | Neo ECOの実行API世代を、Mixinが参照する前に文字列だけで判定する。 |
| `com.syaru.ae2craftingoptimizer.integration.OptionalAqeBigCraftingExecution` | Advanced AE未導入環境で対象クラスを解決しないための遅延境界。 |
| `com.syaru.ae2craftingoptimizer.integration.OptionalNativeBatchIntegrations` | 動作確認済みの依存MODバージョンに限ってNative Batch Adapterを遅延登録する。 |
| `com.syaru.ae2craftingoptimizer.integration.PlanningExactInventorySnapshot` | BigInteger Planner専用の在庫Snapshotを作る。 |
| `com.syaru.ae2craftingoptimizer.integration.ProgramFingerprintRevalidationCache` | 現在のPattern/recipe世代で再検証済みの数式Program指紋を保持する。 |

### `com.syaru.ae2craftingoptimizer.intent`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.intent.InputIntent` | Providerが機械へ渡した具体的なItem、Fluid、Chemical入力を不変値で表す。 |
| `com.syaru.ae2craftingoptimizer.intent.IntentLocation` | Intentの送信元Provider、Level、位置、方向を特定する。 |
| `com.syaru.ae2craftingoptimizer.intent.PatternIntentCapture` | Pattern push直前のrecipe intentをthread-local境界へ短期間保存する。 |
| `com.syaru.ae2craftingoptimizer.intent.PatternIntentExtractor` | AE2 Patternから機械検索用の入力、出力、recipe種別hintを抽出する。 |
| `com.syaru.ae2craftingoptimizer.intent.RecipeIntent` | 一回のPattern pushが作ろうとするrecipeとexact入出力の読取専用表現。 |
| `com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry` | RecipeIntentRegistryが示す実装またはHostの登録、解除、検索を管理する。 |
| `com.syaru.ae2craftingoptimizer.intent.RecipeIntentSignature` | RecipeIntentSignatureが示す対象を、順序と内容から安定して識別する。 |
| `com.syaru.ae2craftingoptimizer.intent.StackIntent` | 一つのItem、Fluid、Chemicalと要求量をrecipe intent内で表す。 |

### `com.syaru.ae2craftingoptimizer.lifecycle`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.lifecycle.ACOServerLifecycle` | サーバーの開始・tick・データ再読込・停止に伴うACO状態を一元管理する。 |
| `com.syaru.ae2craftingoptimizer.lifecycle.ACOStartupReport` | 起動時に有効機能と安全上限を一度だけ報告する。 |

### `com.syaru.ae2craftingoptimizer.mekanism`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.mekanism.MekanismNativeBatchBridge` | MekanismNativeBatchBridgeが示す二つのAPI境界を接続し、対応不能時は元の所有者へ判断を戻す。 |
| `com.syaru.ae2craftingoptimizer.mekanism.MekanismNativePatternBatchAdapter` | MekanismのItem・Fluid・Chemical入力を完全一致でまとめて受理させるAdapter。 |
| `com.syaru.ae2craftingoptimizer.mekanism.MekanismRecipeIntentFastPath` | Provider Intentに一致するMekanism recipe候補だけを優先し、CachedRecipeの成立判定へ渡す。 |

### `com.syaru.ae2craftingoptimizer.mixin`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.mixin.AcoMixinPlugin` | AcoMixinPluginが担当するMixin群の適用可否を、対象MODと対応版から決定する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeBigCapacityPlanSubmissionMixin` | AdvancedAeBigCapacityPlanSubmissionMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingBlockEntityTransactionAccessMixin` | AdvancedAeCraftingBlockEntityTransactionAccessMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingClusterBigWindowMixin` | AdvancedAeCraftingClusterBigWindowMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuAccessorMixin` | AdvancedAeCraftingCpuAccessorMixinの対象となる非公開状態を型付きAccessorとして公開するMixin。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuClusterRecoveryMixin` | AdvancedAeCraftingCpuClusterRecoveryMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuLogicBatchSourceReceiptMixin` | AdvancedAeCraftingCpuLogicBatchSourceReceiptMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuLogicBigChildMixin` | AdvancedAeCraftingCpuLogicBigChildMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuLogicExecutionBudgetMixin` | AdvancedAeCraftingCpuLogicExecutionBudgetMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuLogicTransactionalBatchV2Mixin` | AdvancedAeCraftingCpuLogicTransactionalBatchV2Mixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuTransactionAccessMixin` | AdvancedAeCraftingCpuTransactionAccessMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeExactCraftingLogicMixin` | AdvancedAeExactCraftingLogicMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeExecutingCraftingJobTransactionAccessMixin` | AdvancedAeExecutingCraftingJobTransactionAccessMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAePatternProviderIntentCaptureMixin` | AdvancedAePatternProviderIntentCaptureMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAePatternProviderLogicNativeBatchReceiptMixin` | AdvancedAePatternProviderLogicNativeBatchReceiptMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeReactionChamberRecipeCacheMixin` | AdvancedAeReactionChamberRecipeCacheMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.AdvancedAeTaskProgressTransactionAccessMixin` | AdvancedAeTaskProgressTransactionAccessMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.Ae2BigCapacityPlanSubmissionMixin` | Ae2BigCapacityPlanSubmissionMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.Ae2ExactCraftingLogicMixin` | Ae2ExactCraftingLogicMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.Ae2OverclockMachineReflectionCacheMixin` | Ae2OverclockMachineReflectionCacheMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.Ae2OverclockParallelRuntimeCacheMixin` | Ae2OverclockParallelRuntimeCacheMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.Ae2OverclockRuntimeCacheMixin` | Ae2OverclockRuntimeCacheMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.Ae2ScrollbarReleaseSafetyMixin` | Ae2ScrollbarReleaseSafetyMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.ClientRepoUpdateCoalescingMixin` | ClientRepoUpdateCoalescingMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftAmountMenuLongAmountMixin` | CraftAmountMenuLongAmountMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftAmountScreenLongAmountMixin` | CraftAmountScreenLongAmountMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftConfirmMenuLongAmountMixin` | CraftConfirmMenuLongAmountMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftConfirmScreenBigIntegerMixin` | CraftConfirmScreenBigIntegerMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftConfirmTableRendererBigIntegerMixin` | CraftConfirmTableRendererBigIntegerMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingBlockEntityTransactionAccessMixin` | CraftingBlockEntityTransactionAccessMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingCalculationCheckedMathMixin` | CraftingCalculationCheckedMathMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingCalculationDiagnosticsMixin` | CraftingCalculationDiagnosticsMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingCalculationMemoLifecycleMixin` | CraftingCalculationMemoLifecycleMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingCpuClusterTransactionAccessMixin` | CraftingCpuClusterTransactionAccessMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingCpuHelperFluidFastPathMixin` | CraftingCpuHelperFluidFastPathMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingCpuLogicBatchSourceReceiptMixin` | CraftingCpuLogicBatchSourceReceiptMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingCpuLogicExecutionBudgetMixin` | CraftingCpuLogicExecutionBudgetMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingCpuLogicTransactionalBatchV2Mixin` | CraftingCpuLogicTransactionalBatchV2Mixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingPlanSummaryWidePlanMixin` | CraftingPlanSummaryWidePlanMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingProviderRefreshCoalescingMixin` | CraftingProviderRefreshCoalescingMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingServiceCalculationDeduplicationMixin` | CraftingServiceCalculationDeduplicationMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingServiceInvalidationMixin` | CraftingServiceInvalidationMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingServicePatternLookupCacheMixin` | CraftingServicePatternLookupCacheMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingSimulationStateCheckedMathMixin` | CraftingSimulationStateCheckedMathMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingTreeCalculationMemoMixin` | CraftingTreeCalculationMemoMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingTreeCandidatePruningMixin` | CraftingTreeCandidatePruningMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingTreeNodeCheckedMathMixin` | CraftingTreeNodeCheckedMathMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.CraftingTreeProcessCheckedMathMixin` | CraftingTreeProcessCheckedMathMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.DelegatingMEInventoryAccessor` | DelegatingMEInventoryAccessorの対象となる非公開状態を型付きAccessorとして公開するMixin。 |
| `com.syaru.ae2craftingoptimizer.mixin.ExecutingCraftingJobTransactionAccessMixin` | ExecutingCraftingJobTransactionAccessMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.ExtendedAeAssemblerMatrixClusterCacheMixin` | ExtendedAeAssemblerMatrixClusterCacheMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.ExtendedAeAssemblerMatrixCrafterCacheMixin` | ExtendedAeAssemblerMatrixCrafterCacheMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.ExtendedAeCircuitCutterRecipeCacheMixin` | ExtendedAeCircuitCutterRecipeCacheMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusAssemblerMatrixBusyCaptureMixin` | ExtendedAePlusAssemblerMatrixBusyCaptureMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.ExportBusCandidateCacheMixin` | Export Busの設定keyだけを設定世代内で再利用し、搬出処理には介入しない。 |
| `com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellConsistencyMixin` | ExtendedAePlusBigIntegerCellConsistencyMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellInventoryAccessor` | ExtendedAePlusBigIntegerCellInventoryAccessorの対象となる非公開状態を型付きAccessorとして公開するMixin。 |
| `com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusInfinityDataStorageConsistencyMixin` | ExtendedAePlusInfinityDataStorageConsistencyMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.GenericStackInvGenerationMixin` | GenericStackInvGenerationMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.IOPortSlotWindowMixin` | AE2のIO Port搬送を置換せず、入力セルの走査窓と再確認modulationだけを公平に制御する。 |
| `com.syaru.ae2craftingoptimizer.mixin.GTCEuRecipeLogicIntentFastPathMixin` | GTCEuRecipeLogicIntentFastPathMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.IncrementalUpdateHelperDeepRangeMixin` | IncrementalUpdateHelperDeepRangeMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.KeyCounterBigIntegerSidecarLifecycleMixin` | KeyCounterBigIntegerSidecarLifecycleMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.ListCraftingInventoryExactCountsMixin` | ListCraftingInventoryExactCountsMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.MEInventoryUpdatePacketBuilderRangeMixin` | MEInventoryUpdatePacketBuilderRangeMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.MekanismCachedRecipeAccessor` | MekanismCachedRecipeAccessorの対象となる非公開状態を型付きAccessorとして公開するMixin。 |
| `com.syaru.ae2craftingoptimizer.mixin.MekanismRecipeIntentFastPathMixin` | MekanismRecipeIntentFastPathMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.MixinFeatureCatalog` | 全Mixinを監査済みOptimizationFeatureへ対応付け、未登録Mixinをfail-closedにする正本。 |
| `com.syaru.ae2craftingoptimizer.mixin.MultiCraftingTrackerCraftRequestThrottleMixin` | MultiCraftingTrackerCraftRequestThrottleMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.NeoEco20_3CraftingCpuExecutionBudgetMixin` | NeoEco20_3CraftingCpuExecutionBudgetMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.NeoEco20_4CraftingCpuExecutionBudgetMixin` | NeoEco20_4CraftingCpuExecutionBudgetMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.NeoEcoExecutionBudgetSupport` | Neo ECO 20.3/20.4の記述子差分から独立した、共通の実行予算計算。 |
| `com.syaru.ae2craftingoptimizer.mixin.NetworkCraftingSimulationStateAccessor` | NetworkCraftingSimulationStateAccessorの対象となる非公開状態を型付きAccessorとして公開するMixin。 |
| `com.syaru.ae2craftingoptimizer.mixin.NetworkCraftingSimulationStateBigIntegerSnapshotMixin` | NetworkCraftingSimulationStateBigIntegerSnapshotMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.NetworkStorageBigIntegerSnapshotMixin` | NetworkStorageBigIntegerSnapshotMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.NetworkStorageMountsAccessor` | NetworkStorageMountsAccessorの対象となる非公開状態を型付きAccessorとして公開するMixin。 |
| `com.syaru.ae2craftingoptimizer.mixin.NumberEntryWidgetAccessor` | NumberEntryWidgetAccessorの対象となる非公開状態を型付きAccessorとして公開するMixin。 |
| `com.syaru.ae2craftingoptimizer.mixin.P2PServiceTopologyDeduplicationMixin` | P2PServiceTopologyDeduplicationMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.StorageImportScanOrderMixin` | Import Busの前回成功スロットを先に読むが、抽出・挿入・返却・操作数会計はAE2へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.PatternProviderLogicIntentCaptureMixin` | PatternProviderLogicIntentCaptureMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.PatternProviderLogicNativeBatchReceiptMixin` | PatternProviderLogicNativeBatchReceiptMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.StorageServiceDeepCoalescingMixin` | StorageServiceDeepCoalescingMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.StorageServiceExactSnapshotInvalidationMixin` | StorageServiceExactSnapshotInvalidationMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.StorageServiceWatcherThrottleMixin` | StorageServiceWatcherThrottleMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |
| `com.syaru.ae2craftingoptimizer.mixin.TaskProgressTransactionAccessMixin` | TaskProgressTransactionAccessMixinが示す最適化またはexact会計を既存処理へ接続する薄いMixin境界。業務ロジックは非Mixin層へ委譲する。 |

### `com.syaru.ae2craftingoptimizer.network`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork` | BigInteger Host状態とlongルート注文を運ぶProtocol Version付き通信Channel。 |

### `com.syaru.ae2craftingoptimizer.optimization`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.optimization.Ae2OverclockUpgradeCountCache` | Ae2OverclockUpgradeCountCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.AssemblerMatrixBusyCountCache` | AssemblerMatrixBusyCountCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.BatchedCraftingExecutor` | 機械が正確に受理した実行数だけをまとめて会計する旧Batch実行器。 |
| `com.syaru.ae2craftingoptimizer.optimization.BatchExecutionOffer` | 物理配送回数と、一つのNative Batchが所有する論理実行係数を分離する。 |
| `com.syaru.ae2craftingoptimizer.optimization.BatchMinimumExecutionPolicy` | Native Batchを開始する最小実行回数の共通検証。 |
| `com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDeclineReason` | BigInteger計画を採用できなかった理由を、ログと統計で安定して識別するコード。 |
| `com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDiagnostics` | BigInteger経路の採用・辞退理由を、計算結果と分離して記録する。 |
| `com.syaru.ae2craftingoptimizer.optimization.BusFuzzySearchCache` | BusFuzzySearchCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.BusTransferSimulationCache` | BusTransferSimulationCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.CircuitCutterRecipeCache` | CircuitCutterRecipeCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.ConfigInventoryGenerationAccess` | BusやProviderの設定Inventoryに変更世代を付与する最小Access契約。 |
| `com.syaru.ae2craftingoptimizer.optimization.GenerationSlotCache` | nullを含む設定slot値を世代とサイズが一致する間だけ保持する固定長cache。 |
| `com.syaru.ae2craftingoptimizer.optimization.CraftableSetCache` | CraftableSetCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator` | 同一世代・同一注文の計算Futureを共有し、待機者と取消所有権を分離する。 |
| `com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDiagnostics` | CraftingCalculationDiagnosticsが示す診断理由と観測値を集約する。ゲーム結果は変更しない。 |
| `com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo` | 一回のクラフト計算内で在庫照会、候補、返却物などの純粋結果を共有する。 |
| `com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget` | CraftingExecutionBudgetが示す上限、時間予算、適格条件を副作用なしで判定する。 |
| `com.syaru.ae2craftingoptimizer.optimization.CraftingRequestThrottle` | CraftingRequestThrottleが示す仕事へtick予算を配分し、公平性とbackpressureを維持する。 |
| `com.syaru.ae2craftingoptimizer.optimization.DeepRangeUpdateHelper` | 端末の巨大な差分範囲を上限付きchunkへ分け、順序を保って同期する。 |
| `com.syaru.ae2craftingoptimizer.optimization.DeterministicCraftingPreflight` | DeterministicCraftingPreflightが示す入力や互換条件を検証し、証明不能な高速経路を拒否する。 |
| `com.syaru.ae2craftingoptimizer.optimization.DeterministicMissingProof` | DeterministicMissingProofが示す所有権移転または完了事実を、検証可能な証跡として保持する。 |
| `com.syaru.ae2craftingoptimizer.optimization.ExactBatchInputLimiter` | 一回分のキー別入力合計から、signed-longで安全に所有できる実行回数を求める。 |
| `com.syaru.ae2craftingoptimizer.optimization.FallbackBoundary` | ACOがAE2標準経路へ安全に戻せる最終地点を、ownership取得の前後で分類する。 |
| `com.syaru.ae2craftingoptimizer.optimization.GridTickBudget` | GridTickBudgetが示す上限、時間予算、適格条件を副作用なしで判定する。 |
| `com.syaru.ae2craftingoptimizer.optimization.MethodHandleInvocationCache` | MethodHandleInvocationCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.MissingOnlyCraftingPlan` | MissingOnlyCraftingPlanが示すクラフト計画またはコンパイル済みプログラムを不変値として保持する。 |
| `com.syaru.ae2craftingoptimizer.optimization.NativeBatchTargetGuard` | NativeBatchTargetGuardが示す入力や互換条件を検証し、証明不能な高速経路を拒否する。 |
| `com.syaru.ae2craftingoptimizer.optimization.OptimizationDomain` | 最適化をnetwork、storage IO、provider、client sync、planning、execution、BigInteger、任意連携へ分割する。 |
| `com.syaru.ae2craftingoptimizer.optimization.OptimizationFeature` | 各最適化の安定ID、実装状態、domain、risk、状態所有者、失効条件、fallback境界、関連回帰Issueを宣言する正本。 |
| `com.syaru.ae2craftingoptimizer.optimization.OptimizationFeatureGate` | master、domain、個別設定、実装状態を順に評価し、無効または互換No-opの機能がAE2状態へ触れる前に停止する。 |
| `com.syaru.ae2craftingoptimizer.optimization.PreferredSlotScanOrder` | 前回成功スロットを先頭へ移しつつ全候補を一度ずつ返す全単射の走査順を計算する。 |
| `com.syaru.ae2craftingoptimizer.optimization.RoundRobinSlotWindow` | 固定スロット群を欠落なく巡回する窓のindexと次cursorを計算する。 |
| `com.syaru.ae2craftingoptimizer.optimization.OptimizationImplementationStatus` | 実行経路が有効な機能と、既存TOML互換のためキーだけ残すNo-opを区別する。 |
| `com.syaru.ae2craftingoptimizer.optimization.OptimizationInvalidation` | cacheとtransactionを破棄するstorage、provider、topology、reload、lifecycle等の正本イベントを列挙する。 |
| `com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics` | OptimizationMetricsが示す診断理由と観測値を集約する。ゲーム結果は変更しない。 |
| `com.syaru.ae2craftingoptimizer.optimization.OptimizationRisk` | 最適化の影響範囲をLOW、MEDIUM、HIGHへ分類し、必要な回帰証拠を決める。 |
| `com.syaru.ae2craftingoptimizer.optimization.P2PNotificationDeduplicator` | 同一tick・同一topology世代の重複P2P通知を一度へまとめる。 |
| `com.syaru.ae2craftingoptimizer.optimization.PatternAvailabilitySorter` | 直前成功や利用可能性をhintに候補順だけを変え、適格性判定は変更しない。 |
| `com.syaru.ae2craftingoptimizer.optimization.PatternCandidatePruner` | 証明済みの非候補だけを計算前に除外し、曖昧なら元候補集合を維持する。 |
| `com.syaru.ae2craftingoptimizer.optimization.PatternLookupCache` | PatternLookupCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.PatternProviderBatchEligibility` | Pattern ProviderをBatch対象にできるか、所有権・入力・target能力から保守的に判定する。 |
| `com.syaru.ae2craftingoptimizer.optimization.PatternPushContext` | PatternPushContextが示す一回の要求に必要な入力、所有者、実行条件を保持する。 |
| `com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker` | ProviderPatternGenerationTrackerが示す世代、進捗、tick時刻を単調に追跡する。 |
| `com.syaru.ae2craftingoptimizer.optimization.ReactionChamberRecipeCache` | ReactionChamberRecipeCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.ReflectionLookupCache` | ReflectionLookupCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |
| `com.syaru.ae2craftingoptimizer.optimization.SequentialInstantDispatcher` | AE2本来のexecuteCraftingを小さな計測波へ分け、同じserver tick内で時間予算まで継続する。 |
| `com.syaru.ae2craftingoptimizer.optimization.ServerTickClock` | ServerTickClockが示す世代、進捗、tick時刻を単調に追跡する。 |
| `com.syaru.ae2craftingoptimizer.optimization.SharedCalculationFuture` | 一つの計算Futureを複数呼出者へ共有し、各待機者の取消を所有Futureの取消と分離する。 |
| `com.syaru.ae2craftingoptimizer.optimization.StateOwnership` | 最適化中の正本がAE2、ACO cache、ACO transaction、外部アドオンのどれかを示す。 |
| `com.syaru.ae2craftingoptimizer.optimization.StorageWatcherUpdateBuffer` | client可視のstorage差分を上限付きで集約し、screenやtopology変更時に即flushする。 |
| `com.syaru.ae2craftingoptimizer.optimization.TransactionalCraftingExecutorV2` | Batch V2のprepare、transfer、commit、rollbackをReceipt契約に従って進める。 |

### `com.syaru.ae2craftingoptimizer.scheduler`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.scheduler.DeficitRoundRobinScheduler` | tick予算付きDeficit Round Robin Scheduler。 |
| `com.syaru.ae2craftingoptimizer.scheduler.FairCraftingJobScheduler` | FairCraftingJobSchedulerが示す仕事へtick予算を配分し、公平性とbackpressureを維持する。 |
| `com.syaru.ae2craftingoptimizer.scheduler.FairSchedulerPersistentState` | FairSchedulerPersistentStateが示す時点の状態を、検証可能な値として保持する。 |
| `com.syaru.ae2craftingoptimizer.scheduler.FairSchedulerStateStore` | FairSchedulerStateStoreが示す記録を保存、検索、削除する。 |
| `com.syaru.ae2craftingoptimizer.scheduler.PatternProviderRoutingCache` | PatternProviderRoutingCacheが示す既知結果を世代またはrevision付きで再利用し、変化時に失効する。 |

### `com.syaru.ae2craftingoptimizer.transaction`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.transaction.BatchConservationLedger` | Native Batch一件の保存則を検証する副作用のない状態機械。 |
| `com.syaru.ae2craftingoptimizer.transaction.BatchRecoveryCompatibilityBridge` | BatchRecoveryCompatibilityBridgeが示す二つのAPI境界を接続し、対応不能時は元の所有者へ判断を戻す。 |
| `com.syaru.ae2craftingoptimizer.transaction.BatchTransactionCoordinator` | 一回のNative Batchと永続Journalを結ぶ状態遷移窓口。 |
| `com.syaru.ae2craftingoptimizer.transaction.BatchTransactionJournal` | Overworld SavedDataへ未完了取引を保存する台帳。 |
| `com.syaru.ae2craftingoptimizer.transaction.BatchTransactionPhase` | BatchTransactionPhaseが表す実行方針または状態遷移段階を列挙する。 |
| `com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord` | Native Batch一件の不変入力、期待出力、現在Phase、送受信Receiptを保持する永続Record。 |
| `com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecovery` | 未完了Journalをtick予算内で再照合する復旧処理。 |

### `com.syaru.ae2craftingoptimizer.util`

| クラス | 仕事 |
|---|---|
| `com.syaru.ae2craftingoptimizer.util.StableFingerprint` | StableFingerprintが示す対象を、順序と内容から安定して識別する。 |
