# AE2 Crafting Optimizer 1.5.26

## English

### Fixed

- Fixed dedicated-server log spam from Mekanism Recipe Intent optimization.
- ACO now filters fields by the `inputHandler` naming contract before resolving
  their Java types. This prevents Mekanism's client-only `SoundInstance` field
  from being resolved on a dedicated server.
- Prevented failed `ClassValue` initialization from being retried for every
  affected machine on every tick.
- Recipe candidates, input priority, recipe validation, and the standard
  Mekanism fallback path remain unchanged.

### Compatibility

- Forge 1.20.1: Applied Energistics 2 15.4.x and AE2 UELM 15.5.x.
- NeoForge 1.21.1: Applied Energistics 2 19.2.x.

## 日本語

### 修正

- Mekanism Recipe Intent最適化がDedicated Serverのログを大量に汚染する問題を修正しました。
- Javaの型を解決する前に、フィールド名が`inputHandler`規約へ一致するか判定します。
  これにより、Mekanismのクライアント専用`SoundInstance`フィールドをDedicated Serverで
  読み込まなくなります。
- `ClassValue`の初期化失敗が、対象機械ごとに毎tick再試行される問題を解消しました。
- レシピ候補、入力優先順、レシピ成立判定、Mekanism標準Fallbackは変更していません。

### 対応環境

- Forge 1.20.1: Applied Energistics 2 15.4.x / AE2 UELM 15.5.x
- NeoForge 1.21.1: Applied Energistics 2 19.2.x
