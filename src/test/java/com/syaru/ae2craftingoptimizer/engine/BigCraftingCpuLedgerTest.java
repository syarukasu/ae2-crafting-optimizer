package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BigCraftingCpuLedgerTest {
    @Test
    void reservesAtomicallyAndSchedulesEveryJob() {
        BigCraftingCpuLedger<String> ledger = new BigCraftingCpuLedger<>(BigInteger.valueOf(20));
        BigCraftingJob<String> first = job(6);
        BigCraftingJob<String> second = job(7);
        assertTrue(ledger.submit(first));
        assertTrue(ledger.submit(second));
        assertEquals(BigInteger.valueOf(13), ledger.reserved());

        var windows = ledger.schedule(2, 1);
        assertEquals(2, windows.size());
        assertEquals(2, windows.stream().map(window -> window.job().id()).distinct().count());

        var firstWindow = windows.stream()
                .filter(window -> window.job() == first)
                .findFirst()
                .orElseThrow();
        first.rollbackPreparedWindow(firstWindow.prepared().transactionId());
        first.cancel();
        ledger.removeTerminal(first.id());
        assertEquals(BigInteger.valueOf(7), ledger.reserved());
    }

    @Test
    void doesNotScheduleASecondWindowBeforeTheFirstIsResolved() {
        BigCraftingCpuLedger<String> ledger = new BigCraftingCpuLedger<>(BigInteger.valueOf(20));
        BigCraftingJob<String> job = job(10);
        assertTrue(ledger.submit(job));

        var first = ledger.schedule(4L, 4L);
        assertEquals(1, first.size());
        assertTrue(ledger.schedule(4L, 4L).isEmpty());

        job.rollbackPreparedWindow(first.get(0).prepared().transactionId());
        assertEquals(1, ledger.schedule(4L, 4L).size());
    }

    @Test
    void dividesOneTickBudgetAcrossRunnableJobs() {
        BigCraftingCpuLedger<String> ledger = new BigCraftingCpuLedger<>(BigInteger.valueOf(100));
        for (int index = 0; index < 4; index++) {
            assertTrue(ledger.submit(job(10)));
        }

        var windows = ledger.schedule(8L, 8L);

        assertEquals(4, windows.size());
        assertEquals(4, windows.stream().map(window -> window.job().id()).distinct().count());
        assertEquals(8L, windows.stream().mapToLong(
                window -> window.prepared().window().executions()).sum());
        assertTrue(windows.stream().allMatch(
                window -> window.prepared().window().executions() == 2L));
    }

    @Test
    void quarantinedJobKeepsCapacityUntilExplicitlyDiscarded() {
        BigCraftingCpuLedger<String> ledger = new BigCraftingCpuLedger<>(BigInteger.TEN);
        BigCraftingJob<String> job = job(10);
        assertTrue(ledger.submit(job));
        job.prepareWindow("p", 1L);

        assertTrue(ledger.cancel(job.id()));
        assertEquals(BigCraftingJob.State.QUARANTINED, job.state());
        assertEquals(BigInteger.TEN, ledger.reserved());
        assertTrue(ledger.discardQuarantined(job.id()));
        assertEquals(BigInteger.ZERO, ledger.reserved());
    }

    private static BigCraftingJob<String> job(long amount) {
        BigInteger count = BigInteger.valueOf(amount);
        return new BigCraftingJob<>(
                UUID.randomUUID(),
                "out",
                count,
                count,
                Map.of("p", count),
                Map.of());
    }
}
