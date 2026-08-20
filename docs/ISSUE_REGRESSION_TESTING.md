# Issue回帰試験台帳

ACOでは、GitHub Issueを単なる作業履歴ではなく、再発を防ぐ試験仕様として扱います。
機械可読な正本は`docs/issues/REGRESSION_MATRIX.tsv`です。

## 試験レベル

| レベル | 証明できること | 証明できないこと |
|---|---|---|
| `STATIC` | 依存範囲、Mixin定義、リソース、文書化された契約 | 実際のMinecraftライフサイクルとGUI操作 |
| `UNIT` | 算術、台帳、永続形式、境界分岐、純Java契約 | AE2が実際に呼ぶ順序、チャンク、ネットワーク同期 |
| `GAMETEST` | AE2/アドオンを読み込んだゲーム内の再現手順 | 実プレイヤーGUI、長時間負荷、Arclight固有挙動 |
| `RUNTIME` | 指定環境でのGUI、再起動、負荷、クライアント/サーバー相互作用 | 未試験の別構成すべて |

静的検査だけで、過去Issueの全症状が再発しないとは証明できません。通常の`build`は
台帳の構造と自動試験を検証し、実機証拠が必要なIssueは専用リリースゲートが止めます。

## コマンド

台帳、証拠パス、全JUnitを検証します。

```powershell
.\gradlew.bat verifyIssueRegressionManifest --no-daemon
```

「過去Issueの必要試験をすべて確認済み」として公開可能か判定します。

```powershell
.\gradlew.bat verifyIssueRegressionReleaseReadiness --no-daemon
```

`runtime_status=PENDING`が一件でもあれば、Issue番号と必要レベルを表示して失敗します。
この失敗を無視して「再発なし」「完全検証済み」と表現してはいけません。

## 現在の未確認項目

次のIssueはGameTestまたは実機試験が必要です。

- #30 AE2端末のクライアントクラッシュ
- #32 AE2 UELM実環境互換
- #51 Advanced AE会計Mixinの除外
- #74 Pattern ProviderラウンドロビンGameTest
- #93 Java 25でのネイティブクラッシュ
- #102 Forge側ラウンドロビンGameTest
- #109 通常AE2の在庫・GUI境界
- #115 AE2標準経路の正確な物理実行
- #118 Exact実行の自己隔離
- #119 停止時のExact Job NBT保存
- #120 Advanced AE Mixin起動境界

## 新しいIssueを追加する時

1. `docs/issues/ISSUE-<番号>.md`へ再現、禁止事項、期待値を記録します。
2. `REGRESSION_MATRIX.tsv`へIssue番号を昇順で追加します。
3. 必要な最小試験レベルを選び、実在する自動証拠を登録します。
4. GameTestまたは実機確認前は`PENDING`のままにします。
5. 確認後だけ`VERIFIED`へ変更し、ログ、Issueコメント、試験結果のURLまたはファイルを登録します。
6. `synchronized_through_issue`を台帳に含めた最大Issue番号へ更新します。

Release PRは`verifyIssueRegressionReleaseReadiness`の結果を本文へ記載します。起動試験を
行わない作業では、ゲートが残した`PENDING`をそのまま未実施として報告します。
