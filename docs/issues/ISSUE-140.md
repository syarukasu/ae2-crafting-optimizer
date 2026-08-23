# Issue #140: Mekanism Recipe IntentがDedicated ServerでSoundInstanceを毎tick誤ロードする

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/140
- 状態: Fixed in branch
- 対象版: 1.5.25
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: Issue #129

## 問題

Mekanism Recipe Intent高速化がMekanism機械の全フィールド型をDedicated Serverで解決し、
client-onlyの`SoundInstance`を読み込もうとしてRuntimeDistCleanerエラーを毎tick出力します。
実環境では約20件/tick、合計137万件以上を確認しました。

## 再現と証拠

- `enableMekanismRecipeIntentFastPath = true`でMekanism機械へRecipe Intentを配信する
- `TileEntityMekanism`の`activeSound`フィールドへ`Field#getType()`が到達する
- `ClassValue`初期化が完了せず、対象機械ごとに次tickも同じ型解決を再試行する
- ログ: `Attempted to load class net/minecraft/client/resources/sounds/SoundInstance for invalid dist DEDICATED_SERVER`

## 期待結果

入力ハンドラー候補だけをReflection対象にし、レシピ結果と通常Fallbackを変えず、
Dedicated Serverではクライアント専用フィールド型を解決しません。

## 所有権

- Mekanismが機械・入力ハンドラー・レシピ成立判定を所有する
- ACOはProvider Intentに対応する候補の索引とキャッシュだけを所有する
- ACOが候補を証明できなければMekanism標準探索へ戻す

## 維持する不変条件

- 入力ハンドラーの優先順、配列判定、Recipe Intent候補、レシピ結果を変更しない
- Reflection失敗時はレシピを捏造せず、既存どおり標準探索へ戻す

## やってはいけないこと

- Log4jフィルターだけでエラーを隠す
- Mekanism本体JARを改変する
- フィールド名で候補を限定する前に`Field#getType()`を呼ぶ
- Recipe Intent高速化の成立条件を緩和する

## 修正方針

`buildInputFieldAccessors`で`inputHandler`を含まない名前を先に除外し、候補フィールドにだけ
`Field#getType()`を実行します。処理結果は変えず、危険な型解決順序だけを修正します。

## 試験計画

- JUnitで名前ゲートが型解決より前に存在することを固定する
- Forge 1.20.1とNeoForge 1.21.1で`clean build`を実行する
- 起動試験はユーザー指示により実施しない

## 実装結果

- `MekanismRecipeIntentFastPath`の型解決順序を修正
- `MekanismRecipeIntentDedicatedServerSafetyTest`を追加

## 検証結果

- NeoForge 1.21.1: `gradlew.bat clean build --no-daemon` 成功
- JUnit: 412件成功、失敗0件
- 起動試験: ユーザー指示により未実施
