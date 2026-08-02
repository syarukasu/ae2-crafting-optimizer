# AE2 Crafting Optimizer 1.6.1

## English

ACO 1.6.1 fixes AQE crafting plans whose intermediate count exceeds signed
`long` even for a single finished root.

The proven `BigInteger` plan is now retained as an AQE Exact Vector-only parent
instead of being discarded. Such a parent is never sent through standard AE2
checked-`long` child-window scheduling. When no compatible physical Exact
Vector target is available, the order waits without truncating counts or
throwing `CountOverflowException`.

Regression coverage includes:

```text
9 * 1,590,831,717,672,932,009
= 14,317,485,459,056,388,081
```

Install the same JAR on the server and every client.

## 日本語

ACO 1.6.1では、完成品1個分だけでも中間要求数が符号付き`long`を超えるAQE
クラフト計画を修正しました。

証明済みの`BigInteger`計画を破棄せず、AQE Exact Vector専用の親Jobとして保持
します。この親Jobは通常AE2のchecked-`long`子Windowへ流れません。対応する物理
Exact Vector設備がない場合は、個数を切り詰めたり`CountOverflowException`を出したり
せず、安全に待機します。

次の実報告値を回帰テストへ追加しています。

```text
9 * 1,590,831,717,672,932,009
= 14,317,485,459,056,388,081
```

サーバーと全クライアントへ同じJARを導入してください。
