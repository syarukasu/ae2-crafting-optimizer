package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.syaru.ae2craftingoptimizer.mixin.CraftingCalculationDiagnosticsMixin;
import com.syaru.ae2craftingoptimizer.engine.CompiledCraftingGraph;
import com.syaru.ae2craftingoptimizer.engine.CompiledPattern;
import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import com.syaru.ae2craftingoptimizer.engine.OverflowPromotingCraftingPlanner;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Executes the actual wait redirect without a Minecraft launch or live world. */
class CraftingCalculationTickHandoffTest {
    @Test
    void ticksDoNotWaitDuringPureWorkButStillWaitForLiveAe2Work() throws Exception {
        var calculation = new Harness();
        Object monitor = new Object();
        var type = CraftingCalculationDiagnosticsMixin.class;
        var detached = type.getDeclaredField("aco$detachedPlanning");
        var running = type.getDeclaredField("running");
        var wait = type.getDeclaredMethod("aco$waitOnlyForLiveAe2Work", Object.class);
        detached.setAccessible(true);
        running.setAccessible(true);
        wait.setAccessible(true);
        var tick = Executors.newSingleThreadExecutor();
        try {
            detached.setBoolean(calculation, true);
            var pureTick = tick.submit(() -> {
                synchronized (monitor) {
                    running.setBoolean(calculation, true);
                    // The same while condition and monitor as AE2 simulateFor.
                    while (running.getBoolean(calculation)) {
                        wait.invoke(calculation, monitor);
                    }
                }
                return true;
            });
            assertTrue(pureTick.get(5, TimeUnit.SECONDS)); // Deadlock deadline, not tick policy.
            detached.setBoolean(calculation, false);
            var entered = new CountDownLatch(1);
            var liveTick = tick.submit(() -> {
                synchronized (monitor) {
                    running.setBoolean(calculation, true);
                    entered.countDown();
                    while (running.getBoolean(calculation)) {
                        wait.invoke(calculation, monitor);
                    }
                }
                return true;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            synchronized (monitor) {
                assertFalse(liveTick.isDone());
                running.setBoolean(calculation, false);
                monitor.notifyAll();
            }
            assertTrue(liveTick.get(5, TimeUnit.SECONDS));
        } finally {
            tick.shutdownNow();
        }
    }

    @Test
    void virtualOrdersDoNotBlockTicksDuringLongOrPromotedBigIntegerPass() throws Exception {
        var graph = CompiledCraftingGraph.compile(0L, List.of(
                new CompiledPattern<String>("output", List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>("input", 2L)))), Map.of("output", 1L), false),
                new CompiledPattern<String>("input", List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>("raw", 1L)))), Map.of("input", 1L), false)));
        var program = CompiledRootProgram.tryCompile(graph, "output", key -> false).orElseThrow();
        var inventory = program.captureLongInventory(key -> key.equals("raw") ? Long.MAX_VALUE : 0L);
        var calculation = new Harness();
        var type = CraftingCalculationDiagnosticsMixin.class;
        var detached = type.getDeclaredField("aco$detachedPlanning");
        var running = type.getDeclaredField("running");
        var wait = type.getDeclaredMethod("aco$waitOnlyForLiveAe2Work", Object.class);
        detached.setAccessible(true);
        running.setAccessible(true);
        wait.setAccessible(true);
        detached.setBoolean(calculation, true);
        var worker = Executors.newSingleThreadExecutor();
        var tick = Executors.newSingleThreadExecutor();
        Object monitor = new Object();
        try {
            // 同じGraph/在庫で通常注文と、中間需要だけがoverflowする注文を通す。
            for (long amount : new long[] { 10L, Long.MAX_VALUE }) {
                var entered = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                var passes = new AtomicInteger();
                int observedPass = amount == Long.MAX_VALUE ? 2 : 1; // long失敗後の2周目がBigInteger。
                var result = worker.submit(() -> new OverflowPromotingCraftingPlanner<String>().plan(
                        program, BigInteger.valueOf(amount), inventory, expanded -> {
                            if (expanded == 1 && passes.incrementAndGet() == observedPass) {
                                entered.countDown();
                                try {
                                    assertTrue(release.await(5, TimeUnit.SECONDS));
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(interrupted);
                                }
                            }
                        }));
                assertTrue(entered.await(5, TimeUnit.SECONDS)); // 試験のdeadlock期限。製品の時間制限ではない。
                try {
                    var tickResult = tick.submit(() -> {
                        synchronized (monitor) {
                            running.setBoolean(calculation, true);
                            while (running.getBoolean(calculation)) {
                                wait.invoke(calculation, monitor);
                            }
                        }
                        return true;
                    });
                    assertTrue(tickResult.get(5, TimeUnit.SECONDS));
                    assertFalse(result.isDone());
                } finally {
                    release.countDown();
                }
                var plan = result.get(5, TimeUnit.SECONDS);
                assertEquals(amount == Long.MAX_VALUE, plan.usesBigInteger());
                if (plan instanceof OverflowPromotingCraftingPlanner.BigResult<String> big) {
                    assertEquals(BigInteger.valueOf(amount).multiply(BigInteger.TWO),
                            big.plan().patternExecutions().get("input"));
                    assertEquals(BigInteger.valueOf(amount), big.plan().missing().get("raw"));
                } else {
                    assertTrue(plan.craftable());
                }
            }
        } finally {
            worker.shutdownNow();
            tick.shutdownNow();
        }
    }

    private static final class Harness extends CraftingCalculationDiagnosticsMixin {
        @Override
        protected void aco$invokeHandlePausing() {
        }
    }
}
