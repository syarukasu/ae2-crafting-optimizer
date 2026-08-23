package com.syaru.ae2craftingoptimizer.optimization;

/** IO Portの固定スロットを、永続cursorから公平に巡回する純粋計算。 */
public final class RoundRobinSlotWindow {
    private RoundRobinSlotWindow() {
    }

    public static int map(int cursor, int windowIndex, int slotCount) {
        // 破損した呼び出し値は補正せず、呼び出し側の通常境界へ返す。
        if (slotCount <= 0 || windowIndex < 0 || windowIndex >= slotCount) {
            return windowIndex;
        }
        return Math.floorMod(cursor + windowIndex, slotCount);
    }

    public static int advance(int cursor, int inspectedSlots, int slotCount) {
        // スロットが存在しない場合はcursorを進めない。
        if (slotCount <= 0) {
            return cursor;
        }
        return Math.floorMod(cursor + Math.max(0, inspectedSlots), slotCount);
    }
}
