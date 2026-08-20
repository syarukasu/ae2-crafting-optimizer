# Issue #103: wide計画の非同期compile競合と実行裏付け不足の誤診断

## 問題

NeoForge 1.21.1では、Provider索引更新中に非同期compileが古い世代を観測すると、後から同じ
rootを取得できる場合でも`NO_COMPILED_PROGRAM`になりました。また、正確なBigInteger
実行viewまたは外部Consumerが無いwide計画を`CPU_TOO_SMALL`と誤報していました。

## 修正

- Provider世代はAE2の索引更新完了後に公開する。
- root program採用前にProvider世代とRecipe世代を再検証する。
- 一時的なcompile失敗をnegative cacheへ残さない。
- compile不能と実行裏付け不足を別の診断理由として記録する。
- 裏付け不足は初回WARNと`INCOMPLETE_PLAN`でfail closedする。
- `CPU_TOO_SMALL`は本当のCPU容量不足だけに使用する。
- Root Programの失敗を循環、複数Producer、複数出力、上限超過、
  不完全Pattern Snapshot、Snapshot欠落へ分類する。
- Snapshot由来の失敗だけを一回再構築し、同じSnapshotを無限に再構築しない。
- 再構築後も不完全なら`INCOMPLETE_GRAPH_SNAPSHOT`として統計と限定WARNへ残す。

## 不変条件

- 通常long計画へ介入しない。
- wide計画を標準long計算へフォールバックしない。
- simulationを実行計画として提出しない。
- 外部CPUの実行、GUI、構造、容量計算へACOから介入しない。
- 循環や曖昧なレシピを再構築で解決しようとしない。
- wide計画をoverflowするAE2標準long計算へ無言で戻さない。

## 回帰試験

- 世代更新途中のcompile結果を採用しないこと。
- 一時的な失敗後に再compileできること。
- 裏付け不足を`CPU_TOO_SMALL`と報告しないこと。
- `CompiledRootProgramFailureReasonTest`で失敗理由の分類を固定すること。
- `Ae2AuthoritativeCraftingPlannerPolicyTest`で一回だけの再構築条件を固定すること。
- `WidePlanSubmissionGuardTest`で非対応CPU拒否の詳細を固定すること。

## 1.5.xバックポート

- 状態: Gradle verified
- 対象: Forge 1.20.1 / NeoForge 1.21.1
- 起動・GameTest: ユーザー側確認。Gradle試験では代替しない。
