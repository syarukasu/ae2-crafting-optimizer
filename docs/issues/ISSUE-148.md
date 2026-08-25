# Issue #148: Long.MAX_VALUE超過在庫が端末から消える

## 問題

Applied Fluxなど、同じAEKeyを複数のmounted storageが公開する環境で、合計在庫が
`Long.MAX_VALUE`を超えるとME端末の表示が消える、または負数化する。

## 原因

AE2 15.4.10の`NetworkStorage#getAvailableStacks`は、各mountの値を`KeyCounter.add(long)`で
単純加算する。合計がlong境界を超えると負数へwrapする。

`MEInventoryUpdatePacket.Builder#addChanges`は`storedAmount <= 0`を空在庫として送るため、
クライアントRepoは該当キーを削除する。

## 修正

- `MEStorageMenu#broadcastChanges`の表示用全量Snapshotだけをmount単位で集計する。
- 各キーは`Long.MAX_VALUE`で飽和させる。
- BigInteger正本はSidecarへ保持し、longへ切り捨てない。
- Grid本体以外のPortable CellやAddon固有端末はAE2本来の経路へ委譲する。
- Storageの`insert`、`extract`、watcher、serial、クラフト計算は変更しない。

ストレージモニターは別のcached inventory経路を使用するため、Issue #153で独立して修正する。

## Issue #109との境界

Issue #109で削除した`NetworkStorage#getAvailableStacks`常時Mixinと共有cacheは復活させない。
今回の処理は端末Packetを作る一時Snapshotに限定し、通常AE2の在庫正本と操作経路を所有しない。

## 回帰試験

- 通常合計`2,000 + 3,000`は`5,000`のまま。
- `Long.MAX_VALUE + Long.MAX_VALUE`は負数化せず`Long.MAX_VALUE`表示。
- 機能OFF時は元の`MEStorage#getAvailableStacks()`結果をそのまま返す。
- Mixin対象は`broadcastChanges`だけで、`handleInteraction`、`insert`、`extract`を対象にしない。
