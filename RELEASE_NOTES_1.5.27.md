# AE2 Crafting Optimizer 1.5.27

## English

### Architecture

- Completed the optimization-domain and safety-gate architecture introduced by Issue #129.
- Added a central feature registry shared by Config, diagnostics, and the complete Mixin catalog.
- Removed the 64-feature diagnostics ceiling. The lock-free diagnostic bit set now grows with the feature registry.
- Added static release gates that reject active features without a Config or Mixin boundary.

### Safety

- Eleven unsafe legacy switches are now explicitly classified as retired compatibility keys.
- Existing TOML files remain readable, but removed mutable inventory, GUI packet, and broad Grid Tick hooks cannot be re-enabled.
- Each retired key records its reason and the active, narrowly scoped optimization that replaces it.

### Compatibility

- Forge 1.20.1: Applied Energistics 2 15.4.x and AE2 UELM 15.5.x.
- NeoForge 1.21.1: Applied Energistics 2 19.2.x.

## 日本語

### アーキテクチャ

- Issue #129で導入した最適化domainと安全gateの設計を完成させました。
- Config、診断、全Mixin台帳が共有する中央機能レジストリを追加しました。
- 診断機能数の64件上限を撤廃し、機能台帳に応じて増えるlock-free bit集合へ変更しました。
- ACTIVE機能がConfigまたはMixin境界へ接続されていない場合、ビルドを失敗させる静的検査を追加しました。

### 安全性

- 危険だった旧11設定を、未完成ではなく廃止済み互換キーとして確定しました。
- 既存TOMLは引き続き読み込めますが、撤去済みの可変在庫、GUI packet、広範囲Grid Tick hookは再有効化されません。
- 各廃止キーへ理由と、現在稼働する限定的な代替最適化を記録しました。

### 対応環境

- Forge 1.20.1: Applied Energistics 2 15.4.x / AE2 UELM 15.5.x
- NeoForge 1.21.1: Applied Energistics 2 19.2.x
