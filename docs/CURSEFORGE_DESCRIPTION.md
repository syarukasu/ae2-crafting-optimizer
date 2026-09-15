# CurseForge Description

## English

AE2 Crafting Optimizer (ACO) is an optimization and exact-count integration
layer for Applied Energistics 2. It reduces repeated autocrafting work while
keeping AE2 authoritative for normal recipes, providers, jobs, storage, and UI.

### What ACO does

- generation-keyed Pattern lookup and compiled-graph caches;
- calculation-local inventory and candidate memoization;
- checked arithmetic with `BigInteger` promotion on `long` overflow;
- versioned exact-plan APIs for compatible add-ons;
- measured standard-AE2 CPU execution budgets and sequential dispatch waves;
- durable Transactional Pattern Batch V2 contracts for explicitly registered
  external adapters;
- optional Recipe Intent hints and bounded add-on lookup caches;
- diagnostics for slow calculations, plan declines, caches, and exact stalls.

ACO uses a compiled result only when recipe choice and accounting are proven.
Unsupported or ambiguous planning returns to AE2 before ACO takes ownership.
After an exact transaction owns input, it resumes, returns input exactly on
cancellation, or quarantines an uncertain state; it never retries through a
legacy path.

### What ACO does not do

- modify recipes, crafting validity, Quantum Computer structures, or storage;
- replace external CPU or machine execution;
- own AQE, InsaneAE, Advanced AE, NeoECO, GTCEu, or Mekanism progress/power;
- rewrite terminal, storage watcher, packet, bus, IO Port, P2P, or Grid Tick
  behavior;
- clamp exact counts to `long` for decisions;
- generate final output directly from a crafting plan.

Issue #164 removes the former Pattern Batch V1, external-CPU execution
managers, built-in GTCEu/Mekanism native batching, independent Fair Scheduler,
and retired no-op configuration. They are not compatibility fallbacks.

### Environment

- Minecraft 1.20.1
- Forge 47.4.18+
- Java 17
- Applied Energistics 2 15.4.10
- AE2 UELM 15.5.0-uelm replacement profile supported
- optional Advanced AE 1.3.x, Neo ECO AE Extension 20.3/20.4, and GTCEu
  integrations; Mekanism recipe lookup remains unmodified
- dedicated server, singleplayer, and Arclight as a normal Forge mod

Install the same ACO version on the server and every client. Report ACO-only
problems to this project first; do not report them upstream unless they also
reproduce without ACO.

## 日本語

AE2 Crafting Optimizer（ACO）は、Applied Energistics 2向けの最適化・exact数量
連携レイヤーです。通常レシピ、Provider、Job、ストレージ、GUIの正本をAE2に残した
まま、巨大自動クラフトで繰り返される処理を減らします。

### ACOが行うこと

- 世代付きPattern検索・Compiled Graph Cache
- 一計算内の在庫・候補メモ化
- checked演算と`long` overflow時の`BigInteger`昇格
- 対応アドオン向け版付きexact plan API
- 標準AE2 CPUの実測実行予算とSequential Dispatch Wave
- 明示登録Adapter向け永続Transactional Pattern Batch V2
- Recipe Intent hintと上限付きアドオン検索Cache
- 遅い計算、計画辞退、Cache、exact停滞の診断

レシピ選択と会計を証明できる場合だけCompiled結果を採用します。未対応または曖昧な
計画は、ACOが所有権を取る前にAE2へ返します。入力所有後は再開、正確な取消返却、
隔離のいずれかとし、legacy経路へ再投入しません。

### ACOが行わないこと

- レシピ、クラフト可否、Quantum Computer構造、ストレージ内容の変更
- 外部CPUや機械の実行置換
- AQE、InsaneAE、Advanced AE、NeoECO、GTCEu、Mekanismの進捗・電力所有
- 端末、Storage Watcher、Packet、Bus、IO Port、P2P、Grid Tickの書換え
- 判定用exact数量の`long`クランプ
- Planからの最終成果物直接生成

Issue #164で旧Pattern Batch V1、外部CPU実行Manager、内蔵GTCEu/Mekanism Native
Batch、独立Fair Scheduler、退役no-op設定を削除しました。互換Fallbackとしても
残していません。

サーバーと全クライアントへ同じACO版を導入してください。ACO固有の問題はまず本
プロジェクトへ報告し、ACOなしで再現しない問題を依存MOD作者へ直接報告しないで
ください。
