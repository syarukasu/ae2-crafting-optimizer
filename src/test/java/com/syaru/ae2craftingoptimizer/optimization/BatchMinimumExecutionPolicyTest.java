package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BatchMinimumExecutionPolicyTest {
    /** AAC既定閾値の直前までは通常AE2経路へ戻す。 */
    @Test
    void rejectsFinalCountsBelowAacDefault() {
        assertFalse(
                BatchMinimumExecutionPolicy.isEligible(
                        1L,
                        256L));
        assertFalse(
                BatchMinimumExecutionPolicy.isEligible(
                        255L,
                        256L));
    }

    /** 閾値以上とlong最大値は係数を狭めず受理できる。 */
    @Test
    void acceptsThresholdAndWideLongCounts() {
        assertTrue(
                BatchMinimumExecutionPolicy.isEligible(
                        256L,
                        256L));
        assertTrue(
                BatchMinimumExecutionPolicy.isEligible(
                        Long.MAX_VALUE,
                        256L));
    }

    /** 在庫制限後に200まで縮小された大量Taskも小口として扱う。 */
    @Test
    void usesTheFinalInventoryLimitedCount() {
        assertFalse(
                BatchMinimumExecutionPolicy.isEligible(
                        200L,
                        256L));
    }

    /** Adapterの契約違反を通常Fallbackとして隠さない。 */
    @Test
    void rejectsInvalidAdapterMinimum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BatchMinimumExecutionPolicy.isEligible(
                        1L,
                        0L));
    }
}
