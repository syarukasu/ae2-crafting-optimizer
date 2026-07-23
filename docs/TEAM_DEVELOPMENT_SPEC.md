# ACO Team Development Specification

この文書は、AE2 Crafting Optimizer（ACO）を複数人で開発するための共通仕様です。現行基準は `1.4.1` 開発ブランチです。AQE専用のchecked/BigInteger経路以外の深い実験機能は既定で無効とし、有効化前にコピーしたワールドで復旧・ゲーム内試験を行います。

## 製品定義

- MOD名は `AE2 Crafting Optimizer` とします。
- MOD IDは `ae2_crafting_optimizer` とします。
- 対象はMinecraft `1.20.1`、Forge `47.4.18+`、Java `17`です。
- 必須依存はApplied Energistics 2 `15.4.10`、許容範囲は `15.4.x` です。
- ACOはAE2のフォークではなく、Forge/Mixinで動く独立アドオンです。
- ACOはレシピ追加MODでも、クラフトCPU性能追加MODでもありません。
- 専用サーバー、シングルプレイ、Arclight上の通常Forge MODとして動作させます。
- 専用サーバーと全クライアントへ同一JARを導入します。
- ライセンスは `LGPL-3.0-only` です。

## 目的

- 巨大自動クラフトの計算待ち時間と重複計算を減らします。
- 巨大クラフトCPUが一tickへ集中させるパターン投入負荷を制御します。
- 複数CPUが同時に動いた際のMEグリッド全体のMSPT上昇を抑えます。
- Pattern Providerとクラフト計算の反復処理を減らします。可変ストレージ、端末、Import/Export Bus、IO Portは1.2.2でAE2本体へ完全に戻しており、再導入には独立した安全性試験を必須とします。
- GTCEu、Mekanism、Advanced AE、ExtendedAEなどの頻出ホットパスを安全な範囲で短縮します。
- TPSを守りながら、元のAE2と同じクラフト結果、在庫、進捗、復元結果を維持します。
- 大規模工業環境で、最適化の有無をConfig一つで比較できるようにします。

## 最重要不変条件

- `MUST`: AE2をクラフト計画、欠品判定、ジョブ投入、在庫操作、ネットワーク状態の唯一の正とします。
- `MUST`: ACOがアイテム、液体、化学物質を増殖、消失、置換してはいけません。
- `MUST`: 表示キャッシュを実際の搬入、搬出、クラフト可否判定へ使用してはいけません。
- `MUST`: キャッシュヒット時も、結果の意味は元のAE2または対象MODと一致しなければなりません。
- `MUST`: 高速経路が正しさを証明できない場合、状態変更前に元の処理へ戻します。
- `MUST`: タイミング、順序、投入量、同期頻度を変える機能には個別Configを設けます。
- `MUST`: `enableOptimizer = false` でACOの機能を実質的に全停止できるようにします。
- `MUST`: キャッシュには件数上限、TTL、世代番号、または明確な所有期間のいずれかを設けます。
- `MUST`: Provider、パターン、ストレージ、グリッド、レシピ再読込の変更に応じて関連キャッシュを失効させます。
- `MUST`: オプションMODが存在しない環境で、そのMODのクラスをCommon初期化からロードしてはいけません。
- `MUST`: クライアント専用クラスをDedicated Serverでロードしてはいけません。
- `MUST`: ワールド、Block Entity、AEグリッドへ非同期スレッドから直接アクセスしてはいけません。
- `MUST`: 実装上の上限値計算では、加算・乗算・型変換によるオーバーフローを防ぎます。
- `MUST`: ログ抑制のために例外やデータ不整合を握り潰してはいけません。
- `MUST`: ACO無効時に元のAE2処理が動作することを回帰試験します。

## 実装構造

