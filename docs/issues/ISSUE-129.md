# Issue #129: GTNH/AE2-UEL思想に基づく最適化アーキテクチャの再構築

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/129
- 状態: 実装完了
- 対象版: 1.5.x
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: #79, #84, #87, #90, #93, #98, #101, #102, #103, #109, #115, #118, #119, #120, #123, #125

## 問題

ACOにはクラフト計算、実行予算、Provider世代管理、IO、端末同期、P2P、外部機械連携、BigInteger実行の多数の最適化が存在します。しかし約350型、80前後のMixin、約200設定が個別に設定と互換判定を参照しており、次の安全契約が機械的に保証されていません。

- 機能の所有者がAE2、ACO、外部アドオンのどれか
- 読み取る状態と書き換える状態
- キャッシュの失効世代
- 最適化を辞退できる時点
- AE2標準経路へ戻せる時点
- 失敗時に通常AE2、GUI、在庫、クラフト実行へ影響しないこと

この分散が、過去の通常GUI停止、通常クラフト失敗、在庫境界破壊、stale snapshot例外、wide計画停止、Mixin競合の再発要因になっています。

## 目的

AE2 1.12.2系、AE2-UEL、GTNH系の設計思想を参考にしつつ、現行AE2をforkせず、次の領域を意味論不変で最適化します。

1. ネットワーク再計算の差分化と構造世代管理
2. Import/Export Bus、IO Port、Interfaceの増分処理と負結果backoff
3. Pattern Provider索引、ルーティング、組立マトリックス情報の世代付き再利用
4. 端末・監視・検索・同期の差分化と表示中範囲優先
5. P2Pと経路探索の構造変更時再評価
6. クラフト計算のメモ化、Compiled Graph、long優先とoverflow時のみBigInteger昇格
7. クラフト実行のtick予算、公平性、backpressure、transaction会計
8. GTCEu/Mekanism/外部アドオン連携を任意Adapterとして分離

## 所有権

- AE2: ネットワーク、通常在庫、GUI、スロット操作、通常クラフト状態、最終的なrecipe validity
- ACO: 最適化用世代、bounded cache、exact計画、ACOが明示的に取得したtransaction/escrow、診断
- 外部アドオン: 固有CPU、機械、構造、電力、GUI、実行進捗
- fallback: ACOが状態所有権を取得する前だけAE2標準経路へ戻せる。取得後はcommit、rollback、quarantineのいずれかで閉じる

## 維持する不変条件

- ACO無効時は通常AE2の結果、在庫、GUI、パケット、実行順を変更しない
- 最適化有効時もrecipe validity、必要数、不足数、在庫収支、CPU容量、キャンセル結果を変更しない
- BigIntegerをlongへ切り捨て、飽和、符号反転させない
- クライアント表示用の近似値をサーバー会計へ使用しない
- キャッシュは世代、上限、失効理由を持つ
- 非同期処理はworld、grid、BlockEntityへ直接アクセスしない
- 外部アドオンへは公開APIだけを提供し、固有実行へ介入しない
- Mixin失敗を無言で機能成功として扱わない

## やってはいけないこと

- AE2通常在庫一覧をACOの疑似在庫へ置換する
- AE2 GUIやvirtual slotのクリックを横取りする
- 最終成果物をreceipt/escrowなしで直接生成する
- ownership取得後にAE2標準計算へfallbackする
- 無制限cache、無制限queue、1tick全走査を追加する
- 外部MODのprivate実装を恒久的な必須契約にする
- 対象メソッド不一致を握り潰して機能を部分的に動かす

## 修正方針

### 1. 中央機能台帳

`OptimizationFeature`と`OptimizationFeatureRegistry`を追加し、各機能を以下のドメインへ分類します。

- NETWORK_TOPOLOGY
- STORAGE_IO
- PATTERN_PROVIDER
- CLIENT_SYNC
- CRAFTING_PLANNING
- CRAFTING_EXECUTION
- BIG_INTEGER
- OPTIONAL_INTEGRATION

各機能は設定、既定値、risk、所有権、失効条件、fallback境界を一か所で宣言します。

### 2. fail-closed gate

Mixinとruntime入口は共通gateを通します。master switch、domain switch、個別設定、互換性、必要hookのいずれかが満たされない場合は状態へ触れず辞退します。辞退理由は統計と任意ログへ残します。

### 3. lifecycleと世代

storage、pattern、topology、resource reload、server lifecycleを共通世代へ接続し、cacheがどの世代へ依存するかを明示します。無関係な世代変更でwide計画を失効させません。

### 4. 回帰試験

過去Issueの症状を機能ドメイン別に対応付け、最低限次を自動検証します。

- master/domain/feature無効時のno-op
- 通常longクラフト結果のAE2 parity
- GUI/slot/network inventory非介入
- cache上限と世代失効
- overflow時だけBigIntegerへ昇格
- ownership前fallbackとownership後rollback
- cancellation、restart、chunk unload会計
- Mixinと機能台帳の対応漏れ

## 試験計画

- 単体試験: feature gate、世代、bounded cache、decline理由、所有権状態機械
- 境界試験: 0、1、Long.MAX_VALUE、Long.MAX_VALUE+1、BigInteger上限
- 故障・取消・復旧試験: stale snapshot、target unload、partial receipt、cancel、journal restore
- 静的検査: mixin/feature台帳対応、クライアント参照分離、禁止API利用
- ビルド: 両版`clean test build`
- GameTest: 実行条件を文書化し、実行はユーザー側確認

## 完了条件

- 両ローダーで同じ意味のfeature domainと安全gateを持つ
- 既存の個別設定を壊さず中央台帳へ接続する
- 過去Issueの回帰対応表が全機能を覆う
- 通常AE2経路へ影響するMixinにno-op試験がある
- 両版のJUnit、静的検査、clean buildが成功する
- Forge/NeoForge別のDraft PRを作成する

## 実装結果

- 八つのdomain switchと、master/domain/individual/implementation-statusの共通gateを追加
- 全登録Mixinを`MixinFeatureCatalog`へ対応付け、未登録Mixinをfail-closed化
- 各機能へrisk、正本所有者、fallback境界、関連Issue、実装状態、失効イベントを宣言
- server start/stopでgate診断を初期化し、`/aco stats`用summaryへ拒否理由を追加
- 1.2.2で削除した可変Storage I/O・GUI・Grid Tick経路を`RETIRED_COMPATIBILITY_KEY`へ固定
- Export Busの安全な失敗クラフト要求backoffを、削除済みGrid Tick masterから分離
- Import Busは直前成功slotを検査順のhintとしてだけ再利用し、抽出・挿入・rollbackはAE2へ維持
- Export Busは設定世代ごとの候補keyだけをcacheし、未知のConfigInventory実装ではAE2の直接読取へfallback
- IO Portは六つの入力slotの開始位置だけをround-robin化し、セル移動と会計はAE2へ維持
- 端末の非同期検索は不変Projectionだけをworkerへ渡し、世代不一致を破棄し、失敗時はAE2同期更新へfallback
- Forge/NeoForgeのcore・performance・client Mixinを同じ中央台帳へ統合
- `OptimizationFeatureRegistry`を機能一覧、ID検索、domain別一覧の唯一の正本として追加
- 旧危険Mixinの再登録、No-op機能へのMixin割当、失効契約漏れを検出するJUnitを追加
- [実装状態表](../optimization/IMPLEMENTATION_STATUS.md)と[domain仕様](../optimization/FEATURE_DOMAINS.md)を追加
