package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PreferredSlotScanOrderTest {
    @Test
    void everyPreferredSlotProducesAPermutation() {
        // AE2の外部Inventoryサイズを広く模擬し、全preferred位置を検証する。
        for (int slotCount = 1; slotCount <= 256; slotCount++) {
            for (int preferred = 0; preferred < slotCount; preferred++) {
                var visited = new HashSet<Integer>();
                // 全走査indexが一度ずつ別スロットへ写ることを確認する。
                for (int scanIndex = 0; scanIndex < slotCount; scanIndex++) {
                    visited.add(PreferredSlotScanOrder.map(scanIndex, preferred, slotCount));
                }
                assertEquals(slotCount, visited.size());
                assertEquals(preferred, PreferredSlotScanOrder.map(0, preferred, slotCount));
            }
        }
    }

    @Test
    void invalidPreferenceKeepsOriginalOrder() {
        // 失効済み履歴では全スロットを通常順で走査する。
        for (int scanIndex = 0; scanIndex < 6; scanIndex++) {
            assertEquals(scanIndex, PreferredSlotScanOrder.map(scanIndex, 99, 6));
        }
    }
}
