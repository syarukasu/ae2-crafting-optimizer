# Issue #84: Issue仕様書を先に読む開発手順とプロジェクト境界を明文化する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/84
- 状態: Verified
- 対象版: ACO 1.5.18以降の開発手順
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: Issue #79、PR #80、PR #81、PR #82、PR #83

## 問題

過去の修正理由がIssue、会話、コード差分へ分散し、後の変更で同じ危険な設計を
再導入できる状態でした。Issue #79では、exact計画とlegacy root-windowの可否を
再び同一視し、以前扱えていた境界条件を壊しました。

## 再現と証拠

- Issue #79は8倍圧縮21段の`2^63`境界で再発しました。
- Javaコメントと`REGRESSION_HISTORY.md`は追加済みですが、修正前の必須手順は未定義です。
- 既存PRテンプレートにはIssue仕様書の読了・更新確認がありません。

## 期待結果

全変更はIssue番号を持ち、実装前に問題、所有権、不変条件、禁止事項、修正方針、
試験条件をMarkdownへ記録して読了します。ACOの目的と非目標を一つの憲章から
確認でき、PRで手順の完了を検査できます。

## 現在結果

安全境界は複数文書へ存在しますが、読了順、Issue単位の仕様書、実装開始条件が
統一されていません。

## 所有権

- GitHub Issueは問題報告と議論を所有します。
- `docs/issues/ISSUE-<番号>.md`は実装判断と検証履歴を所有します。
- Javaコメントは再発しやすい局所的不変条件だけを所有します。
- `PROJECT_CHARTER.md`はACO全体の目的と禁止事項を所有します。

## 維持する不変条件

- 文書化の追加でランタイム挙動、Config、API、バイナリ版を変更しません。
- ForgeとNeoForgeで同じ開発ルールを維持します。
- 既存の詳細資料は削除せず、新しい入口から参照できるようにします。

## やってはいけないこと

- Issue仕様書を書かずに実装を始める
- GitHub Issue本文だけを長期の実装仕様書として扱う
- Javaへ長大な履歴を詰め込み、実装意図を読みにくくする
- 文書化を理由に既存の試験や安全境界を弱める

## 修正方針

`PROJECT_CHARTER.md`、`ISSUE_WORKFLOW.md`、Issueテンプレート、`AGENTS.md`を追加し、
`CONTRIBUTING.md`とPRテンプレートから必須手順へ誘導します。Issue #79を最初の
Issue別仕様書へ移し、回帰履歴を索引として整理します。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`の目的と境界を確定した
- [x] Issue #79の再発原因を確認した
- [x] 既存`CONTRIBUTING.md`とPRテンプレートを確認した
- [x] ランタイム変更が不要であることを確認した
- [x] Forge/NeoForgeの両方へ反映することを確定した

## 試験計画

- Markdownリンクと参照先の存在確認
- Forge/NeoForge間の共通文書差分確認
- `git diff --check`
- 両版`clean build`

## 実装結果

- `AGENTS.md`からIssue先行手順を必須化
- `docs/PROJECT_CHARTER.md`へ目的、非目標、所有権、禁止事項を集約
- `docs/ISSUE_WORKFLOW.md`へ実装前・中・後の手順と状態を定義
- `docs/issues/TEMPLATE.md`と一覧を追加
- Issue #79を`docs/issues/ISSUE-79.md`へ移行
- `REGRESSION_HISTORY.md`をIssue仕様書への索引へ変更
- JavaのIssue #79コメントを詳細仕様書へ接続
- `CONTRIBUTING.md`、README、PRテンプレートから必須手順へ誘導

## 検証結果

- Markdown相対リンク検査: 成功
- Issue仕様書の必須見出し検査: 成功
- Forge/NeoForge共通文書のSHA-256一致: 成功
- `gradlew.bat clean build --no-daemon`: 成功
- JUnit: 356件、失敗0、エラー0
- 起動、GameTest、ゲーム内動作: 文書変更のため未実施

## 完了

- PR: https://github.com/syarukasu/ae2-crafting-optimizer/pull/86
- マージコミット:
- 修正版: 文書のみのため版番号変更なし
- リリース: 不要
