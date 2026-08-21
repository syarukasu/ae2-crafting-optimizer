# Issue #102 Forge 1.20.1 GameTest evidence

- Date: 2026-08-21
- Minecraft: 1.20.1
- Forge: 47.4.20
- AE2: 15.4.10
- ACO metadata version: 1.5.23 with PR #124 fixes
- InsaneAE diagnostic branch: `diag/aco-bigint` at `0b1fa84`
- Command: `gradlew runGameTestServer -PwithAco=true --no-daemon`

## Baseline

ACOなしでは67件中、既知の`ae2.import_from_cauldron`だけが失敗しました。

## Before fix

ACOありでは、baselineに加えて次の4件が失敗しました。

- `ae2.pattern_provider_faces_round_robin`
- `ae2.insaneae_pattern_provider_patterns`
- `ae2.insaneae_crafting_batch`
- `ae2.insaneae_crafting_batch_chain`

## After fix

ACO側の実行所有権ガードと外部Provider即時通知、診断用InsaneAE側のACO profile defer削除を
組み合わせると、追加失敗は0件になりました。失敗はbaselineと同じ
`ae2.import_from_cauldron` 1件だけです。

同じ実行で、`CraftingService.beginCraftingCalculation`実経路のBigInteger計画、
正確容量`10706000000000000024 B`、計画sidecarの存在も確認しました。
