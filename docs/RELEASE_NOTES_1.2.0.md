# AE2 Crafting Optimizer 1.2.0

## English

ACO 1.2.0 rebuilds processing-pattern batching around an explicit accepted-execution-count API.

The former aggregate-input experiment treated one Pattern Provider success as proof that every represented machine execution had been accepted. AE2 may return success while retaining partially inserted inputs in the provider, so that assumption could multiply `waitingFor` outputs and leave a job permanently incomplete.

The new API requires adapters to return the exact number of complete executions they durably accepted. ACO charges energy, decrements task progress, and registers expected outputs only for that validated count. Unsupported patterns and targets fall back before ownership changes.

The built-in standard/Advanced AE adapter is conservative: it preserves one original AE2 `pushPattern` call per accepted execution and stops immediately on provider backpressure. The adapter API is ready for future native GTCEu/Mekanism batching, but a native adapter must provide a durable accepted-count contract rather than relying on aggregate inventory simulation.

Instant dispatch may now continue through multiple ready tasks and adapter transactions during one CPU call. Operation, transaction, and wall-clock limits stop the pass before it can monopolize a server tick. Machine processing itself remains unchanged.

This build has been clean-compiled and statically inspected. It is not automatically deployed over the stable 1.1.1 server/client jars; in-game validation is required before replacement.

## 日本語

ACO 1.2.0では、処理パターンのバッチ機構を「実受理数を明示する専用API」として作り直しました。

旧方式はPattern Providerの1回の成功を、まとめた全回数が機械へ受理された証拠として扱っていました。しかしAE2は一部だけ搬入し、残りをProvider内部へ保持した状態でも成功を返すため、`waitingFor`だけが増えてクラフトが永久に完了しない可能性がありました。

新APIでは、アダプターが永続的に受理した完全な実行数を正確に返します。ACOは検証済みの実受理数に対してのみ、電力消費、タスク減算、期待出力登録を行います。未対応のパターンや機械は、入力所有権が移る前にAE2標準処理へ戻ります。

標準AE2/Advanced AE向け内蔵アダプターは保守的です。受理1回ごとにAE2本来の`pushPattern`を呼び、ProviderがBusyになった時点で即停止します。将来のGTCEu/Mekanismネイティブ一括処理も同じAPIへ登録できますが、単なる一括挿入シミュレーションではなく、永続的な実受理数の保証が必要です。

インスタント投入では、1回のCPU呼び出し中に複数の実行可能タスクとバッチを続けて処理できます。操作数・トランザクション数・実時間の上限で必ず停止し、機械本体の加工時間や出力生成は変更しません。

クリーンコンパイルと静的検査は行いますが、安定版1.1.1のサーバー・クライアントJARへは自動配置しません。置換前にゲーム内検証が必要です。
