# 回帰履歴

最適化経路を変更する前に、この一覧から関連Issue仕様書を読みます。詳細な症状、原因、
修正、不変条件、禁止事項、試験は`docs/issues/ISSUE-<番号>.md`を正本とします。

| Issue | 症状 | 影響版 | 修正版 | 仕様書 |
|---|---|---:|---:|---|
| [#79](https://github.com/syarukasu/ae2-crafting-optimizer/issues/79) | 一つのroot内部だけでsigned long境界を超えるexact計画が失われる | 1.5.17 | 1.5.18 | [ISSUE-79.md](issues/ISSUE-79.md) |
| [#98](https://github.com/syarukasu/ae2-crafting-optimizer/issues/98) | 外部CPU登録済みでも合計bytesだけlong超過するBigCapacity計画が拒否される | 1.5.19 | 未リリース | [ISSUE-98.md](issues/ISSUE-98.md) |
| [#44](https://github.com/syarukasu/ae2-crafting-optimizer/issues/44), [#55](https://github.com/syarukasu/ae2-crafting-optimizer/issues/55) | 実AE2経路でexact sidecarが失われ、外部CPUが論理操作数を物理Bulk上限として扱う | 1.5.18 | 1.5.19 | [BigInteger external consumer](ISSUE-BIGINT-EXTERNAL-CONSUMER.md) |

## 運用

- 修正前に`docs/ISSUE_WORKFLOW.md`を実行します。
- 再発しやすい局所条件だけをJavaコメントへ残します。
- 詳細な履歴はIssue仕様書へ集約し、同じ説明を複数ファイルへ複製しません。
- 新しい回帰を修正したら、この表へIssueと仕様書を追加します。
