# AE2 Crafting Optimizer 1.4.0

## English

This release adds a generation-cached formula planner for deterministic AE2
crafting graphs. ACO compiles each eligible root into immutable arrays and
evaluates every reachable recipe once in topological order. Planning work is
therefore tied primarily to the number of distinct recipes instead of the
requested amount.

Normal requests use checked `long` arithmetic. Only calculations that overflow
restart from the same immutable inventory snapshot with `BigInteger`. The exact
implementation ceiling is `10^16384 - 1`. NBT, packets, planning, and runtime
accounting enforce the same limit.

Safety remains conservative. Alternative inputs, multiple Patterns, fuzzy
matches, cycles, returns, catalysts, and byproducts fall back to AE2. A compiled
root becomes authoritative only after 64 complete Shadow comparisons with AE2
by default. Provider generation, recipe generation, referenced inventory, and
emitter state are revalidated before accepting a result.

The clean build completed with 176 passing automated tests. Forge client,
dedicated-server, multiplayer, and long-running live-world qualification still
need to be performed by the pack operator. Install the same `1.4.0` jar on the
server and every client.

## 日本語

決定的なAE2クラフトグラフを世代単位で配列プログラムへコンパイルする
数式Plannerを追加しました。対象ルートから到達する各固有レシピを
トポロジカル順に一度だけ処理するため、計算量は注文個数よりも
到達した固有レシピ数へ強く依存します。

通常注文は検査付き`long`で計算し、overflowした計算だけ同じ不変在庫
Snapshotから`BigInteger`で最初から再計算します。実装上の厳密な上限は
`10^16384 - 1`です。NBT、Packet、Planner、Runtimeで同じ上限を適用します。

結果を変えないため、代替入力、複数Pattern、ファジー候補、循環、返却物、
触媒、副産物を含む経路はAE2へ戻します。コンパイル済みルートは既定で
AE2との完全なShadow比較に64回一致した後だけ本番結果として採用します。
採用前にはProvider世代、recipe世代、参照在庫、Emitter状態も再検証します。

クリーンビルドと176件の自動試験は成功しています。Forgeクライアント、
専用サーバー、マルチプレイ、長時間ワールド試験は運用環境での確認が必要です。
サーバーと全クライアントへ同じ`1.4.0` JARを導入してください。
