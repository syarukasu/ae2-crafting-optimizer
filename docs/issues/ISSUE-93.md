# Issue #93: Java 25でBigInteger在庫Sidecarの可視コピー中にJVMが終了する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/93
- 状態: Verified
- 対象版: ACO 1.5.18
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: PR未作成

## 問題

正確なBigInteger在庫SnapshotをAE2のクラフト計算用`KeyCounter`へ伝播中、
Java例外ではなくJVMが`EXCEPTION_ACCESS_VIOLATION`で終了しました。Minecraftの
クラッシュレポートや正常停止処理は残らず、接続中クライアントも切断されます。

## 再現と証拠

- Minecraft 1.20.1、ACO 1.5.18、Temurin 25.0.4+7、G1 GC
- Arclight Forge 1.20.1のServer threadで発生
- `hs_err_pid5116.log`のproblematic frameはC2コンパイル済み
  `BigKeyCounterSidecars.copyVisible(KeyCounter, KeyCounter)`
- 読み取り先は`0x000000007f7c8de0`
- compiled methodは`ImmutableCollections$MapN`を反復しながら、AE2の
  `KeyCounter.get`が持つfastutil木を同じループ内で検索していた
- サーバー消失後のクライアントNPEはTooManyRecipeViewersのlogout処理で発生した
  二次障害であり、このIssueの変更対象ではない

## 期待結果

- 正確なBigInteger在庫値を丸めずに別`KeyCounter`へ伝播できる。
- Java 25で対象処理が高頻度にC2コンパイルされてもJVMが終了しない。
- AE2側で除外済みのキーをSidecarだけで復活させない。
- source/targetが同一でも別インスタンスでも既存の可視性契約を維持する。

## 現在結果

不変Snapshotの`MapN`反復と可変`KeyCounter`検索が一つの大きなC2 methodへ融合し、
Java 25上でネイティブアクセス違反を起こしました。

## 所有権

- AE2が所有する状態: long facadeの`KeyCounter`と可視キー集合
- ACOが所有する状態: `KeyCounter`へ関連付ける不変BigInteger Snapshot
- 任意アドオンが所有する状態: 一基ごとの正確なBigInteger在庫値
- fallback可能な境界: Sidecarが存在しない場合だけlong facadeを正本にする

## 維持する不変条件

- BigInteger正本を`long`へクランプ、飽和、切り捨てしない。
- targetのlong facadeに正量で存在するキーだけを伝播する。
- Snapshot公開後に呼出側からMap/Setを変更できない。
- source Snapshotとtarget可視キーを独立した一時点Snapshotとして扱う。
- SidecarのIdentity弱参照による寿命管理を維持する。

## やってはいけないこと

- ACOのBigInteger在庫機能を無効化して回避する。
- JVM起動引数へACO固有の`CompileCommand`を必須化する。
- Java 25だけを一律非対応にして原因を隠す。
- 可視性判定を省略して、抽出済みキーを復活させる。
- クライアント側TooManyRecipeViewersをACOから改変する。

## 修正方針

`Snapshot`のMap/SetをACO所有の順序保持コレクションへ複製し、その読み取り専用viewを
公開します。`copyVisible`はtargetの正量キー取得とsourceの正確値抽出を別工程へ分け、
JDKの`MapN`反復とfastutil検索を一つのC2ループへ融合させません。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] 関連クラスと既存試験を読んだ
- [x] 再現条件を試験へ変換した
- [x] 所有権とfallback境界を確定した
- [x] 禁止事項を明記した
- [x] Forge/NeoForgeの適用範囲を確定した

## 試験計画

- 単体試験: 可視キーだけのexact値・exact key・complete flag伝播
- 境界試験: `Long.MAX_VALUE + 1`を丸めず維持
- 故障試験: 元Map/Set変更後もSnapshotが変わらない
- ストレス試験: 同一targetへの高頻度コピーで値と正確性が変化しない
- ビルド: 両版のJUnit、静的検査、`clean build`
- GameTestまたはユーザー側確認: Minecraft起動はユーザー側確認

## 実装結果

- `BigKeyCounterSidecars.copyVisible`から`KeyCounter.get`を使う複合ループを除去した。
- targetの正量キー取得とsource BigInteger値の抽出を別メソッドへ分離した。
- SnapshotのMapをACO所有の`LinkedHashMap`へ複製し、
  `Collections.unmodifiableMap`で公開するよう変更した。
- 12,000キーを8回コピーするJava 25向け回帰試験を追加した。
- BigInteger値、可視キー、complete flag、弱参照管理を維持した。

## 検証結果

- Forge 1.20.1: JUnit 344件、失敗0、エラー0、スキップ2
- NeoForge 1.21.1: JUnit 366件、失敗0、エラー0、スキップ0
- Temurin 25.0.4+7: 両版の12,000キー回帰試験成功
- Forge 1.20.1 `clean build`: 成功
- NeoForge 1.21.1 `clean build`: 成功
- Minecraftクライアント・専用サーバー起動: 指示により未実施

## 完了

- PR: 未作成
- マージコミット: 未定
- 修正版: 未定
- リリース: 未定
