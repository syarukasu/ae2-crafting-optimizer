# CurseForge Description

## English

### Short Summary

Configurable AE2 optimizer with faster crafting calculations, TPS-bounded CPU dispatch, exact transactional batching, and validated GTCEu/Mekanism recipe lookup.

### Full Description

**AE2 Crafting Optimizer (ACO)** is a configurable performance add-on for Applied Energistics 2 on Minecraft Forge 1.20.1.

It is intended for large automation packs where AE2 networks perform frequent or extremely large autocrafting jobs. ACO reduces repeated work around crafting calculations, pattern/provider lookups, machine recipe discovery, terminal synchronization, and giant crafting CPU execution bursts.

### Version 1.2.0: Instant and Transactional Dispatch

ACO 1.2.0 rebuilds processing-pattern batching around an explicit accepted-execution-count contract.

- Instant pattern dispatch may visit multiple ready tasks and adapter transactions in one crafting CPU call.
- The default pass is bounded by AE2's effective CPU operation allowance, 1,024 transactions, and a 4 ms wall-clock deadline. Remaining work resumes on a later server tick.
- A batch adapter reports exactly how many complete pattern executions the target durably accepted.
- Energy, task progress, expected outputs, and AE2's waiting-output accounting advance only by that accepted count.
- Unaccepted inputs are returned, and unsupported patterns or targets fall back to AE2 before ownership is transferred.
- The built-in standard/Advanced AE adapter deliberately preserves one original `pushPattern` call per accepted execution and stops on provider backpressure.

"Instant" describes rapid pattern dispatch. It does not skip machine duration, create outputs directly, or make GTCEu/Mekanism machines process in zero ticks. A public internal adapter API is available for integrations that can provide a genuinely durable accepted-count contract; ACO 1.2.0 does not claim a native one-call GTCEu or Mekanism machine queue.

### Main Features

- Shares identical in-flight crafting calculations from the same requester and ME network.
- Uses bounded, short-lived caches for pattern lookups, craftable sets, and missing/simulation plans.
- Invalidates crafting caches when relevant providers, storage, or network structure changes.
- Limits how much of AE2's pattern-push execution loop one giant CPU may spend in a server tick.
- Adapts the execution budget when a CPU exceeds the configured server-tick time target.
- Leaves AE2 terminal, storage-watcher, inventory packet, Import/Export Bus, and IO Port behavior untouched in 1.2.2; the earlier mutable rewrite Mixins are compatibility-disabled.
- Captures short-lived processing intent when a Pattern Provider successfully pushes a pattern.
- Optionally uses that intent to try a small, validated recipe candidate set in GTCEu Modern and Mekanism before their normal recipe search.
- Supports item, fluid, and chemical-aware recipe-intent lookup where the target integration exposes those inputs.
- Applies per-CPU adaptive execution budgets and a shared per-ME-grid execution budget so several giant CPUs cannot independently consume the full tick target.
- Optimizes selected Import Bus, Export Bus, IO Port, Assembly Matrix, Circuit Cutter, Reaction Chamber, and AE2 Overclock hot paths with bounded caches or incremental work.
- Includes diagnostics for slow crafting calculations and recipe-intent inspection commands.

### What ACO Does Not Change

ACO does not add or remove recipes, alter crafting eligibility, replace AE2's crafting graph solver, change storage contents, increase crafting CPU storage, increase the displayed co-processor count, or modify Advanced AE Quantum Computer structures.

AE2 remains authoritative for crafting plans, missing ingredients, job submission, storage mutation, and network state. GTCEu and Mekanism fast paths only return candidates accepted by the target mod's normal validation; otherwise their original search path runs. ACO does not change machine recipes, processing duration, energy costs, or output generation.

### Requirements

- Minecraft 1.20.1
- Forge 47.4.18 or newer
- Java 17
- Applied Energistics 2 15.4.10 (`15.4.x`)

Install the same ACO jar on the dedicated server and every client.

GTCEu Modern, Mekanism, Advanced AE, and Neo ECO AE Extension integrations are optional. Their pseudo-Mixins remain inactive when the corresponding mod is not installed. Advanced AE Quantum Computers receive only ACO's effective server execution cap. Neo ECO 20.3.x custom CPUs can join ACO's adaptive and shared execution budgets while retaining Neo ECO's own scheduler and FastPath. Capacity, structure rules, displayed values, and crafting results remain unchanged. The 1.2.0 accepted-execution-count API remains link-compatible, but its CPU execution Mixins are unregistered in 1.2.2.

