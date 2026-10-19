# AE2 Crafting Optimizer agent entrypoint

このファイルはCodex・LLM・自動レビューが、巨大なcrafting engine・Mixin・integration群を毎回すべて読み込まずに作業範囲を決めるための入口です。

## 最小読込手順

1. 最初に本書と [`docs/CODEBASE_MAP.md`](docs/CODEBASE_MAP.md) だけを読む。
2. MapのTask routeを1つ選び、そのrouteに書かれた文書・package・直近testだけを開く。
3. Javaファイルは対象class/method/symbolを検索し、必要範囲だけを読む。
4. compile error、test failure、実依存関係が示した場合だけ隣接packageへ範囲を広げる。
5. `CHANGELOG.md`、全release notes、`TEAM_DEVELOPMENT_SPEC.md`、全engine、全Mixin、全testの一括読込を開始条件にしない。

## 固定契約

```text
Minecraft                 1.21.1
Loader                    NeoForge 21.1.247+
Runtime Java              21
Applied Energistics 2     19.2.17
Advanced AE               optional 1.6.11-1.21.1
Neo ECO AE Extension      optional 21.1.1
GTCEu / Mekanism          optional integrations
Sides                     client + server
```

AE2は通常recipe、provider、crafting job、storage、extraction、insertion、energy、task progress、output accountingのauthorityです。ACOのdeep pathは、完全なaccounting contractをinput mutation前に証明できる場合だけ使用します。

Ambiguous substitution、cycle、dynamic output、unsupported remaining item、generation mismatch、overflow、unknown integrationはAE2通常経路へ戻します。性能目的でstorage mutation、Registry、network、save contractを推測変更しません。

BigInteger pathはexact transaction modelを維持し、暗黙の`longValue()`、数量比例loop、未証明のwhole-tree collapseを入れません。Physical crafting treeではrecipe stepごとのdependency順、escrow、durable receipt、一度だけのcreditを維持します。

## 安全規則

- input移動前にcomplete proof、checked arithmetic、generation validationを終える。
- AE2 authorityを維持できない場合はconservative fallbackする。
- cancellation、stale result、duplicate receipt、partial transactionを安全に拒否・回収する。
- machine intentは候補探索を助けるだけで、machine側のlive input、voltage、energy、tank、output validationを省略しない。
- Mixin target/versionが不一致なら推測で適用しない。
- build/test成功だけで大規模ME network、実Server MSPT、restart recoveryを検証済みと書かない。

## 編集規則

- source変更では同じpackageのtestと [`docs/TESTING.md`](docs/TESTING.md) を先に確認する。
- API変更は [`docs/BATCH_API.md`](docs/BATCH_API.md) とpublic contractを同じ変更で更新する。
- engine/transaction ownership変更は `IMPLEMENTATION.md`、`EXPERIMENTAL_ENGINE.md`、`FEATURE_OWNERSHIP.md` の該当箇所を更新する。
- entrypoint、主要package、重要testの位置が変わる場合は `docs/CODEBASE_MAP.md` を更新する。
- 大型engine/Mixin/transaction fileは対象symbol周辺だけを読む。

## 検証順

```text
対象test class
-> ./gradlew test --no-daemon
-> ./gradlew clean test build --no-daemon
-> 必要な場合だけNeoForge実環境でAE2 job / save / restart / performance確認
```

unit test、fixture、CIだけの結果をruntime verifiedやperformance verifiedとして扱いません。
