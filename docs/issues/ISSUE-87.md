# Issue #87: ACO全クラスの責務を明文化し安全にリファクタリングする

## 状態

`Verified`

## 対象

- Forge 1.20.1: `mc/1.20.1`
- NeoForge 1.21.1: `mc/1.21.1`
- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/87

## 問題とユーザー影響

ACOは計画、BigInteger会計、物理クラフト、Batch、tick予算、任意MOD連携、通信、診断を
含むが、全クラスの責務と依存方向を一望できる正本がない。修正者が名前だけで所有権を
推測すると、Mixinへ業務ロジックを戻す、外部CPUの仕事をACOへ取り込む、同じ検証を
別実装する、過去回帰の禁止条件を見落とす、といった再発につながる。

## 調査結果

- Forge版はJava source 348件。`package-info.java`を除く本番型は328件。
- NeoForge版はJava source 374件。`package-info.java`を除く本番型は354件。
- 最大クラスは`PhysicalCraftingTreeTransaction`で、状態遷移、Receipt、会計、永続化、
  Pattern identity、scheduler判断を一つに保持する。
- `ACOConfig`は約1,800行だが、Config keyと説明を一か所へ固定する責務自体は一貫する。
- `AqeBigCraftingExecutionManager`は外部Host連携の高リスク境界であり、起動試験なしの
  大分割は今回行わない。
- 正の`BigInteger`数量Mapの複製、検証、包含判定が`ExactCraftingEscrow`と
  `PhysicalCraftingTreeTransaction`へ重複している。

## 期待結果

- `docs/CLASS_RESPONSIBILITIES.md`から、各本番クラスの役割と所属レイヤーを確認できる。
- 新規クラスを追加して責務表を更新し忘れると自動試験が失敗する。
- BigInteger数量Mapの不変条件を一つの副作用なしヘルパーへ集約する。
- 既存挙動、公開API、Config、NBT、Packet、Mixin注入位置は変わらない。

## 所有権

- AE2は通常クラフト、Provider、CPU Job、通常在庫操作を所有する。
- ACO engineは計画とexact数量を所有する。
- `PhysicalCraftingTreeTransaction`は所有権移転後のEscrow、Receipt、取消、復旧を所有する。
- 外部CPUアドオンは構造、実行、GUI、電力、進捗を所有する。
- Mixinは入口とAccessorだけを持ち、計算・会計ロジックを所有しない。

## 維持する不変条件

- 正の数量Mapはnull key、null amount、0、負数を受理しない。
- Mapの反復順序を保存する。
- 物理取引の最大キー件数65,536件を維持する。
- Escrowには今回新しいキー件数上限を追加しない。
- `containsAll`はMapを変更しない。
- 加算は`BigInteger`のまま行い、`long`へ変換しない。
- 空Mapを許可する呼出元と禁止する呼出元の既存契約を維持する。

## やってはいけないこと

- BigInteger正本を`long`へクランプ、飽和、切り捨てする。
- 所有権移転後にAE2標準経路へfallbackする。
- NBT schema、Config key、network protocol、公開APIを今回変更する。
- Mixin対象または注入位置を広げる。
- 外部MODのCPU、構造、実行、GUI、レシピ、テクスチャをACOへ移す。
- 巨大クラスを小さく見せるためだけに、相互依存する断片へ機械的に分割する。

## 修正方針

1. `docs/CLASS_RESPONSIBILITIES.md`へレイヤー、依存方向、主要所有権、全本番クラスの
   一行責務、巨大クラスの今後の分割境界を記録する。
2. `ClassResponsibilitiesDocumentationTest`を追加し、各本番Java型が文書へ一度以上
   掲載されることと空の責務説明がないことを検査する。
3. package-privateな`ExactCountMap`を追加し、正数Mapの検証、順序付き複製、包含判定、
   正確な加算だけを担当させる。
4. `ExactCraftingEscrow`と`PhysicalCraftingTreeTransaction`は同ヘルパーへ委譲する。
5. `ExactCountMapTest`で境界値、順序、不変性、上限、空Map契約を固定する。
6. 3,000行超クラスの追加分割は、責務表にリスクと推奨境界を残し、別Issueで行う。

## fallback境界

今回の変更は計画または実行経路を追加しない。既存fallback条件には触れない。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] Issue #87を作成した
- [x] 両ローダーのクラス数と最大クラスを調査した
- [x] 公開API、永続形式、Mixinを変更しない範囲を確定した
- [x] 状態を`Ready`へ変更した

## 試験計画

- `ExactCountMapTest`
- `ClassResponsibilitiesDocumentationTest`
- 既存JUnit全件
- `git diff --check`
- Forge 1.20.1 `clean build`（通常AE2および可能ならUELM）
- NeoForge 1.21.1 `clean build`
- 起動、GameTest、ゲーム内試験は実施しない

## 実装結果

- `docs/CLASS_RESPONSIBILITIES.md`へ、目的、禁止事項、依存方向、パッケージ責務、
  大規模クラスの分割判断、全本番トップレベル型328件の一行責務を記録した。
- `tools/update-class-responsibilities.ps1`を追加し、責務一覧をsourceから再生成可能にした。
- `ClassResponsibilitiesDocumentationTest`を追加し、sourceと文書の型集合、重複、空説明、
  placeholder、ローカルパス混入を検査するようにした。
- `ExactCountMap`へ、正のBigInteger数量Mapの検証、順序付き複製、exact加算、包含判定を
  集約した。
- `ExactCraftingEscrow`と`PhysicalCraftingTreeTransaction`の重複実装を同ヘルパーへの
  委譲へ置換した。公開API、Config、NBT、Packet、Mixin descriptorは変更していない。
- README、CONTRIBUTING、AGENTS、Issue手順から責務一覧を必須読了文書として参照した。

## 試験結果

- `git diff --check`: 成功
- 対象JUnit 10件: 成功
- Forge 1.20.1 upstream AE2 `clean build`: 成功
- Forge 1.20.1 UELM `clean build`: 341件中失敗0、skip 2
- NeoForge 1.21.1: 別PRの同義変更で363件中失敗0
- 起動、GameTest、ゲーム内試験: 指示により未実施
- リリースとバージョン更新: 内部整理のため実施しない

## Pull Requests

作成前。Forge 1.20.1とNeoForge 1.21.1を別PRにする。