- Mixinは既存処理への最小フックに限定します。
- 重い処理、キャッシュ、予算計算、検証は `optimization`、`intent`、`api` 配下へ分離します。
- 任意連携は `@Pseudo` Mixinまたは反射境界を使用し、対象MOD不在時は適用しません。
- Mixin全体は `required: false`、個別注入は原則として元処理を壊さないフォールバックを持ちます。
- 対象バージョン更新時は、クラス名だけでなく対象メソッドの記述子と周辺バイトコードを再確認します。
- Forge Common Configを使用し、`config/ae2_crafting_optimizer-common.toml`をサーバーとクライアントで揃えます。
- Configのソース既定値を変更しても、既存ワールドのTOMLは自動更新されない前提で扱います。
- 公開内部APIは `api` パッケージへ置き、互換性を壊す変更はメジャーまたは明示的な破壊的変更として扱います。
- 診断値はゲーム状態を変更しない専用カウンタへ記録します。

## クラフト計算

- 同一要求元、同一MEグリッド、同一出力、同一個数、同一計算方式の進行中計算を一本化します。
- 進行中計算の共有は結果Futureの共有に限定し、別要求の所有権やキャンセル状態を混同しません。
- 完了済み計画キャッシュは、既定では欠品結果とシミュレーション結果だけを短時間保存します。
- 成功済み計画の再利用は在庫変動リスクがあるため既定OFFとします。
- Pattern LookupはProviderまたはグリッドの世代が変わるまで再利用します。
- Craftable Setは同じフィルターと世代の間だけ再利用します。
- 一つの計算内で不変な問い合わせだけをメモ化します。
- シミュレーション在庫の現在量など、探索途中で変化する値はメモ化しません。
- 候補除外は `null`、重複、出力不一致、構造的に不正な候補だけに限定します。
- 欠品の早期終了は、代替候補、タグ置換、複数パターンがなく「絶対に成立しない」と証明できる場合だけ許可します。
- 早期終了結果もAE2標準の欠品計画として返し、独自クラフト結果を作りません。
- 「計算中」画面の独自二段表示は挙動差があるため既定OFFとします。

## CPU実行予算

- コプロセッサは計算速度そのものではなく、Pattern Providerへの投入機会を増やす値として扱います。
- ACOはCPUの表示容量、表示コプロセッサ数、構造、保存データを変更しません。
- 一CPUの一tick内パターン投入量に有効上限を適用します。
- 標準の最大有効コプロセッサ既定値は `264192` とします。
- CPU別適応予算は処理時間目標 `4 ms`、最小進捗 `1024` 有効操作を既定とします。
- MEグリッド共有予算は合計 `8 ms/tick` を既定とします。
- 共有予算を使い切った後も、各稼働CPUへ最低一操作の前進機会を残します。
- 実行予算はクラフト結果を変えず、未実行分を後続tickへ送ります。
- Advanced AE Quantum Computerにも表示値を変えず、同じ実効予算境界を適用します。
- Neo ECO CPUは自身のScheduler/FastPathを正とし、ACOは返却されるtick上限だけをさらに狭めます。

## トランザクションバッチ

- `PatternBatchAdapter` は、対象が永続的に受理した完全なパターン実行数を返します。
- `acceptedExecutions == 0` は、対象が何も変更せず入力を保持していないことを意味します。
- `acceptedExecutions == N` は、完全なN実行分の所有権を対象が永続的に受け取ったことを意味します。
- 受理数は提示数および `PatternBatchBudget` の上限を超えてはいけません。
- 挿入シミュレーション、部分挿入、一時的な空き容量を受理証明として扱ってはいけません。
- AdapterはAE2のタスク進捗、期待出力、電力、`waitingFor` を直接変更しません。
- ACOは検証済みの実受理数だけを進捗、期待出力、電力、`waitingFor` へ反映します。
- Adapterは所有権移転後に例外を投げてはいけません。
- 所有権移転後の無例外を保証できない連携は保守的Adapterを使用します。
- 対象は具体的な入力キーを持つ処理パターンに限定します。
- 代替入力、返却容器、Provider Lock、曖昧な複数出力を含む場合は元のAE2処理へ戻します。
- 既定ではPattern Providerの対象面を一つに限定し、ルーティングを決定的にします。
- 内蔵Adapterは実受理一回ごとに元の `pushPattern` を一回呼びます。
- 内蔵AdapterはProviderのBusyを毎回確認し、詰まった時点で停止します。
- 内蔵Adapterは安全性の基準実装であり、O(1)の機械投入を主張しません。
- GTCEu/MekanismネイティブAdapterは、機械側に原子的なキューまたは並列レシピAPIがある場合だけ追加できます。
- 過去の入力一括挿入型Micro Batchは、出力数を保証できないため恒久的に無効とします。

