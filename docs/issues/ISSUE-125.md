# Issue #125: BigInteger物理クラフト受理後の停止

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/125
- 状態: Implemented
- 対象版: 2.0.0
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: #115、#118、#119、#120、#126、#127、#128、#151、#176

## 問題

標準AE2 CPUへBigInteger計画を投入した後、物理Batchが受理されても完成品が0個のまま
停止することが報告されている。現行コードにはReceipt、Escrow、最終搬入の状態機械があるが、
Issue #176の仮想完走試験はこれらを通らず、Recipe式を直接テスト在庫へ適用している。
したがって、Plannerの数量正当性は検査できてもIssue #125の物理状態遷移は証明できない。

## 再現と証拠

- 再現手順: 標準AE2 CPUで`oak_log -> oak_planks -> oak_button`を
  `Long.MAX_VALUE`個発注し、BigInteger対応入出力セルと汎用
  `CraftingTableBatchTarget`を使用する。
- 境界値: 通常long、入力だけwide、中間だけwide、最終出力だけwide、CPU bytesだけwide。
- 現在の自動証拠: `Issue125RegressionSourceTest`はソース文字列検査であり、状態機械を実行しない。
- 実環境証拠: 未確認。`REGRESSION_MATRIX.tsv`の`PENDING`を維持する。

## 期待結果

実Batchの`OUTPUT_READY` ReceiptだけがEscrowへ一度だけ計上され、acknowledge、forget、
exact storageへの最終搬入、CPU完了同期の順で終了する。取消時は未消費入力と完成済み中間物を
一度だけ返し、再起動またはchunk unload後も同じTransaction IDから復旧する。

## 現在結果

本体コードは上記順序を実装しているが、自動試験はPlanner結果と個別会計部品しか検査していない。
実ゲームでIssue #125が再発しないことは未証明である。

## 所有権

- AE2が所有する状態: CPU選択、job提出、通常long実行、Gridとstorageのlive状態、完了同期。
- ACOが所有する状態: wide計画、物理Tree Transaction、Receipt照合、Escrow、exact境界mutation。
- 任意アドオンが所有する状態: 実Worker、電力、Recipe実行、物理出力Receipt。
- fallback可能な境界: ACOが物理入力を予約する前の通常long計画だけAE2へ戻せる。

## 維持する不変条件

- 計画値から完成品を生成しない。
- `OUTPUT_READY`の実出力を検証してから一度だけEscrowへ計上する。
- 最終出力はEscrowに実在する量だけexact storageへ搬入する。
- 同じReceiptの再読込、acknowledge、forgetで二重計上しない。
- ACOからAQE、InsaneAE、AAC固有のCPU実行処理へ介入しない。

## やってはいけないこと

- 仮想試験でPlanの最終出力を直接在庫へ追加して完走扱いにする。
- 待機、容量、内部失敗を別の診断理由へ読み替える。
- wide値を`Long.MAX_VALUE`へ丸める。
- 実環境証拠なしにIssue #125を解決済みまたは2.0.0を完成扱いにする。

## 修正方針

既存の位置独立wide試験を、境界入力予約、Receipt遷移、Escrow会計、最終搬入を順に通す
小さな仮想fixtureへ変更する。これはMinecraft worldや実Targetを使用しないため、実ゲーム証拠とは
区別する。本体状態機械はfixtureが具体的な不整合を示した場合だけ最小修正する。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] 関連クラスと既存試験を読んだ
- [x] 再現条件を試験へ変換した
- [x] 所有権とfallback境界を確定した
- [x] 禁止事項を明記した
- [x] Forge/NeoForgeの適用範囲を確定した

## 試験計画

- 単体試験: wide Planner結果をReceipt、Escrow、最終搬入の順で仮想実行する。
- 境界試験: 入力、中間、最終出力がそれぞれ単独でlongを超える既存ケースを維持する。
- 故障・取消・復旧試験: Receipt取消とEscrow返却、既存NBT codec/recovery試験を実行する。
- ビルド: 両版の関連JUnit、回帰manifest、`clean build --no-build-cache`。
- GameTestまたはユーザー側確認: 実Target、chunk unload、再起動、CPU完了同期は`PENDING`。

## 実装結果

- `OverflowPromotingCraftingPlannerTest`の位置独立wide注文を、境界入力予約、段ごとの
  Receipt遷移、Escrow会計、acknowledge、forget、Escrow由来の最終搬入まで通すfixtureへ変更した。
- 同一素材を二つの入力slotで消費し、その合計だけがlongを超える注文を追加した。
- `ExactCraftingEscrowTest`で、実行中Receiptの取消、予約入力返却、forgetを一続きに検査した。
- Java本体の状態機械には変更を加えていない。今回の仮想経路から具体的な本体不整合は出なかった。
- `README_ja.md`を、Mekanismのrecipe cacheと探索へACOが介入しない現行責務へ合わせた。

## 検証結果

- Forge 1.20.1 / Java 17: `clean build --no-build-cache`成功、114 suites、483 tests、
  失敗0、エラー0、skip 2。
- NeoForge 1.21.1 / Java 21: `clean build --no-build-cache`成功、122 suites、503 tests、
  失敗0、エラー0。
- 両版: `verifyIssueRegressionManifest`、`git diff --check`成功。
- 仮想完走: 入力wide、同一素材slot合算wide、中間wide、最終出力wideでReceipt/Escrowが
  終端まで進み、残Task 0、waitingFor 0、remainingOutput 0、Escrow 0を確認した。
- 未確認: 実Block Entity Target、実exact cell、CPU完了packet、chunk unload、server restart。
  `REGRESSION_MATRIX.tsv`のIssue #125は`PENDING`を維持する。

## 完了

- PR:
- マージコミット:
- 修正版:
- リリース:
