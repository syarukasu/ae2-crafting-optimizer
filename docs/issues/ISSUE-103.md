# Issue #103: wide計画の実行裏付け不足をCPU容量不足と誤報する

## 問題

正確なBigInteger実行viewまたは外部Consumerが無いwide計画を、標準CPUへ渡さないための
fail-closedガードが`CPU_TOO_SMALL`を返していました。これは実容量不足ではないため、
CPUを増設しても解消せず、診断も残りませんでした。

## 修正

- exact viewと外部Consumerが揃う実行計画は従来どおり提出する。
- exact view欠落、simulation、Consumer欠落を個別の診断文へ分ける。
- `SUBMISSION_BACKING_MISSING`を統計へ記録する。
- 初回だけWARNを出し、`INCOMPLETE_PLAN`でfail closedする。
- `CPU_TOO_SMALL`は本当のCPU容量不足だけに使用する。

## 不変条件

- 通常long計画へ介入しない。
- wide計画を標準long計算へフォールバックしない。
- simulationを実行計画として提出しない。
- 外部CPUの実行、GUI、構造、容量計算へACOから介入しない。

## 回帰試験

`CraftingCpuClusterBigCapacityGuardContractTest`が、裏付け不足の分岐に
`CPU_TOO_SMALL`が残らず、診断と`INCOMPLETE_PLAN`を使用することを固定します。
