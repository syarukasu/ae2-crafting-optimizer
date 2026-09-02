package com.syaru.ae2craftingoptimizer.engine.parallel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.engine.CompiledCraftingGraph;
import com.syaru.ae2craftingoptimizer.engine.CompiledPattern;
import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import com.syaru.ae2craftingoptimizer.engine.LongCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.PlanningGuard;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ParallelPlannerEngineTest {
    private static final long FUTURE_TIMEOUT_SECONDS = 10L;
    private static final int TEST_MAXIMUM_BITS = 512;

    @Test
    void oneWideTreeUsesTheFixedFourThreadSessionPool() throws Exception {
        List<CompiledPattern<String>> patterns = new ArrayList<>();
        List<CompiledPattern.InputSlot<String>> rootInputs = new ArrayList<>();
        for (int branch = 0; branch < 2_048; branch++) {
            String branchKey = "branch-" + branch;
            String rawKey = "raw-" + branch;
            rootInputs.add(slot(branchKey, 1L));
            patterns.add(pattern("p-" + branch, rawKey, 1L, branchKey, 1L));
        }
        patterns.add(new CompiledPattern<>(
                "root-pattern",
                rootInputs,
                Map.of("root", 1L),
                false));

        try (ParallelPlannerEngine engine = new ParallelPlannerEngine()) {
            ParallelPlanResult<String> result = get(engine.submit(request(patterns, "root", BigInteger.ONE, Map.of())));

            assertEquals(ParallelPlannerPool.PARALLELISM, engine.plannerParallelism());
            assertEquals(4_097, result.metrics().graphExpandedNodes());
            assertTrue(result.metrics().graphWorkersUsed() > 1);
            assertTrue(result.metrics().amountWorkersUsed() > 1);
            assertTrue(result.metrics().maximumActiveWorkers() <= ParallelPlannerPool.PARALLELISM);
            assertEquals(2_048, result.blueprint().orElseThrow().missing().size());
        }
    }

    @Test
    void sharedIntermediateIsExpandedOnceAndOverflowPromotesTheAmountPass() throws Exception {
        var output = new CompiledPattern<>(
                "output",
                List.of(slot("a", 1L), slot("b", 1L)),
                Map.of("output", 1L),
                false);
        var a = pattern("a", "shared", 1L, "a", 1L);
        var b = pattern("b", "shared", 1L, "b", 1L);
        var shared = pattern("shared", "raw", 3L, "shared", 1L);
        List<CompiledPattern<String>> patterns = List.of(output, a, b, shared);
        Map<String, BigInteger> inventory = Map.of("raw", BigInteger.valueOf(6L));

        try (ParallelPlannerEngine engine = new ParallelPlannerEngine()) {
            ParallelPlanResult<String> result = get(engine.submit(
                    request(patterns, "output", BigInteger.ONE, inventory)));
            ParallelPlanBlueprint<String> plan = result.blueprint().orElseThrow();

            assertTrue(plan.craftable());
            assertEquals(BigInteger.TWO, plan.patternExecutions().get("shared"));
            assertEquals(Map.of("raw", BigInteger.valueOf(6L)), plan.usedInventory());
            assertEquals(5, result.metrics().graphExpandedNodes());
            assertTrue(result.metrics().duplicateExpansionsPrevented() >= 1L);
            assertFalse(plan.ae2BytesProven());

            CompiledRootProgram<String> serialProgram = CompiledRootProgram.tryCompile(
                            CompiledCraftingGraph.compile(1L, patterns),
                            "output",
                            ignored -> false)
                    .orElseThrow();
            LongCraftingPlan<String> serial = serialProgram.planLong(
                    1L,
                    serialProgram.captureLongInventory(key -> key.equals("raw") ? 6L : 0L),
                    PlanningGuard.none());
            assertEquals(widen(serial.patternExecutions()), plan.patternExecutions());
            assertEquals(widen(serial.usedInventory()), plan.usedInventory());
            assertEquals(widen(serial.missing()), plan.missing());
            ParallelPlanResult<String> promoted = get(engine.submit(request(
                    List.of(pattern("huge", "raw", 8L, "huge", 1L)),
                    "huge",
                    BigInteger.ONE.shiftLeft(60),
                    Map.of())));
            assertTrue(promoted.metrics().promotedFromLong());
            assertEquals(
                    BigInteger.ONE.shiftLeft(63),
                    promoted.blueprint().orElseThrow().missing().get("raw"));
        }
    }

    @Test
    void boundedQueueNeverRunsOnCallerAndQueuedCancellationDoesNotStartASecondSession()
            throws Exception {
        ParallelPlannerPool pool = new ParallelPlannerPool();
        CountDownLatch workersOccupied = new CountDownLatch(ParallelPlannerPool.PARALLELISM);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        for (int worker = 0; worker < ParallelPlannerPool.PARALLELISM; worker++) {
            pool.execute(() -> {
                workersOccupied.countDown();
                try {
                    releaseWorkers.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertTrue(workersOccupied.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        ParallelPlannerEngine engine = new ParallelPlannerEngine(pool);
        List<CompiledPattern<String>> patterns = List.of(
                pattern("output", "raw", 1L, "output", 1L));
        ParallelPlanRequest<String> request = request(patterns, "output", BigInteger.ONE, Map.of());
        Future<ParallelPlanResult<String>> active = engine.submit(request);
        List<Future<ParallelPlanResult<String>>> queued = new ArrayList<>();
        for (int index = 0; index < ParallelPlannerEngine.MAXIMUM_QUEUED_SESSIONS; index++) {
            queued.add(engine.submit(request));
        }
        Future<ParallelPlanResult<String>> rejected = engine.submit(request);

        assertEquals(ParallelPlannerEngine.MAXIMUM_QUEUED_SESSIONS, engine.queuedSessionCount());
        assertEquals(ParallelPlanFailure.QUEUE_FULL, get(rejected).failure());
        assertFalse(active.isDone());
        assertTrue(queued.get(0).cancel(false));
        assertEquals(ParallelPlannerEngine.MAXIMUM_QUEUED_SESSIONS - 1, engine.queuedSessionCount());

        releaseWorkers.countDown();
        assertTrue(get(active).blueprint().isPresent());
        engine.close();
        assertTrue(pool.awaitTerminationForTest());
    }

    private static ParallelPlanResult<String> get(Future<ParallelPlanResult<String>> future)
            throws Exception {
        return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static ParallelPlanRequest<String> request(
            List<CompiledPattern<String>> patterns,
            String root,
            BigInteger requested,
            Map<String, BigInteger> inventory) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(root);
        for (CompiledPattern<String> pattern : patterns) {
            keys.addAll(pattern.outputs().keySet());
            for (CompiledPattern.InputSlot<String> slot : pattern.inputs()) {
                for (CompiledPattern.Stack<String> alternative : slot.alternatives()) {
                    keys.add(alternative.key());
                }
            }
        }
        Map<String, Long> divisors = new LinkedHashMap<>();
        for (String key : keys) {
            divisors.put(key, 8L);
        }
        return new ParallelPlanRequest<>(
                ParallelPatternIndex.fromPatterns(
                        1L,
                        patterns,
                        Set.of(),
                        Comparator.naturalOrder()),
                root,
                requested,
                inventory,
                divisors,
                TEST_MAXIMUM_BITS,
                new ParallelRevisionVector(1L, 1L, 1L, 1L, 1L));
    }

    private static Map<String, BigInteger> widen(Map<String, Long> source) {
        Map<String, BigInteger> result = new HashMap<>();
        source.forEach((key, value) -> result.put(key, BigInteger.valueOf(value)));
        return result;
    }

    private static CompiledPattern.InputSlot<String> slot(String key, long amount) {
        return new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(key, amount)));
    }

    private static CompiledPattern<String> pattern(
            String id,
            String input,
            long inputAmount,
            String output,
            long outputAmount) {
        return new CompiledPattern<>(
                id,
                List.of(slot(input, inputAmount)),
                Map.of(output, outputAmount),
                false);
    }
}
