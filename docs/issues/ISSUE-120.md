# Issue #120: Advanced AE Mixinが自分で無効化され監査だけが失敗する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/120
- 状態: Implemented
- 影響版: ACO 1.5.23
- 対象ローダー: NeoForge 1.21.1
- 対象依存: Advanced AE 1.6.x

## 問題

Mixin config pluginがMixin初期化中に未完成の`ModList`を参照し、Advanced AE導入済みでも
全連携Mixinを無効化する。サーバー開始後の監査は完成済み`ModList`を見てMixin欠落を検出し、
自己矛盾した起動失敗になる。

## 不変条件

- Mixin適用可否はMixin初期化時点で利用可能な実クラス資源から決める。
- サーバー開始後の監査は、導入済み連携先に必要な変換が欠ければFail-fastする。
- Advanced AE未導入環境では連携Mixinを選択しない。

## やってはいけないこと

- 監査をWARNへ落として欠落した変換のまま実行する。
- Mixin初期化時の`ModList`だけを任意依存の存在判定に使う。
- 対象クラスを初期化してクライアント専用依存や静的初期化を発火させる。

## 修正

Advanced AE pluginへ`AdvCraftingCPUCluster.class`の資源マーカーを追加し、ClassLoaderの
`getResource`で副作用なく存在を判定する。バージョン範囲と必須Interface監査は、
ModList完成後の既存Validatorで継続する。

## 回帰試験

- 実在するclass resourceはModListなしでも検出できる。
- 存在しないclass resourceは選択されない。
- Advanced AE pluginが実在するClusterクラスをマーカーとして宣言する。
