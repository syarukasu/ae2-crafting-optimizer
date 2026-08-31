# Issue #170: revision未取得時にクラフト計算を停止させない

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/170
- 状態: Implemented
- 対象版: 1.5.31
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: #167

## 問題

ME Requesterなどが通常AE2のクラフト計算を開始した際、ACOの同期frameは開始されるが
`CraftingCalculation`側でrevisionが取得されない互換経路がある。1.5.31はsubmit境界で
`IllegalStateException`を投げるため、一つの要求が`Ticking GridNode`としてサーバー全体を停止させる。

## 再現と証拠

- 再現手順: 未処理要求を持つME Requesterを接続したAE2グリッドを読み込む。
- 境界値: 注文数量に依存せず、revision captureが行われない一回の計算で再現する。
- ログ・スタックトレース: `CraftingCalculationSnapshotContext.finish()`の
  `CraftingCalculation revisions were not captured`からME Requester tickへ到達する。
- 正常だった版: Issue #167変更前は、この例外によるサーバー停止はない。
- 失敗する版: 1.5.31。

## 期待結果

revisionを証明できない要求だけactive calculation deduplicationを使わず、AE2がsubmitした
元の`Future<ICraftingPlan>`を変更せず返す。在庫、欠品、計画結果、CPU実行はAE2のまま維持する。

## 現在結果

revision未取得を互換fallbackではなく致命的な内部不変条件違反として扱い、サーバーを停止する。

## 所有権

- AE2が所有する状態: クラフト計算、Future、計画結果、在庫とCPU実行。
- ACOが所有する状態: 同期frameとrevision付きdedup索引。
- 任意アドオンが所有する状態: ME Requesterの要求状態とtick。
- fallback可能な境界: executor submit直後で、入力・実行所有権をACOが取得する前。

## 維持する不変条件

- revision不明のFutureを現在世代のdedup索引へ登録しない。
- frameを必ず消費し、次のrequesterへ漏らさない。
- revision取得済み要求のdedup動作を変えない。
- AE2が作成したFuture、計画、在庫、CPU実行を置換しない。

## やってはいけないこと

- 不明なrevisionを現在値で後付けする。
- ME Requester、AE2グリッド、ワールドNBTを変更して回避する。
- ownership取得後の例外を同じfallbackで握り潰す。
- catch-allや無制限retryを追加する。

## 修正方針

`CraftingCalculationSnapshotContext.finish()`はframeを消費した後、revision未取得なら`null`を返す。
既存submit redirectは`null`時にAE2の元Futureを返すため、追加の互換レイヤーは作らない。
既存単体試験を致命例外期待からno-dedup fallback契約へ変更する。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] 関連クラスと既存試験を読んだ
- [x] 再現条件を試験へ変換した
- [x] 所有権とfallback境界を確定した
- [x] 禁止事項を明記した
- [x] Forge/NeoForgeの適用範囲を確定した

## 試験計画

- 単体試験: revision未取得時に`null`を返しframeを破棄する既存試験。
- 境界試験: revision取得済み時の同一オブジェクト返却を既存試験で維持する。
- 故障・取消・復旧試験: ownership前のため追加しない。
- ビルド: 関連試験、`verifyIssueRegressionManifest`、clean build、`git diff --check`。
- GameTestまたはユーザー側確認: 指定ワールドで`Done`後も同例外なしで待受を維持する。

## 実装結果

`CraftingCalculationSnapshotContext.finish()`はframeを必ず消費し、revision未取得時は
`null`を返す。既存submit redirectが元のAE2 Futureを返すため、この要求だけdedupを辞退する。

## 検証結果

- Forge 1.20.1 / Java 17: 対象3テスト成功。
- `clean build --no-build-cache`成功。
- `verifyIssueRegressionManifest`成功。
- `git diff --check`成功。

## 完了

- PR: #172
- マージコミット: `831269d2933459333b33976259150ed68adc7608`
- 修正版: 1.5.32
- リリース: 未リリース
