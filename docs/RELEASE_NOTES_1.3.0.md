## English

### BigInteger CPU backend and conservative crafting acceleration

- Added BigInteger crafting-host API v3 for explicitly integrated CPU add-ons such as Advanced Quantum Engineering 2.0.0.
- Added exact capacity reservation, multiple fair jobs, bounded long execution windows, versioned NBT, cancellation recovery, and paged status synchronization.
- Added a deterministic missing-input proof for item, fluid, and chemical keys. It returns early only when every recipe and alternative path is proven impossible; all ambiguous cases use AE2's original planner.
- Added a checked-long compiled planner, durable V2 transaction protocol, native GTCEu/Mekanism adapter boundaries, and a deficit-round-robin scheduler.
- Added strict receipt barriers around extraction, target acceptance, energy accounting, output accounting, and restart recovery.
- Kept all custom live-storage mutation Mixins removed. AE2 remains the sole owner of terminal insertion, extraction, rollback, Import/Export Bus transfer, IO Port transfer, and standard crafting decisions.

The compiled planner, V2 native batching, and fair scheduler are experimental and disabled by default. The BigInteger host API is enabled by default but does nothing unless a compatible CPU add-on explicitly registers a host.

Install the same jar on the server and every client. This release was clean-built and passed Forge client bootstrap and Arclight dedicated-server startup on the documented dependency versions.

## 日本語

### BigInteger CPU基盤と保守的なクラフト高速化

- Advanced Quantum Engineering 2.0.0など、明示的に連携するCPU追加MOD向けのBigIntegerクラフトホストAPI v3を追加しました。
- 正確な容量予約、複数ジョブの公平実行、long範囲の実行窓、バージョン付きNBT、キャンセル復旧、ページ分割ステータス同期を追加しました。
- アイテム、液体、Chemicalの決定的な不足証明を追加しました。全レシピと全代替経路が不可能と証明できる場合だけ早期終了し、曖昧な場合は必ずAE2本来のPlannerへ戻ります。
- checked long型コンパイル済みPlanner、永続V2トランザクション、GTCEu/MekanismネイティブAdapter境界、公平なDeficit Round Robinスケジューラを追加しました。
- 抽出、機械受理、電力会計、出力会計、再起動復旧に厳密な受領票バリアを追加しました。
- 可変ストレージへ独自に書き込むMixinは撤去したままです。端末投入、抽出、ロールバック、Import/Export Bus、IO Port、通常クラフト判定はAE2本体だけが担当します。

コンパイル済みPlanner、V2ネイティブ一括処理、公平スケジューラは実験機能で、既定では無効です。BigIntegerホストAPIは既定で有効ですが、対応CPU追加MODがホストを登録しない限り何も処理しません。

サーバーと全クライアントへ同じJARを導入してください。このリリースは記載した依存バージョンでクリーンビルド、Forgeクライアント起動、Arclight専用サーバー起動を確認しています。
