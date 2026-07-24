# AE2 Crafting Optimizer 1.4.1

## English

This compatibility patch adds optional support for AppliedE `0.14.3` and
AppliedE TPS Fix `0.14.7-fix2`.

- AppliedE transmutation routes remain on AE2's authoritative crafting tree.
- ACO does not bypass AppliedE's request-sized temporary-pattern lifecycle.
- EMC Module refreshes no longer require ACO to compare every known EMC recipe.
- Completed plans containing temporary transmutation patterns are not reused.
- Same-tick duplicate refreshes remain coalesced, while the final update and
  cache-generation invalidation are preserved.
- `/aco stats` reports AppliedE fallbacks and preserved provider refreshes.
- AppliedE remains optional and is not bundled.

Automated tests and a clean Gradle build cover ACO's compatibility boundary.
Real ProjectE/AppliedE world testing must still be performed separately for the
original and TPS Fix builds before production deployment.

## 日本語

AppliedE `0.14.3`およびAppliedE TPS Fix `0.14.7-fix2`向けの任意互換対応を
追加するパッチです。

- AppliedEの変換クラフトはAE2本来のクラフト木で計算します。
- 注文量専用の一時Pattern生成・削除をACOが迂回しません。
- EMC Moduleの更新判定だけのために全既知EMCレシピを比較しません。
- 一時変換Patternを含む完了計画は次回注文へ再利用しません。
- 同一tickの重複通知はまとめますが、最終更新とACOキャッシュ世代の失効は維持します。
- `/aco stats`へAppliedEのFallback数とProvider更新数を追加しました。
- AppliedEは任意MODのままで、ACOには同梱しません。

自動テストとGradleクリーンビルドではACO側の互換境界を確認します。本番導入前の
ProjectE/AppliedE実ワールド試験は、本家版とTPS Fix版で個別に行ってください。
