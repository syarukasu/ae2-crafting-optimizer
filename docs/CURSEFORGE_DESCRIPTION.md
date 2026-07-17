# CurseForge Description

## English

### Short Summary

Performance add-on for AE2 that reduces duplicate crafting work, paces giant CPU execution, and accelerates validated GTCEu/Mekanism recipe lookup.

### Full Description

**AE2 Crafting Optimizer (ACO)** is a configurable performance add-on for Applied Energistics 2 on Minecraft Forge 1.20.1.

It is intended for large automation packs where AE2 networks perform frequent or extremely large autocrafting jobs. ACO reduces repeated work around crafting calculations, pattern/provider lookups, machine recipe discovery, terminal synchronization, and giant crafting CPU execution bursts.

### Main Features

- Shares identical in-flight crafting calculations from the same requester and ME network.
- Uses bounded, short-lived caches for pattern lookups, craftable sets, and missing/simulation plans.
- Invalidates crafting caches when relevant providers, storage, or network structure changes.
- Limits how much of AE2's pattern-push execution loop one giant CPU may spend in a server tick.
- Adapts the execution budget when a CPU exceeds the configured server-tick time target.
- Reuses ME terminal inventory snapshots and craftable lists for a small configurable interval.
- Coalesces selected client-visible storage and terminal synchronization work.
- Captures short-lived processing intent when a Pattern Provider successfully pushes a pattern.
- Optionally uses that intent to try a small, validated recipe candidate set in GTCEu Modern and Mekanism before their normal recipe search.
- Includes diagnostics for slow crafting calculations and recipe-intent inspection commands.

### What ACO Does Not Change

ACO does not add or remove recipes, alter crafting eligibility, replace AE2's crafting graph solver, change storage contents, increase crafting CPU storage, increase the displayed co-processor count, or modify Advanced AE Quantum Computer structures.

AE2 remains authoritative for crafting plans, missing ingredients, job submission, storage mutation, and network state. GTCEu and Mekanism fast paths only return candidates accepted by the target mod's normal validation; otherwise their original search path runs.

### Requirements

- Minecraft 1.20.1
- Forge 47.4.18 or newer
- Java 17
- Applied Energistics 2 15.4.10 (`15.4.x`)

Install the same ACO jar on the dedicated server and every client.

GTCEu Modern, Mekanism, Advanced AE, and Neo ECO AE Extension integrations are optional. Their pseudo-Mixins remain inactive when the corresponding mod is not installed. Advanced AE Quantum Computers receive only ACO's effective server execution cap. Neo ECO 20.3.x custom CPUs can join ACO's adaptive and shared execution budgets while retaining Neo ECO's own scheduler and FastPath. Capacity, structure rules, displayed values, and crafting results remain unchanged. ACO 1.2.0 adds an exact accepted-execution-count batch adapter API; the former unsafe aggregate-inventory path remains permanently disabled.

### Configuration

ACO uses a per-world Forge server config:

```text
<world>/serverconfig/ae2_crafting_optimizer-server.toml
```

Every optimization can be disabled through configuration, and `enableOptimizer = false` disables all ACO behavior for comparison or recovery testing.

High-impact controls such as deterministic missing fast-fail, grid-tick deferral, Import/Export Bus operation caps, failed automatic craft-request cooldowns, availability-based pattern ordering, fuzzy Export Bus caching, successful-plan reuse, and terminal snapshot/range rewriting are disabled by default.

Existing worlds retain their current config values when the mod is updated. Back up the world and test pack-specific automation before enabling disabled-by-default options.

### Compatibility and Reporting

ACO Mixins into AE2 15.4.x internals. Do not assume compatibility with another AE2 minor or major series without testing.

If a problem appears, restart once with `enableOptimizer = false` and reproduce it before reporting upstream. Please report ACO-specific issues to the ACO project instead of AE2, GTCEu, Mekanism, Advanced AE, or Neo ECO unless the problem also occurs without ACO.

- Source code and issue tracker: https://github.com/syarukasu/ae2-crafting-optimizer
- License: GNU Lesser General Public License v3.0 only (`LGPL-3.0-only`)

---

## 日本語

### 短い説明

