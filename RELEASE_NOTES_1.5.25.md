# AE2 Crafting Optimizer 1.5.25

## English

### Fixed

- Wide BigInteger plans approved by ACO can now pass AE2's standard `long`
  CPU-capacity gate. Ordinary AE2 plans keep their original capacity checks.
- Exact capacity reservations can be promoted before an optional add-on
  registers its facade, avoiding false `CPU_TOO_SMALL` results during
  integration startup.
- Optional integration Mixins are now selected by the target class owned by
  each integration. An unrelated installed add-on no longer suppresses a
  compatible integration Mixin.
- ACO proves the exact-storage boundary before taking execution ownership.
  Unsupported routes remain available to registered external BigInteger plan
  consumers instead of becoming an owned stalled job.

### API

- Added the versioned BigInteger capacity-limit query used by CPU add-ons.
- Added AEKey amount-ledger factories so optional integrations no longer need
  ACO's internal key codec type.

### Verification

- `clean build` and the complete automated NeoForge test suite passed.

## 日本語

### 修正

- ACOが承認したBigInteger計画が、AE2標準の`long` CPU容量判定を通過できるように
  しました。通常のAE2計画には従来の容量判定をそのまま適用します。
- OptionalアドオンがFacadeを登録する前でもexact容量予約を昇格できるようにし、
  連携初期化中の誤った`CPU_TOO_SMALL`を防止しました。
- Optional連携Mixinを、それぞれの連携が所有する対象クラスで選択するようにしました。
  無関係なアドオンの導入によって互換Mixinが抑止される問題を解消します。
- ACOが実行所有権を取得する前にexactストレージ境界を証明します。対応できない経路は
  ACO所有の停止ジョブにせず、登録済みの外部BigInteger計画Consumerへ渡せます。

### API

- CPUアドオン向けのバージョン付きBigInteger容量上限照会APIを追加しました。
- Optional連携がACO内部のキーCodec型へ依存せずに使えるAEKey数量Ledger Factoryを
  追加しました。

### 検証

- `clean build`とNeoForge版の全自動テストが成功しました。
