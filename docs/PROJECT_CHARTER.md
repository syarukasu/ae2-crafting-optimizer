# ACOプロジェクト憲章

この文書は、AE2 Crafting Optimizer（ACO）が何を守り、何を行わないかを定義する
最上位の開発基準です。実装、Issue仕様書、レビュー、リリース判断が衝突した場合は、
この文書の安全境界を優先します。

## 目的

ACOはApplied Energistics 2のフォークや代替実装ではなく、巨大な自動クラフト環境を
正確さを変えずに軽量化する最適化・連携レイヤーです。

- 重複するPattern探索、在庫問い合わせ、候補展開を減らす
- 決定的なクラフト計算を注文数量ではなく固有レシピ数へ比例させる
- 要求量・中間量・不足量・容量を`BigInteger`で正確に計算・連携する
- 通常Pattern投入はAE2・CPUアドオン本来の速度を維持し、ACO独自の時間・件数制限を重ねない
- ACOが所有するexact物理取引だけを有限の作業単位で進める
- AQE、InsaneAE、AACなどへ、版管理された正確な公開APIを提供する
- 最適化前後で結果、在庫、欠品、容量、進捗、取消、再起動復元を一致させる

速度向上は成功条件の一部です。正確さ、所有権、復旧可能性を損なう高速化は失敗です。

## 2.0.0の維持範囲と完成基準

2026-09-24のユーザー指定に基づき、次の契約を再設計の基準とします。
過去に選んだ計算方式や同梱構成を維持することは成功条件ではありません。

### 維持するもの

- 公開BigInteger APIの互換性。AQE・AACなど既存利用側の呼出しと動作を壊さない。
- 数量の正確さと、予約・取消・復旧の整合性。既存ジョブと保存済み取引も対象にする。

API互換性は型やメソッドが残るだけでは成立しません。同じ呼出しの意味、返却値、
所有権とライフサイクルを保ちます。保存形式を変える場合も、既存取引を正確に復元
できる移行が必要です。API内部のデータ構造や処理手順は維持対象ではありません。

### 作り直せるもの

- 計算方式、データ構造、スレッド構成、キャッシュ。
- VMの採用・不採用、Jar-in-Jar構成。
- 旧計算器や最適化機能。目的に寄与しなければ削除対象にする。

既存のVMも新しい方式も、正確さと実測結果で選びます。先に計算器やスレッド数を
固定したり、既存実装の維持を理由に完成基準を狭めたりしてはいけません。
以下の安全境界は守りますが、現在の所有クラスや内部手順まで固定しません。

### 完成の基準

- 通常注文も巨大注文も、消費・不足・副産物を正確に計算できる。
- 在庫が足りても足りなくても速い。不足を一件見つけただけの打切りは計算完了ではない。
- 複数注文と搬入出でサーバーを長時間占有しない。
- 指定サーバーのクリエイティブ制御回路約922京個の計画を、原料からの全工程込みで
  10秒未満に構築する。正確な注文数と計測条件を記録し、実測で証明する。

計画計算、注文受付、実設備での完了・取消・再起動復旧は別々に検証します。
単体試験やビルドの成功、VMの組込み、既存APIの温存だけでは完成としません。
検証方法は`docs/TESTING.md`の「2.0.0 Acceptance Contract」に従います。

2026-09-25指定の容量基準は、AQE BigInteger Quantum Coreの
`10^1024 - 1 bytes`（3402 bits）です。注文個数の固定上限ではありません。
実際の計画容量とCPUの空き容量をBigIntegerで比較し、容量判定ではぴったりを許可し、
1 byteでも超える予約を拒否します。中間数量や複数コアの容量を3402 bitへ
切り詰めず、上記の性能・実機完了条件も引き続き満たす必要があります。

## 正本と所有権

2026-09-26 follow-up: pure VM arithmetic must not be tick-throttled. Keep AE2's
pool and TickHandler entry, but require exclusive server coordination only for
live world/grid/pattern access. A worker may continue arithmetic after releasing
the interval. Cancellation is independent of ticking. This does not authorize
concurrent live callbacks or restoring per-input server task queues/retries.

2026-09-26 clarification: the user requests AE2's own calculation lifecycle
with VM BigInteger arithmetic. Pattern callbacks in AE2's exclusive
CraftingCalculation time slice are permitted, just as in AE2 itself: the server
is waiting on AE2's monitor, not concurrently mutating the world. Arbitrary
detached worker access remains forbidden. This restores AE2's bounded server
wait; it does not satisfy the eventual zero/unmeasurable overhead aspiration.

- 通常クラフトのレシピ適格性、Provider、CPU Job、リンク、通常在庫操作はAE2が正本です。
- ACOのexact経路は、明示的に取得した入力、Escrow、計画、予約、復旧だけを所有します。
- 入力所有権が移る前だけ、安全に証明できない高速経路からAE2へ戻れます。
- 入力所有権が移った後はfallbackせず、再開、正確な取消返却、隔離のいずれかにします。
- 外部CPUアドオンは構造、実行、GUI、電力、進捗を所有し、ACOは計画とAPIだけを提供します。
- AACは実WorkerとReceiptを所有しますが、親計画、ME在庫、最終完了を決定しません。

## 非目標

ACOは次のMODではありません。

- レシピや進行を追加・変更するMOD
- クラフトCPUブロックや構造を追加するMOD
- AE2の在庫・機械・CPUの所有権まで置き換えるフォーク
- GTCEu、Mekanism、Advanced AE、InsaneAE、Neo ECOの実行ロジックを所有するMOD
- 計画値から最終成果物を直接生成する変換MOD
- 表示を軽くするために実在庫や実進捗を近似するMOD

## やってはいけないこと

- BigInteger正本を`long`へクランプ、飽和、切り捨てして判定へ使う
- exact計画の正しさと、legacy long実行へ分割できるかを同一条件にする
- 計画上の出力を、実クラフトまたは検証済みReceiptなしで在庫へ生成する
- 所有権移転後に通常AE2へfallbackして同じ処理を二重実行する
- 在庫、Provider、Block Entity、World、Gridへ非同期スレッドから直接アクセスする
- 成功済み計画を在庫・Pattern・recipe世代の再検証なしで再利用する
- 上限・寿命・失効条件のないキャッシュを追加する
- 例外や会計不一致をログ抑制のために握り潰す
- 任意MODやクライアント専用クラスをCommon初期化から直接ロードする
- 対象記述子を確認せず、広範囲なMixinで外部MOD全体を置換する
- 外部MODのCPU実行、構造、GUI、レシピ、テクスチャをACOから私物化する
- ビルド成功を、起動、ゲーム内動作、TPS改善の証明として報告する

## 変更判断の優先順位

1. アイテム・液体・Chemicalの消失と複製を防ぐ
2. 所有権、容量、欠品、進捗、取消、復旧の正確さを守る
3. 通常AE2と任意連携MODの既存挙動を守る
4. TPS、MSPT、割り当て量、計算時間を改善する
5. 表示と操作感を改善する

上位条件を満たせない変更は、下位の性能利益が大きくても採用しません。

## 開発開始条件

コード変更前に`docs/ISSUE_WORKFLOW.md`を実行し、対象の
`docs/issues/ISSUE-<番号>.md`を`Ready`にしてください。
