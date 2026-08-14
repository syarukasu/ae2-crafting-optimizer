# AE2 Crafting Optimizer 1.5.18

## English

- Fixes exact BigInteger planning when one root item expands to more than
  `Long.MAX_VALUE` lower-pattern executions or boundary inputs.
- Keeps the exact Pattern counts, inventory usage, missing amounts, and CPU byte
  total available through ACO's public BigInteger plan API.
- Separates exact-plan publication from the legacy root-count execution window.
- Preserves existing AE2 calculations and root-window execution below the
  signed-long boundary.
- Adds regression tests for a 21-stage 8x compression tree where one final item
  requires `2^63` base inputs.

## 日本語

- 完成品1個から展開される下位Pattern実行数または境界入力数が
  `Long.MAX_VALUE`を超える場合も、正確なBigInteger計画を返すよう修正しました。
- 正確なPattern回数、在庫使用量、不足量、CPU容量を、ACOの公開BigInteger計画APIから
  そのまま取得できます。
- 正確な計画の公開可否と、旧root個数単位の実行Window可否を分離しました。
- signed long境界未満のAE2計算およびroot-window実行は従来どおり維持します。
- 完成品1個に`2^63`個の最下層素材が必要な、8倍圧縮21段の回帰試験を追加しました。
