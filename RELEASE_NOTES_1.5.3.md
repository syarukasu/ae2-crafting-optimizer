# AE2 Crafting Optimizer 1.5.3

## English

- Fixed exact BigInteger inventory capture for ExtendedAE Plus cells mounted
  through AE2 DriveWatcher and other standard delegating storage wrappers.
- Crafting plans no longer treat `Long.MAX_VALUE` as the real stock when the
  mounted cell contains a larger exact BigInteger amount.
- Preserved wrapper visibility and partition filtering by importing only keys
  exposed by the wrapper's normal AE2 facade.
- Fixed AE2 Crafting Tree summary initialization for ACO wide and BigInteger
  plans.

## 日本語

- AE2のDriveWatcherなど委譲Storage経由で搭載したExtendedAE Plusセルから、
  正確なBigInteger在庫を取得できるよう修正。
- セルの正確な在庫が`Long.MAX_VALUE`を超える場合、クラフト計画が
  `Long.MAX_VALUE`を実在庫として扱って材料不足にする問題を修正。
- Wrapperが通常AE2経路で公開したキーだけを取得し、partitionや可視性設定を維持。
- ACOのwide/BigInteger計画でAE2 Crafting TreeのSummary初期化が欠ける問題を修正。
