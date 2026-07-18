package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class BigExecutionWindowTest {
    @Test
    void splitsAnArbitraryCountIntoLongBoundedWindows() {
        BigInteger total = BigInteger.TEN.pow(128);
        BigExecutionWindow first = BigExecutionWindow.next(total, BigInteger.ZERO, 65_536L);
        BigExecutionWindow second = BigExecutionWindow.next(total, BigInteger.valueOf(first.executions()), 65_536L);

        assertEquals(65_536L, first.executions());
        assertEquals(BigInteger.valueOf(65_536L), second.offset());
        assertEquals(total.subtract(BigInteger.valueOf(131_072L)), second.remainingAfter());
    }

    @Test
    void taskProgressNeverAllowsOverAccounting() {
        BigCraftingTaskProgress progress = new BigCraftingTaskProgress(BigInteger.TEN);
        progress.complete(9L);
        assertEquals(1L, progress.nextWindow(100L).executions());
        assertThrows(IllegalArgumentException.class, () -> progress.complete(2L));
    }
}
