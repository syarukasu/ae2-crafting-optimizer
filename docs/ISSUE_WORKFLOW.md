# Issue先行開発手順

ACOの修正は、問題を理解する前にコードへ触らないことを原則とします。

## 必須の読了順

1. `docs/PROJECT_CHARTER.md`
2. `docs/REGRESSION_HISTORY.md`
3. `docs/CLASS_RESPONSIBILITIES.md`
4. 対象の`docs/issues/ISSUE-<番号>.md`
5. 対象クラスの回帰防止コメント
6. 対応する自動試験と`docs/TESTING.md`

## 実装前

1. GitHub Issue番号を確定します。番号がなければ先にIssueを作成します。
2. `docs/issues/TEMPLATE.md`を`docs/issues/ISSUE-<番号>.md`へ複製します。
3. 次の項目を実証可能な内容で埋めます。
   - 問題とユーザー影響
   - 対象版と環境
   - 再現手順、境界値、ログ、スタックトレース
   - 期待結果と現在結果
   - ACO、AE2、任意アドオンの所有権
   - 維持する不変条件
   - やってはいけないこと
   - 修正方針とfallback境界
   - 先に失敗を確認する回帰試験
4. `docs/issues/REGRESSION_MATRIX.tsv`へIssueを登録し、必要な最小試験レベルと証拠を指定します。
5. 不明点を推測で埋めず、JAR、ソース、API、ログを調査します。
6. `docs/CLASS_RESPONSIBILITIES.md`で所有クラスと依存方向を確認します。
7. 実装前チェックをすべて満たし、状態を`Ready`へ変更します。

`Ready`になる前にJava、Mixin、リソース、Config、永続形式を変更してはいけません。

## 実装中

- Issue仕様書の範囲だけを変更します。
- 実コードが想定と違う場合は、先にIssue仕様書を更新してから設計を変更します。
- 過去Issueと同じ危険な分岐には、Issue番号と禁止理由をJavaへ短く残します。
- 詳細な経緯はJavaへ詰め込まず、Issue仕様書へ記録します。
- Forge 1.20.1とNeoForge 1.21.1で同じ意味を持つ変更は、別PRで同期します。

## 実装後

1. Issue仕様書へ実際の変更ファイルと採用した修正を記録します。
2. 単体試験、境界試験、故障試験、ビルド結果を記録します。
3. 未実施の起動、GameTest、実環境試験を明示します。
4. 回帰修正の場合だけ、`docs/REGRESSION_HISTORY.md`へIssueの索引を追加します。
5. `.\gradlew.bat verifyIssueRegressionManifest --no-daemon`で台帳と自動証拠を検証します。
6. Release PRでは`RELEASE_SCOPE_<version>.tsv`を作成し、基準版と今回の変更Issueを固定します。
7. `.\gradlew.bat verifyIssueRegressionReleaseReadiness --no-daemon`を実行します。
8. PR本文でIssue仕様書の読了・更新と、厳格監査に残る`PENDING`を明記します。
9. CI成功後にマージし、PRとリリース番号をIssue仕様書へ追記します。

## 例外

文章の誤字修正だけでもGitHub Issueまたは既存Issueへ関連付けます。セキュリティ上、
公開Issueへ詳細を書けない場合は非公開の識別子を使い、公開可能になった時点で履歴を
補完します。緊急修正でも、少なくとも問題、禁止事項、試験条件を書いてから実装します。

## 状態

- `Draft`: 調査中で実装禁止
- `Ready`: 実装前項目が揃い、実装可能
- `Implemented`: コードと試験を作成済み
- `Verified`: 必須試験とレビューを通過
- `Released`: 対応版を公開済み
