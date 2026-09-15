package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WideArithmeticPreflightTest {
    @Test
    void detectsTwoDistinctMaximumChemicalInputs() {
        CompiledPattern<String> root = pattern(
                "chemical_process",
                List.of(stack("gas_a", Long.MAX_VALUE), stack("gas_b", Long.MAX_VALUE)),
                "result");

        assertTrue(WideArithmeticPreflight.requiresWideArithmetic(
                "result",
                BigInteger.ONE,
                Map.of("result", root),
                key -> key.startsWith("gas_") ? 8_000L : 8L,
                256));
    }

    @Test
    void detectsMaximumChemicalInputsHiddenBehindIntermediatePattern() {
        CompiledPattern<String> root = pattern(
                "root", List.of(stack("intermediate", 1L)), "result");
        CompiledPattern<String> intermediate = pattern(
                "chemical_process",
                List.of(stack("gas_a", Long.MAX_VALUE), stack("gas_b", Long.MAX_VALUE)),
                "intermediate");

        assertTrue(WideArithmeticPreflight.requiresWideArithmetic(
                "result",
                BigInteger.ONE,
                Map.of("result", root, "intermediate", intermediate),
                key -> key.startsWith("gas_") ? 8_000L : 8L,
                256));
    }

    @Test
    void leavesOrdinaryRecipeOnStandardAe2Path() {
        CompiledPattern<String> root = pattern(
                "ordinary",
                List.of(stack("iron", 64L), stack("carbon", 64L)),
                "result");

        assertFalse(WideArithmeticPreflight.requiresWideArithmetic(
                "result",
                BigInteger.valueOf(1_000L),
                Map.of("result", root),
                ignored -> 8L,
                256));
    }

    @Test
    void detectsWideDemandAfterTwoBranchesConvergeOnOneIntermediate() {
        long halfPastSignedLong = (Long.MAX_VALUE / 2L) + 1L;
        CompiledPattern<String> root = pattern(
                "root",
                List.of(stack("branch_a", 1L), stack("branch_b", 1L)),
                "result");
        CompiledPattern<String> branchA = pattern(
                "branch_a",
                List.of(stack("shared", halfPastSignedLong)),
                "branch_a");
        CompiledPattern<String> branchB = pattern(
                "branch_b",
                List.of(stack("shared", halfPastSignedLong)),
                "branch_b");
        CompiledPattern<String> shared = pattern(
                "shared",
                List.of(stack("raw", 1L)),
                "shared");
        CompiledCraftingGraph<String> graph =
                CompiledCraftingGraph.compile(1L, List.of(root, branchA, branchB, shared));
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "result",
                        Set.of()::contains)
                .orElseThrow();

        assertTrue(WideArithmeticPreflight.requiresWideArithmetic(
                "result",
                BigInteger.ONE,
                program,
                ignored -> 8L,
                256));
    }

    @Test
    void conservativeAlternativeBoundDoesNotBecomeAWideVerdict() {
        CompiledPattern<String> root = new CompiledPattern<>(
                "alternative_process",
                List.of(new CompiledPattern.InputSlot<>(List.of(
                        stack("ordinary", 1L),
                        stack("wide", Long.MAX_VALUE)))),
                Map.of("result", 1L),
                true);
        CompiledCraftingGraph<String> graph =
                CompiledCraftingGraph.compile(1L, List.of(root));
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "result",
                        Set.of()::contains)
                .orElseThrow();

        WideArithmeticPreflight.LongSafetyCertificate<String> certificate =
                WideArithmeticPreflight.longSafetyCertificate(program, ignored -> 8L, 256);

        // 二番目の候補によりlong安全は証明できないが、実選択が先頭候補なら正確な計画はwideではない。
        assertFalse(certificate.certify(BigInteger.ONE));
        assertFalse(WideArithmeticPreflight.requiresWideArithmetic(
                "result",
                BigInteger.ONE,
                program,
                ignored -> 8L,
                256));
        certificate.recordExactSafe(BigInteger.ONE);
        assertTrue(certificate.certifiesCached(BigInteger.ONE));
    }

    @Test
    void reusesTheLargestCertifiedSafeRequest() {
        CompiledPattern<String> root = pattern(
                "ordinary",
                List.of(stack("iron", 1L)),
                "result");
        CompiledCraftingGraph<String> graph =
                CompiledCraftingGraph.compile(1L, List.of(root));
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "result",
                        Set.of()::contains)
                .orElseThrow();
        WideArithmeticPreflight.LongSafetyCertificate<String> certificate =
                WideArithmeticPreflight.longSafetyCertificate(program, ignored -> 8L, 256);

        // 1,000個の一巡証明後は、それ以下をDAG再走査なしで判定できる。
        assertTrue(certificate.certify(BigInteger.valueOf(1_000L)));
        assertTrue(certificate.certifiesCached(BigInteger.valueOf(999L)));
        assertFalse(certificate.certifiesCached(BigInteger.valueOf(1_001L)));
        assertFalse(certificate.certify(BigInteger.valueOf(Long.MAX_VALUE)));
    }

    @Test
    void cachedSmallerRequestDoesNotTraverseTheProgramAgain() {
        CompiledPattern<String> root = pattern(
                "ordinary",
                List.of(stack("iron", 1L)),
                "result");
        CompiledCraftingGraph<String> graph =
                CompiledCraftingGraph.compile(1L, List.of(root));
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "result",
                        Set.of()::contains)
                .orElseThrow();
        AtomicInteger amountPerByteReads = new AtomicInteger();
        WideArithmeticPreflight.LongSafetyCertificate<String> certificate =
                WideArithmeticPreflight.longSafetyCertificate(
                        program,
                        ignored -> {
                            amountPerByteReads.incrementAndGet();
                            return 8L;
                        },
                        256);

        assertTrue(certificate.certify(BigInteger.valueOf(1_000L)));
        int readsAfterFirstProof = amountPerByteReads.get();
        assertTrue(certificate.certify(BigInteger.valueOf(500L)));
        assertEquals(readsAfterFirstProof, amountPerByteReads.get());
    }

    @Test
    void publishesTheLargestSafeRequestAcrossConcurrentPlanningWorkers() throws Exception {
        CompiledPattern<String> root = pattern(
                "ordinary",
                List.of(stack("iron", 1L)),
                "result");
        CompiledCraftingGraph<String> graph =
                CompiledCraftingGraph.compile(1L, List.of(root));
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "result",
                        Set.of()::contains)
                .orElseThrow();
        WideArithmeticPreflight.LongSafetyCertificate<String> certificate =
                WideArithmeticPreflight.longSafetyCertificate(program, ignored -> 8L, 256);
        int requestCount = 32; // 複数のAE2計算workerが同じRootを同時証明する負荷を再現する件数。
        var executor = Executors.newFixedThreadPool(8); // AE2の非同期計算poolを模した固定worker数。
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            // 大きさの異なる安全注文を同時投入し、最大値のpublication競合を発生させる。
            for (int index = 1; index <= requestCount; index++) {
                long amount = index * 1_000L;
                tasks.add(() -> certificate.certify(BigInteger.valueOf(amount)));
            }
            List<Future<Boolean>> results = executor.invokeAll(tasks);
            // 全workerの証明が成功し、途中の古い値で最大証明を上書きしていないことを確認する。
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        } finally {
            executor.shutdownNow();
        }
        assertTrue(certificate.certifiesCached(
                BigInteger.valueOf(requestCount * 1_000L)));
    }

    @Test
    void recursiveByteVisitsCannotBeMistakenForAUniqueNodeCount() {
        List<CompiledPattern<String>> patterns = new ArrayList<>();
        // 各段の需要は2個以内に保ちつつ、byte式の再帰訪問だけを2倍ずつ増やす。
        for (int stage = 1; stage <= 63; stage++) {
            String input = stage == 1 ? "raw" : "stage_" + (stage - 1);
            patterns.add(new CompiledPattern<>(
                    "visit_pattern_" + stage,
                    List.of(
                            new CompiledPattern.InputSlot<>(List.of(stack(input, 1L))),
                            new CompiledPattern.InputSlot<>(List.of(stack(input, 1L)))),
                    Map.of("stage_" + stage, Long.MAX_VALUE),
                    true));
        }
        CompiledCraftingGraph<String> graph =
                CompiledCraftingGraph.compile(1L, patterns);
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "stage_63",
                        Set.of()::contains)
                .orElseThrow();
        WideArithmeticPreflight.LongSafetyCertificate<String> certificate =
                WideArithmeticPreflight.longSafetyCertificate(program, ignored -> 8L, 256);

        assertFalse(certificate.certify(BigInteger.ONE));
    }

    @Test
    void strictTreeLongSafetyCertificateNeverHidesAnExactWidePlan() {
        // 固定seedで200通り生成し、Strict Topology採用領域でfalse negativeが無いことを検証する。
        Random random = new Random(0xA_C0_156L);
        int certifiedSamples = 0;
        int unsupportedSamples = 0;
        for (int sample = 0; sample < 200; sample++) {
            int depth = 1 + random.nextInt(8);
            List<CompiledPattern<String>> patterns = new ArrayList<>();
            // 各段へ複数slotと代替候補を作り、下位段の共有による合流も含める。
            for (int stage = 1; stage <= depth; stage++) {
                List<CompiledPattern.InputSlot<String>> inputs = new ArrayList<>();
                int slotCount = 1 + random.nextInt(3);
                // 一つのPatternへ1から3個の独立slotを追加する。
                for (int slot = 0; slot < slotCount; slot++) {
                    List<CompiledPattern.Stack<String>> alternatives = new ArrayList<>();
                    int alternativeCount = 1 + random.nextInt(2);
                    // 各slotへ1または2候補を追加し、保守的上界と実選択の差も通す。
                    for (int alternative = 0; alternative < alternativeCount; alternative++) {
                        boolean usePriorStage = stage > 1 && random.nextBoolean();
                        String input = usePriorStage
                                ? "stage_" + (1 + random.nextInt(stage - 1))
                                : "raw_" + stage + "_" + slot + "_" + alternative;
                        alternatives.add(stack(input, 1L + random.nextInt(100)));
                    }
                    inputs.add(new CompiledPattern.InputSlot<>(alternatives));
                }
                patterns.add(new CompiledPattern<>(
                        "pattern_" + stage,
                        inputs,
                        Map.of("stage_" + stage, 1L),
                        true));
            }
            CompiledCraftingGraph<String> graph =
                    CompiledCraftingGraph.compile(sample + 1L, patterns);
            String root = "stage_" + depth;
            CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                            graph,
                            root,
                            Set.of()::contains)
                    .orElseThrow();
            // 合流DAG・代替候補は正確なAE2 byte式を証明できず、本番では証明器作成前にAE2へ返す。
            if (!program.hasUniqueInputOccurrencePerKey()) {
                unsupportedSamples++;
                continue;
            }
            BigInteger request = BigInteger.valueOf(1L + random.nextLong(1_000_000_000L));
            WideArithmeticPreflight.LongSafetyCertificate<String> certificate =
                    WideArithmeticPreflight.longSafetyCertificate(program, ignored -> 8L, 1_024);

            // 安全証明が成立した標本だけ、正確判定も必ずlong範囲であることを要求する。
            if (certificate.certify(request)) {
                certifiedSamples++;
                assertFalse(
                        WideArithmeticPreflight.requiresWideArithmetic(
                                root,
                                request,
                                program,
                                ignored -> 8L,
                                1_024),
                        "certificate hid a wide plan at sample " + sample);
            }
        }
        assertTrue(certifiedSamples > 0, "fixed-seed trees must exercise the safe certificate path");
        assertTrue(unsupportedSamples > 0, "fixed-seed set must also exercise the AE2 fallback domain");
    }

    @Test
    void wideRecipeTreeCanCollapseToLongUsingExactIntermediateInventory() {
        List<CompiledPattern<String>> patterns = new ArrayList<>();
        // 20段の各Patternを九倍圧縮として作り、在庫0なら最下層需要がsigned longを超える。
        for (int stage = 1; stage <= 20; stage++) {
            String input = stage == 1 ? "raw" : "stage_" + (stage - 1);
            patterns.add(pattern(
                    "pattern_" + stage,
                    List.of(stack(input, 9L)),
                    "stage_" + stage));
        }
        CompiledCraftingGraph<String> graph =
                CompiledCraftingGraph.compile(1L, patterns);
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "stage_20",
                        Set.of()::contains)
                .orElseThrow();

        assertTrue(WideArithmeticPreflight.requiresWideArithmetic(
                "stage_20",
                BigInteger.valueOf(1_000L),
                program,
                ignored -> 8L,
                256));

        CompiledRootProgram.BigInventorySnapshot<String> exactInventory =
                program.captureBigInventory(
                        key -> key.equals("stage_19")
                                ? BigInteger.valueOf(9_000L)
                                : BigInteger.ZERO,
                        256);
        BigCraftingPlan<String> plan = program.planBig(
                BigInteger.valueOf(1_000L),
                exactInventory,
                PlanningGuard.none(),
                256);

        assertEquals(
                Map.of("stage_19", BigInteger.valueOf(9_000L)),
                plan.usedInventory());
        assertEquals(
                Map.of("pattern_20", BigInteger.valueOf(1_000L)),
                plan.patternExecutions());
        assertTrue(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                false,
                true));
    }

    private static CompiledPattern<String> pattern(
            String id,
            List<CompiledPattern.Stack<String>> inputs,
            String output) {
        return new CompiledPattern<>(
                id,
                inputs.stream()
                        .map(input -> new CompiledPattern.InputSlot<>(List.of(input)))
                        .toList(),
                Map.of(output, 1L),
                true);
    }

    private static CompiledPattern.Stack<String> stack(String key, long amount) {
        return new CompiledPattern.Stack<>(key, amount);
    }
}
