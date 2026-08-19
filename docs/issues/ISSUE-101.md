# Issue #101: 外部セル向け正確BigInteger在庫API

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/101
- 状態: Implemented
- 対象版: 1.5.20
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1

## 問題

外部ストレージアドオンが`Long.MAX_VALUE`を超える実在庫をACOへ渡す公開境界がなく、
ExtendedAE Plus専用の内部Mixinインターフェイスへ依存していました。

## 不変条件

- 正確在庫の正本を`long`へ変換・クランプしない
- AE2へ公開されないpartition済みキーをBigInteger側だけへ復活させない
- 不完全・不正・過大なMapを正確なSnapshotとして採用しない
- 従来のExtendedAE Plus互換経路を壊さない

## 修正

公開`ExactStorageAmountProvider`を追加し、`ExactCountLimits`でキー数と全数量を検査します。
通常のAE2 Facadeが公開する全キーを覆う場合だけ完全Snapshotとして採用し、失敗時は
不完全なlong Facadeへ戻します。1.20.1にも`api.contract`一式と起動時Capability公開を移植します。

## 試験

- 256桁相当の正確在庫を公開APIから保持
- Facadeキー欠落を不完全Snapshotとして拒否
- Capability交渉で`EXACT_STORAGE_AMOUNT_PROVIDER`を公開
- 両版のJUnitと`clean build`
