package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.engine.parallel.ParallelPatternIndex;
import com.syaru.ae2craftingoptimizer.engine.parallel.ParallelPlanBlueprint;
import com.syaru.ae2craftingoptimizer.engine.parallel.ParallelPlanRequest;
import com.syaru.ae2craftingoptimizer.engine.parallel.ParallelPlanResult;
import com.syaru.ae2craftingoptimizer.engine.parallel.ParallelPlannerEngine;
import com.syaru.ae2craftingoptimizer.engine.parallel.ParallelRevisionVector;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Issue #179の仮想AE2注文と、同一JVM内のserial/parallel比較。 */
class ParallelPlannerVirtualOrderBenchmarkTest {
    private static final int INPUTS_PER_PATTERN = 6;
    private static final int CRAFTING_LEVELS = Integer.parseInt(
            System.getenv().getOrDefault("ACO_BENCHMARK_LEVELS", "6"));
    private static final int WARMUPS = 3;
    private static final int SAMPLES = 7;
    private static final int MAXIMUM_BITS = 512;
    private static volatile Object blackhole;

    @Test
    void virtualLongAndPromotedOrdersMatchSerialAndReportMedian() throws Exception {
        Fixture fixture = fixture();
        try (ParallelPlannerEngine engine = new ParallelPlannerEngine()) {
            BenchmarkResult normal = benchmark(
                    engine,
                    fixture,
                    BigInteger.valueOf(64L),
                    BigInteger.valueOf(512L));
            BenchmarkResult promoted = benchmark(
                    engine,
                    fixture,
                    BigInteger.ONE.shiftLeft(60),
                    BigInteger.ONE.shiftLeft(63));

            assertEquals(ParallelPlanBlueprint.ArithmeticMode.CHECKED_LONG, normal.mode);
            assertEquals(ParallelPlanBlueprint.ArithmeticMode.BIG_INTEGER, promoted.mode);
            assertTrue(normal.craftable && promoted.craftable);
            System.out.printf(
                    "ACO_PARALLEL_BENCHMARK nodes=%d normal_serial_ns=%d normal_parallel_ns=%d "
                            + "normal_amount_ns=%d normal_speedup=%.3f promoted_serial_ns=%d "
                            + "promoted_parallel_ns=%d promoted_amount_ns=%d promoted_speedup=%.3f "
                            + "serial_alloc_bytes=%d/%d parallel_alloc_bytes=%d/%d "
                            + "serial_cpu_ns=%d/%d parallel_cpu_ns=%d/%d workers=%d/%d%n",
                    fixture.program.nodeCount(),
                    normal.serialMedianNanos,
                    normal.parallelMedianNanos,
                    normal.parallelAmountMedianNanos,
                    speedup(normal),
                    promoted.serialMedianNanos,
                    promoted.parallelMedianNanos,
                    promoted.parallelAmountMedianNanos,
                    speedup(promoted),
                    normal.serialAllocatedBytes,
                    promoted.serialAllocatedBytes,
                    normal.parallelAllocatedBytes,
                    promoted.parallelAllocatedBytes,
                    normal.serialCpuNanos,
                    promoted.serialCpuNanos,
                    normal.parallelCpuNanos,
                    promoted.parallelCpuNanos,
                    normal.amountWorkers,
                    promoted.amountWorkers);
            assertTrue(normal.amountWorkers > 1 && promoted.amountWorkers > 1);
        }
    }

