# Issue #109: BigInteger API連携が通常AE2の責務境界を越える

## 問題

外部CPUがBigInteger plan consumerを登録すると、通常AE2のクラフト計算と標準CPUへの提出まで
ACOのwide互換経路へ巻き込まれていました。

- 通常の自動クラフト注文を提出できない
- `enableExperimentalCraftingEngine=false`でも通常long計画が置換され得る
- `enableOptimizer=false`でもBigInteger backendのランタイム介入が残る

## 原因

BigInteger APIの「利用可能」「consumer登録済み」「AE2標準経路を置換してよい」が同じ条件として
扱われていました。また、標準`CraftingCPUCluster.submitJob`が外部consumerの存在だけでwide計画を
受理する分岐を持ち、CPUの所有者を判定できないまま実行責務へ介入していました。

## 修正

- 通常long計画のAuthoritative置換は`enableExperimentalCraftingEngine=true`の時だけ許可する
- BigInteger profileはwide計画の生成だけに使用する
- 標準AE2 CPUの提出結果をACOで上書きせず、AE2本来の容量判定へ委譲する
- `enableOptimizer=false`ならBigInteger backendも停止する
- ACOは外部CPUへexact plan APIを提供するだけで、外部CPUの実行ロジックを所有しない

## 1.5.21後に判明した残存介入

1.5.21では上位の実行介入を外しましたが、下層の
`NetworkStorage#getAvailableStacks`へ刺さる`NetworkStorageBigIntegerSnapshotMixin`が
残っていました。このMixinは端末、バス、監視、通常クラフトを含む全在庫列挙を
独自集計と同一tick cacheへ置換していました。

また、ExtendedAE Plusセルの通常`insert/extract`前にも、ACO直接取引の有無にかかわらず
内部cache refreshを強制していました。

## 追加修正

- NetworkStorage、StorageService、NetworkCraftingSimulationStateへのexact在庫常時Mixinを登録しない
- BigInteger正確量はクラフト計算開始時の`PlanningExactInventorySnapshot`だけで取得する
- 通常AE2の在庫一覧、serial、insert、extract、watcherを変更しない
- ExtendedAE Plusの通常搬入出へは、ACO直接変更後の整合台帳が存在する時だけ介入する
- テストセルの`10^64`正本とAE2向け`Long.MAX_VALUE`互換表示は維持する

## 回帰禁止事項

- consumer登録の有無だけで標準AE2 CPUの提出結果を変更しない
- BigInteger APIを有効化しただけで通常long計画を置換しない
- master switchがOFFの時にクラフト演算へ介入しない
- `NetworkStorage#getAvailableStacks`を正確量取得のために常時Redirectしない
- Planner用SidecarをStorageServiceの共有Cached Inventoryへ格納しない

## 確認項目

- 通常int/long注文はAE2標準Plannerへ委譲される
- wide注文だけACO Plannerへ昇格する
- 標準AE2 CPUの提出、使用中判定、容量判定はAE2本来の経路で決まる
- AQE/InsaneAEは各MOD自身のCPU境界でexact planを受理する
