# 回帰履歴

最適化経路を変更する前に、この一覧から関連Issue仕様書を読みます。詳細な症状、原因、
修正、不変条件、禁止事項、試験は`docs/issues/ISSUE-<番号>.md`を正本とします。

| Issue | 症状 | 影響版 | 修正版 | 仕様書 |
|---|---|---:|---:|---|
| [#79](https://github.com/syarukasu/ae2-crafting-optimizer/issues/79) | 一つのroot内部だけでsigned long境界を超えるexact計画が失われる | 1.5.17 | 1.5.18 | [ISSUE-79.md](issues/ISSUE-79.md) |
| [#90](https://github.com/syarukasu/ae2-crafting-optimizer/issues/90) | 無関係なProvider世代更新でBigInteger計画が提出時に失効する | 1.5.18 | 1.5.20 | [ISSUE-90.md](issues/ISSUE-90.md) |
| [#93](https://github.com/syarukasu/ae2-crafting-optimizer/issues/93) | Java 25でBigInteger在庫Sidecarの可視コピー中にJVMが終了する | 1.5.18 | 1.5.20 | [ISSUE-93.md](issues/ISSUE-93.md) |
| [#98](https://github.com/syarukasu/ae2-crafting-optimizer/issues/98) | 外部CPU登録済みでも合計bytesだけlong超過するBigCapacity計画が拒否される | 1.5.19 | 1.5.20 | [ISSUE-98.md](issues/ISSUE-98.md) |
| [#101](https://github.com/syarukasu/ae2-crafting-optimizer/issues/101) | 外部セルが正確なBigInteger在庫を公開する安定APIがない | 1.5.19 | 1.5.20 | [ISSUE-101.md](issues/ISSUE-101.md) |
| [#102](https://github.com/syarukasu/ae2-crafting-optimizer/issues/102) | 小規模ジョブが初回probeへ縮小されPattern Provider配送が遅延する | 1.5.19 | 1.5.20 | [ISSUE-102.md](issues/ISSUE-102.md) |
| [#103](https://github.com/syarukasu/ae2-crafting-optimizer/issues/103) | wide計画の実行裏付け不足がCPU容量不足と誤報される | 1.5.19 | 1.5.20 | [ISSUE-103.md](issues/ISSUE-103.md) |
| [#109](https://github.com/syarukasu/ae2-crafting-optimizer/issues/109) | BigInteger API連携が通常AE2の責務境界を越える | 1.5.20 | 1.5.21 | [ISSUE-109.md](issues/ISSUE-109.md) |
| [#140](https://github.com/syarukasu/ae2-crafting-optimizer/issues/140) | Mekanism入力探索がDedicated Serverでclient-only音声型を毎tick解決する | 1.5.25 | 1.5.26 | [ISSUE-140.md](issues/ISSUE-140.md) |
| [#148](https://github.com/syarukasu/ae2-crafting-optimizer/issues/148) | 同一キーのmounted storage合計がlong境界を超えると端末表示が消える | 1.5.27以前 | 1.5.28 | [ISSUE-148.md](issues/ISSUE-148.md) |
| [#151](https://github.com/syarukasu/ae2-crafting-optimizer/issues/151) | 版統合時にPR #127のBigInteger物理実行基準が失われる | 1.5.25-1.5.27 | 統合PR | [ISSUE-151.md](issues/ISSUE-151.md) |
| [#153](https://github.com/syarukasu/ae2-crafting-optimizer/issues/153) | 同一キーのmounted storage合計がlong境界を超えるとストレージモニターが負数化する | 1.5.28以前 | 1.5.29 | [ISSUE-153.md](issues/ISSUE-153.md) |
| [#179](https://github.com/syarukasu/ae2-crafting-optimizer/issues/179) | worker上の純粋計算中もsimulateForがtickを待たせる。固定4-thread化だけでは解決しない | 2.0.0開発版 | ローカル修正 / Snapshot適格な標準経路も分離。動的経路は対象外 | [ISSUE-179.md](issues/ISSUE-179.md) |
| [#182](https://github.com/syarukasu/ae2-crafting-optimizer/issues/182) | AQE専用実行の削除後、外部CPUから既存物理加工を呼び出す公開経路がない | 2.0.0開発版 | PR / 実機未確認 | [ISSUE-182.md](issues/ISSUE-182.md) |
| 外部コンシューマ回帰 | 実経路でsidecarが消える、またはQuantum Bulkが`maxPatterns=1`へ誤って制限される | 1.5.18系 | 作業中 | [ISSUE-BIGINT-EXTERNAL-CONSUMER.md](ISSUE-BIGINT-EXTERNAL-CONSUMER.md) |

## 2.0.0開発版の再発記録

- [Issue #190](issues/ISSUE-190.md), 2026-09-19 / AQE capacity alignment:
  Byte accounting bounded amount * 8 and unreduced rational numerators before
  unit conversion; tests reproduced rejection despite an in-range final cost.
  Split whole bytes and reduced fractional remainder, ceil once, and keep real
  count/byte limits. Tests cover AQE default and maximum capacities, existing
  reservations, exact fit, one-byte overflow, NBT and physical branch cancellation.
  No AQE capacity ownership change, deployment or live performance claim.

- [Issue #190](issues/ISSUE-190.md), 2026-09-19 / receipt validation:
  Worker snapshots were trusted without checking the returned transaction ID
  and digest. After output credit, cancellation could also release a changed
  receipt. Validate identity on every read and persisted outputs/state before
  acknowledgement retry or receipt removal. Keep waiting for a same-identity
  running worker after a lagging save, without crediting it twice or forgetting
  owned work. Nine contract integration tests
  cover exact completion, unload, cancellation, NBT and faulty receipts on both
  loaders. This is injected-fault evidence, not a reproduced live-server fault.
  Build passed; no deployment or runtime acceptance.

- [Issue #190](issues/ISSUE-190.md), 2026-09-18 / lightweight follow-up:
  fixed singleton inputs used redundant server observations; Forge rebuilt exact
  accounting and persistence on idle ticks. Reuse immutable input semantics with
  adoption revalidation and the existing active-step/revision implementation.
  Also retain polled cancellation steps when another provider is unloaded, and
  share receipt accounting across external CPU reads. Tests cover 10,000 repeated
  reads (one rebuild), bounded cancellation/reload of 1,024 wide steps, missing
  providers, and item/fluid input parity with actual AE2. Runtime acceptance pending.

- [Issue #190](issues/ISSUE-190.md), 2026-09-18 / BigInteger実装:
  複数の製造方法を選んだ巨大計画を、実行時に単一製造方法へ作り直していた。
  固定入力・非循環の作業台分岐について、選択済み回数・投入素材・全余剰を保存し、
  標準AE2と外部物理APIへ同じ計画を渡す。既存のReceipt、取消・復旧の所有者は変更しない。
  Forge通常AE2/UELMは各602件中601成功・任意JAR検査1件未実施、NeoForgeは610件成功。
  未配置。実設備での完了・途中取消・再起動復旧と、加工機械/循環分岐は未確認・未対応。

- [Issue #190](issues/ISSUE-190.md), 2026-09-18: 代替素材、返却容器、独自Pattern形式を
  一律に取得対象外としていた。公開APIの構造取得とサーバー側での入力判定を分離し、
  NBT・耐久値・液体の単位・返却順をAE2に合わせて計算する。
  両版のAE2比較試験とビルドは完了。未配置、実環境の採用率と巨大分岐の物理実行は未確認。

- [Issue #190](issues/ISSUE-190.md), 2026-09-17: 複数の製造候補を一律に拒否して標準計算へ戻る。
  AE2順の候補試行、失敗の巻戻し、読取区間の証明による反復短縮を追加。
  世代変更は新しい在庫とグラフで最大3回試行し、古い在庫の標準計算へ戻さない。
  未取得Patternの実体、数量がlongを超える分岐計画の物理実行、実環境の採用率は未確認。

- [Issue #190](issues/ISSUE-190.md): Emitterで切れる循環の誤拒否、および従来Map計算で
  生成済み副産物を元在庫として数える不具合。局所DAG検証と元在庫の最大不足量へ修正。
  共有素材を持つ単一出力DAGにも順序付き会計を使い、Snapshotの再検証でEmitterを誤拒否しない。
  実レシピの全経路高速化と10秒目標は別の未達成条件として残す。

- [Issue #125](issues/ISSUE-125.md): ACO所有Jobの外側で通常BatchがTaskを削除して隔離される。
  通常実行予算の確定位置で排他化する。Taskの再生成・不一致の握り潰しは禁止。

## 運用

- 修正前に`docs/ISSUE_WORKFLOW.md`を実行します。
- 再発しやすい局所条件だけをJavaコメントへ残します。
- 詳細な履歴はIssue仕様書へ集約し、同じ説明を複数ファイルへ複製しません。
- 新しい回帰を修正したら、この表へIssueと仕様書を追加します。