    private static BenchmarkResult benchmark(
            ParallelPlannerEngine engine,
            Fixture fixture,
            BigInteger requested,
            BigInteger rawPerBranch) throws Exception {
        Map<String, BigInteger> inventory = new LinkedHashMap<>();
        for (String rawKey : fixture.rawKeys) {
            inventory.put(rawKey, rawPerBranch);
        }
        var bigInventory = fixture.program.captureBigInventory(
                key -> inventory.getOrDefault(key, BigInteger.ZERO),
                MAXIMUM_BITS);
        ParallelPlanRequest<String> request = new ParallelPlanRequest<>(
                fixture.index,
                "root",
                requested,
                inventory,
                fixture.divisors,
                MAXIMUM_BITS,
                new ParallelRevisionVector(1L, 1L, 1L, 1L, 1L));

        for (int warmup = 0; warmup < WARMUPS; warmup++) {
            blackhole = serialPlan(fixture, requested, bigInventory);
            blackhole = engine.submit(request).get(10L, TimeUnit.SECONDS);
        }
        long[] serialNanos = new long[SAMPLES];
        long[] parallelNanos = new long[SAMPLES];
        long[] parallelAmountNanos = new long[SAMPLES];
        long[] serialAllocatedBytes = new long[SAMPLES];
        long[] parallelAllocatedBytes = new long[SAMPLES];
        long[] serialCpuNanos = new long[SAMPLES];
        long[] parallelCpuNanos = new long[SAMPLES];
        int maximumWorkers = 0;
        AllocationProbe allocations = AllocationProbe.capturePlannerThreads();
        ParallelPlanResult<String> parallel = null;
        SerialResult serial = null;
        for (int sample = 0; sample < SAMPLES; sample++) {
            long started = System.nanoTime();
            long allocatedBefore = allocations.currentThreadBytes();
            long cpuBefore = allocations.currentThreadCpuNanos();
            serial = serialPlan(fixture, requested, bigInventory);
            serialCpuNanos[sample] = allocations.currentThreadCpuNanos() - cpuBefore;
            serialAllocatedBytes[sample] = allocations.currentThreadBytes() - allocatedBefore;
            serialNanos[sample] = System.nanoTime() - started;
            blackhole = serial;

            started = System.nanoTime();
            allocatedBefore = allocations.allPlanningThreadsBytes();
            cpuBefore = allocations.allPlanningThreadsCpuNanos();
            parallel = engine.submit(request).get(10L, TimeUnit.SECONDS);
            parallelCpuNanos[sample] = allocations.allPlanningThreadsCpuNanos() - cpuBefore;
            parallelAllocatedBytes[sample] = allocations.allPlanningThreadsBytes() - allocatedBefore;
            parallelNanos[sample] = System.nanoTime() - started;
            parallelAmountNanos[sample] = parallel.metrics().amountNanos();
            maximumWorkers = Math.max(maximumWorkers, parallel.metrics().amountWorkersUsed());
            blackhole = parallel;
        }
        ParallelPlanBlueprint<String> blueprint = parallel.blueprint().orElseThrow();
        assertEquals(serial.plan.patternExecutions(), blueprint.patternExecutions());
        assertEquals(serial.plan.usedInventory(), blueprint.usedInventory());
        assertEquals(serial.plan.emitted(), blueprint.emitted());
        assertEquals(serial.plan.missing(), blueprint.missing());
        assertEquals(serial.exactBytes, blueprint.exactBytes());
        assertEquals(serial.mode, blueprint.arithmeticMode());
        assertTrue(parallel.metrics().graphCacheHit());
        return new BenchmarkResult(
                median(serialNanos),
                median(parallelNanos),
                median(parallelAmountNanos),
                median(serialAllocatedBytes),
                median(parallelAllocatedBytes),
                median(serialCpuNanos),
                median(parallelCpuNanos),
                blueprint.arithmeticMode(),
                blueprint.craftable(),
                maximumWorkers);
    }

    private static SerialResult serialPlan(
            Fixture fixture,
            BigInteger requested,
            CompiledRootProgram.BigInventorySnapshot<String> inventory) {
        OverflowPromotingCraftingPlanner.Result<String> promoted =
                new OverflowPromotingCraftingPlanner<String>(MAXIMUM_BITS).plan(
                        fixture.program,
                        requested,
                        inventory,
                        PlanningGuard.none());
        BigCraftingPlan<String> plan;
        ParallelPlanBlueprint.ArithmeticMode mode;
        if (promoted instanceof OverflowPromotingCraftingPlanner.BigResult<String> big) {
            plan = big.plan();
            mode = ParallelPlanBlueprint.ArithmeticMode.BIG_INTEGER;
        } else if (promoted instanceof OverflowPromotingCraftingPlanner.LongResult<String> normal) {
            LongCraftingPlan<String> source = normal.plan();
            plan = new BigCraftingPlan<>(
                    source.requestedKey(),
                    BigInteger.valueOf(source.requestedAmount()),
                    widen(source.patternExecutions()),
                    widen(source.usedInventory()),
                    widen(source.emitted()),
                    widen(source.missing()),
                    fixture.program.nodeCount());
            mode = ParallelPlanBlueprint.ArithmeticMode.CHECKED_LONG;
        } else {
            throw new IllegalStateException("unknown serial planner result");
        }
        BigInteger bytes = BigExactCraftingByteCounter.calculate(
                "root",
                requested,
                fixture.program.patternsByOutput(),
                plan.patternExecutions(),
                ignored -> 8L,
                MAXIMUM_BITS);
        return new SerialResult(plan, bytes, mode);
    }

