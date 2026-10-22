# ACO 2.0.0-rc.5-processing.1 (NeoForge 1.21.1)

**開発者向けプレリリースです。安定版ではありません。**

## 変更点

- 加工機向けに `ProcessingPatternBatchTarget` と `ProcessingPatternBatchRequest` を追加。
- longを超える製造回数・入力総量・出力総量をBigIntegerのまま受け渡します。
- 既存のBigInteger API・作業台用APIの公開シグネチャは維持しています。
- 加工機側の受理、実出力Receipt、取消を既存のEscrow会計へ接続します。
- 受理後に所有・Receiptが失われた場合は隔離し、推測で返金・再発注しません。

## 加工機MODの開発者へ

[新APIの使い方（日本語・English summary）](https://github.com/syarukasu/ae2-crafting-optimizer/blob/v2.0.0-rc.5-processing.1-1.21.1/docs/PROCESSING_PATTERN_API.md)

加工機のBlockEntityで新インターフェースを実装してください。
long用のBatch V2や、作業台用のCraftingTableBatchTargetとは別の追加APIです。
入力はACOが予約済みです。二重に取り出さず、加工機が実行した全出力をReceiptで返します。
予定値をそのまま「実出力」として返すことはできません。

**この更新だけでOAE等の未対応加工機がBigInteger対応になるわけではありません。**
BigInteger実行に対応したCPU・在庫連携と、加工機側の実装が必要です。

## 導入

- 対象: **NeoForge 1.21.1**。別Minecraft・別ローダーのJARを混用しないでください。
- 導入するファイル: `aco2.0.0-rc.5-processing.1_1.21.1.jar` の1つ。サーバーとクライアントを合わせます。
- ソース・説明書・チェックサムはmodsへ入れません。

## 検証済みと未確認

- ACOの実取引・Receipt・保存Codecを使う契約テストで、`Long.MAX_VALUE + 1` 以上の依頼、
  混合工程、副産物、取消、毎tickの保存・読戻しを検証しています。
- 既存API 92クラスの公開宣言とJVMシグネチャに削除・変更なし。
- 665 tests: 664 passed, 1 existing optional AQE artifact check skipped because its JAR was not supplied.
- ワールド・在庫・加工機の境界はテスト用実装です。OAE/AQE等との実機完了、
  同時注文、MOD間のディスククラッシュ復旧は未受入です。
- 入力は現行の保存契約で1～9枠。動的出力・循環物理工程等の対象拡大は含みません。
- 通常搬入出の速度や加工機の処理速度を変更するリリースではありません。
- 全工業レシピ対応や、巨大注文10秒未満という最終目標の達成を主張しません。

## Source / provenance

- Base ACO commit: `2c8f919dab0bc5c0ae2adec2523dc685412cd03d`.
- Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/227

## English

Adds an opt-in BigInteger processing worker API while retaining existing public
contracts. Native machines must implement the interface and provide actual,
durable output receipts. ACO retains exact escrow/accounting ownership. This is
contract-tested, not external-machine runtime or cross-mod crash-recovery certified.
Platform-specific prerelease only; no stable or performance acceptance claim.
