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

- 世代更新途中のcompile結果を採用しないこと。
- 一時的な失敗後に再compileできること。
- 裏付け不足を`CPU_TOO_SMALL`と報告しないこと。
- `CompiledRootProgramFailureReasonTest`で失敗理由の分類を固定すること。
- `Ae2AuthoritativeCraftingPlannerPolicyTest`で一回だけの再構築条件を固定すること。
- `Ae2CraftingPlanSidecarsTest`で公開計画から正確なbyte数を失わないこと。

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
