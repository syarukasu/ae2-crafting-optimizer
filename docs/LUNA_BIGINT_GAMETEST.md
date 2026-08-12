# LUNA BigInteger 回帰試験

この文書は、InsaneAE作者の診断GameTestをACO側の回帰条件へ対応付けたものです。
GameTestの実行はこの作業では行わず、ユーザー側のMinecraft試験項目として残します。

## 参照

- 診断ブランチ: https://github.com/taikun24/InsaneAE/tree/diag/aco-bigint
- 再現報告: https://github.com/taikun24/InsaneAE/issues/6#issuecomment-5264634773

## 回帰条件

1. 鉄ナゲット在庫 `8,600,000,000,000,000,000` と鉄ブロック要求
   `106,000,000,000,000,000` で、正確な不足または成立判定を確認する。
2. direct planner APIと`CraftingService.beginCraftingCalculation`経路で、同じSnapshotから
   `BigInteger`の要求量、使用量、不足量、exact bytesが一致する。
3. long超過かつ素材不足の計画が`ArithmeticException`にならず、simulationとして返る。
4. wide計画がAE2標準long計算へ無言で戻らず、辞退理由を診断統計で確認できる。
5. 同じ計算を二つの呼出し元から共有し、一方のキャンセルで他方の結果を壊さない。
6. 全購読者がキャンセルした場合だけ下流Futureをキャンセルする。

## 合格条件

- BigInteger正本が`Long.MAX_VALUE`へ切り捨てられない。
- simulation計画が実行Jobへ変換されない。
- 通常long範囲の結果は変更前と一致する。
- GameTest以外の自動確認は`clean build`とJUnitで実施する。
