# Issue #102: 小規模クラフトの初回プローブによる配送遅延

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/102
- 関連Issue: #74
- 状態: Reopened / fixed in PR #124
- 対象版: 1.5.20 / 1.5.23回帰修正
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

## 1.5.23での再発

InsaneAE診断GameTestで、ACOなしは既知の`ae2.import_from_cauldron`だけが失敗するのに対し、
ACO 1.5.23ありでは次の4件が追加で失敗しました。

- `ae2.pattern_provider_faces_round_robin`
- `ae2.insaneae_pattern_provider_patterns`
- `ae2.insaneae_crafting_batch`
- `ae2.insaneae_crafting_batch_chain`

原因は二つです。

1. ACOの実行予算Redirectが、同じAE2境界を所有する専門アドオンより先に適用されていた
2. ACOが内部更新時機を証明できない外部Pattern Providerまで、同一tick通知の間引き対象にしていた

ACOの実行予算Mixinは優先度900で専門実装へ譲り、実際に予算フックを所有できなかった場合は
`executeCrafting`を再制限しません。Provider通知の間引きは`appeng.*`実装だけへ限定し、
外部Providerの通知は即時通過させます。

InsaneAE固有の計算バッチ2件は、InsaneAE側がACO profile active時に自身のバッチを止める分岐を
外した状態で再検証しました。ACOはBigInteger APIを提供するだけで、外部MODの計算バッチを
所有しません。

検証記録: [ISSUE-102-FORGE-1.20.1.md](evidence/ISSUE-102-FORGE-1.20.1.md)
