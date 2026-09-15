# ACO チーム開発仕様

この文書はACOの変更を始める際の実務ルールです。最上位の目的と禁止事項は
`PROJECT_CHARTER.md`、クラス単位の責務は`CLASS_RESPONSIBILITIES.md`を正本とします。

## 開発目的

ACOはAE2の結果を変えず、巨大自動クラフト環境の重複計算と一tick集中負荷を減らす
最適化・exact数量基盤です。

- Pattern/recipe/provider世代ごとの構造を再利用する
- 一回の計算内で同じ問い合わせを繰り返さない
- 証明できる経路だけ数式Plannerで計算する
- `long` overflow時だけ同じ計画を`BigInteger`へ昇格する
- 標準AE2 CPUの投入量を実測予算で制御する
- 外部MODへ不変なBigInteger/V2/crafting-table/vector APIを提供する
- 辞退、停滞、回復不能状態を正しい理由で診断する

## 責務境界

### AE2

- 通常レシピ適格性と候補選択
- 通常Provider、CPU Job、リンク、在庫、GUI
- ACOが所有権を取らなかった通常実行

### ACO

- bounded cacheと世代
- immutable compiled graphと計算内memo
- checked long/BigInteger計画とsidecar
- 標準AE2 CPUに限定したexact transaction/escrow
- 標準AE2実行予算
- 公開APIと診断

### 外部MOD

- 固有CPU/Worker/機械/構造
- 電力、進捗、取消、保存、Receipt、完了
- ACO APIを利用するadapter

ACOから外部MODのprivate状態を実行台帳として所有しません。Advanced AEやNeoECOへ
残すMixinは、状態を保持しない実行予算hookまたはread-only hintだけです。

## 実装開始手順

1. `PROJECT_CHARTER.md`と`REGRESSION_HISTORY.md`を読む。
2. 対象Issueと過去の同種Issueを確認する。
3. `docs/issues/ISSUE-<番号>.md`へ問題、再現、正本、所有権、禁止事項、試験を書く。
4. `CLASS_RESPONSIBILITIES.md`で変更対象の既存責務を確認する。
5. 正常時と異常時の状態遷移をコード上で比較する。
6. 原因が確定してから最小変更を実装する。
7. Issue固有試験、全JUnit、clean build、静的検査を両ローダーで通す。
8. UTF-8 body fileから版別Draft PRを作る。

## コード規約

- 深い`if`ネストを避け、異常条件はガード節で返す。
- 新しい`if`と`for`には、分岐・反復の目的が分かる日本語コメントを書く。
- マジックナンバーには単位と根拠を書く。
- async処理へWorld、Grid、Block Entity、mutable inventoryを渡さない。
- async境界ではimmutable snapshotとgenerationを明示的にcaptureする。
- `BigInteger`正本を`longValue()`、飽和値、指数表示文字列から復元しない。
- cacheにはkey、上限、TTLまたはgeneration、invalidate条件を必ず持たせる。
- Mixin対象は実JARでdescriptorを確認し、失敗時は機能単位でfail-closedにする。

## Fallback規則

Fallbackできるのは入力・Job実行の所有権を取得する前だけです。

```text
proof不足 -> AE2標準計算
所有権取得 -> resume / exact cancel / quarantine
```

例外を隠すretry、`+1`注文、飽和long、旧API、legacy pathを追加してはいけません。
未対応を正常扱いするのではなく、未対応理由を明示して所有権取得前に辞退します。

## 計画最適化

- compiled graphはPattern構造だけを保持し、在庫や注文固有状態を埋め込まない。
- cache keyにはprovider/recipe generationとroot identityを含める。
- 一時的な在庫Snapshotは一計算内だけ共有する。
- タグ、代替候補、返却物、触媒、循環、動的出力を証明できなければAE2へ返す。
- Shadow modeは結果比較だけを行い、AE2の結果を上書きしない。
- Authoritative採用後も計画公開前に世代を再検証する。

## 実行最適化

- 実行予算はCPU容量や表示値を変更しない。
- 予算計測は対象処理開始時から行う。
- 待機中の仕事に有効操作予算を消費させない。
- V2は登録adapterがない時、mutable Job状態を読む前に終了する。
- V2 targetはprepare/commit/reconcile/rollbackをdurable receiptで証明する。
- Pattern Batch V1、独立Fair Scheduler、内蔵Native Batchは存在しない。

## Optional Integration

Recipe Intentは候補を先に試すための短命hintです。GTCEu/Mekanismが最終検証と実行を
所有します。ACOはmachine tick、ParallelLogic、CachedRecipe、tank内容を書き換えません。

Circuit Cutter、Reaction Chamber、AE2 Overclock、Assembly Matrix cacheはimmutableな
入力または一tick状態へ限定し、入力・構造・resource generation変更で失効させます。

## 削除済み機能

次は互換性目的でも戻しません。

- Pattern Batch V1
- AQE/Advanced AE外部CPU実行manager
- GTCEu/Mekanism内蔵Native Batch
- 独立Fair Scheduler
- 端末、同期packet、storage watcher、Bus、IO Port、P2P、Grid Tickの書換え
- first-missing打切りと在庫量によるPattern並べ替え
- 実装を持たない旧Config key

必要なら別MODまたは新Issueとして、責務と回帰試験を先に定義します。

## 必須検証

- 通常小規模クラフトの結果一致
- 素材不足simulationの全不足量一致
- long境界とoverflow昇格
- stale generationとcache invalidation
- 同時Futureの戻り値とcancel所有権
- V2部分失敗、再起動、取消、二重計上防止
- 外部adapter未登録時の完全非介入
- Mixin JSON、catalog、Config、責務一覧の一致
- Forge 1.20.1 / NeoForge 1.21.1の`clean test`と`clean build`

起動、World参加、実クラフト、TPS/MSPTは別のruntime acceptanceです。ビルド成功だけで
確認済みと書きません。

## GitHub運用

- 原則としてIssue先行、ローダー別Draft PRとする。
- PR本文はUTF-8ファイルから投稿し、文字化けと文字列`\\n`をAPIで再確認する。
- 既存の未コミット変更を破棄しない。
- previewは実環境受入れまでIssueとDraft PRを閉じない。
- 外部プロジェクトへ無断でpush/PRしない。
