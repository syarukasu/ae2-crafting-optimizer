# AE2 Crafting Optimizer 1.5.0

## English

This release completes the first practical wide-order execution path for
Advanced Quantum Engineering while preserving AE2's standard order path.

- Adds a separate root-order path from `Integer.MAX_VALUE + 1` through
  `Long.MAX_VALUE` without replacing AE2's existing int packet and menu path.
- Adds exact BigInteger parent plans for deterministic AQE crafts and executes
  them as recipe-specific checked-long child windows.
- Uses a generation-cached compiled root program so deterministic calculations
  scale primarily with the number of distinct reachable recipes.
- Promotes checked-long calculations to BigInteger only when arithmetic
  overflows.
- Keeps exact item, fluid, and chemical inventory sidecars, including optional
  ExtendedAE Plus BigInteger cell integration.
- Adds fair child-window scheduling, restart-safe parent state, exact capacity
  reservation, stale-plan rejection, and bounded status synchronization.
- Fixes AE2 `CraftingPlan` compatibility, converging intermediate accounting,
  per-recipe execution-window overflow, and double charging during temporary
  Advanced AE CPU creation.
- Keeps AppliedE dynamic transmutation patterns on AE2's authoritative path.

Unsupported, ambiguous, cyclic, fuzzy, alternative-input, catalyst, return, and
byproduct routes continue to fall back to AE2. Install the same `1.5.0` jar on
the server and every client.

## 日本語

AE2の既存注文経路を維持したまま、Advanced Quantum Engineering向けの
巨大注文を実働させる最初の実用経路を完成させたリリースです。

- AE2本来のint PacketとMenu経路を置き換えず、
  `Integer.MAX_VALUE + 1`から`Long.MAX_VALUE`までのルート注文経路を追加。
- 決定的なAQEクラフトをBigInteger親計画として保持し、レシピごとの
  検査済みlong子Windowへ分割して実行。
- 世代キャッシュ付きCompiled Root Programにより、決定的な計算時間を
  注文数ではなく到達する固有レシピ数へ寄せる。
- 検査付きlong演算がoverflowした計算だけBigIntegerへ昇格。
- Item、Fluid、Chemicalの正確な在庫Sidecarと、任意のExtendedAE Plus
  BigInteger Cell連携を追加。
- 公平な子Window実行、再起動対応の親状態、正確な容量予約、古い計画の
  破棄、上限付き状態同期を追加。
- AE2 `CraftingPlan`互換、合流する中間素材の会計、レシピ単位Windowの
  overflow、Advanced AE一時CPU生成時の二重計上を修正。
- AppliedEの動的変換Patternは引き続きAE2本来の計算経路へ戻す。

不確定、循環、ファジー、代替入力、触媒、返却物、副産物を含む経路は
引き続きAE2へ安全にFallbackします。サーバーと全クライアントへ同じ
`1.5.0` JARを導入してください。
