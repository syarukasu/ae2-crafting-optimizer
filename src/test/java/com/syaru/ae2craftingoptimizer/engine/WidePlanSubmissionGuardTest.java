package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.UnsuitableCpus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** issue #103の非対応CPU拒否がCPU_TOO_SMALLへ戻らないことを固定する。 */
class WidePlanSubmissionGuardTest {
    @AfterEach
    void clearDeduplicationTable() {
        WidePlanSubmissionGuard.clearForTests();
    }

    @Test
    void reportsUnsupportedCpuInsteadOfInsufficientCapacity() {
        var result = WidePlanSubmissionGuard.unsupportedCpuResult();

        assertFalse(result.successful());
        assertNotEquals(CraftingSubmitErrorCode.CPU_TOO_SMALL, result.errorCode());
        assertEquals(CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND, result.errorCode());
        UnsuitableCpus detail = assertInstanceOf(UnsuitableCpus.class, result.errorDetail());
        assertEquals(0, detail.tooSmall());
        assertEquals(1, detail.excluded());
    }
}
