# AE2 Crafting Optimizer 1.5.25

## English

### Fixed

- Wide BigInteger plans approved by ACO can now pass AE2's standard `long`
  CPU-capacity gate. Ordinary AE2 plans keep their original capacity checks.
- Exact capacity reservations can be promoted before an optional add-on
  registers its facade, avoiding false `CPU_TOO_SMALL` results during
  integration startup.
- ACO now proves the exact-storage boundary before taking execution ownership:
  required inputs must be releasable and the final output must be acceptable.
  Plans that ACO cannot own remain available to a registered external
  BigInteger plan consumer.
- Capacity rejection logs now identify the deciding CPU and the exact reason
  an attempted BigInteger capacity promotion was declined.

### Verification

- `clean build` and the complete automated test suite passed with upstream
  AE2 15.4.10 and AE2 UELM 15.5.0 build profiles.

## 日本語

### 修正

- ACOが承認したBigInteger計画が、AE2標準の`long` CPU容量判定を通過できるように
  しました。通常のAE2計画には従来の容量判定をそのまま適用します。
- OptionalアドオンがFacadeを登録する前でもexact容量予約を昇格できるようにし、
  連携初期化中の誤った`CPU_TOO_SMALL`を防止しました。
- ACOが実行所有権を取得する前に、必要入力を解放でき、最終成果物をexact経路へ
  搬入できることを証明します。ACOが所有できない計画は、登録済みの外部BigInteger
  計画Consumerへ引き続き渡せます。
- 容量拒否ログへ、判定したCPUとBigInteger容量昇格を辞退した正確な理由を追加しました。

### 検証

- upstream AE2 15.4.10とAE2 UELM 15.5.0の両ビルドプロファイルで、
  `clean build`と全自動テストが成功しました。
