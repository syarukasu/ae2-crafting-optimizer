# AE2 Crafting Optimizer 1.5.17

## English

- Adds optional Neo ECO AE Extension 20.4.x compatibility for Forge 1.20.1.
- Keeps the existing Neo ECO 20.3.x execution integration intact.
- Selects one version-specific CPU execution Mixin at bootstrap, matching Neo ECO's installed API line.
- Preserves Neo ECO 20.4's own long FastPath, scheduler, storage, power accounting, and crafting results.
- Expands the optional Neo ECO dependency range to `[20.3.0,20.5.0)`.
- Adds bytecode contract tests against the published Neo ECO 20.3.0 and 20.4.0 JARs.
- Verifies AAC 1.0.7 builds against ACO 1.5.17 and Neo ECO 20.4.0 with both upstream AE2 and AE2-UELM profiles.

## 日本語

- Forge 1.20.1版でNeo ECO AE Extension 20.4.xの任意連携に対応しました。
- 既存のNeo ECO 20.3.x実行連携は維持します。
- 起動時に導入版を判定し、API世代と一致するCPU実行Mixinを片方だけ適用します。
- Neo ECO 20.4本来のlong FastPath、Scheduler、ストレージ、電力会計、クラフト結果は変更しません。
- Neo ECOの任意依存範囲を`[20.3.0,20.5.0)`へ広げました。
- 公開Neo ECO 20.3.0/20.4.0 JARに対するバイトコード契約試験を追加しました。
- ACO 1.5.17、AAC 1.0.7、Neo ECO 20.4.0の組み合わせを、通常AE2とAE2-UELMの両プロファイルでクロスビルド検証しました。