## 即時ディスパッチ

- 一回のCPU呼び出し内で、複数の実行可能タスクと複数Adapterトランザクションを処理できます。
- 既定の実時間上限は `4 ms` です。
- 既定のトランザクション上限は `1024` です。
- CPU操作数、実時間、トランザクション数のいずれかへ達した時点で停止します。
- 残りの処理は後続tickへ安全に持ち越します。
- 「即時」は投入を進める意味であり、外部機械の処理時間をゼロにしません。
- ACOが機械出力を直接生成してはいけません。

## Recipe Intent

- Pattern Providerがパターンを正常にpushした時点で、入力、期待出力、対象位置を短時間記録します。
- Intentの既定TTLは `20 ticks`、既定上限は `4096 entries` です。
- Item、Fluid、Mekanism Chemicalを別種の入力として保持します。
- GTCEuでは期待出力から少数の候補を索引し、通常候補列の前へ追加します。
- GTCEu候補はGTCEu本来の入力、電圧、機械、レシピ条件検証を必ず通します。
- GTマルチブロックはチャンク単位の近傍索引でControllerとInput Bus/HatchのIntentを関連付けます。
- Mekanismでは候補取得後も現在の機械入力へ本来のRecipe Testを実行します。
- 候補を確定できない場合は対象MOD本来の全探索へ戻します。
- Intentはレシピを強制指定する命令ではなく、検証対象を絞るヒントです。
- `/reload`、レシピ世代変更、期限切れでIntent索引を破棄します。
- Create連携キーは予約済みですが、実体のない高速経路を実装済みとして扱いません。

## 搬入出とGrid Tick（1.2.2では互換No-op）

- Import/Export Bus、IO Port、Capability、搬入出シミュレーション、Grid Tick遅延のMixinは1.2.2で登録解除しています。
- 対応Configキーは既存TOML互換のため残りますが、`true`でも動作しません。
- 再実装する場合、Import Busは前回成功スロット失敗後に必ず全スキャンへ戻す必要があります。
- 再実装する場合、セル移動は原子的で、途中状態・重複排出・超過分消失を作ってはいけません。
- 再実装する場合、Capability無効化、隣接Block Entity交換、満杯解除、チャンク再読込を独立試験します。
- 自動クラフト失敗のCooldownは、在庫・パターン変更後の有効要求を妨げない失効条件を持たせます。
- 自動クラフト失敗Cooldownは別経路であり、実搬入出を置換しません。

## 端末と同期

- 1.2.2では、Storage Watcher、aggregate refresh、端末snapshot、craftable-set、range packet、client Repo coalescingのMixinを互換性のため登録解除しています。
- 上記のConfigキーは既存TOML互換のため残しますが、対応Mixinを再登録するまでは動作しない互換キーとして扱います。
- Storage Watcherの間引きはクライアント表示用更新だけを対象にします。
- 実際の挿入、抽出、クラフトは常に現在のAE2ストレージへ問い合わせます。
- 端末在庫スナップショット再利用は、在庫ゼロからの挿入消失回帰が起き得るため既定OFFとします。
- クリック可能な仮想スロットと表示世代がずれるClient Coalescingは既定OFFとします。
- 表示範囲だけを分割送信する機能は、一つの整合した世代を複数packetへ分けるため既定OFFとします。
- 非同期検索・ソートを行う場合、immutableな投影データだけをWorkerへ渡します。
- 非同期結果は世代番号が最新の場合だけ反映し、古い結果を破棄します。
- 端末最適化でContainer ID、スロット数、クリックpacketの意味を変更してはいけません。
- 無登録の同期経路を再導入する場合、在庫ゼロへの投入、抽出、検索、rapid click、server/client世代差を独立した回帰試験で通します。

## TopologyとProvider

- Providerパターン索引は内容世代が変わった時だけ再構築します。
- 同一tick内の重複Provider Refreshはまとめますが、クラフト計算開始前には必ずflushします。
- P2P再評価は同一内容の重複通知だけを短時間除外します。
- P2Pの接続、切断、向き、周波数、電力状態が変わった場合は即時再評価します。
- Storage aggregate更新の再実装を行う場合、最終状態が設定間隔内に必ず収束することを証明します。現行1.2.2では対応Mixinは未登録です。

