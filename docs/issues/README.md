# Issue仕様書

各修正は、コードへ触る前に`ISSUE-<GitHub Issue番号>.md`を作成します。

## 読む順番

1. `../PROJECT_CHARTER.md`
2. `../REGRESSION_HISTORY.md`
3. `../CLASS_RESPONSIBILITIES.md`
4. 対象の`ISSUE-<番号>.md`
5. 対象クラスのコメントと自動試験

新規文書は`TEMPLATE.md`から作り、実装前に状態を`Ready`へ変更します。

## 一覧

- [Issue #79](ISSUE-79.md): 一つのroot内部だけでsigned long境界を超える計画
- [Issue #84](ISSUE-84.md): Issue先行開発手順とプロジェクト境界
- [Issue #87](ISSUE-87.md): 全クラスの責務明文化とexact数量Mapの安全な共通化
- [Issue #98](ISSUE-98.md): 外部BigInteger CPUがBigCapacity計画を誤拒否する
- [Issue #101](ISSUE-101.md): 外部セル向け正確BigInteger在庫API
- [Issue #102](ISSUE-102.md): 小規模クラフトの初回プローブによる配送遅延
- [Issue #90](ISSUE-90.md): 無関係なProvider更新によるexact計画失効
- [Issue #93](ISSUE-93.md): Java 25でBigInteger在庫Sidecarの可視コピー中にJVMが終了する
- [Issue #103](ISSUE-103.md): wide計画の非同期compile競合と実行裏付け不足の誤診断
- [Issue #109](ISSUE-109.md): BigInteger API連携が通常AE2の責務境界を越える
- [Issue #115](ISSUE-115.md): 標準AE2クラスタでBigInteger物理実行を所有する
- [Issue #118](ISSUE-118.md): 正常な空Journalを成功量0として自己隔離する
- [Issue #119](ISSUE-119.md): 停止時のRegistry Provider消失でexact Job保存が失敗する
- [Issue #120](ISSUE-120.md): Advanced AE連携Mixinが初期化順で自己無効化する
