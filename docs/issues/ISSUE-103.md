# Issue #103: wide計画の実行裏付け不足をCPU容量不足と誤報する

## 問題

正確なBigInteger実行viewまたは外部Consumerが無いwide計画を、標準CPUへ渡さないための
fail-closedガードが`CPU_TOO_SMALL`を返していました。これは実容量不足ではないため、
CPUを増設しても解消せず、診断も残りませんでした。

1.5.22では#106の修正が1.5.x成果物へ含まれておらず、Graph自体には正常な
`rootProgram`があっても更新途中のSnapshotが`NO_COMPILED_PROGRAM`として固定され、
wide計画が作られない再発も確認されました。

## 修正

- exact viewと外部Consumerが揃う実行計画は従来どおり提出する。
- exact view欠落、simulation、Consumer欠落を個別の診断文へ分ける。
- `SUBMISSION_BACKING_MISSING`を統計へ記録する。
- 初回だけWARNを出し、`INCOMPLETE_PLAN`でfail closedする。
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

- `CompiledRootProgramFailureReasonTest`
- `Ae2AuthoritativeCraftingPlannerPolicyTest`
- `WidePlanSubmissionGuardTest`

## 1.5.xバックポート

- 状態: Gradle verified
- 対象: Forge 1.20.1 / NeoForge 1.21.1
- 起動・GameTest: ユーザー側確認。Gradle試験では代替しない。