ACO 1.2.2 unregisters terminal snapshots and craftable caching, storage-watcher pacing, aggregate refresh coalescing, rolling terminal range packets, client view coalescing, mutable Import/Export Bus and IO Port paths, capability/storage-simulation caches, grid-tick deferral, and legacy transactional CPU execution. Their old config keys remain readable but cannot reactivate those paths.

### Configuration

ACO uses a global Forge common config. Keep the server and client copies aligned:

```text
config/ae2_crafting_optimizer-common.toml
```

Every optimization can be disabled through configuration, and `enableOptimizer = false` disables all ACO behavior for comparison or recovery testing.

Ambiguous crafting paths still use AE2's normal solver. More invasive controls such as availability-based pattern ordering, successful-plan reuse, and terminal snapshot/range rewriting remain disabled by default.

Existing worlds retain their current config values when the mod is updated. Back up the world and test pack-specific automation before enabling disabled-by-default options.

### Compatibility and Reporting

ACO Mixins into AE2 15.4.x internals. Do not assume compatibility with another AE2 minor or major series without testing.

If a problem appears, restart once with `enableOptimizer = false` and reproduce it before reporting upstream. Please report ACO-specific issues to the ACO project instead of AE2, GTCEu, Mekanism, Advanced AE, or Neo ECO unless the problem also occurs without ACO.

- Source code and issue tracker: https://github.com/syarukasu/ae2-crafting-optimizer
- License: GNU Lesser General Public License v3.0 only (`LGPL-3.0-only`)

---

## 日本語

### 短い説明

AE2のクラフト計算、巨大CPUの投入負荷、端末同期、GTCEu/Mekanismのレシピ探索を軽減し、実受理数ベースの安全なバッチ処理を追加する最適化MODです。

### 詳細説明

**AE2 Crafting Optimizer（ACO）**は、Minecraft Forge 1.20.1向けApplied Energistics 2最適化アドオンです。

頻繁な自動クラフトや非常に巨大なクラフト要求を扱う大規模工業環境を対象にしています。クラフト計算、パターン・Provider検索、機械側のレシピ探索、端末同期、巨大クラフトCPUによる1 tickへの処理集中を軽減します。

### バージョン1.2.0: 即時・トランザクション投入

ACO 1.2.0では、処理パターンのバッチ機構を「実際に受理された完全な実行回数」を返す契約として作り直しました。

- 一回のクラフトCPU呼び出し内で、複数の実行可能タスクと複数のアダプタ処理を続けて投入できます。
- 既定では、AE2 CPUの有効操作数、最大1,024トランザクション、実時間4 msの三つで処理を制限し、残りは後続tickへ持ち越します。
- アダプタは、対象機械が永続的に受理した完全なパターン実行数を正確に返します。
- 電力、タスク進捗、期待出力、AE2の出力待ち数は、その実受理数だけ更新されます。
- 受理されなかった入力は戻し、非対応パターン・対象は所有権を移す前にAE2本来の経路へ戻します。
- 標準AE2・Advanced AE向け内蔵アダプタは、安全性のため実受理一回ごとに元の`pushPattern`を一回呼び、Providerが詰まった時点で停止します。

ここでいう「即時」はパターン投入を素早く進める機能です。機械の処理時間を飛ばしたり、出力を直接生成したり、GTCEu/Mekanism機械をゼロtick化したりはしません。永続的な実受理数を保証できる連携向けに内部公開APIを用意していますが、ACO 1.2.0はGTCEu/Mekanismへの一回呼び出し型ネイティブキューを実装済みとはしていません。

### 主な機能