## Add-on連携

- Advanced AEはQuantum Computer実行予算、Transactional Batch、Advanced Pattern Provider Intentを対象にします。
- Advanced AE Reaction Chamberは本来のRecipe Lookup完了結果から既存Task Cacheをseedします。
- ExtendedAE Circuit Cutterは正確な入力signatureで正・負のRecipe結果を再利用します。
- ExtendedAE Circuit Cutterの負結果は入力変更とDatapack Reloadで直ちに失効させます。
- ExtendedAE Assembly Matrixは使用thread数、busy総数、route、同一tick statusを短時間再利用します。
- Assembly Matrixの形成、解体、チャンク再読込、保存ジョブを変更してはいけません。
- AE2 Overclockは反射探索をキャッシュし、利用可能ならMethodHandleへ変換します。
- AE2 OverclockのUpgrade Card数は同一host・同一tickだけ再利用します。
- Upgrade Card変更は遅くとも次tickで反映します。
- Neo ECOは `20.3.x` の独自Scheduler、FastPath、保存、表示、Recipe Logicを維持します。
- 任意連携の対象クラスまたは記述子が変わった場合、その連携だけを無効化して原因をログへ出します。

## Config方針

- すべての主要機能は意味の分かるセクションへ分類します。
- Configコメントには目的、単位、既定値、上限、主な危険性を記載します。
- 安全なRead-through Cache、世代管理、正確な候補除外は既定ONにできます。
- 投入順、tick頻度、表示世代、非同期処理、成功計画再利用を変える機能は原則既定OFFとします。
- 新しい実験機能は個別スイッチを追加し、既定OFFから開始します。
- 廃止したキーは既存TOML読込のため残せますが、no-opであることを起動ログと文書へ明記します。
- Config名変更には移行方針を用意し、無言で別の意味へ再利用しません。
- ソース既定値と本番ワールドの既存値を混同しません。
- 詳細な現行値は `docs/CONFIGURATION.md` を正とします。

## 診断機能

- `/aco stats` はキャッシュ、予算制限、Intent、Batch受理数などの集計を表示します。
- `/aco intents list <count>` は短時間保持中のIntentを確認します。
- `/aco intents clear` はIntentと関連索引を安全に破棄します。
- 遅いクラフト計算ログは閾値をConfig化します。
- hot pathで一件ごとのINFO/WARNを出し続けてはいけません。
- 同じ互換失敗はclassまたはfeature単位で一度だけ報告します。
- 性能改善PRには、変更前後のSpark、JFR、処理時間、Allocationのいずれかを添付します。
- 平均値だけでなく最大MSPT、p95/p99、処理件数、試験設備を記録します。

## 対応基準

- ハード対象はForge `47.4.18+`、Minecraft `1.20.1`、AE2 `15.4.10-15.4.x` です。
- 現行パック確認対象はGTCEu Modern `7.5.3` です。
- 現行パック確認対象はMekanism `10.4.16.80` です。
- 現行パック確認対象はAdvanced AE `1.3.5` です。
- 現行パック確認対象はExtendedAE `1.4.15` です。
- 現行パック確認対象はAE2 Overclocked `1.2.3-fix3` です。
- Neo ECO AE Extension `20.3.0` は任意のcompile-only検証対象です。
- バージョン範囲を広げる場合、クラス存在だけでなく実動作を試験します。
- Arclight固有API、Bukkit API、Paper APIを使用しません。
- EMI、JEI、KubeJSのRecipe表示・再読込を妨げてはいけません。

## 完了条件