    private static Fixture fixture() {
        List<CompiledPattern<String>> patterns = new ArrayList<>();
        Map<String, Long> divisors = new LinkedHashMap<>();
        List<String> rawKeys = new ArrayList<>();
        divisors.put("root", 8L);
        List<String> currentLevel = List.of("root");
        int nextKey = 0;
        for (int level = 0; level < CRAFTING_LEVELS; level++) {
            List<String> nextLevel = new ArrayList<>(
                    Math.multiplyExact(currentLevel.size(), INPUTS_PER_PATTERN));
            for (String output : currentLevel) {
                List<CompiledPattern.InputSlot<String>> inputs = new ArrayList<>(
                        INPUTS_PER_PATTERN);
                for (int input = 0; input < INPUTS_PER_PATTERN; input++) {
                    boolean terminalInput = level == CRAFTING_LEVELS - 1;
                    String child = terminalInput
                            ? "raw-" + nextKey++
                            : "node-" + (level + 1) + '-' + nextKey++;
                    inputs.add(slot(child, terminalInput ? 8L : 1L));
                    nextLevel.add(child);
                    divisors.put(child, 8L);
                    if (terminalInput) {
                        rawKeys.add(child);
                    }
                }
                patterns.add(new CompiledPattern<>(
                        "pattern-" + output,
                        inputs,
                        Map.of(output, 1L),
                        false));
            }
            currentLevel = nextLevel;
        }
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        CompiledCraftingGraph.compile(1L, patterns),
                        "root",
                        ignored -> false)
                .orElseThrow();
        ParallelPatternIndex<String> index = ParallelPatternIndex.fromPatterns(
                1L,
                patterns,
                Set.of());
        return new Fixture(program, index, Map.copyOf(divisors), List.copyOf(rawKeys));
    }

    private static CompiledPattern.InputSlot<String> slot(String key, long amount) {
        return new CompiledPattern.InputSlot<>(List.of(
                new CompiledPattern.Stack<>(key, amount)));
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static <K> Map<K, BigInteger> widen(Map<K, Long> source) {
        Map<K, BigInteger> result = new LinkedHashMap<>();
        source.forEach((key, amount) -> result.put(key, BigInteger.valueOf(amount)));
        return Map.copyOf(result);
    }

    private static double speedup(BenchmarkResult result) {
        return (double) result.serialMedianNanos / (double) result.parallelMedianNanos;
    }

    private record Fixture(
            CompiledRootProgram<String> program,
            ParallelPatternIndex<String> index,
            Map<String, Long> divisors,
            List<String> rawKeys) {
    }

    private record SerialResult(
            BigCraftingPlan<String> plan,
            BigInteger exactBytes,
            ParallelPlanBlueprint.ArithmeticMode mode) {
    }

    private record BenchmarkResult(
            long serialMedianNanos,
            long parallelMedianNanos,
            long parallelAmountMedianNanos,
            long serialAllocatedBytes,
            long parallelAllocatedBytes,
            long serialCpuNanos,
            long parallelCpuNanos,
            ParallelPlanBlueprint.ArithmeticMode mode,
            boolean craftable,
            int amountWorkers) {
    }

    private static final class AllocationProbe {
        private final com.sun.management.ThreadMXBean bean;
        private final long currentThreadId;
        private final long[] plannerThreadIds;

        private AllocationProbe(
                com.sun.management.ThreadMXBean bean,
                long currentThreadId,
                long[] plannerThreadIds) {
            this.bean = bean;
            this.currentThreadId = currentThreadId;
            this.plannerThreadIds = plannerThreadIds;
        }

        private static AllocationProbe capturePlannerThreads() {
            com.sun.management.ThreadMXBean bean =
                    (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            if (bean.isThreadAllocatedMemorySupported()
                    && !bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            long[] plannerIds = Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> thread.getName().startsWith("ACO Tree Planner #"))
                    .mapToLong(Thread::getId)
                    .toArray();
            return new AllocationProbe(bean, Thread.currentThread().getId(), plannerIds);
        }

        private long currentThreadBytes() {
            return allocated(currentThreadId);
        }

        private long allPlanningThreadsBytes() {
            long total = currentThreadBytes();
            for (long threadId : plannerThreadIds) {
                total = Math.addExact(total, allocated(threadId));
            }
            return total;
        }

        private long currentThreadCpuNanos() {
            return Math.max(0L, bean.getThreadCpuTime(currentThreadId));
        }

        private long allPlanningThreadsCpuNanos() {
            long total = currentThreadCpuNanos();
            for (long threadId : plannerThreadIds) {
                total = Math.addExact(total, Math.max(0L, bean.getThreadCpuTime(threadId)));
            }
            return total;
        }

        private long allocated(long threadId) {
            long allocated = bean.getThreadAllocatedBytes(threadId);
            return Math.max(0L, allocated);
        }
    }
}
