# 回帰履歴

最適化経路を変更する前に、この一覧から関連Issue仕様書を読みます。詳細な症状、原因、
修正、不変条件、禁止事項、試験は`docs/issues/ISSUE-<番号>.md`を正本とします。

| Issue | 症状 | 影響版 | 修正版 | 仕様書 |
|---|---|---:|---:|---|
| [#79](https://github.com/syarukasu/ae2-crafting-optimizer/issues/79) | 一つのroot内部だけでsigned long境界を超えるexact計画が失われる | 1.5.17 | 1.5.18 | [ISSUE-79.md](issues/ISSUE-79.md) |
| [#90](https://github.com/syarukasu/ae2-crafting-optimizer/issues/90) | 無関係なProvider世代更新でBigInteger計画が提出時に失効する | 1.5.18 | 未リリース | [ISSUE-90.md](issues/ISSUE-90.md) |
| [#93](https://github.com/syarukasu/ae2-crafting-optimizer/issues/93) | Java 25でBigInteger在庫Sidecarの可視コピー中にJVMが終了する | 1.5.18 | 未定 | [ISSUE-93.md](issues/ISSUE-93.md) |
| 外部コンシューマ回帰 | 実経路でsidecarが消える、またはQuantum Bulkが`maxPatterns=1`へ誤って制限される | 1.5.18系 | 作業中 | [ISSUE-BIGINT-EXTERNAL-CONSUMER.md](ISSUE-BIGINT-EXTERNAL-CONSUMER.md) |

## 運用

- 修正前に`docs/ISSUE_WORKFLOW.md`を実行します。
- 再発しやすい局所条件だけをJavaコメントへ残します。
- 詳細な履歴はIssue仕様書へ集約し、同じ説明を複数ファイルへ複製しません。
- 新しい回帰を修正したら、この表へIssueと仕様書を追加します。
