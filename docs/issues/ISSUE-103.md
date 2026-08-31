# Issue #103: wide計画の実行裏付け不足をCPU容量不足と誤報する

## 問題

正確なBigInteger実行viewまたは外部Consumerが無いwide計画を、外部CPUの提出境界で
`CPU_TOO_SMALL`へ変換すると、実容量不足ではないのにCPU増設を促す誤診断になりました。

1.5.22では#106の修正が1.5.x成果物へ含まれておらず、Graph自体には正常な
`rootProgram`があっても更新途中のSnapshotが`NO_COMPILED_PROGRAM`として固定され、
wide計画が作られない再発も確認されました。

## 修正

- ACO公開計画へ正確なbyte数と失敗理由を保持し、外部CPUが自分の容量判定を行えるようにする。
- ACOは外部CPUの提出結果を`CPU_TOO_SMALL`へ変換しない。
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
- `Ae2CraftingPlanSidecarsTest`

## 1.5.xバックポート

- 状態: Gradle verified
- 対象: Forge 1.20.1 / NeoForge 1.21.1
- 起動・GameTest: ユーザー側確認。Gradle試験では代替しない。

## Issue #167後のcache境界

Issue #103の「一時的なcompile失敗をnegative cacheへ残さない」は、mutableなAE2索引から
取得した途中状態を新しい世代で再利用しないための規則である。Issue #167以降は、公開前に
pattern、recipe、config revisionを検証したimmutable Snapshot内だけで、構造上の
`RootProgramFailure`を有界LRUへ保持する。storage不足結果はここへ保存せず、storage revisionを
含む別の完了Plan cacheで管理する。異なるrevisionへ失敗結果を持ち越してはならない。
