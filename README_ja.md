# AE2 Crafting Optimizer

<p align="center">
  <img src="docs/aco-icon.png" alt="AE2 Crafting Optimizer icon" width="192">
</p>

[![Build](https://github.com/syarukasu/ae2-crafting-optimizer/actions/workflows/build.yml/badge.svg)](https://github.com/syarukasu/ae2-crafting-optimizer/actions/workflows/build.yml)
[![License: LGPL-3.0-only](https://img.shields.io/badge/License-LGPL--3.0--only-blue.svg)](LICENSE)

[English](README.md) | 日本語

AE2 Crafting Optimizer（ACO）は、Minecraft Forge 1.20.1向けのApplied Energistics 2最適化MODです。

巨大自動クラフトCPUが1 tickに行うパターン投入を制御し、同一計算やクラフト可能パターンの再走査を減らし、Pattern ProviderからGTCEu/Mekanism機械へ渡されたレシピ意図を短時間だけ再利用します。レシピ、クラフト可否、ストレージの増減、CPU容量や表示上の並列数は変更しません。

## 対象環境

- Minecraft 1.20.1
- Forge 47.4.18以上
- Java 17
- Applied Energistics 2 15.4.10（15.4.x）
- 専用サーバー / シングルプレイ
- 任意連携: GTCEu Modern、Mekanism、Neo ECO AE Extension 20.3.x
- 共存対象: Advanced AE、Advanced Quantum Engineering、ExtendedAE、EMI、JEI、KubeJS、Arclight

ACOはAE2内部へMixinするため、別のAE2系列や未検証バージョンとの互換性は保証しません。サーバーと全クライアントへ同じJARを導入してください。

## 主な機能

- 同じネットワーク・要求元・出力・個数・計算方式の重複クラフト計算を一本化
- 欠品/シミュレーション結果だけを短時間再利用する完了計画キャッシュ
- パターン検索とクラフト可能一覧のキャッシュおよび構造変更時の無効化
- 巨大CPUの1 tickあたりパターン投入予算と適応制御
- 標準AE2、Advanced AE、Neo ECO AEのCPUが使う投入予算の制御
- ストレージ監視通知の表示間引き（端末スナップショット再利用は既定OFF）
- Pattern Providerが押し出した入出力をGTCEu/Mekanismの候補検索に使うレシピ意図ブリッジ
- GT入力バス/ハッチとマルチブロックコントローラの位置差を補う空間索引、および実投入物＋期待出力による候補優先
- 実際に受理された実行数だけをAE2へ反映するトランザクション型パターンバッチAPI
- AdvancedAE反応室の重複レシピ検索と、AE2 Overclockランタイムヘルパーの反射・MethodHandle・同一tickカード数キャッシュ
- ExtendedAE組立マトリックスのスレッド集計・稼働数集計・Crafter経路・同一tick状態通知のキャッシュ
- クラフト計算内メモ、Provider内容世代、IO Port増分処理、Import成功スロット、Export候補、回路スライサー負結果を含むAE2-UEL/GTNH型最適化
- 遅いクラフト計算の診断ログ

AE2が最終的なクラフト計算、ジョブ投入、ストレージ操作を担当し続けます。ACOの高速経路が候補を確定できない場合は元のAE2・機械MOD処理へ戻ります。

アドオン機械最適化は新規Configで既定有効ですが、`[addonMachineOptimizations]`以下から個別に無効化できます。反応室・回路スライサーの処理waveや搬入出、組立マトリックスの構造判定・8本の実クラフトスレッドは間引きません。

AE2 Overclock自身のランタイムヘルパーに対する反射・MethodHandle・カード数キャッシュは有効です。一方、AE2 OverclockのMixinが機械へ追加するメソッドへの二重注入だけはForge 1.20.1で安全に適用できないため無効化しています。AE2 Overclock本体の処理は変更しません。

`[uelOptimizations]`の各項目も個別に無効化できます。Import/Export Busと回路スライサーはGrid Tick予算による実行キャンセルとアイドル待機の対象外です。実搬送は毎回AE2本体を通り、Importの成功位置が外れた場合は必ず全走査へ戻ります。回路スライサーの正候補もExtendedAE本体の`testRecipe`を通過した場合だけ使用します。

tickを跨ぐCapabilityキャッシュと端末の非同期検索・ソートは既定OFFです。後者はMinecraft表示APIをワーカーから呼ばず、クライアントスレッドで作った不変データだけを非同期処理し、古い世代の結果を破棄します。

Neo ECO AE Extension 20.3.xが存在する場合、ACOは独自ECO CPUの通常投入上限とFastPath上限を既存のCPU別適応予算・MEグリッド共有予算へ接続します。Neo ECO本体のバッチ処理、Aggressive FastPath、レシピ、容量、表示性能、クラフト会計は置き換えません。Neo ECOが無い環境では対象Mixinは適用されません。

## トランザクション型バッチAPI

ACO 1.2.0では、機械への一括挿入成功をN回のクラフト完了とみなす旧実験を廃止し、アダプターが返した`acceptedExecutions`だけをAE2へ反映します。0件を返す場合は外部状態を変更してはならず、N件を返す場合はN実行ぶんの入力を永続的に所有したことが必須です。部分搬入や挿入シミュレーションだけでは受理になりません。

インスタント投入を有効にすると、同じCPU呼び出し内で複数の実行可能タスクとバッチを続けて配布します。CPU操作数、トランザクション数、既定4 msの実時間予算で停止し、残りは次tickへ継続します。GTCEu/Mekanism機械の加工時間、電力、副産物、出力処理を飛ばす機能ではありません。

内蔵アダプターは入力抽出とCPU会計をまとめますが、機械への投入はAE2本来の`pushPattern`を1実行ずつ呼びます。毎回ProviderのBusy状態を確認し、搬入待ちが発生した時点で停止します。期待出力、電力、タスク残数へ反映するのは実際に成功した回数だけです。

置換候補のない完全一致処理パターン、容器返却なし、ロックなし、対応ターゲットという条件を満たさない場合は、入力を動かす前にAE2標準処理へ戻ります。GTCEu/Mekanism向けの将来のネイティブ一括アダプターも同じAPIへ登録できます。

## 既定で無効な機能

次の機能は影響範囲が広いため、新規生成Configでは無効です。

- 二段階欠品プレビュー
- 決定論的な欠品早期終了
- Grid Tickの遅延予算
- Import/Export Busの操作回数上限
- 失敗したExport Bus自動要求のクールダウン
- 在庫量によるパターン候補順の変更
- Export Busのfuzzy検索キャッシュ
- 成功したクラフト計画の再利用
- Create用レシピ意図高速経路（予約項目）
- Pattern Providerの旧集約挿入設定（互換キーだけ残り、常に無効）
- Capabilityの世代付きtick跨ぎキャッシュ
- 端末の世代付き非同期検索・ソート
- 端末在庫スナップショットの再利用
- 端末クラフト可能一覧の短期キャッシュ
- 端末表示更新の同一tick集約と範囲分割

設定ごとの既定値と危険度は[Config解説](docs/CONFIGURATION.md)を参照してください。

## 導入

1. Forge 1.20.1、AE2 15.4.10、ACOをサーバーと全クライアントへ導入します。
2. 一度ワールドを起動してConfigを生成します。
3. 必要に応じて次のファイルを編集し、サーバーを再起動します。

```text
<ワールド>/serverconfig/ae2_crafting_optimizer-server.toml
```

既存ワールドのConfigは過去の値を保持します。READMEの既定値へ自動的に戻るわけではありません。変更前にワールドをバックアップしてください。

## ビルド

Java 17で実行します。ローカルのPrism Launcherや`mods`フォルダは不要です。

```bat
gradlew.bat clean build
```

生成物:

```text
build/libs/ae2-crafting-optimizer-1.1.1.jar
```

ForgeはForge Maven、AE2 15.4.10はModMaven、任意連携のシグネチャ検証用Neo ECO 20.3.0はCurseMavenから`compileOnly`で取得します。Neo ECO本体は生成JARへ同梱されません。GitHub Actionsも同じクリーンビルドを実行します。

## ドキュメント

- [英語版の全機能・全Config](README.md)
- [Configと安全区分](docs/CONFIGURATION.md)
- [実装詳細](docs/IMPLEMENTATION.md)
- [試験手順](docs/TESTING.md)
- [公開手順](docs/PUBLISHING.md)
- [CurseForge投稿用説明（英語・日本語）](docs/CURSEFORGE_DESCRIPTION.md)
- [変更履歴](CHANGELOG.md)

## ライセンスと報告先

ACOのソースコードは[GNU Lesser General Public License v3.0 only](LICENSE)（`LGPL-3.0-only`）です。

設計調査では、LGPL v3で公開されているAE2 1.20.1とAE2-UEL 1.12.xの挙動・Issue上の議論を参考にしています。これらのソースやアセットはACOへ同梱していません。由来と帰属は[NOTICE.md](NOTICE.md)に記載しています。

AE2、Advanced AE、GTCEu、Mekanismなどの依存MOD本体やリソースは同梱しません。

ACO使用時だけ発生する問題を、再現確認なしでAE2や連携先MODの作者へ報告しないでください。まずACOを外した状態でも再現するか確認し、本リポジトリのIssueへログ、設定、再現手順を添えて報告してください。
