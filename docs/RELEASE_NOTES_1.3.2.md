# AE2 Crafting Optimizer 1.3.2

## English

This maintenance release fixes an AE2 scrollbar input edge case seen in large
Pattern Access Terminals. If another screen mod consumes the mouse-up event,
AE2's Page Up/Down repeater could continue after the player released the mouse.
ACO now verifies the physical left-button state and stops only the orphaned
repeat.

Normal holding, dragging, wheel scrolling, crafting, item transfer, and
server-side accounting are unchanged. The safeguard is enabled by default and
can be disabled with `fixStuckAe2ScrollbarRepeat`.

Install the same `1.3.2` jar on the server and every client.

## 日本語

大規模なパターンアクセスターミナルなどで、別の画面MODがマウス解放イベントを
消費すると、AE2のPage Up/Down反復だけが残ることがある問題を修正しました。
ACOが物理的な左ボタンの状態を確認し、解放後に残った反復だけを停止します。

通常の長押し、ドラッグ、ホイールスクロール、クラフト、アイテム搬入出、
サーバー側の会計処理は変更しません。安全弁は既定で有効で、
`fixStuckAe2ScrollbarRepeat`から無効化できます。

サーバーと全クライアントへ同じ`1.3.2` JARを導入してください。
