# Feature Ownership

ACOの責務は、AE2の計算結果を変えずに計算・同期・実行を軽量化し、
巨大数量を正確に会計できる共通基盤を提供することです。
特定設備の構造、レシピ、電力、見た目はアドオン側が所有します。

## Active core

- AE2互換のクラフト計算キャッシュ、世代管理、Shadow検証
- Long/BigInteger注文、Execution Window、保存、同期
- Exact Vector計画、Receipt API、実行予算、診断
- CPU/Grid単位の公平な実行予算
- Pattern Provider、Bus、端末、アドオン機械の安全なキャッシュ
- Recipe Intentと検証済みAdapter API

## Compatibility paths

- Compiled Crafting Islands
  - Neo ECO自身が所有する既存Jobを原子的に処理するための互換経路です。
  - Exact Vectorとは別のJob会計へ接続するため、現時点では削除しません。
  - Neo ECO Jobが永続Exact Vector Hostへ移行できた時点で廃止を再検討します。
- Sequential Instant
  - Native/Exact設備を持たない通常ProviderのFallbackです。
  - 一回ずつのAE2会計を維持し、論理回数を一括所有したことにはしません。

## Experimental and opt-in

- Transactional Batch V2
- GTCEu/Mekanism Native Batch Adapter
- Deep AE2 rewrite flags
- Compiled Crafting Islands

これらは独立したConfigで無効化でき、無効時はAE2または対象MODの通常経路へ戻ります。

## Retained no-op compatibility keys

過去Configとの読込互換だけを目的に残すキーは、起動ログで
`compatibility-disabled`と明示します。実行経路として復活させません。

## Boundary with AAC

ACOはAACのController、Pattern Bus、構造、電力式を探索しません。
AACが設備の可用性と所有権を判定し、ACOの公開APIへOffer/Receiptを返します。
