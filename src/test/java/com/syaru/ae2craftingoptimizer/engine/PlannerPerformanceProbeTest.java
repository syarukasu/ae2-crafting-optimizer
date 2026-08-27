package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlannerPerformanceProbeTest {
    /** JITが安定するまで両経路を実行する回数。 */
    private static final int WARMUP_ITERATIONS = 40;
    /** 一時オブジェクトの差が測定誤差へ埋もれないようにする計測回数。 */
    private static final int MEASURED_ITERATIONS = 120;
    /** 実環境の巨大ツリーを模した直列Pattern数。 */
    private static final int PATTERN_COUNT = 1_000;
    /** 計算量と注文量を分離して確認する十分に大きなlong注文数。 */
    private static final long REQUESTED_AMOUNT = 1_000_000_000_000L;

    @Test
    void compareMapAndCompiledPlannerHotPaths() {
        List<CompiledPattern<String>> patterns = new ArrayList<>();
        // 1,000段の一意な直列Patternを作り、候補選択差を計測へ混ぜない。
        for (int index = 1; index <= PATTERN_COUNT; index++) {
            patterns.add(new CompiledPattern<>(
                    "p" + index,
                    List.of(new CompiledPattern.InputSlot<>(List.of(
                            new CompiledPattern.Stack<>("k" + (index - 1), 1L)))),
                    Map.of("k" + index, 1L),
                    false));
        }
        CompiledCraftingGraph<String> graph = CompiledCraftingGraph.compile(1L, patterns);
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "k" + PATTERN_COUNT,
                        ignored -> false)
                .orElseThrow();
        CompiledRootProgram.InventorySnapshot<String> inventory =
                program.captureLongInventory(ignored -> 0L);
        LongCraftingPlanner<String> mapPlanner = new LongCraftingPlanner<>();

        // 両実装を交互に暖機し、片方だけがJIT済みになる偏りを避ける。
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            consume(program.planLong(REQUESTED_AMOUNT, inventory, PlanningGuard.none()));
            consume(mapPlanner.plan(
                    graph,
                    "k" + PATTERN_COUNT,
                    REQUESTED_AMOUNT,
                    Map.of()));
        }

        Measurement compiled = measure(() -> consume(
                program.planLong(REQUESTED_AMOUNT, inventory, PlanningGuard.none())));
        Measurement mapped = measure(() -> consume(mapPlanner.plan(
                graph,
                "k" + PATTERN_COUNT,
                REQUESTED_AMOUNT,
                Map.of())));

        System.out.printf(
                "ACO-PERF nodes=%d iterations=%d compiledMs=%.3f mapMs=%.3f speedup=%.2fx "
                        + "compiledMiB=%.3f mapMiB=%.3f allocationReduction=%.2fx%n",
                PATTERN_COUNT,
                MEASURED_ITERATIONS,
                compiled.nanos() / 1_000_000.0D,
                mapped.nanos() / 1_000_000.0D,
                (double) mapped.nanos() / (double) compiled.nanos(),
                compiled.allocatedBytes() / 1_048_576.0D,
                mapped.allocatedBytes() / 1_048_576.0D,
                allocationReduction(compiled, mapped));
    }

    private static Measurement measure(Runnable action) {
        long allocatedBefore = allocatedBytes();
        long start = System.nanoTime();
        // 同じ計画を十分な回数繰り返し、タイマー分解能より計画コストを大きくする。
        for (int iteration = 0; iteration < MEASURED_ITERATIONS; iteration++) {
            action.run();
        }
        long nanos = System.nanoTime() - start;
        long allocatedAfter = allocatedBytes();
        long allocated = allocatedBefore < 0L || allocatedAfter < 0L
                ? -1L
                : allocatedAfter - allocatedBefore;
        return new Measurement(nanos, allocated);
    }

    private static long allocatedBytes() {
        var bean = ManagementFactory.getThreadMXBean();
        // HotSpot以外で割り当て計測APIが無い場合は、時間だけを記録する。
        if (!(bean instanceof com.sun.management.ThreadMXBean allocationBean)
                || !allocationBean.isThreadAllocatedMemorySupported()) {
            return -1L;
        }
        // 無効なJVMではテストスレッドだけの計測を一度有効化する。
        if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
            allocationBean.setThreadAllocatedMemoryEnabled(true);
        }
        return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static double allocationReduction(Measurement compiled, Measurement mapped) {
        // 計測非対応JVMでは倍率を0として表示し、機能試験自体は失敗させない。
        if (compiled.allocatedBytes() <= 0L || mapped.allocatedBytes() < 0L) {
            return 0.0D;
        }
        return (double) mapped.allocatedBytes() / (double) compiled.allocatedBytes();
    }

    private static void consume(LongCraftingPlan<String> plan) {
        assertEquals(PATTERN_COUNT, plan.patternExecutions().size());
        assertEquals(1, plan.missing().size());
    }

    private record Measurement(long nanos, long allocatedBytes) {
    }
}
