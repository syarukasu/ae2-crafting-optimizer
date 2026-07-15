# AE2 Crafting Optimizer 1.0.0

## English

AE2 Crafting Optimizer is a Forge 1.20.1 performance add-on for Applied Energistics 2 15.4.x. It targets repeated crafting/provider lookups, giant crafting CPU pattern-push bursts, terminal synchronization, and repeated machine recipe discovery after Pattern Provider pushes.

Highlights:

- Keeps AE2 authoritative for craft planning, recipes, storage mutation, and job submission.
- Shares identical active calculations and caches bounded, invalidated lookup results.
- Paces giant CPU execution without changing displayed capacity or co-processor count.
- Captures short-lived Pattern Provider intent for optional GTCEu Modern and Mekanism candidate fast paths.
- Includes per-world server config switches and an emergency master disable.
- Supports dedicated servers and singleplayer; install the same jar on server and clients.

Required: Minecraft 1.20.1, Forge 47.4.18+, Java 17, and AE2 15.4.10.

License: GNU Lesser General Public License v3.0 only (`LGPL-3.0-only`).

This is a development release that Mixins into AE2 15.4.x internals. Back up the world and test pack-specific automation before enabling disabled-by-default deep options.

## 日本語

AE2 Crafting Optimizerは、Forge 1.20.1 / AE2 15.4.x向けの最適化アドオンです。同一クラフト計算やパターン検索の重複、巨大CPUによる1 tickへのパターン投入集中、端末同期、Pattern Provider搬入後の機械側レシピ再検索を軽減します。

主な内容:

- クラフト可否、レシピ、ストレージ操作、ジョブ投入は引き続きAE2が判定します。
- 同一の進行中計算を一本化し、上限と無効化条件を持つ検索キャッシュを利用します。
- CPU容量や表示並列数を変えず、巨大CPUの1 tickあたり実行量を制御します。
- Pattern Providerの短時間レシピ意図をGTCEu Modern/Mekanismの任意高速経路へ渡します。
- ワールド単位のServer Configと緊急停止用マスタースイッチを備えます。
- 専用サーバーとシングルプレイに対応し、サーバーとクライアントへ同一JARが必要です。

必須環境: Minecraft 1.20.1、Forge 47.4.18以上、Java 17、AE2 15.4.10。

ライセンス: GNU Lesser General Public License v3.0 only（`LGPL-3.0-only`）。

AE2 15.4.x内部へMixinする開発版です。導入前にワールドをバックアップし、既定で無効な深い機能は自環境で個別に試験してください。
