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
- 任意連携: GTCEu Modern、Mekanism
- 共存対象: Advanced AE、Advanced Quantum Engineering、ExtendedAE、EMI、JEI、KubeJS、Arclight

ACOはAE2内部へMixinするため、別のAE2系列や未検証バージョンとの互換性は保証しません。サーバーと全クライアントへ同じJARを導入してください。

## 主な機能

- 同じネットワーク・要求元・出力・個数・計算方式の重複クラフト計算を一本化
- 欠品/シミュレーション結果だけを短時間再利用する完了計画キャッシュ
- パターン検索とクラフト可能一覧のキャッシュおよび構造変更時の無効化
- 巨大CPUの1 tickあたりパターン投入予算と適応制御
- ME端末の表示スナップショット、クラフト可能一覧、ストレージ監視通知の間引き
- Pattern Providerが押し出した入出力をGTCEu/Mekanismの候補検索に使うレシピ意図ブリッジ
- 遅いクラフト計算の診断ログ

AE2が最終的なクラフト計算、ジョブ投入、ストレージ操作を担当し続けます。ACOの高速経路が候補を確定できない場合は元のAE2・機械MOD処理へ戻ります。

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
build/libs/ae2-crafting-optimizer-1.0.0.jar
```

ForgeはForge Maven、AE2 15.4.10はModMavenから取得します。GitHub Actionsも同じクリーンビルドを実行します。

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