- `gradlew.bat clean build` がfresh cloneから成功します。
- `build/libs/` にJARが生成されます。
- Forge Clientが起動します。
- Forge Dedicated Serverが起動します。
- Arclight試験環境で起動し、接続できます。
- 通常の小規模クラフトが計算、開始、完了します。
- 明らかな欠品クラフトが終了し、永久に「計算中」になりません。
- 大規模クラフトがCPU予算内で前進し続けます。
- 標準AE2 CPU、Advanced AE Quantum Computer、任意対応CPUで結果が一致します。
- Item、Fluid、Chemical処理パターンが完了します。
- Import/Export Bus、IO Portが長時間停止しません。
- 端末で在庫ゼロのキーへ投入してもアイテムが消えません。
- 端末表示、実在庫、クリック結果が一致します。
- Pattern変更、Provider追加削除、`/reload` 後に古いRecipeを再利用しません。
- チャンク再読込、サーバー再起動、クラフト取消後も進捗と在庫が一致します。
- `enableOptimizer = false` で同じ設備が元のAE2経路で動きます。
- 追加機能に対応する試験を `docs/TESTING.md` へ追記します。
- Config、README、CHANGELOG、実装資料を同じPRで更新します。

## 回帰試験で必ず見る問題

- クラフト計算が永久に終了しない問題を再発させないこと。
- Batch受理数と期待出力の差でクラフトが完了しない問題を再発させないこと。
- 在庫ゼロの品目を端末から入れた際の消失を再発させないこと。
- Import/Export BusがGrid Tick遅延で停止する問題を再発させないこと。
- Circuit Cutterが負結果キャッシュから復帰しない問題を再発させないこと。
- Provider変更後も古いPattern索引を使う問題を再発させないこと。
- Client/ServerのContainer Slot数やpacket長をずらさないこと。
- Optional Mod更新時のMixin失敗を無関係な環境へ波及させないこと。
- ログを毎tickのWARN/ERRORで汚染しないこと。

## チーム開発ルール

- 一つのPRは一つの性能問題または一つの連携へ絞ります。
- Issueへ対象設備、負荷条件、再現手順、変更前profile、期待する不変条件を書きます。
- PR本文へ対象Config、Mixin対象、失効条件、Fallback、試験結果を書きます。
- Mixin内へ複雑なキャッシュロジックを直接置きません。
- 新しいCache Keyは、等価性を構成する全要素を含めます。
- Weak Referenceを正しさの保証として使用しません。
- BlockPosだけで別Dimensionの対象を同一視しません。
- Recipe IDだけで異なる入力状態やRecipe Typeを同一視しません。
- 外部MODのprivate実装へ依存する場合、対象バージョンと記述子を文書化します。
- 例外時に状態変更済みか未変更かを説明できない高速経路はmergeしません。
- 他MODのJAR、逆コンパイル済みsource、texture、world、runtime configをcommitしません。
- ログやcrash reportをcommitする前にtoken、IP、ユーザーディレクトリなどを除去します。
- レビューでは「速いか」より先に「同じ結果か」「失効できるか」「無効化できるか」を確認します。
- 実行していない試験を完了扱いにしません。

## バージョニング

- Patchは不具合修正、同一系列内の互換修正、文書修正に使用します。
- Minorは新しい最適化群、Config、任意連携、後方互換API追加に使用します。
- Majorは対応AE2系列、公開API、Config意味、保存形式の破壊的変更に使用します。
- 公開済みtagを作り直しません。
- Release Notesには既定ON/OFF、互換性、危険性、移行手順を明記します。
- native Batch Adapter追加時は、対応機械と保証する永続化境界を明記します。

## 安定版ランタイムの非目標

- AE2のCrafting Graph Solverを独自実装へ置換しません。
- AE2-UELを1.20.1へ丸ごと移植しません。
- クラフトCPUの容量や表示コプロセッサ数を増やしません。
- GTCEu/MekanismのRecipe LogicをACO側で再実装しません。
- 外部機械をzero-tick化しません。
- 機械の出力をACOが代理生成しません。
- Storage内容やNetwork TopologyをACO独自形式で保存しません。
- Bukkit/Paper/Arclight専用処理を追加しません。
- 通常AE2の在庫、標準CPU、標準PacketをBigInteger型へ置換しません。実験ツリーの
  BigInteger機能は、明示連携するCPU追加MOD用のversioned sidecar APIに限定します。
- 非同期スレッドでAE2のmutable stateを探索しません。

## 実験機能の合格条件

- 通常実行はSequential Instantを使用し、AE2/AdvancedAE本来の一回分の入力抽出、電力、
  Provider、task進捗、waitingFor会計を変更しません。固定の低いtick回数上限ではなく、
  maxPatterns、Provider Backpressure、CPU/Grid実時間予算で停止します。
