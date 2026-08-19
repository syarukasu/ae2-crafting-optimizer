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

## 回帰禁止事項

- consumer登録の有無だけで標準AE2 CPUの提出結果を変更しない
- BigInteger APIを有効化しただけで通常long計画を置換しない
- master switchがOFFの時にクラフト演算へ介入しない

## 確認項目

- 通常int/long注文はAE2標準Plannerへ委譲される
- wide注文だけACO Plannerへ昇格する
- 標準AE2 CPUの提出、使用中判定、容量判定はAE2本来の経路で決まる
- AQE/InsaneAEは各MOD自身のCPU境界でexact planを受理する
