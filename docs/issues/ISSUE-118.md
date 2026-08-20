# Issue #118: exact在庫変更が正常な空Journalで自己隔離する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/118
- 状態: Verified
- 修正版: ACO 1.5.23再公開ビルド
- 影響版: ACO 1.5.23
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1

## 問題

標準AE2 exact物理実行が入力取得前に`successful exact storage mutation must apply a
positive amount`で隔離される。実変更前のJournal復旧確認が、復旧対象なしを
`ExactStorageMutationResult.success(0)`として返していた。

## 不変条件

- 成功した実在庫変更量は必ず正数とする。
- 復旧対象なしは在庫変更成功として扱わず、次の変更へ進める制御結果とする。
- 復旧不能または状態不明だけ現在の変更を拒否・隔離する。

## やってはいけないこと

- `success(0)`を許可して実変更結果の契約を弱める。
- Journal異常を正常扱いして新しい変更を開始する。
- 隔離済み操作を推測で再実行する。

## 修正

`recoverPendingInterruption`を`Optional<ExactStorageMutationResult>`へ分離した。空は続行可、
値ありは現在の変更を止める結果であり、正常な空Journalは実変更結果を生成しない。
復旧を実施したtickも待機へ戻し、復旧前の古いbefore状態を使った二重変更を防ぐ。

## 回帰試験

- 成功量0は引き続き例外になる。
- 復旧完了経路に`success(BigInteger.ZERO)`が存在しない。
- 復旧を一件でも実施した呼出しは新規変更を重ねず、次tickの再照合を待つ。
- 正常な復旧確認後は実際のprepare/commitへ進む。
