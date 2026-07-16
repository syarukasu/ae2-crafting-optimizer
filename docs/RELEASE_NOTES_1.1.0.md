# AE2 Crafting Optimizer 1.1.0

## English

ACO 1.1.0 expands the AE2-UEL/GTNH-inspired optimization layer for large Forge 1.20.1 automation networks while keeping AE2 and each machine mod authoritative.

### Highlights

- Adds a shared per-grid crafting execution budget and adaptive pacing for very large crafting CPUs.
- Adds crafting-job memoization, provider generations, IO Port cursors, Import/Export Bus locality caches, and terminal synchronization controls.
- Adds GTCEu and Mekanism recipe-intent candidate fast paths for item, fluid, and supported chemical processing.
- Adds optional processing-pattern micro-batching for supported GTCEu and Mekanism targets.
- Adds conservative caches for AdvancedAE Reaction Chambers, ExtendedAE Circuit Cutters and Assembly Matrices, and AE2 Overclock runtime helpers.
- Adds `/aco stats` diagnostics and independent server config switches for each optimization family.

### Compatibility Fix

Direct redirects into Reaction Chamber and Circuit Cutter methods added by AE2 Overclock's own Mixins are disabled. Forge Mixin cannot safely inject into those merged methods. AE2 Overclock's original machine-handler path remains active, while its directly targetable runtime-helper reflection, MethodHandle, and upgrade-count caches remain enabled.

### Requirements

- Minecraft 1.20.1
- Forge 47.4.20 or compatible 47.4.x runtime
- Applied Energistics 2 15.4.x
- Java 17 or newer as supported by the server runtime
- The same ACO JAR on the server and every client

Experimental paths remain independently configurable. Back up the world before enabling every experimental option in an existing production pack.

## 日本語

ACO 1.1.0では、大規模なForge 1.20.1自動化環境向けに、AE2-UEL/GTNHの設計思想を参考にした最適化を拡張しました。クラフト可否、レシピ判定、実搬送は引き続きAE2および各機械MODが最終決定します。

### 主な変更

- MEネットワーク単位のクラフト実行予算と、巨大クラフトCPU向けの適応制御を追加。
- クラフト計算内メモ、Provider世代管理、IO Portカーソル、Import/Export Bus局所性キャッシュ、端末同期制御を追加。
- アイテム・液体・対応Chemical処理向けにGTCEu/Mekanismのレシピ意図候補高速経路を追加。
- 対応GTCEu/Mekanism機械向けに、実験的な処理パターン・マイクロバッチを追加。
- AdvancedAE反応室、ExtendedAE回路スライサー・組立マトリックス、AE2 Overclockランタイムヘルパー向けの保守的キャッシュを追加。
- `/aco stats`診断と、最適化系統ごとの独立したサーバーConfigを追加。

### 互換性修正

AE2 Overclock自身のMixinが反応室・回路スライサーへ追加するメソッドへの二重Redirectは無効化しました。Forge Mixinでは他Mixinが結合したメソッドへ安全に注入できないためです。機械側はAE2 Overclock本来の処理へ戻り、直接対象にできるランタイムヘルパーの反射・MethodHandle・カード数キャッシュは有効なままです。

### 必須環境

- Minecraft 1.20.1
- Forge 47.4.20、または互換性のある47.4.xランタイム
- Applied Energistics 2 15.4.x
- サーバー環境が対応するJava 17以降
- サーバーと全クライアントへ同一のACO JARを導入

実験的機能は個別に切り替えられます。既存の本番ワールドですべてを有効化する前に、ワールドをバックアップしてください。
