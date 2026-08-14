# 回帰履歴

この文書は、過去に実際に発生した不具合について、症状、原因、修正、不変条件、
対応する自動試験を残すための記録です。最適化経路を変更する際は、関連する項目を
先に確認してください。

## Issue #79: 一つのroot内部だけでsigned long境界を超える計画が失われる

- 発生日: 2026-08-14
- 影響版: ACO 1.5.17
- 修正版: ACO 1.5.18
- 報告: https://github.com/syarukasu/ae2-crafting-optimizer/issues/79
- Forge 1.20.1修正: https://github.com/syarukasu/ae2-crafting-optimizer/pull/80
- NeoForge 1.21.1修正: https://github.com/syarukasu/ae2-crafting-optimizer/pull/81

### 症状

8個から1個を作る圧縮Patternを21段つなぐと、完成品1個でも最下層素材の需要は
`8^21 = 2^63`になります。ACO 1.5.17では、十分な在庫があってもexact計画を
公開できず、`WidePlanUnavailableException`で計算が失敗しました。

### 原因

`Ae2BigCraftingPlanFactory`が次の二つを同じ条件として扱っていました。

1. BigIntegerで計算したexact計画が正しいこと
2. 完成品個数単位でAE2標準のchecked-long子Jobへ分割できること

一つのroot自体がlongへ収まらない場合、最大root windowは0になります。旧実装は
これを計画失敗として扱い、既に正確に求めたPattern回数、在庫使用量、不足量、
CPU byte数まで破棄していました。

### 修正

exact計画の公開とlegacy root-window実行を分離しました。

- root単位で安全に分割できる場合は`ROOT_WINDOWS`を使います。
- 一つのrootがlongへ収まらない場合は`EXACT_PATTERN_EXECUTOR`を使います。
- Exact方式でもexact sidecarは保持します。
- Exact方式では`rootWindowJob`だけを作りません。
- legacy root-windowコマンドはExact専用計画を誤実行せず、明確に拒否します。

### 維持すべき不変条件

- root windowが0でもexact計画を`null`へ変えてはいけません。
- Exact専用計画を偽の`window=1`へ丸めてはいけません。
- BigIntegerのPattern回数、在庫、missing、CPU byte数をlongへクランプしてはいけません。
- signed long境界未満では従来のroot-window経路を維持します。
- ACOはexact計画と公開APIを提供し、外部CPUアドオンの実行ロジックを置換しません。

### 回帰試験

- `Ae2BigCraftingPlanFactoryRootWindowTest`
  - 21段8倍圧縮、在庫`2^63`: craftable exact plan
  - 21段8倍圧縮、在庫0: missing `2^63`
  - 20段8倍圧縮: 従来のroot-window経路
- `BigCraftingEngineApiPlanInspectionTest`
  - Exact専用計画でも公開APIから正確なPattern回数、在庫、CPU byte数を取得できること

