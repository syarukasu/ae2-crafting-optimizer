# AE2 Crafting Optimizer

Minecraft Forge 1.20.1 / Java 17向けのAE2最適化・正確数量連携MODです。

## 2.0.0-rc.7-beta.1

安定版ではなくForge向けベータです。配布JARをサーバーとクライアントの両方に入れてください。内包VMを別途重複導入しないでください。クラフト通信はprotocol 5のため両側のビルドを揃える必要があります。

- 発注数量で `2^63-1`、`2^63`、`10^1024-1` などの計算式を扱います。
- BigIntegerの注文数・通信・確認画面から戻る操作・再計算を既存のExact Count制限内で扱います。
- MOD依存条件は最低版以上を許可します。AE2は15.4.10以上です。Minecraft/Forgeの対象範囲とAPI・Mixin構造検査は維持します。
- 任意で使える有限の計算プロファイラと、明示操作でのみ実行する読み取り専用のVM/AE2比較を含みます。
- 入力規則の事前取得、AE2計算ワーカーでの数量演算、正確な分数演算の高速経路を含みます。

新しい依存版を許可することは、すべての将来版との互換保証ではありません。実クラフト、電力、予約、進捗、キャンセル・返却はAE2や外部CPUの所有です。入力できる数量と、各CPUが実行できる数量は別です。

AE2 15.4.11でclean build、JUnit 996件（失敗・エラー0、既存の任意skip 8件）、回帰台帳、内包モジュールとライセンス境界、実AE2 GameTest 16件を確認しました。試験とローカル証拠は開発用checkoutに保持し、この公開ソースには含めていません。

本番GUI操作、工業環境での計算時間・TPS、巨大注文の物理完走、返却・再起動復旧は未検証です。TPS改善率は断定しません。NeoForge版は別系統で、このForgeベータには含みません。

## ビルド

Java 17が必要です。[AE2-VM-ACO](https://github.com/syarukasu/AE2-VM-ACO) の `release/2.0.0-rc.7-beta.1-public` ブランチを隣の `vm` ディレクトリへ配置します。

```shell
./gradlew clean build -PacoExactVmDir=../vm/exact-engine -PacoAe2Version=15.4.11
```

既定のコンパイル対象はAE2 15.4.10です。ローカル依存JARのディレクトリは `-PacoLocalModsDir`、Mekanism JARは `-PacoMekanismJar` で指定できます。依存MOD本体は配布物へ含めません。

導入するのは `build/distributions/<version>/aco<version>_1.20.1.jar` のみです。`NOT-FOR-INSTALLATION` は中間成果物です。内包VMに対応するソースアーカイブは `sources/` に出力します。

LGPL-3.0-only。LICENSEとNOTICE.mdを参照してください。内包VMの原作者はTaoLe-si/AE2-VMで、ライセンスと帰属情報を維持しています。[English](README.md)
