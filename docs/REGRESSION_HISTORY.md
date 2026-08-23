# 回帰履歴

最適化経路を変更する前に、この一覧から関連Issue仕様書を読みます。詳細な症状、原因、
修正、不変条件、禁止事項、試験は`docs/issues/ISSUE-<番号>.md`を正本とします。

| Issue | 症状 | 影響版 | 修正版 | 仕様書 |
|---|---|---:|---:|---|
| [#79](https://github.com/syarukasu/ae2-crafting-optimizer/issues/79) | 一つのroot内部だけでsigned long境界を超えるexact計画が失われる | 1.5.17 | 1.5.18 | [ISSUE-79.md](issues/ISSUE-79.md) |
| [#90](https://github.com/syarukasu/ae2-crafting-optimizer/issues/90) | 無関係なProvider世代更新でBigInteger計画が提出時に失効する | 1.5.18 | 1.5.20 | [ISSUE-90.md](issues/ISSUE-90.md) |
| [#93](https://github.com/syarukasu/ae2-crafting-optimizer/issues/93) | Java 25でBigInteger在庫Sidecarの可視コピー中にJVMが終了する | 1.5.18 | 1.5.20 | [ISSUE-93.md](issues/ISSUE-93.md) |
| [#98](https://github.com/syarukasu/ae2-crafting-optimizer/issues/98) | 外部CPU登録済みでも合計bytesだけlong超過するBigCapacity計画が拒否される | 1.5.19 | 1.5.20 | [ISSUE-98.md](issues/ISSUE-98.md) |
| [#101](https://github.com/syarukasu/ae2-crafting-optimizer/issues/101) | 外部セルが正確なBigInteger在庫を公開する安定APIがない | 1.5.19 | 1.5.20 | [ISSUE-101.md](issues/ISSUE-101.md) |
| [#102](https://github.com/syarukasu/ae2-crafting-optimizer/issues/102) | 小規模ジョブが初回probeへ縮小されPattern Provider配送が遅延する | 1.5.19 | 1.5.20 | [ISSUE-102.md](issues/ISSUE-102.md) |
| [#103](https://github.com/syarukasu/ae2-crafting-optimizer/issues/103) | wide計画の実行裏付け不足がCPU容量不足と誤報される | 1.5.19 | 1.5.20 | [ISSUE-103.md](issues/ISSUE-103.md) |
| [#109](https://github.com/syarukasu/ae2-crafting-optimizer/issues/109) | BigInteger API連携が通常AE2の責務境界を越える | 1.5.20 | 1.5.21 | [ISSUE-109.md](issues/ISSUE-109.md) |
| [#140](https://github.com/syarukasu/ae2-crafting-optimizer/issues/140) | Mekanism入力探索がDedicated Serverでclient-only音声型を毎tick解決する | 1.5.25 | 未リリース | [ISSUE-140.md](issues/ISSUE-140.md) |
| 外部コンシューマ回帰 | 実経路でsidecarが消える、またはQuantum Bulkが`maxPatterns=1`へ誤って制限される | 1.5.18系 | 作業中 | [ISSUE-BIGINT-EXTERNAL-CONSUMER.md](ISSUE-BIGINT-EXTERNAL-CONSUMER.md) |

## 運用

- 修正前に`docs/ISSUE_WORKFLOW.md`を実行します。
- 再発しやすい局所条件だけをJavaコメントへ残します。
- 詳細な履歴はIssue仕様書へ集約し、同じ説明を複数ファイルへ複製しません。
- 新しい回帰を修正したら、この表へIssueと仕様書を追加します。
