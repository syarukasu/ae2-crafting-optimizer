# Issue #119: 停止時のexact Job保存がRegistry Provider消失で失敗する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/119
- 状態: Verified
- 修正版: ACO 1.5.23再公開ビルド
- 影響版: ACO 1.5.23
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1

## 問題

`ServerStopping`でグローバルRegistry Providerを破棄した後にBlock Entityの最終保存が走り、
exact SidecarのAEKey符号化が失敗する。例外がAE2の`CraftingBlockEntity`全体のNBT保存まで
中断させ、通常AE2状態も保存できなくなる。

## 不変条件

- exact SidecarはAE2の保存メソッドが渡した実Registry Providerで保存・読込する。
- Registry Providerはサーバー停止完了まで有効とする。
- exact Sidecarだけを黙って捨て、AE2の一回分Facadeだけを保存してはならない。

## やってはいけないこと

- 保存例外を握り潰してSidecarなしのJobを復元可能にする。
- `ServerStopping`でRegistry Providerを破棄する。
- AEKeyを文字列IDへ近似変換してData Componentを失う。

## 修正

`ExactCraftingJobState`のsave/loadへ`HolderLookup.Provider`を必須引数として渡し、
AE2およびAdvanced AEの`writeToNBT`/`readFromNBT`引数をそのまま使用する。グローバルProviderは
`ServerStopped`でのみ破棄する。

## 回帰試験

- `ExactCraftingJobState`は`ACORegistryAccess.require()`を参照しない。
- 両実Job MixinがAE2から受け取ったProviderをSidecarへ渡す。
- `ACORegistryAccess.clear()`は`onServerStopped`より後にだけ存在する。
