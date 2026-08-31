# Issue #170: revision未取得時にクラフト計算を停止させない

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/170
- 状態: Implemented
- 対象版: 1.5.31
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: #167

## 問題

互換経路から通常AE2のクラフト計算を開始した際、ACOの同期frameは開始されるが
`CraftingCalculation`側でrevisionが取得されない場合がある。1.5.31はsubmit境界で
`IllegalStateException`を投げるため、一つの要求がグリッドtick全体を停止させる。

## 期待結果

revisionを証明できない要求だけactive calculation deduplicationを使わず、AE2がsubmitした
元の`Future<ICraftingPlan>`を変更せず返す。在庫、欠品、計画結果、CPU実行はAE2のまま維持する。

## 維持する不変条件

- revision不明のFutureを現在世代のdedup索引へ登録しない。
- frameを必ず消費し、次のrequesterへ漏らさない。
- revision取得済み要求のdedup動作を変えない。
- AE2が作成したFuture、計画、在庫、CPU実行を置換しない。

## やってはいけないこと

- 不明なrevisionを現在値で後付けする。
- AE2グリッドやワールド状態を変更して回避する。
- ownership取得後の例外を同じfallbackで握り潰す。
- catch-allや無制限retryを追加する。

## 修正方針

`CraftingCalculationSnapshotContext.finish()`はframeを消費した後、revision未取得なら`null`を返す。
既存submit redirectは`null`時にAE2の元Futureを返すため、追加の互換レイヤーは作らない。

## 試験計画

- 単体試験: revision未取得時に`null`を返しframeを破棄する既存試験。
- 境界試験: revision取得済み時の同一オブジェクト返却を既存試験で維持する。
- ビルド: 関連試験、`verifyIssueRegressionManifest`、clean build、`git diff --check`。

## 実装結果

`CraftingCalculationSnapshotContext.finish()`はframeを必ず消費し、revision未取得時は
`null`を返す。既存submit redirectが元のAE2 Futureを返すため、この要求だけdedupを辞退する。

## 検証結果

- NeoForge 1.21.1 / Java 21: 対象3テスト成功。
- `clean build --no-build-cache`成功。
- `verifyIssueRegressionManifest`成功。
- `git diff --check`成功。

## 完了

- PR: #171
- マージコミット: `96bf480a7c14f4b09267b58d997d1e39283c12a7`
- 修正版: 1.5.32
- リリース: 未リリース
