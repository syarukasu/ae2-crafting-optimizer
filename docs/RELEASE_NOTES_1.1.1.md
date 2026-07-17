# AE2 Crafting Optimizer 1.1.1

## English

ACO 1.1.1 adds optional execution-budget integration for Neo ECO AE Extension 20.3.x.

Neo ECO uses its own `ECOCraftingCPULogic`, so standard AE2 CPU pacing does not automatically cover it. When enabled, ACO now passes Neo ECO's existing normal and FastPath tick limits through the same adaptive per-CPU and shared per-ME-grid budgets used by ACO. Neo ECO's own lower limits still win.

ACO does not replace Neo ECO's scheduler, recipes, batch/aggressive FastPath, storage, CPU statistics, energy accounting, status updates, or saved crafting jobs. Neo ECO 20.3.0 is a compile-only validation target and is not bundled or required when the integration is unused.

This patch also changes the risky ME terminal snapshot, craftable-set, client-view-coalescing, and visible-range defaults to OFF. Existing world configs retain their current values and must be changed manually if desired.

ACO 1.1.1 also compatibility-disables the experimental aggregate processing-pattern micro-batch path. A target machine accepting multiplied inputs does not prove that it will perform every represented recipe or return exactly the multiplied outputs. That could desynchronize AE2's task progress and `waitingFor` accounting and leave a job permanently incomplete. Both standard AE2 and Advanced AE micro-batch Mixins are now unregistered; legacy config keys remain readable, but `enablePatternMicroBatching = true` is ignored with a startup warning.

Build and published-jar bytecode signature checks passed. In-game Neo ECO runtime testing is still requested for this initial integration.

## 日本語

ACO 1.1.1では、Neo ECO AE Extension 20.3.x向けの任意CPU実行予算連携を追加しました。

Neo ECOは独自の`ECOCraftingCPULogic`を使用するため、標準AE2向けのCPU制御だけでは対象になりません。有効時は、Neo ECO本体が計算した通常投入上限とFastPath上限を、ACOのCPU別適応予算およびMEグリッド共有予算へ通します。Neo ECO本体側の上限がACOより低い場合は、本体側の値が優先されます。

Neo ECOのスケジューラ、レシピ、バッチ/Aggressive FastPath、ストレージ、CPU表示値、電力会計、状態同期、保存中クラフトは置き換えません。Neo ECO 20.3.0はビルド時のシグネチャ検証にだけ使用し、ACOのJARへ同梱せず、未導入環境の必須依存にもなりません。

あわせて、ME端末の在庫スナップショット再利用、クラフト可能一覧キャッシュ、クライアント表示集約、表示範囲分割の新規Config既定値をOFFへ変更しました。既存ワールドのConfig値は自動変更されません。

さらに、実験的な処理パターン一括投入（マイクロバッチ）を1.1.1で互換性無効化しました。機械が複数回分の入力を受け取れたことは、その回数ぶんのレシピ実行と期待出力を保証しません。そのためAE2のタスク進捗と`waitingFor`会計がずれ、クラフトが永久に完了しない可能性がありました。標準AE2用・Advanced AE用の両Mixinを登録解除し、旧Configキーは読み込めますが`enablePatternMicroBatching = true`は起動時に警告して無視します。

クリーンビルドと公開Neo ECO 20.3.0 JARのバイトコードシグネチャ照合は成功しています。初回連携のため、ゲーム内でのNeo ECO実働確認とフィードバックを募集します。
