# AE2 Crafting Optimizer

<p align="center">
  <img src="docs/aco-icon.png" alt="AE2 Crafting Optimizer icon" width="192">
</p>

[![Build](https://github.com/syarukasu/ae2-crafting-optimizer/actions/workflows/build.yml/badge.svg)](https://github.com/syarukasu/ae2-crafting-optimizer/actions/workflows/build.yml)
[![License: LGPL-3.0-only](https://img.shields.io/badge/License-LGPL--3.0--only-blue.svg)](LICENSE)

[English](README.md) | 日本語

AE2 Crafting Optimizer（ACO）は、Minecraft Forge 1.20.1向けのApplied Energistics 2最適化MODです。

巨大自動クラフトCPUが1 tickに行うパターン投入を制御し、同一計算やパターン検索の再走査を減らし、Pattern ProviderからGTCEu/Mekanism機械へ渡されたレシピ意図を短時間だけ再利用します。レシピ、クラフト可否、ストレージの増減、CPU容量や表示上の並列数は変更しません。

## 対象環境

- Minecraft 1.20.1
- Forge 47.4.18以上
- Java 17
- Applied Energistics 2 15.4.10（15.4.x）
- 専用サーバー / シングルプレイ
- 任意連携: GTCEu Modern、Mekanism、Applied Mekanistics 1.4.3、Neo ECO AE Extension 20.3.x
- 共存対象: Advanced AE、Advanced Quantum Engineering、ExtendedAE、EMI、JEI、KubeJS、Arclight

ACOはAE2内部へMixinするため、別のAE2系列や未検証バージョンとの互換性は保証しません。サーバーと全クライアントへ同じJARを導入してください。

1.2.2では、Import/Export Bus、IO Port、端末クラフト可能一覧、旧トランザクション実行など、可変ストレージへ触れる独自Mixinを撤去しました。これらの互換Configキーを`true`にしても再有効化されません。

`1.3.2`は、記載した依存関係でクリーンビルドと自動テストを完了した
P0-P8実装版です。起動、復旧、マルチプレイ、長時間ワールド試験は
運用者が行うP9です。深いPlanner、ネイティブ一括処理、公平スケジューラは既定で無効のため、実運用で
有効化する前にコピーしたワールドで復旧試験を行ってください。

## 実験的クラフトエンジン

開発ツリーには、checked longからoverflow時だけBigIntegerへ昇格するコンパイル済み
クラフトグラフ、永続受領票付きGTCEu/Mekanismネイティブ一括処理、公平な複数ジョブ
スケジューラ、バージョン付きBigInteger CPUホストAPI、ページ分割ステータス同期まで
実装されています。
ただしゲーム動作を変えるPlanner、V2一括処理、公平スケジューラは既定で
無効です。明示的なCPUホストにしか効かないBigInteger APIだけは既定で有効ですが、
対応MODがなければ処理は発生しません。設計、NBT、復旧条件、試験項目は
[Experimental Crafting Engine](docs/EXPERIMENTAL_ENGINE.md)を参照してください。

BigInteger機能は通常AE2やAdvanced AEのCPUを自動改変しません。
`BigCraftingEngineApi` v3をCPU追加MODが明示的に所有する方式で、AQE 2.0.1は
最初の任意利用者です。巨大値は
BigIntegerのまま保存・会計し、既存機械へは上限付きのlong/int実行窓だけを渡します。
設定したbit上限は、完成ジョブだけでなくPlannerの中間加算・乗算、NBT読込、Packet読込にも
適用されます。実験マスターはAE2 `15.4.10`へfail-fast固定され、Advanced AEおよび
GTCEu/Mekanism連携も文書に記載した実測対象バージョンだけを受け付けます。

## 主な機能

- 同じネットワーク・要求元・出力・個数・計算方式の重複クラフト計算を一本化
- 欠品/シミュレーション結果だけを短時間再利用する完了計画キャッシュ
- パターン検索キャッシュおよび構造変更時の無効化
- 巨大CPUの1 tickあたりパターン投入予算と適応制御
- 標準AE2、Advanced AE、Neo ECO AEのCPUが使う投入予算の制御
- Pattern Providerが押し出した入出力をGTCEu/Mekanismの候補検索に使うレシピ意図ブリッジ
- GT入力バス/ハッチとマルチブロックコントローラの位置差を補う空間索引、および実投入物＋期待出力による候補優先
- AdvancedAE反応室の重複レシピ検索と、AE2 Overclockランタイムヘルパーの反射・MethodHandle・同一tickカード数キャッシュ
- ExtendedAE組立マトリックスのスレッド集計・稼働数集計・Crafter経路・同一tick状態通知のキャッシュ
- クラフト計算内メモ、Provider内容世代、回路スライサー負結果を含む非破壊のAE2-UEL/GTNH型最適化
- PATなどAE2端末でmouse-upが欠落した時に、スクロールのPage Up/Down反復を物理ボタン状態から停止する安全弁
- 遅いクラフト計算の診断ログ

AE2が最終的なクラフト計算、ジョブ投入、ストレージ操作を担当し続けます。ACOの高速経路が候補を確定できない場合は元のAE2・機械MOD処理へ戻ります。

アドオン機械最適化は新規Configで既定有効ですが、`[addonMachineOptimizations]`以下から個別に無効化できます。反応室・回路スライサーの処理waveや搬入出、組立マトリックスの構造判定・8本の実クラフトスレッドは間引きません。

AE2 Overclock自身のランタイムヘルパーに対する反射・MethodHandle・カード数キャッシュは有効です。一方、AE2 OverclockのMixinが機械へ追加するメソッドへの二重注入だけはForge 1.20.1で安全に適用できないため無効化しています。AE2 Overclock本体の処理は変更しません。

`[uelOptimizations]`の有効な項目も個別に無効化できます。1.2.2ではImport/Export Bus、IO Port、Capability、ストレージシミュレーション用Mixinを登録せず、搬入出とロールバックをAE2本体だけに任せます。回路スライサーの候補は引き続きExtendedAE本体の`testRecipe`を通過した場合だけ使用します。

Capabilityキャッシュは、同一tick・tick跨ぎの両方とも1.2.2では
Mixin未登録の互換No-opです。端末の非同期検索・ソートは既定OFFで、
Minecraft表示APIをワーカーから呼ばず、クライアントスレッドで作った
不変データだけを非同期処理し、古い世代の結果を破棄します。

Neo ECO AE Extension 20.3.xが存在する場合、ACOは独自ECO CPUの通常投入上限とFastPath上限を既存のCPU別適応予算・MEグリッド共有予算へ接続します。Neo ECO本体のバッチ処理、Aggressive FastPath、レシピ、容量、表示性能、クラフト会計は置き換えません。Neo ECOが無い環境では対象Mixinは適用されません。

## トランザクション型バッチAPI

1.2.0/1.2.1のAPIとConfigキーは互換性のため残っていますが、1.2.2ではCPU実行Mixinを登録していないため動作しません。

開発ツリーには、CPU・ターゲット・ワールドジャーナルのNBTを使う別系統のV2を実装しています。これは新しいマスターと子スイッチがすべて既定OFFで、現行1.2.2の有効機能ではありません。詳細は[Experimental Crafting Engine](docs/EXPERIMENTAL_ENGINE.md)を参照してください。

## 既定で無効な機能

次の機能は影響範囲が広いため、新規生成Configでは無効です。

- 二段階欠品プレビュー
- 決定論的な欠品早期終了
- Grid Tickの遅延予算（1.2.2ではMixin未登録）
- Import/Export Busの操作回数上限（1.2.2ではMixin未登録）
- 失敗したExport Bus自動要求のクールダウン
- 在庫量によるパターン候補順の変更
- Export Busのfuzzy検索キャッシュ（1.2.2ではMixin未登録）
- 成功したクラフト計画の再利用
- Create用レシピ意図高速経路（予約項目）
- Pattern Providerの旧集約挿入設定（互換キーだけ残り、常に無効）
- Capabilityキャッシュ（1.2.2ではMixin未登録）
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
build/libs/ae2-crafting-optimizer-1.3.2.jar
```

`-dev`は安定版JARとの取り違えを防ぐための開発用接尾辞です。このJARは
実行環境へ自動配置されません。

ForgeはForge Maven、AE2 15.4.10はModMaven、任意連携のシグネチャ検証用Neo ECO 20.3.0はCurseMavenから`compileOnly`で取得します。Neo ECO本体は生成JARへ同梱されません。GitHub Actionsも同じクリーンビルドを実行します。

## ドキュメント

- [英語版の全機能・全Config](README.md)
- [実験エンジン・BigInteger API・実働試験](docs/EXPERIMENTAL_ENGINE.md)
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
