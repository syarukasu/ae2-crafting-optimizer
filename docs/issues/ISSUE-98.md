# Issue #98: 外部BigInteger CPUがBigCapacity計画をCPU_TOO_SMALLで拒否される

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/98
- 状態: Implemented
- 対象版: 1.5.19
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: Issue #44、Issue #55、PR #96、PR #97

> 1.5.21ではIssue #109の責務分離により、標準AE2 CPU提出Mixinそのものを削除した。
> 本文は1.5.19時点の障害と修正履歴として保持し、現在の実装根拠には使用しない。

## 問題

形成済みの外部BigInteger CPUがACOへコンシューマ登録されていても、個別数量は`long`内、
合計CPU bytesだけが`Long.MAX_VALUE`を超える注文が`CPU_TOO_SMALL`で拒否されます。

## 再現と証拠

- 注文: `1,000,000,000,000,000,000`個の単一成果物
- 計画型: `BigCapacityCraftingPlan`
- 外部コンシューマ登録ログ: 出力済み
- 結果: `CPU_TOO_SMALL`

## 原因

`CraftingCpuClusterBigCapacityGuardMixin`が提出可能なexact計画を
`Ae2CraftingPlanSidecars.bigInteger(plan)`だけで判定していました。このAPIは個別数量まで
`long`を超えた`BigIntegerCraftingPlan`だけを返し、合計bytesだけ超過した
`BigCapacityCraftingPlan`を返しません。

公開APIの`BigCraftingEngineApi.inspectBigIntegerPlan`は両方を同じ正確なViewとして公開するため、
ガードの局所判定が公開API契約より狭くなっていました。

## 所有権

- AE2: 標準CPUの選択、使用中判定、ジョブ提出
- ACO: exact計画Sidecarと公開View、未対応CPUへの誤投入防止
- 外部CPUアドオン: exact容量比較、実行、進捗、保存、取消

## 維持する不変条件

- 通常long計画はAE2本来の経路へ渡す
- simulation計画を実行へ渡さない
- 外部コンシューマ未登録のwide計画を標準long CPUへ渡さない
- ACOから外部CPUの実装名、構造、実行へ介入しない

## やってはいけないこと

- `BigIntegerCraftingPlan`だけをexact計画として扱う
- 正確なbytesを`Long.MAX_VALUE`へ切り捨てて比較する
- 外部CPUのmod IDをACOへハードコードする

## 修正

wide計画を検出した後、公開APIが非simulationの正確なViewを返せて、かつ外部コンシューマが
登録済みの場合だけ標準CPU保護ガードを通過させます。外部CPU側の容量判定と実行は変更しません。

## 試験

- 公開APIが`BigCapacityCraftingPlan`を正確なViewとして返す既存試験
- CPUガードが公開APIを使い、狭い`bigInteger(plan)`判定へ戻らない契約試験
- Forge 1.20.1 / NeoForge 1.21.1のJUnitと`clean build`
- 実環境の再注文はユーザー側確認
