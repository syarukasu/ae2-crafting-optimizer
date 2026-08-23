package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class RoundRobinSlotWindowTest {
    /** AE2 IO Portの入力セルスロット数。 */
    private static final int IO_PORT_SLOTS = 6;

    @Test
    void twoSlotWindowsVisitEveryIoPortSlotInThreeTicks() {
        int cursor = 0;
        var visited = new HashSet<Integer>();
        // 2スロットずつ3tick処理し、6スロット全部へ到達することを確認する。
        for (int tick = 0; tick < 3; tick++) {
            for (int index = 0; index < 2; index++) {
                visited.add(RoundRobinSlotWindow.map(cursor, index, IO_PORT_SLOTS));
            }
            cursor = RoundRobinSlotWindow.advance(cursor, 2, IO_PORT_SLOTS);
        }
        assertEquals(IO_PORT_SLOTS, visited.size());
        assertEquals(0, cursor);
    }
}
