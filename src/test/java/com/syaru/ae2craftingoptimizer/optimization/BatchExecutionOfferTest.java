package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchCpuAccountingMode;
import org.junit.jupiter.api.Test;

class BatchExecutionOfferTest {
    /** 実環境のAAC Vector Worker既定物理Thread数。 */
    private static final int AAC_PHYSICAL_THREADS = 256;

    @Test
    void singlePhysicalOperationKeepsTheFullLongCoefficient() {
        assertEquals(
                Long.MAX_VALUE,
                BatchExecutionOffer.select(
                        Long.MAX_VALUE,
                        AAC_PHYSICAL_THREADS,
                        BatchCpuAccountingMode
                                .SINGLE_PHYSICAL_OPERATION,
                        65_536L));
    }

    @Test
    void logicalAccountingStillUsesThePhysicalOperationBudget() {
        assertEquals(
                AAC_PHYSICAL_THREADS,
                BatchExecutionOffer.select(
                        Long.MAX_VALUE,
                        AAC_PHYSICAL_THREADS,
                        BatchCpuAccountingMode.LOGICAL_EXECUTIONS,
                        65_536L));
    }

    @Test
    void logicalAccountingAlsoHonorsItsConfiguredLimit() {
        assertEquals(
                64L,
                BatchExecutionOffer.select(
                        1_000L,
                        AAC_PHYSICAL_THREADS,
                        BatchCpuAccountingMode.LOGICAL_EXECUTIONS,
                        64L));
    }

    @Test
    void rejectsAnInvalidPhysicalBudgetBeforeDispatch() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BatchExecutionOffer.select(
                        1L,
                        0,
                        BatchCpuAccountingMode
                                .SINGLE_PHYSICAL_OPERATION,
                        Long.MAX_VALUE));
    }
}
