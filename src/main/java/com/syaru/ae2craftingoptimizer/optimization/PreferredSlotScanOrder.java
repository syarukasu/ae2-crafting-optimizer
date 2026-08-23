package com.syaru.ae2craftingoptimizer.optimization;

/**
 * 前回成功した外部スロットを先頭へ移す、全単射の走査順序。
 *
 * <p>Issue #74/#109で問題になった搬送処理の置換は行わず、AE2が読むスロット番号だけを
 * 並べ替える。0..slotCount-1の全スロットを一度ずつ返すことが契約である。
 */
public final class PreferredSlotScanOrder {
    private PreferredSlotScanOrder() {
    }

    public static int map(int scanIndex, int preferredSlot, int slotCount) {
        // 不正な外部Inventory情報では順序を変えず、AE2本来の境界検査へ委ねる。
        if (scanIndex < 0 || scanIndex >= slotCount || slotCount <= 0) {
            return scanIndex;
        }
        // 成功履歴が現在のInventory範囲外なら、通常の先頭走査へ戻す。
        if (preferredSlot < 0 || preferredSlot >= slotCount) {
            return scanIndex;
        }
        // 走査先頭だけを前回成功スロットへ差し替える。
        if (scanIndex == 0) {
            return preferredSlot;
        }
        // preferredより前のスロットは一つ後ろへずらし、重複と欠落を防ぐ。
        if (scanIndex <= preferredSlot) {
            return scanIndex - 1;
        }
        return scanIndex;
    }
}
