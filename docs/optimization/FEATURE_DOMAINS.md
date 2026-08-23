# ACO最適化ドメイン

Issue #129以降、最適化は次の八領域へ分けます。個別機能は`OptimizationFeature`へ登録し、`OptimizationFeatureGate`を通過した場合だけ動作します。設定キーだけ残る廃止済み機能は`OptimizationImplementationStatus.RETIRED_COMPATIBILITY_KEY`とし、値が`true`でも実行しません。現在の稼働状況は[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)を正本とします。

| Domain | 目的 | 正本 | 無効時 |
|---|---|---|---|
| `NETWORK_TOPOLOGY` | P2P、経路、grid tick、構造変更通知の重複排除 | AE2 | AE2へ完全委譲 |
| `STORAGE_IO` | Import/Export Bus、IO Port、Capability探索の増分化 | AE2 | AE2へ完全委譲 |
| `PATTERN_PROVIDER` | Pattern索引、世代、refresh、routing再利用 | AE2、ACO cache | cacheを参照しない |
| `CLIENT_SYNC` | 端末、watcher、検索、同期の差分化 | AE2 server state | packetとGUIを変更しない |
| `CRAFTING_PLANNING` | メモ化、compiled graph、checked math | AE2、ACO immutable plan | AE2 plannerへ委譲 |
| `CRAFTING_EXECUTION` | tick予算、公平性、transaction dispatch | AE2またはACO transaction | AE2 executorへ委譲 |
| `BIG_INTEGER` | long超過計画、exact在庫、exact実行 | ACO exact state | 通常long経路へ影響しない |
| `OPTIONAL_INTEGRATION` | GTCEu、Mekanism、Advanced AE等のAdapter | 各アドオン | Adapterを登録しない |

## 共通判定順

1. `general.enableOptimizer`
2. `optimizationDomains.<domain>`
3. 既存の個別設定
4. 互換性・対象クラス・実行時前提

1から3のどこかで無効なら、対象コードは状態を読んで推測せず、書き換えずにreturnします。4で辞退する場合も、ownership取得前だけAE2標準経路へ戻せます。

## Risk

- `LOW`: immutable lookupや純粋な診断。正本を変更しない
- `MEDIUM`: bounded cacheや処理順の調整。世代失効が必須
- `HIGH`: packet、planner、execution、BigInteger、transaction。過去Issueとの対応と専用回帰試験が必須

## 実装クラス

- `OptimizationDomain`: 八つの責務領域
- `OptimizationFeature`: 機能ID、実装状態、risk、正本所有者、失効条件、fallback境界、関連Issue
- `OptimizationFeatureRegistry`: 機能一覧、ID検索、domain別一覧の唯一の正本
- `OptimizationFeatureGate`: master/domain/個別設定の共通判定と固定個数の診断counter
- `ACOConfig`: 設定schema。個別getterから共通gateを呼ぶ
- `ACOServerLifecycle`: server世代ごとの診断初期化とcache破棄
- `ACOStartupReport`: 起動時にdomain状態を列挙
- `MixinFeatureCatalog`: core/performance/clientの全Mixinを機能責務へ結び、未登録Mixinをfail-closedにする

## 禁止境界

- `CLIENT_SYNC`はserver inventory、slot、recipe validityを変更しない
- `STORAGE_IO`はsimulation成功を実transfer成功としてcacheしない
- `CRAFTING_PLANNING`はownershipを取得しない
- `CRAFTING_EXECUTION`はreceiptなしで成果物を生成しない
- `BIG_INTEGER`は正本を`longValue()`、飽和値、指数表示値へ変換しない
- `OPTIONAL_INTEGRATION`は外部アドオン固有処理の所有者にならない
