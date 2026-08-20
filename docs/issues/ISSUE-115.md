# Issue #115: 標準AE2クラスタでBigInteger物理実行を所有する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/115
- 状態: Verified
- 対象版: ACO 1.5.22以降
- 対象ローダー: NeoForge 1.21.1
- 関連Issue・PR: Issue #79、Issue #98、Issue #103、Issue #109

## 問題

ACOの`PhysicalCraftingTreeTransaction`は、正確なBigInteger回数を注文量に比例せず処理できます。
しかし1.5.22では、この物理実行を所有する入口がAdvanced AEの
`AdvCraftingCPUCluster`に限定されています。標準AE2の`CraftingCPUCluster`を使用するCPUは、
ACOのexact計画を受理できても、アドオン側の`Long.MAX_VALUE`窓へ戻るため、回数に比例して
処理時間が増えます。

InsaneAEのQuantum CPUは標準AE2クラスタを使用する一例です。ACOはInsaneAE固有クラスへ
依存せず、標準AE2クラスタが既に受理したexact計画を同じAE2 Job上で物理実行できる必要が
あります。

## 再現と証拠

- 再現手順: 1024桁級のACO exact計画を、標準`CraftingCPUCluster`を使用するBigInteger CPUへ提出する
- 境界値: `8^100`回を含む多段作業台クラフト
- ログ・スタックトレース: 例外ではなく、long窓が順次減るため進捗が実用時間内に完了しない
- 正常だった版: なし。Advanced AE専用の物理経路では注文量非依存で進む
- 失敗する版: 1.5.22

## 期待結果

- 標準AE2クラスタが受理したACO exact計画だけ、同じ`ExecutingCraftingJob`へ正確Sidecarを設置する
- Pattern、waitingFor、最終出力、物理ReceiptをBigIntegerで保存・復元する
- 作業台の固有段数と実設備応答に比例して進み、注文回数には比例しない
- CPU容量、使用中判定、CraftingLink、構造、GUIはAE2またはCPUアドオンが所有する
- 取消時は物理TransactionがEscrowを返却してからAE2本来のJob終了通知を行う

## 現在結果

- ACO物理TransactionはAdvanced AEクラスタだけを列挙する
- 標準AE2 Jobにはexact Sidecarが設置されない
- InsaneAEなど標準クラスタ型CPUは、アドオン固有のlong窓実行へ戻る

## 所有権

- AE2が所有する状態: CPU構造、容量判定、使用中判定、CraftingLink、Job生成、通常long Job
- ACOが所有する状態: ACO exact計画、BigInteger Sidecar、物理Transaction、Escrow、Receipt、復旧
- 任意アドオンが所有する状態: 追加CPUブロック、正確容量の公開と判定、GUI、電力、独自Provider
- fallback可能な境界: 物理Transactionが入力所有権を取得する前だけ待機または提出失敗へ戻せる

## 維持する不変条件

- 通常long計画はAE2の提出・初期抽出・実行をそのまま使う
- 標準AE2クラスタの提出結果をACOから成功へ上書きしない
- 一つのAE2 CPUは一つのJobだけを所有する
- exact計画のTask、waitingFor、最終出力、Receipt Journalは常に一致する
- 物理開始後は通常AE2 executorへfallbackしない
- InsaneAE、AQE、AAC固有クラスを標準AE2アダプタから参照しない

## やってはいけないこと

- 外部consumer登録だけを理由に任意の標準CPUへwide計画を強制提出する
- `Long.MAX_VALUE`窓の反復をACO物理実行として扱う
- BigInteger正本をlongへ変換して容量・残数・完了を判定する
- 実クラフトまたはReceiptなしで最終成果物を生成する
- InsaneAEのQuantum CPU、Task Fusion、構造、GUI、モデル、レシピへ介入する
- 物理入力取得後にAE2標準executorと同じTaskを二重実行する

## 修正方針

1. Advanced AE専用だったexact Job契約を、標準AE2 Jobも実装できる共通契約へ分離する。
2. 標準`CraftingCPUCluster.submitJob`がACO exact計画を既に受理する場合だけ、AE2 Job生成へ
   一回分Facadeを渡し、成功後に元計画の正確Sidecarを同じJobへ設置する。
3. 標準`CraftingCpuLogic`の保存、復元、取消、完了へSidecarを接続する。
4. 標準AE2クラスタ用Managerを追加し、既存`PhysicalCraftingTreeTransaction`をtickする。
5. exact JobだけAE2標準Pattern配送を停止し、通常Jobは完全に素通しする。
6. 物理Targetが存在しない間は入力へ触れず待機し、存在後に開始する。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] 関連クラスと既存試験を読んだ
- [x] 再現条件を試験へ変換した
- [x] 所有権とfallback境界を確定した
- [x] 禁止事項を明記した
- [x] Forge/NeoForgeの適用範囲を確定した

## 試験計画

- 単体試験: exact Job会計、Facade生成、標準クラスタ登録、通常計画素通し
- 境界試験: `Long.MAX_VALUE`以下、`Long.MAX_VALUE + 1`、`8^100`のTask回数
- 故障・取消・復旧試験: 未開始取消、物理開始後取消、NBT保存復元、会計不一致隔離
- ビルド: `gradlew clean test`、`gradlew clean build`
- GameTestまたはユーザー側確認: 標準AE2クラスタ型CPU、20段作業台クラフト、再起動復旧

## 実装結果

- `ExactCraftingJobAccess`と`ExactCraftingLogicAccess`へ共通契約を分離した。
- 標準AE2 JobへBigInteger Sidecarと正確なTask/waitingFor/remainingOutputを設置した。
- 標準AE2提出が成功した時だけ一回分Facadeから同じ実Jobを初期化するMixinを追加した。
- `Ae2BigCraftingExecutionManager`で既存物理Transaction、Grid予算、世代再検証、
  Receipt照合、取消、隔離、NBT復旧を接続した。
- 標準ManagerはAdvanced AEおよびInsaneAEの実装クラスを参照しない。

## 検証結果

- `gradlew test --no-daemon`: 401件成功、失敗0件
- `gradlew clean build --no-daemon`: 成功
- `git diff --check`: 問題なし
- 生成JAR: `build/libs/aco1.5.23_1.21.1.jar`
- 起動、GameTest、実環境試験: ユーザー指示により未実施

## 完了

- PR: https://github.com/syarukasu/ae2-crafting-optimizer/pull/116
- マージコミット:
- 修正版: 1.5.23 (NeoForge 1.21.1)
- リリース: https://github.com/syarukasu/ae2-crafting-optimizer/releases/tag/v1.5.23
