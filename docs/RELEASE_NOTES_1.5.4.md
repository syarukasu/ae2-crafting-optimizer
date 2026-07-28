# AE2 Crafting Optimizer 1.5.4

## English

This release replaces the former direct whole-tree conversion with a
quantity-independent physical crafting-table execution path.

- Compiles deterministic crafting-table DAGs once per provider generation.
- Plans `Long.MAX_VALUE` and supported BigInteger orders by distinct recipe
  count instead of requested quantity.
- Routes every proven recipe step through a real Neo ECO Pattern Bus, Worker,
  and Crafting Thread when Advanced Assembly Computing is installed.
- Performs one real `assemble` call per recipe step and treats the exact
  BigInteger execution count as a verified multiplication coefficient.
- Keeps Advanced AE's real CPU job, `TaskProgress`, `waitingFor`, final-output
  count, cancellation, persistence, and `CraftingLink` as the authoritative
  lifecycle.
- Adds durable physical receipts, exact escrow accounting, replay protection,
  restart recovery, and explicit quarantine for unprovable ownership.
- Fixes shared-total consistency for ExtendedAE Plus BigInteger cells exposed
  through multiple inventory wrappers.
- Keeps normal AE2 jobs and unsupported recipe shapes on their authoritative
  fallback paths.

The same JAR is required on the dedicated server and every client. AAC is an
optional physical backend and is released separately as version `1.0.0`.

## 日本語

旧Exact Vectorのツリー全体直接変換を廃止し、注文数量に依存しない
作業台物理クラフト経路へ置き換えるリリースです。

- Provider世代ごとに決定的な作業台クラフトDAGを一度だけコンパイル。
- `Long.MAX_VALUE`級と対応範囲内のBigInteger注文を、注文数ではなく
  到達する固有レシピ数に比例して計画。
- Advanced Assembly Computing導入時は、証明済みの各レシピ段を
  Neo ECOの実Pattern Bus、Worker、Crafting Threadへ配送。
- 各段で実`assemble`を一回だけ実行し、正確なBigInteger実行数は
  検証済みの乗算係数として会計。
- Advanced AEの実CPU Job、`TaskProgress`、`waitingFor`、最終出力数、
  取消、保存、`CraftingLink`を正本ライフサイクルとして維持。
- 永続物理Receipt、Exact Escrow、再送防止、再起動復旧、不確定所有権の
  明示隔離を追加。
- 複数Inventory wrapperから公開されるExtendedAE Plus BigIntegerセルの
  共有総量不整合を修正。
- 通常AE2注文と未対応レシピ形状は、それぞれ本来の経路へFallback。

専用サーバーと全クライアントへ同じJARを導入してください。物理Backendの
AACは任意連携で、別プロジェクトの`1.0.0`として公開します。
