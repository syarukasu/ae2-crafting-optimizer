# Issue #153: Long.MAX_VALUE超過在庫でストレージモニターが負数化する

## 問題

同じAEKeyを複数のmounted storageが公開し、合計が`Long.MAX_VALUE`を超えると、
MEストレージモニターと変換モニターの表示量が負数化する。負数を受理しない後続処理では
クラッシュする場合がある。

## 原因

Issue #148の修正対象は`MEStorageMenu#broadcastChanges`の端末Packet用Snapshotだけだった。
モニターは別経路の`AbstractMonitorPart#updateReportingValue`で
`IStorageService#getCachedInventory()`を読み、既にwrapした`long`を`amount`へ保存していた。

## 修正

- `AbstractMonitorPart#updateReportingValue`内のcached inventory取得だけをRedirectする。
- exact mountを列挙できるNetworkStorageでは、Issue #148と同じ表示用飽和Snapshotを使う。
- 機能OFFまたはexact mountを列挙できないStorageでは、AE2のcached counterを同一インスタンスで返す。
- watcher、NBT、Packet形式、render、クリック、挿入、抽出、クラフト会計は変更しない。

## 禁止事項

- `IStorageService#getCachedInventory()`の正本を書き換えない。
- `NetworkStorage#getAvailableStacks`へ常時Mixinを戻さない。
- 負数を0として扱わない。
- exact非対応Storageを推測でBigInteger化しない。

## 回帰試験

- monitor cached値が負数でも、exact mount合計`Long.MAX_VALUE + Long.MAX_VALUE`は`Long.MAX_VALUE`表示になる。
- 機能OFFでは元のcached counterを返す。
- exact非対応Storageでは元のcached counterを返す。
- Mixinは`updateReportingValue`の`getCachedInventory()`一箇所だけを対象にする。
