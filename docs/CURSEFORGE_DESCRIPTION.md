# CurseForge Description

## English

### Short Summary

Configurable AE2 optimizer with checked crafting calculations, TPS-bounded CPU dispatch, exact BigInteger accounting, and optional AQE/AAC integration.

### Full Description

**AE2 Crafting Optimizer (ACO)** is a NeoForge 1.21.1 optimization and
integration layer for Applied Energistics 2. It targets large automation
networks and exceptionally large autocrafting requests while keeping AE2
authoritative for normal recipes, providers, storage mutation, and job
submission.

Main features:

- generation-keyed compiled Pattern graphs and calculation-local memoization;
- checked `long` arithmetic with bounded BigInteger promotion on overflow;
- conservative fallback whenever a recipe cannot be proven equivalent;
- per-CPU and per-grid execution budgets for large co-processor counts;
- durable transaction recovery for compatible batch adapters;
- optional Advanced Quantum Engineering and Advanced Assembly Computing APIs;
- bounded client synchronization and diagnostics.

ACO does not add recipes, increase crafting CPU capacity, or change normal
AE2 crafting eligibility. Install the same JAR on the server and every client.

Requirements for ACO 1.6.x:

- Minecraft 1.21.1
- NeoForge 21.1.247 or newer in the 21.1 series
- Java 21
- Applied Energistics 2 19.2.17

Optional integrations are versioned separately. ACO 1.6.x supports the audited
Advanced AE 1.6.x-1.21.1 series and Neo ECO AE Extension 21.1.1. The 1.5.x release
line remains available for Forge 1.20.1.

## 日本語

### 短い説明

検査付きクラフト計算、TPS予算付きCPU実行、正確なBigInteger会計、AQE/AAC任意連携を備えたAE2最適化MODです。

### 詳細説明

**AE2 Crafting Optimizer（ACO）**は、Applied Energistics 2向けの
NeoForge 1.21.1最適化・連携レイヤーです。大規模な自動化ネットワークと
極端に大きい自動クラフト注文を対象にしつつ、通常レシピ、Provider、
ストレージ変更、ジョブ投入の最終判断はAE2へ残します。

主な機能:

- 世代管理されたCompiled Pattern Graphと計算内メモ化
- 検査付き`long`演算と、オーバーフロー時だけ行うBigInteger昇格
- 同値性を証明できないレシピの保守的なAE2フォールバック
- 巨大なコプロセッサ数向けのCPU単位・ME Grid単位の実行予算
- 対応Batch Adapter向けの永続Transaction復旧
- Advanced Quantum Engineering / Advanced Assembly Computing任意連携API
- 上限付きクライアント同期と診断機能

ACOはレシピ追加、CPU容量増加、通常AE2のクラフト可否変更を行いません。
サーバーと全クライアントへ同じJARを導入してください。

ACO 1.6.xの必須環境:

- Minecraft 1.21.1
- NeoForge 21.1.247以降の21.1系列
- Java 21
- Applied Energistics 2 19.2.17

ACO 1.6.xはAdvanced AE 1.6.x-1.21.1およびNeo ECO AE Extension
21.1.1との組み合わせを対象にしています。Forge 1.20.1向けには
引き続き1.5.x系列を使用してください。
