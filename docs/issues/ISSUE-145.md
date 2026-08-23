# Issue #145: Issue #129完成化と安全境界の固定

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/145
- 対象版: 1.5.27
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1

## 問題

Issue #129の中央台帳とgateは実装済みでしたが、診断bitが64機能固定で、廃止済み互換キーが未完成機能に見え、新しい非Mixin runtime入口のgate接続漏れを十分に検出できませんでした。ForgeのUELM依存ビルドには、実行時に除外されるMixinの`@Shadow`警告も残っていました。

## 修正

- 診断bit集合を機能数に追従する複数wordの`AtomicLongArray`へ変更
- 130機能を仮定したword境界試験を追加
- 旧11キーを`RETIRED_COMPATIBILITY_KEY`として確定
- 全廃止キーへ廃止理由と安全な代替機能を宣言
- ACTIVE機能がConfigまたはMixin台帳へ接続されることを静的検査
- `OptimizationFeatureGate`の直接呼出しを`ACOConfig`へ限定
- ForgeのUELM所有Mixinへ条件付きtargetであることを明示

## 不変条件

- 旧危険Mixinは再登録しない
- 既存Configキーは読み込み可能なまま維持する
- AE2の在庫、GUI、packet、レシピ成立、実行順を変更しない
- BigIntegerと外部アドオンの所有権境界を変更しない