AE2の重複クラフト処理、巨大CPUの投入バースト、端末同期、GTCEu/Mekanismのレシピ探索を軽減する最適化MODです。

### 詳細説明

**AE2 Crafting Optimizer（ACO）**は、Minecraft Forge 1.20.1向けApplied Energistics 2最適化アドオンです。

頻繁な自動クラフトや非常に巨大なクラフト要求を扱う大規模工業環境を対象にしています。クラフト計算、パターン・Provider検索、機械側のレシピ探索、端末同期、巨大クラフトCPUによる1 tickへの処理集中を軽減します。

### 主な機能

- 同じ要求元・MEネットワーク・出力・個数・計算方式の進行中クラフト計算を一本化します。
- パターン検索、クラフト可能一覧、欠品・シミュレーション結果へ上限付き短時間キャッシュを使用します。
- Provider、ストレージ、ネットワーク構造の変化時に関連キャッシュを無効化します。
- 巨大CPUが1 tick内で実行するAE2のパターン投入処理量を制限します。
- CPU処理時間が設定したtick時間目標を超えた場合、実行予算を適応的に調整します。
- ME端末の在庫スナップショットとクラフト可能一覧を短い設定間隔だけ再利用します。
- 一部のクライアント表示用ストレージ・端末同期をまとめて処理します。
- Pattern Providerがパターンを正常投入した際、処理目的を短時間記録します。
- GTCEu ModernとMekanismでは、その目的から少数の候補を先に試し、各MOD本来の検証を通過したレシピだけを使用します。
- 遅いクラフト計算の診断ログと、記録されたレシピ目的を確認するコマンドを追加します。

### ACOが変更しないもの

ACOはレシピの追加・削除、クラフト可否、AE2クラフトグラフソルバー、ストレージ内容、クラフトCPU容量、表示上のコプロセッサ数、Advanced AE Quantum Computerの構造を変更しません。

クラフト計画、欠品判定、ジョブ投入、ストレージ操作、ネットワーク状態は引き続きAE2が最終決定します。GTCEu/Mekanismの高速経路で候補を確定できない場合は、各MOD本来のレシピ検索へ戻ります。

### 必須環境

- Minecraft 1.20.1
- Forge 47.4.18以上
- Java 17
- Applied Energistics 2 15.4.10（`15.4.x`）

専用サーバーと全クライアントへ同じACOのJARを導入してください。

GTCEu Modern、Mekanism、Advanced AE、Neo ECO AE Extensionは任意連携です。対象MODが無い場合、そのPseudo Mixinは適用されません。Advanced AE Quantum Computerには表示値を変えない実行上限だけを適用します。Neo ECO 20.3.xの独自CPUは、本体のスケジューラとFastPathを維持したままACOのCPU別適応予算・MEグリッド共有予算へ参加できます。容量、構造、表示値、クラフト結果は変更しません。

### Config

ACOはワールド単位のForge Server Configを使用します。

```text
<ワールド>/serverconfig/ae2_crafting_optimizer-server.toml
```

各最適化はConfigで無効化できます。`enableOptimizer = false`にすると、比較試験や復旧確認のためACOの全処理を停止できます。

欠品の決定論的早期終了、Grid Tick遅延、Import/Export Bus操作数上限、失敗した自動クラフト要求のクールダウン、在庫量によるパターン順変更、Fuzzy Export Busキャッシュ、成功済み計画の再利用、端末スナップショット・表示範囲書き換えなど、影響範囲が広い機能は既定で無効です。

既存ワールドではアップデート後も現在のConfig値が保持されます。既定で無効な機能を有効化する前にワールドをバックアップし、自環境の自動化設備で確認してください。

### 互換性と問題報告

ACOはAE2 15.4.x内部へMixinします。別のAE2マイナー・メジャー系列での互換性は、試験なしに保証できません。

問題が発生した場合は、まず`enableOptimizer = false`へ変更して再起動し、同じ条件で再現するか確認してください。ACOを外すと発生しない問題は、AE2、GTCEu、Mekanism、Advanced AE、Neo ECO側ではなくACO側へ報告してください。

- ソースコード・Issue: https://github.com/syarukasu/ae2-crafting-optimizer
- ライセンス: GNU Lesser General Public License v3.0 only（`LGPL-3.0-only`）
