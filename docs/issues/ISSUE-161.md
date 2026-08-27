# Issue #161: クラフト開始後の実行負荷

## 目的

クラフト計算完了後の実行時間とサーバー負荷を分離して計測し、AE2の結果と会計を変えずにACO固有の不要処理を除去する。

## 確定した第一の無駄

`enableTransactionalBatchingV2=true`でも、V2 Adapterが一件も登録されていない環境では、V2が成功する可能性はない。従来はその状態でも`CraftingCpuLogic.executeCrafting`の各呼出からJob、Task、Inventory、Provider候補の探索へ進み得た。

## 修正

- Adapter登録が0件なら、Job状態へ触れる前に`NOT_HANDLED`を返す。
- `/aco stats`へSequential Instantの平均実時間を追加する。
- V2 probe、Adapter 0件bypass、Task走査、route成立、標準fallbackを個別に数える。

## 第二段: Adapter登録環境の静的metadata再利用

`PatternProviderRoutingCache`は既にProvider候補一覧をPattern世代単位で再利用していた。一方、`TransactionalCraftingExecutorV2`は各waveで全Taskを先頭から調べ、作業台Patternの入力候補配列、係数乗算、出力集約を毎回作り直していた。加工Patternも、V2対象外と分かる前にtask fingerprintへ到達していた。

次の範囲だけを`TransactionalExactPatternCache`へ分離した。

- 作業台Patternであること
- 入力slot順、候補順、入力係数と候補一個あたりのchecked long量
- 出力の一実行あたり集約
- 静的に対象外であるPattern

Cacheは`ProviderPatternGenerationTracker`の世代に結び付ける。compile中または取得直後に世代が変化した結果は使わず、そのwaveだけ現在のPatternを直接読む。直接読取中にも世代が進んだ場合はV2が所有権を取らず、AE2標準実行へ残す。

次はキャッシュしない。

- Task進捗と`waitingFor`
- 在庫量と代替入力の選択
- `IInput.isValid`と返却物
- Providerの順序、busy、target、chunk load
- Adapterの`supports`、Receipt健全性、backpressure
- 電力、抽出、成果物、取引状態

これにより実行結果と選択順を維持したまま、同じPatternをwaveごとに再解析するallocationと、非作業台Taskの不要なfingerprint生成を除去する。

## 第三段: int並列数の桁あふれと未計測wave

最新のprofilerでは、ACOのAE2実行境界の内側でExtended AE Plus
`PatternProviderLogicAdvancedMixin#eap$redirectBlockingContains`が支配的だった。
同MixinはAdvanced Blocking時にPattern入力slotと候補を走査し、候補ごとに
`PatternProviderTarget.containsPatternInput`を呼ぶ。ACOがこの判定結果を置き換えることはできないため、
一回の未計測waveへ65,536操作を渡す従来値では最初のtickだけで時間予算を大きく超え得た。

同時に、AE2の`CraftingCPUCluster.accelerator`と
`CraftingCpuLogic#tickCraftingLogic`は次の計算をすべて`int`で行う。

```text
accelerator += unitThreads
remaining = getCoProcessors() + 1 - (usedOps[0] + usedOps[1] + usedOps[2])
```

Extended AE Plusを含む16スレッド上限拡張Mixinは、巨大ユニットをintフィールドへ直接加算する。
合計が`Integer.MAX_VALUE`を越えると、ACO従来実装も負数を
`Math.min(negative, configuredCap)`のまま返していたため、AE2の`remaining > 0`へ入れず
例外なしでクラフトが停止した。合計が2^32を越えて正値へ再ラップする場合は、負数判定だけでも検出できない。

修正は次のとおり。

- 標準AE2クラスタの構成ユニットを形成後の最初の実行時に一度だけ走査し、各
  `getAcceleratorThreads()`をlongで合計する。
- クラスタ変更時はAE2が新しいクラスタインスタンスを作るため、Weak cacheは旧クラスタと共に破棄する。
- ACOの1 tick実行窓へ渡す時だけ`Integer.MAX_VALUE - 1`以下へ投影する。
  正確な合計や表示をクランプする処理ではなく、直後のAE2 `+ 1`を安全にするint API境界である。
- 説明できない負数は0操作へ潰さず、明示的な例外としてfail closedする。
- 既存Configに65,536 probeが残っていても、新しい未計測wave上限の初期値1,024を先に適用する。
- 64操作以下のcold jobは分割せず、Issue #74/#102のProvider面ラウンドロビンを維持する。
- 一度計測した後は、要求が最大wave以下でも実測時間から次waveを計算する。
- 完了0件でも高価だったProvider探索時間を記録し、次tickで同じcold-start大波を繰り返さない。
- 専門アドオンが高優先度Redirectを所有する場合にACOが二重予算を重ねない規則は変更しない。

## 不変条件

- `NOT_HANDLED`後は同じ呼出内でAE2標準`executeCrafting`が実行される。
- Task、waitingFor、電力、入力抽出、Provider順序、成果物を変更しない。
- Adapterが一件以上ある場合のV2選択と取引処理は変更しない。
- Cache hit後も在庫、候補有効性、返却物、Provider、Adapterを毎回再検証する。
- 世代変更中のmetadataをCacheへ公開しない。
- 専門アドオンが実行Redirectを所有する場合は、従来どおりACOの予算処理を重ねない。

## 残る実環境確認

JUnitは桁あふれ前のlong復元、正値への再ラップ、AE2 `+ 1`境界、cold probe、
計測済み小wave、完了0件の費用記録を固定する。実環境では同じ注文で
`/aco stats`の`Wide co-processor execution count`とSequential Instant最大wave時間を確認し、
Extended AE Plus側の一操作自体が時間予算を越える場合だけ、同MOD側のアルゴリズム改善を別件として扱う。