- 同じ要求元・MEネットワーク・出力・個数・計算方式の進行中クラフト計算を一本化します。
- パターン検索、クラフト可能一覧、欠品・シミュレーション結果へ上限付き短時間キャッシュを使用します。
- Provider、ストレージ、ネットワーク構造の変化時に関連キャッシュを無効化します。
- 巨大CPUが1 tick内で実行するAE2のパターン投入処理量を制限します。
- CPU処理時間が設定したtick時間目標を超えた場合、実行予算を適応的に調整します。
- 1.2.2ではAE2本来の端末・Storage Watcher・在庫packet・Import/Export Bus・IO Port処理を維持し、以前の可変経路Mixinは互換性のため無効化しています。
- Pattern Providerがパターンを正常投入した際、処理目的を短時間記録します。
- GTCEu ModernとMekanismでは、その目的から少数の候補を先に試し、各MOD本来の検証を通過したレシピだけを使用します。
- 対象連携が入力を公開している場合、アイテム・液体・化学物質を含むレシピ目的検索に対応します。
- CPU単位の適応実行予算とMEグリッド共有予算を併用し、複数の巨大CPUがそれぞれtick予算を使い切ることを防ぎます。
- Import Bus、Export Bus、IO Port、組立マトリックス、回路スライサー、反応室、AE2 Overclockの一部ホットパスを、上限付きキャッシュや増分処理で軽減します。
- 遅いクラフト計算の診断ログと、記録されたレシピ目的を確認するコマンドを追加します。

### ACOが変更しないもの

ACOはレシピの追加・削除、クラフト可否、AE2クラフトグラフソルバー、ストレージ内容、クラフトCPU容量、表示上のコプロセッサ数、Advanced AE Quantum Computerの構造を変更しません。

クラフト計画、欠品判定、ジョブ投入、ストレージ操作、ネットワーク状態は引き続きAE2が最終決定します。GTCEu/Mekanismの高速経路で候補を確定できない場合は、各MOD本来のレシピ検索へ戻ります。機械レシピ、処理時間、消費電力、出力生成も変更しません。

### 必須環境

- Minecraft 1.20.1
- Forge 47.4.18以上
- Java 17
- Applied Energistics 2 15.4.10（`15.4.x`）

専用サーバーと全クライアントへ同じACOのJARを導入してください。

GTCEu Modern、Mekanism、Advanced AE、Neo ECO AE Extensionは任意連携です。対象MODが無い場合、そのPseudo Mixinは適用されません。Advanced AE Quantum Computerには表示値を変えない実行上限だけを適用します。Neo ECO 20.3.xの独自CPUは、本体のスケジューラとFastPathを維持したままACOのCPU別適応予算・MEグリッド共有予算へ参加できます。容量、構造、表示値、クラフト結果は変更しません。1.2.0の実受理数APIはリンク互換のため残しますが、1.2.2ではCPU実行Mixinを登録していません。

ACO 1.2.2では、端末snapshot/craftable、Storage Watcher、aggregate refresh、端末range packet、client view、Import/Export Bus、IO Port、Capability、ストレージsimulation、Grid Tick遅延、旧transaction CPU実行を登録解除しています。以前のConfigキーは読み込めますが、これらの経路を再有効化しません。

### Config

ACOはグローバルなForge Common Configを使用します。サーバー側とクライアント側で同じ設定を使用してください。

```text
config/ae2_crafting_optimizer-common.toml
```

各最適化はConfigで無効化できます。`enableOptimizer = false`にすると、比較試験や復旧確認のためACOの全処理を停止できます。

欠品の決定論的早期終了、Grid Tick遅延、Import/Export Bus操作数上限、失敗した自動クラフト要求のクールダウン、在庫量によるパターン順変更、Fuzzy Export Busキャッシュ、成功済み計画の再利用、端末スナップショット・表示範囲書き換えなど、影響範囲が広い機能は既定で無効です。

既存ワールドではアップデート後も現在のConfig値が保持されます。既定で無効な機能を有効化する前にワールドをバックアップし、自環境の自動化設備で確認してください。

### 互換性と問題報告

ACOはAE2 15.4.x内部へMixinします。別のAE2マイナー・メジャー系列での互換性は、試験なしに保証できません。

問題が発生した場合は、まず`enableOptimizer = false`へ変更して再起動し、同じ条件で再現するか確認してください。ACOを外すと発生しない問題は、AE2、GTCEu、Mekanism、Advanced AE、Neo ECO側ではなくACO側へ報告してください。

- ソースコード・Issue: https://github.com/syarukasu/ae2-crafting-optimizer
- ライセンス: GNU Lesser General Public License v3.0 only（`LGPL-3.0-only`）
