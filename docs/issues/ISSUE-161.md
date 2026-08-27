# Issue #161: クラフト開始後の実行負荷

## 目的

クラフト計算完了後の実行時間とサーバー負荷を分離して計測し、AE2の結果と会計を変えずにACO固有の不要処理を除去する。

## 確定した第一の無駄

`enableTransactionalBatchingV2=true`でも、V2 Adapterが一件も登録されていない環境では、V2が成功する可能性はない。従来はその状態でも`CraftingCpuLogic.executeCrafting`の各呼出からJob、Task、Inventory、Provider候補の探索へ進み得た。

## 修正

- Adapter登録が0件なら、Job状態へ触れる前に`NOT_HANDLED`を返す。
- `/aco stats`へSequential Instantの平均実時間を追加する。
- V2 probe、Adapter 0件bypass、Task走査、route成立、標準fallbackを個別に数える。

## 不変条件

- `NOT_HANDLED`後は同じ呼出内でAE2標準`executeCrafting`が実行される。
- Task、waitingFor、電力、入力抽出、Provider順序、成果物を変更しない。
- Adapterが一件以上ある場合のV2選択と取引処理は変更しない。
- 専門アドオンが実行Redirectを所有する場合は、従来どおりACOの予算処理を重ねない。

## 残る調査

Adapterが登録された環境の負荷は、route不成立探索、実際のAE2 Pattern配送、機械backpressure、予算設定を区別する必要がある。今回追加した統計と実行中sparkを根拠に、次の修正対象を確定する。
