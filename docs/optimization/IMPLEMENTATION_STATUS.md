# Issue #129 実装状態

この表は「設定キーが存在する」と「実行経路が存在する」を分離します。`ACTIVE`だけが稼働対象です。`COMPATIBILITY_NOOP`は既存TOMLとの互換性のため値を読みますが、共通gateが必ず拒否します。

| 領域 | ACTIVE | COMPATIBILITY_NOOP | 理由と境界 |
|---|---|---|---|
| Network topology | P2P同一通知の短時間重複排除 | Grid Tick全面延期、Storage更新coalesce | 接続・切断・周波数・電力変化はAE2へ即時通知する。進捗を持つTickableを遅延しない |
| Storage I/O | Import Busの直前成功slotを先頭検査するscan-order hint、Export Busの設定世代付き候補cacheと失敗要求backoff、IO Portのround-robin slot window | Capability、transfer simulation、bus operation cap | 候補順と設定keyだけをcacheする。抽出・挿入・simulation・rollback・セル移動はAE2本体を正本とし、hint不成立時は同じ呼出し内でAE2通常経路へ戻る |
| Pattern provider | Pattern索引、内容世代、同一tick refresh coalesce | terminal用craftable-set cache | 読み取り前にpending refreshをflushし、Provider内容またはresource reloadで失効する |
| Client sync | 不変Projectionだけをworkerへ渡す端末検索・sort、失敗時のAE2同期更新fallback、scrollbar release安全化 | 端末update coalescing、表示range、Storage Watcher throttle | server inventory・packet・virtual slotを変更しない。可変AEKey/Entryはclient threadで読み、古い世代の結果は破棄する |
| Crafting planning | 計算内memo、候補剪定、compiled graph、checked arithmetic、overflow昇格 | なし | mutable在庫量をcacheせず、辞退はownership取得前だけ行う |
| Crafting execution | CPU/grid予算、receipt付きtransaction、fair scheduler | 旧V1 aggregate実行 | ownership取得後はcommit、rollback、quarantineのいずれかで閉じる |
| BigInteger | exact snapshot、wide plan、long window、exact vector会計 | なし | BigInteger正本をlongへ切り捨てず、表示値を会計へ使用しない |
| Optional integration | GTCEu/Mekanism intent、Advanced AE/ExtendedAE/NeoECO Adapter | Create予約キー | 外部MODのrecipe validity、機械進捗、構造、GUIを所有しない |

## Storage I/Oを再実装する条件

次をすべて自動試験できる独立PRまでは、旧transfer置換Mixinを再登録しません。現在ACTIVEのscan-order、候補key、slot windowはAE2のtransfer結果を所有しません。

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
