# AE2 Crafting Optimizer 1.5.19

## English

- Adds a public registration boundary for add-ons that consume exact
  BigInteger crafting plans.
- Preserves exact plan sidecars when AE2 rebuilds a `CraftingPlan` facade on
  the live calculation path.
- Prevents a wide plan from silently entering AE2's overflowing standard
  `long` CPU path when no compatible external consumer is registered.
- Keeps external CPU execution, progress, cancellation, GUI, structures, and
  persistence owned by the add-on.

## 日本語

- 正確なBigIntegerクラフト計画を利用するアドオン向けに、公開登録境界を追加しました。
- AE2の実計算経路で`CraftingPlan` Facadeが再構築されても、正確なsidecarを維持します。
- 対応する外部コンシューマが未登録の場合、wide計画がoverflowするAE2標準`long` CPU経路へ
  無言で入ることを防止します。
- 外部CPUの実行、進捗、キャンセル、GUI、構造、永続化は各アドオン側の責務として維持します。
