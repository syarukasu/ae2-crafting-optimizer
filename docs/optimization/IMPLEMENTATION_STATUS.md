# Issue #129 実装状態

この表は「設定キーが存在する」と「実行経路が存在する」を分離します。`ACTIVE`だけが稼働対象です。`COMPATIBILITY_NOOP`は既存TOMLとの互換性のため値を読みますが、共通gateが必ず拒否します。

| 領域 | ACTIVE | COMPATIBILITY_NOOP | 理由と境界 |
|---|---|---|---|
| Network topology | P2P同一通知の短時間重複排除 | Grid Tick全面延期、Storage更新coalesce | 接続・切断・周波数・電力変化はAE2へ即時通知する。進捗を持つTickableを遅延しない |
| Storage I/O | Export Busの失敗した自動クラフト要求backoff | Import直前成功slot、Export候補、IO Port cursor、Capability、transfer simulation、bus operation cap | Issue #74/#109および1.2.2で、simulate後の状態変化による消失経路を撤去済み。AE2のtransferを置換しない |
| Pattern provider | Pattern索引、内容世代、同一tick refresh coalesce | terminal用craftable-set cache | 読み取り前にpending refreshをflushし、Provider内容またはresource reloadで失効する |
| Client sync | scrollbar release安全化 | 端末snapshot、非同期検索、表示range、Storage Watcher throttle | Issue #30/#109のvirtual slot・zero-stock回帰を防ぐためGUI/packetを変更しない |
| Crafting planning | 計算内memo、候補剪定、compiled graph、checked arithmetic、overflow昇格 | なし | mutable在庫量をcacheせず、辞退はownership取得前だけ行う |
| Crafting execution | CPU/grid予算、receipt付きtransaction、fair scheduler | 旧V1 aggregate実行 | ownership取得後はcommit、rollback、quarantineのいずれかで閉じる |
| BigInteger | exact snapshot、wide plan、long window、exact vector会計 | なし | BigInteger正本をlongへ切り捨てず、表示値を会計へ使用しない |
| Optional integration | GTCEu/Mekanism intent、Advanced AE/ExtendedAE/NeoECO Adapter | Create予約キー | 外部MODのrecipe validity、機械進捗、構造、GUIを所有しない |

## Storage I/Oを再実装する条件

次をすべて自動試験できる独立PRまでは、旧Mixinを再登録しません。

- simulationとmodulationの間に外部在庫が変化しても収支が一致する
- 部分挿入時に余剰を完全rollbackでき、失敗時にvoidしない
- Capability invalidation、隣接Block Entity交換、chunk unloadを検出する
- Import候補ヒント失敗後に同じtickのAE2通常全走査へ戻る
- IO Portのセル移動が一回だけ行われ、中間状態を保存しない
- 機能OFF時の呼出し回数、順序、戻り値がAE2単体と一致する

## 過去実装の禁止事項

次の旧Mixin名をruntime設定へ戻してはいけません。

- `StorageImportLastSuccessfulSlotMixin`
- `StorageImportSimulationCacheMixin`
- `StorageExportSimulationCacheMixin`
- `IOPortIncrementalProcessingMixin`
- `BlockApiCacheTickCacheMixin`
- `GridTickBudgetMixin`

これらは1.2.2で削除されています。特に旧Import実装は外部から先に抽出し、AE2側の実挿入量がsimulation結果より減った場合に、戻せない余剰をvoidし得ました。
