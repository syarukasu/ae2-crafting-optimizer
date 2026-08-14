# Issue #90: 無関係なProvider更新で全BigInteger計画が提出時に失効する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/90
- 状態: In Progress
- 影響版: ACO 1.5.18
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue: #44、#64、#79

## 問題

正確なBigInteger計画を作成できても、計算後からCPU提出までにネットワーク内の
無関係なPattern Providerが一件更新されると、全Provider共通の世代番号が変わります。
`BigIntegerCraftingPlan`と`BigCapacityCraftingPlan`はこの全体世代だけを比較するため、
注文が実際に使うPatternが一つも変わっていなくても`INCOMPLETE_PLAN`で拒否されます。

## 再現と証拠

- ACO 1.5.18、AQE BigInteger CPU、BigInteger在庫セルを使用
- 20段の決定的な3x3圧縮計画を計算
- 確認画面を開いている間に別Providerの内容または接続状態が更新される
- 開始時に`Couldn't submit crafting job ... INCOMPLETE_PLAN`となる
- 実ログでは同じ注文が4回連続で`INCOMPLETE_PLAN`になった
- BigInteger backend、AQE/InsaneAE profile、compiled graph、atomic plan、gameplay executionは全て有効

## 期待結果

- 注文が参照する全Patternが現在のCraftingServiceに同じ実体として残る場合は提出できる
- 注文が参照するPattern自身、レシピ、在庫、CPU容量が変わった場合は提出前に拒否する
- 通常long計画とBigInteger計画で、無関係なProvider更新に対する扱いを不必要に変えない
- BigInteger正本、在庫、欠品、容量、進捗をlongへ丸めない

## 現在結果

`generationsAreCurrent()`が全Provider共通世代の完全一致だけを要求します。大規模ネットワークでは
確認画面を開いている短時間にも世代が進み、BigInteger計画だけが恒常的に提出不能になります。

## 所有権

- AE2: 現在のCraftingService索引、通常long計画、通常CPU提出
- ACO: exact計画、参照Pattern集合、BigInteger sidecar、提出前のexact計画検証
- AQE・InsaneAE: CPU構造と実行
- fallback境界: exact計画の所有権移転前。wide計画を標準long計算へ落とさない

## 維持する不変条件

- レシピ世代が変わった計画は拒否する
- 参照Patternが一件でも現在のCraftingServiceから消えた計画は拒否する
- 無関係なProvider更新だけではexact計画を拒否しない
- Provider再検証は計画に含まれる固有Pattern数にだけ比例させる
- 提出後の取消、保存、進捗会計は変更しない

## やってはいけないこと

- 世代検査を無条件に削除する
- Patternの内容が変わった計画を許可する
- BigInteger計画をAE2標準long計画へフォールバックする
- AQEまたはInsaneAEのCPU実行ロジックへ新しい介入を追加する
- 全Providerを再走査して提出処理を重くする

## 修正方針

1. 同一の全体世代なら従来どおり即時に有効と判定する。
2. 全体世代だけが変わった場合、計画が使う各`IPatternDetails`について、主出力の
   `ICraftingService#getCraftingFor`に同一参照が残るかを再検証する。
3. レシピ世代が変わった場合は再検証で救済せず拒否する。
4. BigInteger計画とBigCapacity計画で同じ検証器を共有する。
5. 拒否理由を診断へ記録し、`INCOMPLETE_PLAN`の原因を区別できるようにする。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] 関連クラスと既存試験を読んだ
- [x] 再現条件を試験へ変換した
- [x] 所有権とfallback境界を確定した
- [x] 禁止事項を明記した
- [x] Forge/NeoForgeの適用範囲を確定した

## 試験計画

- 同一世代は再走査なしで成功
- 無関係なProvider世代更新後も、全参照Patternが残れば成功
- 参照Pattern削除後は失敗
- 参照Pattern置換後は、内容が同じでも古い実体を実行せず失敗
- レシピ世代更新後は失敗
- Forge 1.20.1 / NeoForge 1.21.1の全JUnitと`clean build`

## 実装結果

- `ExactPlanPatternRevalidator`を追加した。
- 計画時と現在のレシピ世代が異なる場合は従来どおり拒否する。
- Provider世代が一致する場合は索引を再走査しない。
- Provider世代だけが異なる場合は、計画に含まれるPatternを主出力ごとにまとめ、
  現在の`CraftingService`索引へ同一参照で残るか再検証する。
- Patternを参照しない在庫完結計画は、無関係なProvider世代更新だけでは拒否しない。
- `BigIntegerCraftingPlan`と`BigCapacityCraftingPlan`の提出判定を共通検証器へ接続した。
- 再検証に失敗した場合は`GENERATION_CHANGED`診断へ具体的理由を記録する。
- 容量比較、在庫会計、Pattern回数、実行Job、取消・保存処理は変更していない。

## 検証結果

- Forge 1.20.1 targeted JUnit: 6件成功
- Forge 1.20.1 full JUnit: 347件中345件成功、2件skip
- Forge 1.20.1 `clean build`: 成功
- NeoForge 1.21.1 targeted JUnit: 6件成功
- NeoForge 1.21.1 full JUnit: 369件成功、失敗・skipなし
- NeoForge 1.21.1 `clean build`: 成功
- Minecraft起動試験: 指示により未実施

## 完了

- Forge PR: https://github.com/syarukasu/ae2-crafting-optimizer/pull/91
- NeoForge PR: https://github.com/syarukasu/ae2-crafting-optimizer/pull/92
- 修正版:
- リリース:
