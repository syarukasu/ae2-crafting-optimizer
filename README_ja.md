# AE2 Crafting Optimizer

[English](README.md) | 日本語

## 2.0.0 Snapshot refs.8

**Alphaスナップショットです。安定版ではありません。**
Forge 1.20.1 refs.8（`f93adc1`）を正としてNeoForge 1.21.1へ移植しています。
[変更内容・ビルド手順](docs/releases/2.0.0-snapshot-refs.8.md)。

- 同梱VMでクラフト計画を計算し、途中も数量をBigIntegerで保持します。
- 注文ごとの在庫とパターン情報を使い、対応する共通工程の需要をまとめます。
- 公開BigInteger APIと正確な数量会計を維持します。
- AE2 19本来の取得方法に合わせ、プレイヤー注文は在庫を更新し、リクエスター注文はキャッシュ済み在庫を使います。
- 通常のAE2・Pattern Provider搬入出にACO独自の速度制限を加えません。外部CPUの容量・実行所有権も変更しません。
- GTCEu・Mekanismの対応機種ではレシピ意図で候補検索を短縮します。瞬間加工ではなく、電力・処理時間・適格性の判定は機械側に残します。
- Advanced AE・ExtendedAE・AE2 Overclock向けキャッシュは、対象の版・設定に依存します。
- `/aco vm recent` で計算診断を確認できます。

## 導入

Minecraft 1.21.1、NeoForge 21.1.247以降、Java 21、AE2 19.2.17（19.x）が対象です。
サーバーとクライアントに同じACOを導入してください。VMは同梱済みです。
本家VMの重複導入や、Forge 1.20.1用JARの混用はしないでください。
試用前にワールドをバックアップしてください。

配布用は `build/distributions/2.0.0-snapshot-refs.8/aco2.0.0-snapshot-refs.8_1.21.1.jar` です。
`NOT-FOR-INSTALLATION` はVMを含まない中間成果物です。
設定ファイルは `config/ae2_crafting_optimizer-common.toml` です。

## 検証の境界

ビルド・数量試験・API互換性の成功は、実機動作の保証ではありません。
本移植ではクライアント・サーバーの起動、工業環境の誤不足再現、巨大注文の実行完了、
取消・返却、再起動復旧、同時注文、10秒未満の計画計算の実機受入は未完了です。
本番環境への配置・起動・再起動は行っていません。

移植元Forgeの[CI実行37429047093](https://github.com/syarukasu/ae2-crafting-optimizer/actions/runs/37429047093)では
`ae2.aco_vm_ae2_lifecycle` と `ae2.aco_vm_substitution_queries` が失敗しています。
この移植でその問題を解決済みとは扱いません。

BigIntegerで計算できることと、通常AE2 CPUの容量拡張は別です。
物理実行には対応する利用側が必要で、計画値から成果物を生成することはありません。

## 開発資料

- [プロジェクト憲章](docs/PROJECT_CHARTER.md)
- [クラス責務](docs/CLASS_RESPONSIBILITIES.md)
- [今回の移植範囲](docs/issues/ISSUE-218.md)
- [回帰試験台帳](docs/issues/REGRESSION_MATRIX.tsv)
- [公開説明文](docs/CURSEFORGE_DESCRIPTION.md)
- [依存関係を固定したビルド手順](.github/workflows/build.yml)

VMは [syarukasu/AE2-VM-ACO](https://github.com/syarukasu/AE2-VM-ACO) の
`5b57a00e283d355a47ee5276ba528b2dcf7d07db` に固定しています。
ルートではなく `exact-engine` をビルドし、native VMはACO側でNeoForge用にコンパイルします。
ライセンスは [LGPL-3.0-only](LICENSE) です。
