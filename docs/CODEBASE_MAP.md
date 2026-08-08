# AE2 Crafting Optimizer codebase map

> **Navigation only.** このMapはCodex・LLM・reviewerの探索量を減らすためのindexです。要求・完成状態の正本はREADME、現行docs、Issueです。

## 使い方

1. [`../AGENTS.md`](../AGENTS.md)を読む。
2. 下のTask routeを1つ選ぶ。
3. `Read first`と`Source scope`だけを開き、symbol検索から始める。
4. compile/test結果が別package依存を示した場合だけscopeを広げる。

初期読込の対象外:

```text
build/**
.gradle/**
生成JAR / run directory / logs
CHANGELOG.md全文
全release notes
TEAM_DEVELOPMENT_SPEC.md全文
全engine / 全Mixin / 全test
```

## 固定座標

```text
Minecraft        1.21.1
NeoForge         21.1.247+
Java             21
AE2              19.2.17
Advanced AE      optional 1.6.11
Neo ECO          optional 21.1.1
GTCEu/Mekanism   optional
```

## Task router

| Route | Task | Read first | Source scope | Verification scope |
| --- | --- | --- | --- | --- |
| `C0` | 製品定義、ownership、文書 | `../README.md`, `FEATURE_OWNERSHIP.md`, `IMPLEMENTATION.md` | docs中心 | 文書差分、`build` |
| `P1` | compiled Pattern graph、candidate/missing memoization、planner | `IMPLEMENTATION.md`, `EXPERIMENTAL_ENGINE.md` | `engine`と直接使用する`access`/`lifecycle`だけ | planner/engine tests |
| `T1` | transactional batching、escrow、physical crafting tree、receipt | `BATCH_API.md`, `EXPERIMENTAL_ENGINE.md`, `RESEARCH_FINAL_ENGINE.md` | `batch`, transaction関連`engine`, `api` | batch/transaction/recovery tests |
| `E1` | CPU execution pacing、Instant wave、per-CPU/grid budget | READMEのCPU Execution、`IMPLEMENTATION.md` | execution/lifecycle関連packageと対象Mixin | execution/budget tests、実Server計測 |
| `B1` | BigInteger amount/capacity、host API、checked arithmetic | `BATCH_API.md`, `IMPLEMENTATION.md` | `craftingamount`, `api`, BigInteger関連engine/batch | amount/API/overflow tests |
| `I1` | GTCEu、Mekanism、Neo ECO、Advanced AE integration | READMEのMachine Intent/Physical Tree、`FEATURE_OWNERSHIP.md` | `integration`, `gtceu`, 対象mod専用packageだけ | version contract/adapter tests |
| `M1` | Mixin、AE2 internals、accessor、generation hook | `IMPLEMENTATION.md`, `TESTING.md` | `mixin`/`access`の対象classとtargetだけ | Mixin descriptor/contract tests |
| `U1` | Client UI、crafting amount、command、config | `CONFIGURATION.md`, READMEの該当節 | `client`, `command`, `config`, `craftingamount` | client/config/command tests |
| `R1` | Mod metadata、Mixin config、resources、datapack | README、対象release notes | 対象`src/main/resources`だけ | resource validation、game load |
| `V1` | Build、CI、publishing、release evidence | `TESTING.md`, `PUBLISHING.md`, `.github/workflows/build.yml` | `build.gradle`, `gradle.properties`, `src/test`, workflow | `clean test build` |

## Package map

| Package/path | Responsibility |
| --- | --- |
| `AE2CraftingOptimizer.java` | NeoForge mod entrypointとbootstrap |
| `engine` | compiled planning、proof、generation-aware execution internals |
| `batch` | quantity-independent transaction、escrow/receipt/batch adapter model |
| `api` | versioned public/host contracts |
| `access` | AE2/optional-mod internalsへの限定access surface |
| `craftingamount` | long/BigInteger request amount handlingとUI/network bridge |
| `lifecycle` | generation、grid/server lifecycle、cleanup |
| `intent` | Pattern Providerからmachineへのrecipe intent保持 |
| `integration` | optional mod integration orchestration |
| `gtceu` | GTCEu固有intent/adapter path |
| `client` | client-only UI/preview/display behavior |
| `config` | common/client configとfeature gates |
| `command` | diagnostics/administration commands |
| `src/main/resources` | NeoForge metadata、Mixin descriptors、lang/assets |
| `src/test` | planner、transaction、integration、Mixin、config、API contracts |

package一覧は機能追加で増えるため、route選択後に対象packageの直下だけを列挙する。repository全体の再帰treeを初手にしない。

## 主要entrypointとhot areas

| Purpose | Path |
| --- | --- |
| Mod entrypoint | `src/main/java/com/syaru/ae2craftingoptimizer/AE2CraftingOptimizer.java` |
| Public batch contract | `src/main/java/com/syaru/ae2craftingoptimizer/api` |
| Transaction/batching | `src/main/java/com/syaru/ae2craftingoptimizer/batch` |
| Planner/engine | `src/main/java/com/syaru/ae2craftingoptimizer/engine` |
| BigInteger/request amount | `src/main/java/com/syaru/ae2craftingoptimizer/craftingamount` |
| Machine integrations | `src/main/java/com/syaru/ae2craftingoptimizer/integration`, `gtceu` |
| Mixin/access boundary | `src/main/java/com/syaru/ae2craftingoptimizer/mixin`, `access` |
| Config | `src/main/java/com/syaru/ae2craftingoptimizer/config` |
| Mod metadata | `src/main/resources/META-INF/neoforge.mods.toml` |
| Mixin descriptor | `src/main/resources`内のACO mixin JSON |

大型fileは全文から読まず、transaction UUID、generation key、escrow、Pattern push、budget、target methodなど対象symbolを先に検索する。

## 文書の読み分け

| Need | Document |
| --- | --- |
| ユーザー向け機能・固定環境 | `../README.md`, `../README_ja.md` |
| 全体実装とownership | `IMPLEMENTATION.md`, `FEATURE_OWNERSHIP.md` |
| Transactional batch public contract | `BATCH_API.md` |
| Experimental/deep engine | `EXPERIMENTAL_ENGINE.md` |
| 現在の実装status | `P0_P8_IMPLEMENTATION_STATUS.md` |
| config | `CONFIGURATION.md` |
| test/acceptance | `TESTING.md` |
| physical tree research conclusion | `RESEARCH_FINAL_ENGINE.md` |
| team-wide大規模設計 | `TEAM_DEVELOPMENT_SPEC.md`（必要なheadingだけ） |
| release history | 該当versionのrelease notesだけ |

## 検証の選び方

```text
対象packageのtest
-> ./gradlew test --no-daemon
-> ./gradlew clean test build --no-daemon
-> 実AE2 networkでjob/cancel/restart/overflow/integration確認
-> 性能変更時だけ同条件Server benchmark
```

実Minecraftを起動していない結果をruntime verified、実tick計測なしをperformance verifiedと書かない。

## 省トークン用prompt

```text
AGENTS.mdとdocs/CODEBASE_MAP.mdの<Route ID>だけを基準に作業する。
Task: <作業内容>
最初はroute記載の文書、package、直近test以外を読まない。
別scopeへ広げる場合はcompile dependencyまたはtest failureを根拠として示す。
AE2 authorityとconservative fallbackを弱めない。
```
