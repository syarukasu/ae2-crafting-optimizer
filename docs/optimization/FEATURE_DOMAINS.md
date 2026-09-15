# ACO機能領域

Issue #164以降、ACOの実装領域は次の5つだけです。`OptimizationFeature`へ存在する機能は、
ConfigまたはMixinの実入口を必ず持ちます。削除済み機能の互換キーは残しません。

| 領域 | ACOが行うこと | ACOが行わないこと |
|---|---|---|
| `PATTERN_PROVIDER` | Pattern順を変えないlookup cache、内容世代、重複refreshの集約 | Providerの配送、対象選択、入力消費、成果物会計 |
| `CRAFTING_PLANNING` | 計算内memo、構造的候補剪定、世代付きcompiled graph、checked算術、Shadow比較 | 証明不能なレシピの近似、欠品の早期打切り、AE2のPattern選択変更 |
| `CRAFTING_EXECUTION` | 標準AE2 CPUの時間・操作予算、AE2本来の逐次投入を波単位で実行、公開V2取引契約 | 外部CPUのJob、進捗、電力、取消、完了を所有すること |
| `BIG_INTEGER` | exact在庫snapshot、wide plan sidecar、標準AE2 exact取引、公開Host/Plan API | exact値をlongへ切り捨てた判定、外部CPUの実行台帳 |
| `OPTIONAL_INTEGRATION` | AppliedE境界、GTCEu Recipe Intent、検証済みadd-on lookup cache | 外部機械のレシピ可否、速度、入出力、構造を変更すること |

## 共通gate

全機能は次の順序で`OptimizationFeatureGate`を通ります。

1. `enableOptimizer`
2. 対応するdomainスイッチ
3. 個別機能スイッチ

どこかで拒否した場合、対象経路はAE2または外部MODの状態へ触れる前にreturnします。
「退役機能をtrueにしても動かさない」という第4の互換層はありません。

## 所有権

- `ACO_CACHE`: 世代・寿命・上限を持つ派生情報。正本変更で破棄します。
- `ACO_TRANSACTION`: ACOが標準AE2 exact取引または公開V2取引を明示的に取得した後の状態。
- `AE2`: 通常クラフト、通常在庫、通常Provider配送、GUI、リンク、CPU Job。
- `EXTERNAL_ADDON`: 外部CPU・機械の構造、実行、電力、進捗、Receipt、完了。

所有権取得前に証明できない高速経路は辞退できます。取得後はAE2へfallbackせず、commit、
rollback、再開、quarantineのいずれかで閉じます。

## 再追加禁止

独立したIssue、所有権設計、故障注入試験なしに、次をACOへ戻してはいけません。

- 端末のslot、packet、表示range、検索結果の置換
- Import/Export Bus、IO Port、Capability、Storage transferの置換
- P2PまたはGrid Tick全体の延期
- 成功済み計画の無条件再利用
- 在庫量によるPattern順変更
- ACO内蔵GTCEu/Mekanism Native Batch
- AQE、InsaneAE、NeoECO固有のJob実行Mixin
