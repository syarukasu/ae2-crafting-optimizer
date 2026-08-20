# Issue #123: 過去Issueと回帰試験をリリース判定へ接続する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/123
- 状態: Implemented
- 対象版: ACO 1.5.x
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: Issue #84、Issue #87、Issue #118、Issue #119、Issue #120

## 問題

GitHub Issueは番号#120まで存在するが、回帰症状と試験の対応が機械可読ではない。
静的ソース検査、実処理JUnit、GameTest、実ランタイム確認が区別されず、Closedという状態だけで
再発していないように見える。

## 再現と証拠

- 再現手順: 全Issue、`docs/issues`、`src/test`を比較する
- 現在の実Issue数: 45件
- Issue仕様書が存在するIssue: 一部のみ
- Issue番号を直接記載するJUnit: 一部のみ
- GameTest・停止復元・併用起動が必要だが自動化されていないIssueが残る

## 期待結果

- 全Issueの種類、対象ローダー、必要検証レベル、証拠、残作業を一表で確認できる
- Manifestの重複、未分類、未知の列挙値、存在しない証拠パスを通常CIが拒否する
- ランタイム確認が必要な回帰Issueをrelease gateが一覧表示して拒否する
- 静的検査を実動作確認と誤表示しない

## 現在結果

Issue仕様書と試験ファイルは個別に存在するが、全Issueを横断する対応表とrelease gateがない。

## 所有権

- GitHubが所有する状態: Issue番号、題名、Open/Closed状態
- ACOが所有する状態: 保存済みManifest、試験証拠、検証レベル、release gate
- 任意アドオンが所有する状態: 自身のGameTestと再現環境
- fallback可能な境界: 公開前。証拠不足時はリリースを止め、通常buildは継続できる

## 維持する不変条件

- 未実施を成功へ変換しない
- 文書パスを実処理JUnitとして数えない
- GameTestが必要な症状を静的検査だけでVerifiedにしない
- Manifest検査はネットワークなしでも再現できる

## やってはいけないこと

- Closed Issueを自動的に回帰確認済みと扱う
- ソース文字列の存在だけでNBT保存、Mixin適用、GUI操作を保証する
- release gateを通すために必要検証レベルを下げる
- 存在しない試験名または実行していない結果を証拠へ登録する

## 修正方針

1. 全IssueをTSV Manifestへ登録する。
2. 種類、必要検証、証拠、ランタイム状態を固定列挙値で表す。
3. Gradleへ構造検証taskとrelease readiness taskを追加する。
4. 通常`check`はManifest構造を検証する。
5. release readinessは未確認回帰をIssue番号付きで拒否する。
6. 運用手順と現在の未確認一覧を文書化する。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] 関連クラスと既存試験を読んだ
- [x] 再現条件を試験へ変換した
- [x] 所有権とfallback境界を確定した
- [x] 禁止事項を明記した
- [x] Forge/NeoForgeの適用範囲を確定した

## 試験計画

- 単体試験: Manifestの列数、Issue重複、列挙値、証拠パス、実Issue全件登録
- 境界試験: 非連番Issue、複数証拠、URL証拠、`PENDING`ランタイム
- 故障試験: 未分類、存在しない証拠、ランタイム未確認をrelease gateが拒否
- ビルド: `gradlew clean test`、`gradlew clean build`
- GameTestまたはユーザー側確認: 本Issueは試験基盤変更のため不要

## 実装結果

- `REGRESSION_MATRIX.tsv`へ、既存45件と本Issueを合わせた46件を登録した。
- `IssueRegressionManifestTest`で列定義、全Issue登録、重複、昇順、列挙値、証拠パス、
  試験レベルと実機状態の整合性を検証する。
- `verifyIssueRegressionManifest`を通常`check`へ接続した。
- `verifyIssueRegressionReleaseReadiness`を追加し、GameTestまたは実機確認が残るIssueを
  番号付きで拒否する。
- `ISSUE_REGRESSION_TESTING.md`へ試験レベル、コマンド、現在の未確認項目を記録した。

## 検証結果

- `gradlew verifyIssueRegressionManifest --no-daemon`: 成功
- `gradlew clean build --no-daemon`: 成功
- `gradlew verifyIssueRegressionReleaseReadiness --no-daemon`: 想定どおり失敗
- release gateが未確認11件（#30、#32、#51、#74、#93、#102、#109、#115、#118、
  #119、#120）を列挙することを確認した。
- 起動試験、GameTest、実環境試験: 本変更では未実施

## 完了

- PR:
- マージコミット:
- 修正版:
- リリース:
