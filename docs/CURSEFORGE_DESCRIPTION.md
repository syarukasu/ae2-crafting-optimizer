# AE2 Crafting Optimizer

## English

ACO is a crafting-planning and integration mod for Applied Energistics 2, intended for large industrial automation networks.

**ACO 2.0.0 is currently an Alpha snapshot, not a stable release.** The following describes the refs.8 generation, not the older 1.5.x releases. Check each file's Minecraft version and loader before installing.

### Planning and quantities

- Uses the bundled VM fork for crafting-plan calculations.
- Plans from the inventory and registered patterns observed for the order.
- Keeps authoritative VM quantities as BigInteger throughout calculation instead of clamping them to signed long.
- Reuses pattern information within a plan and aggregates eligible shared demands.
- Retains the public BigInteger API and exact-result integration for supported CPU/storage add-ons such as AQE.
- Provides bounded calculation diagnostics and `/aco vm recent`.

### Physical crafting and integrations

- Adds no ACO throughput cap to ordinary AE2 crafting dispatch or Pattern Provider transfer.
- Does not increase external CPU capacity or the displayed co-processor count.
- Keeps machine power, voltage, processing time and recipe validation under the machine mod's control.
- Includes recipe-intent optimizations for supported GTCEu paths and Mekanism item-to-item electric machines. This is not instant processing or support for every machine.
- Includes conditional caches for Advanced AE Reaction Chambers, ExtendedAE Circuit Cutters and Assembly Matrices, and AE2 Overclock lookups.

Optional optimizations depend on the loader, integration version, machine type and configuration. Code presence does not guarantee activation with every add-on release. ACO-owned exact/transactional jobs have separate execution accounting; this is not a global tick-time guarantee for all CPUs.

### Snapshot limitations

Full real-world acceptance remains pending for wide-job completion, cancellation/refunds, restart recovery, concurrent orders and sub-10-second industrial planning. There is no universal performance or lifecycle guarantee. Back up worlds before testing snapshots and include logs, exact versions and reproduction steps in bug reports.

### Installation

- Forge 1.20.1 uses Java 17; NeoForge 1.21.1 uses Java 21. Use only the matching file.
- Install the same ACO build on the server and clients, with the required AE2 version shown in the file metadata.
- The VM fork is bundled; do not install another copy of the upstream VM alongside it.
- BigInteger execution requires a compatible consumer/CPU. A normal AE2 CPU is not automatically enlarged.
- Configuration: `config/ae2_crafting_optimizer-common.toml`.

[Source and issues](https://github.com/syarukasu/ae2-crafting-optimizer) | [VM fork](https://github.com/syarukasu/AE2-VM-ACO)

License: LGPL-3.0-only.

---

## 日本語

ACOは、大規模な工業環境向けのAE2クラフト計画・連携MODです。

**ACO 2.0.0は現在Alphaスナップショットです。安定版ではありません。** 以下はrefs.8世代の説明で、旧1.5.xの仕様とは異なります。導入前に各ファイルのMinecraft版とローダーを確認してください。

### 計画と数量

- 同梱VMフォークでクラフト計画を計算します。
- 注文時に取得した在庫と登録パターンを計画に使います。
- 計算途中も数量をBigIntegerで保持し、long上限へ丸めません。
- 計画内のパターン情報を再利用し、対応する共通工程の需要をまとめます。
- 公開BigInteger APIと、AQEなどの対応CPU・ストレージとの正確な数量連携を維持します。
- 集計形式の診断ログと `/aco vm recent` を提供します。

### 実行と機械連携

- 通常のAE2クラフト投入・Pattern Provider搬入出に、ACO独自の速度制限を加えません。
- 外部CPUの容量や表示上のコプロセッサ数を増やしません。
- 電力・電圧・処理時間・レシピの適格性は、機械MOD本来の判定を維持します。
- GTCEuの対応経路とMekanismのアイテム加工用電動機械に、レシピ意図を使う最適化があります。全機種対応や瞬間加工ではありません。
- Advanced AE反応室、ExtendedAEの回路スライサー・組立マトリックス、AE2 Overclock向けの条件付きキャッシュがあります。

任意連携の有効範囲はローダー・MODの版・機種・設定に依存します。実装が存在しても、すべての版で動作する保証はありません。ACO所有のexact・transactionalジョブの実行会計は別であり、全CPUのtick占有時間を保証する機能ではありません。

### 未完了の実機受入

巨大注文の実行完了、取消・返却、再起動復旧、同時注文、工業環境での10秒未満の計画計算は、実機受入が完了していません。スナップショットの試用前にワールドをバックアップしてください。不具合報告にはログ・正確な導入版・再現手順を添えてください。

### 導入

- Forge 1.20.1はJava 17、NeoForge 1.21.1はJava 21を使用し、対応するファイルを選んでください。
- 必須のAE2版を確認し、サーバーとクライアントに同じACOビルドを導入してください。
- VMフォークは同梱されています。別途本家VMを重複導入しないでください。
- BigIntegerでの実行には対応する利用側・CPUが必要です。通常AE2 CPUの容量は自動拡張されません。
- 設定ファイル：`config/ae2_crafting_optimizer-common.toml`。

[ソースコード・不具合報告](https://github.com/syarukasu/ae2-crafting-optimizer) | [VMフォーク](https://github.com/syarukasu/AE2-VM-ACO)

ライセンス：LGPL-3.0-only。