- V2 Native GTCEu/Mekanism Batchは、完全一致レシピ、上流の実処理上限、Pattern
  Providerの永続send buffer、all-or-zero受領票をすべて確認できる場合だけ実行します。
- 部分受理、曖昧なターゲット、確率出力、返却物、未対応CapabilityはNative Batch対象にしません。
- BigInteger CPUホストは、保存、再起動、複数ジョブ、キャンセル、ページ同期、long実行窓の
  全試験を通過するまでAQEや通常AE2へ接続しません。
- 端末のVisible Range Syncは、世代単位のatomic snapshotとclick整合性を証明してから既定ONを検討します。
- 成功済みCraft Plan Cacheは、提出直前の在庫・Provider世代再検証を実装してから既定ONを検討します。
- tick跨ぎCapability Cacheは、対象MODのInvalidationを実機で確認してから個別allow-list化します。
- 非同期化はimmutable projection、generation check、main-thread commitの三条件を満たす処理だけに限定します。

## 参考資料

- Applied Energistics 2 `forge/1.20.1`: https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/forge/1.20.1
- AE2 1.20.1 Crafting CPU Guide: https://guide.appliedenergistics.org/1.20.1/items-blocks-machines/crafting_cpu_multiblock
- AE2 1.20.1 Autocrafting Guide: https://guide.appliedenergistics.org/1.20.1/ae2-mechanics/autocrafting
- AE2 performance issue `#7884`: https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/7884
- AE2 terminal/network update issue `#2363`: https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/2363
- AE2-UEL source: https://github.com/AE2-UEL/Applied-Energistics-2
- AE2-UEL issue tracker: https://github.com/AE2-UEL/Applied-Energistics-2/issues
- GTNH AE2 feature proposal `#11349`: https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/11349
- AE2 Fluid Crafting Rework: https://github.com/AE2-UEL/AE2FluidCraft-Rework
- GTCEu Modern `1.20.1`: https://github.com/GregTechCEu/GregTech-Modern/tree/1.20.1
- Mekanism `1.20.x`: https://github.com/mekanism/Mekanism/tree/1.20.x
- Advanced AE: https://github.com/pedroksl/AdvancedAE
- ExtendedAE: https://github.com/GlodBlock/ExtendedAE
- AE2 Overclocked: https://github.com/MOAKIEE/ae2overclocked
- Neo ECO AE Extension: https://github.com/DancingSnow0517/NeoECOAEExtension
- Forge Config documentation: https://docs.minecraftforge.net/en/1.20.x/misc/config/
- SpongePowered Mixin documentation: https://github.com/SpongePowered/Mixin/wiki/Advanced-Mixin-Usage---Callback-Injectors

## 参考思想とACOでの採用範囲

- AE2本体から、Crafting CPU、Pattern Provider、Crafting Service、Storage Serviceの正規動作を採用します。
- AE2公式Guideから、コプロセッサは投入頻度を増やす部品であるという意味を採用します。
- AE2-UEL/GTNHから、変更時失効、世代管理、反復走査削減、端末同期抑制の設計思想を採用します。
- GTNH `#11349` から、機械に毎tick全レシピを推測させず、AE側の期待出力を候補絞り込みへ使うIntentの着想を採用します。
- GTNH `#11349` から、Player用CPUとAutomation用CPUを分ける考え方を確認しますが、ACOはAE2既存設定を置換しません。
- GTNH `#11349` から、端末へ全在庫を毎回送らない方向を参考にしますが、クリック整合性を壊す実装は既定OFFとします。
- AE2 Fluid Crafting Reworkから、Fluidをdummy itemへ潰さず型付き入力として扱う思想を参考にします。
- GTCEu/Mekanism本体から、最終候補検証と機械状態判定を必ず対象MODへ委譲します。
- Mixin資料から、最小注入、明確な対象、対象更新時の再検証、決定的失敗を採用します。
- ACOは上記プロジェクトのforkまたはsource portではありません。
- 第三者のsource file、asset、binaryをACOへ同梱しません。
- 参照元のライセンスと帰属は `NOTICE.md` でも管理します。
