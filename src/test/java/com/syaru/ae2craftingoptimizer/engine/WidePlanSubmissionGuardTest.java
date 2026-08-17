package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.UnsuitableCpus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * wide計画を断るCPUが「容量不足」を名乗らないことを固定する。
 *
 * <p>issue #103で報告された通り、この拒否は空き容量と無関係なので、
 * CPU_TOO_SMALLを返すとプレイヤーはストレージを増やす作業へ誘導されてしまう。</p>
 */
class WidePlanSubmissionGuardTest {
    @AfterEach
    void clearDeduplicationTable() {
        WidePlanSubmissionGuard.clearForTests();
    }

    @Test
    void neverReportsAWideDeclineAsInsufficientCapacity() {
        var result = WidePlanSubmissionGuard.unsupportedCpuResult();

        assertFalse(result.successful());
        assertNotEquals(CraftingSubmitErrorCode.CPU_TOO_SMALL, result.errorCode());
    }

    @Test
    void countsTheCpuAsExcludedRatherThanTooSmall() {
        var result = WidePlanSubmissionGuard.unsupportedCpuResult();

        assertEquals(CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND, result.errorCode());
        UnsuitableCpus detail = assertInstanceOf(UnsuitableCpus.class, result.errorDetail());
        assertEquals(0, detail.tooSmall());
        assertEquals(0, detail.offline());
        assertEquals(0, detail.busy());
        assertEquals(1, detail.excluded());
    }
}
