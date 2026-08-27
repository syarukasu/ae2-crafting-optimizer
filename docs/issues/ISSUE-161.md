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

## 不変条件

- `NOT_HANDLED`後は同じ呼出内でAE2標準`executeCrafting`が実行される。
- Task、waitingFor、電力、入力抽出、Provider順序、成果物を変更しない。
- Adapterが一件以上ある場合のV2選択と取引処理は変更しない。
- Cache hit後も在庫、候補有効性、返却物、Provider、Adapterを毎回再検証する。
- 世代変更中のmetadataをCacheへ公開しない。
- 専門アドオンが実行Redirectを所有する場合は、従来どおりACOの予算処理を重ねない。

## 残る調査

Adapterが登録された環境の残りの負荷は、動的route不成立、実際のAE2 Pattern配送、機械backpressure、予算設定を区別する必要がある。`/aco stats`のV2 pattern metadata hit/missと実行中sparkを根拠に、次の修正対象を確定する。
