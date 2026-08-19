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

## 不変条件

- 通常long計画へ介入しない。
- wide計画を標準long計算へフォールバックしない。
- simulationを実行計画として提出しない。
- 外部CPUの実行、GUI、構造、容量計算へACOから介入しない。

## 回帰試験

- 世代更新途中のcompile結果を採用しないこと。
- 一時的な失敗後に再compileできること。
- 裏付け不足を`CPU_TOO_SMALL`と報告しないこと。
