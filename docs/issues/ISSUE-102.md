# Issue #102: 小規模クラフトの初回プローブによる配送遅延

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/102
- 関連Issue: #74
- 状態: Implemented
- 対象版: 1.5.20
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1

## 問題

初回の実行単価が未計測だと、設定済みの一波上限へ収まる小規模ジョブまでprobe件数へ
縮小されます。この余計なtick分割により、AE2標準Pattern Providerの複数面ラウンドロビンが
本来のテスト時刻までに完了しません。

## 不変条件

- Pattern Providerの面選択と順序はAE2へ委譲する
- 一波上限を超える巨大ジョブは従来どおり時間予算で分割する
- CPU容量、レシピ、投入先、クラフト結果を変更しない

## 修正

要求操作数が設定済みの最大一波へ収まる場合は、初回probeへ縮めず全件をAE2へ渡します。
上限を超える要求だけがprobeと実測時間予算の対象です。

## 試験

- 未計測、要求10、probe 2、一波上限65,536で10操作を維持
- 巨大要求は従来のprobe上限を維持
- 両版のJUnitと`clean build`
