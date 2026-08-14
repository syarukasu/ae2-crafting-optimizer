# ACO Repository Instructions

ソース、Mixin、リソース、Config、永続形式、ビルド設定を変更する前に、必ず次を実行してください。

1. GitHub Issue番号を確定する。
2. `docs/PROJECT_CHARTER.md`を読む。
3. `docs/REGRESSION_HISTORY.md`を読む。
4. `docs/issues/ISSUE-<番号>.md`を作成または更新する。
5. そのIssue仕様書の状態を`Ready`にする。
6. `docs/ISSUE_WORKFLOW.md`に従ってから実装を始める。

実装中に前提が変わった場合は、コードより先にIssue仕様書を更新してください。

ACOの最優先事項は、性能ではなく在庫・欠品・容量・進捗・取消・復旧の正確さです。
BigInteger正本のlong化、所有権移転後のfallback、計画値からの成果物生成、外部CPU
実行ロジックの置換は禁止します。

過去Issueと同じ危険な分岐にはIssue番号をJavaコメントへ短く残し、詳細は
`docs/issues/ISSUE-<番号>.md`へ置いてください。
