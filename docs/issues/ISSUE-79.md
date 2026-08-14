# Issue #79: 一つのroot内部だけでsigned long境界を超える計画が失われる

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/79
- 状態: Released
- 影響版: ACO 1.5.17
- 修正版: ACO 1.5.18
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連PR: #80、#81、#82、#83

## 問題

完成品1個の内部で必要になる下位Pattern実行数または境界入力数が
`Long.MAX_VALUE`を超えると、正確なBigInteger計画を計算済みでも
`WidePlanUnavailableException`で計画全体が失敗しました。

## 再現と証拠

8個から1個を作る圧縮Patternを21段つなぎ、最終段を1個注文します。

- 20段: `8^20`でsigned long内に収まり成功
- 21段: `8^21 = 2^63`となり、十分な在庫があっても失敗
- 21段で在庫不足: missing simulationは作成できる場合がある

失敗箇所は`Ae2BigCraftingPlanFactory`のroot window決定でした。

## 期待結果

- 在庫が`2^63`あればcraftable exact planを返す
- 在庫がなければmissing `2^63`のsimulation planを返す
- 正確なPattern回数、在庫使用量、不足量、CPU byte数を公開APIから取得できる
- signed long境界未満は従来のroot-window経路を使う

## 現在結果（修正前）

一つのroot自体をchecked-long子Jobへ分割できない場合、安全なroot windowが0になり、
exact計画の有効性まで否定して破棄していました。NeoForge側には偽の`window=1`を
作る経路もあり、exact計画を旧long実行モデルへ押し戻していました。

## 所有権

- ACO: exact計画、Pattern回数、在庫、missing、CPU byte数、公開sidecar
- AE2: 通常long計画と通常CPU実行
- AQE・InsaneAEなど: ACO APIから受け取った計画を使うCPU実行
- fallback境界: exact sidecar公開前。公開後に値をlongへ落として再計算しない

## 維持する不変条件

- exact計画が正しいことと、legacy root-windowで実行できることを別々に判定する
- 一つのrootがlongへ収まらなくてもexact sidecarを保持する
- Exact方式では`rootWindowJob`だけを作らない
- signed long境界未満は従来の`ROOT_WINDOWS`を維持する
- ACOは外部CPUアドオンの実行ロジックを置換しない

## やってはいけないこと

- root windowが0という理由でexact計画を`null`へ変える
- Exact専用計画を偽の`window=1`へ丸める
- Pattern回数、在庫、missing、CPU byte数をlongへクランプする
- exact-only計画をlegacy root-windowコマンドへ誤投入する
- ACOからInsaneAEなどのCPU構造・GUI・実行を所有する

## 修正方針

exact計画の公開とlegacy root-window実行を分離しました。

- `ROOT_WINDOWS`: root単位で安全にlong子Jobへ分割可能
- `EXACT_PATTERN_EXECUTOR`: 一つのroot内部がlongを超えるがexact計画は有効
- Exact方式でも`PreparedBigRootPlan`と公開sidecarを保持
- Exact方式では`rootWindowJob = null`
- legacyコマンドはExact専用計画を明確に拒否

## 実装前チェック

- [x] `8^21 = 2^63`の境界を特定した
- [x] exact計画と実行windowの所有権を分離した
- [x] Forge/NeoForgeの旧分岐差を確認した
- [x] 外部CPU実行へ介入しない方針を確認した

## 試験計画

- 21段、在庫`2^63`: craftable exact plan
- 21段、在庫0: missing `2^63`
- 20段: root-window方式
- Exact専用planの公開API sidecar
- 両版の全JUnit、clean build、CI

## 実装結果

- `Ae2BigCraftingPlanFactory`へ`ExecutionMode`と`RootWindowDecision`を追加
- exact planとoptional root-window Jobを`PreparedBigRootPlan`で分離
- `BigIntegerCraftingPlan`と`ExactCraftingJobState`をnullable root Jobへ対応
- legacyコマンドへExact専用計画の明示拒否を追加
- Java本体へIssue #79の回帰防止コメントを追加

## 検証結果

- Forge 1.20.1: JUnit 334件、失敗0、エラー0、スキップ2
- NeoForge 1.21.1: JUnit 356件、失敗0、エラー0
- 両版`clean build`: 成功
- GitHub Actions: 成功
- 起動・ゲーム内実行: この修正作業では未実施

## 完了

- Forge PR: https://github.com/syarukasu/ae2-crafting-optimizer/pull/80
- NeoForge PR: https://github.com/syarukasu/ae2-crafting-optimizer/pull/81
- 文書化PR: https://github.com/syarukasu/ae2-crafting-optimizer/pull/82
- 文書化PR: https://github.com/syarukasu/ae2-crafting-optimizer/pull/83
- Forgeマージ: `a0320b12aab342b7b6d4881cc8a231d219bb4943`
- NeoForgeマージ: `f3579db7bcc82930ba27205a644bb754cd141058`
- リリース: https://github.com/syarukasu/ae2-crafting-optimizer/releases/tag/v1.5.18
