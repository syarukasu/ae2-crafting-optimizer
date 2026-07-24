package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.util.StableFingerprint;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 厳格に証明できるAE2 Pattern木から、実行量に依存しないBigInteger Jobを一度だけ構築する。 */
public final class Ae2BigCraftingPlanFactory {
    /** 64ノードごとに世代と割込みを再検証するためのbit mask。 */
    private static final int GENERATION_CHECK_INTERVAL_MASK = 63;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    /** Fingerprint用ノード記述の通常長を再確保せず組み立てる初期容量。 */
    private static final int FINGERPRINT_DESCRIPTOR_INITIAL_CAPACITY = 256;
    /** 同一世代Programの正規化Fingerprintを、Windowごとに再構築しない。 */
    private static final Map<CompiledRootProgram<AEKey>, String> PROGRAM_FINGERPRINTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Ae2BigCraftingPlanFactory() {
    }

    @Nullable
    public static PreparedBigRootPlan tryCreate(
            Level level,
            IGrid grid,
            IActionSource source,
            AEKey output,
            BigInteger requestedAmount) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(output, "output");
        int maximumBits = ACOConfig.getBigIntegerMaximumBits();
        BigCountMath.requireMaximumBits(requestedAmount, "BigInteger root request", maximumBits);
        // 0以下の注文またはキャンセル済みスレッドからBig jobを作らない。
        if (requestedAmount.signum() <= 0 || Thread.currentThread().isInterrupted()) {
            return null;
        }

        long patternGeneration = ProviderPatternGenerationTracker.generation();
        long recipeGeneration = RecipeGenerationTracker.generation();
        Ae2CompiledCraftingGraphCache.Snapshot snapshot =
                Ae2CompiledCraftingGraphCache.getOrCompile(grid, level);
        // Graph構築中にProviderまたはrecipe世代が変わった場合は古いSnapshotを採用しない。
        if (snapshot.graph().generation() != patternGeneration
                || snapshot.recipeGeneration() != recipeGeneration) {
            return null;
        }
        var optionalProgram = snapshot.rootProgram(output);
        // 曖昧、循環、複数出力などを含むルートはBigIntegerでも近似しない。
        if (optionalProgram.isEmpty()) {
            return null;
        }
        CompiledRootProgram<AEKey> program = optionalProgram.get();
        /*
         * 明示的なBig Job作成でも、管理者が要求した場合はShadow一致済みProgramだけを使う。
         * 既定では、AE2が表現できないlong超過注文をShadow実績なしで永久拒否しない。
         */
        if (ACOConfig.requireAqeBigPlanShadowQualification()
                && !CompiledRootQualificationRegistry.isQualified(
                        program,
                        ACOConfig.getAuthoritativeMinimumShadowMatches())) {
            return null;
        }

        CompiledRootProgram.InventorySnapshot<AEKey> inventory =
                Ae2ReferencedInventory.captureLive(program, grid, source, output);
        Ae2StrictCraftingTopology topology = snapshot
                .strictTopology(level, grid, program)
                .orElse(null);
        // 実AE2 Pattern API上の完全一致を証明できなければBig jobを作らない。
        if (topology == null
                || !topology.acceptsInventory(grid.getStorageService().getCachedInventory())) {
            return null;
        }

        PlanningGuard guard = expanded -> {
            // 64ノードごとに割込みと世代変更を検出する。
            if ((expanded & GENERATION_CHECK_INTERVAL_MASK) == 0) {
                // 計算キャンセル後は残りのBigInteger演算を行わない。
                if (Thread.currentThread().isInterrupted()) {
                    throw new PlanningCancelledException(expanded);
                }
                // Patternまたはrecipe変更後のProgram結果は破棄する。
                if (ProviderPatternGenerationTracker.generation() != patternGeneration
                        || RecipeGenerationTracker.generation() != recipeGeneration) {
                    throw new StalePlanningSnapshotException(
                            new PlanningGenerationSnapshot(
                                    patternGeneration, 0L, recipeGeneration),
                            expanded);
                }
            }
        };
        BigCraftingPlan<AEKey> plan = program.planBig(
                requestedAmount,
                inventory,
                guard,
                maximumBits);
        // 不足注文または設定したPattern型数上限を超える計画は実行Jobへ変換しない。
        if (!plan.craftable()
                || plan.patternExecutions().size()
                        > ACOConfig.getCraftingEngineShadowMaximumPatterns()) {
            return null;
        }
        BigInteger bytes = topology.calculateBigExactBytes(
                output, requestedAmount, plan.patternExecutions(), maximumBits);
        // 0以下の容量計算結果は破損計画なので予約しない。
        if (bytes.signum() <= 0) {
            return null;
        }

        // 計算中に世代または参照在庫が変わった結果は採用しない。
        if (ProviderPatternGenerationTracker.generation() != patternGeneration
                || RecipeGenerationTracker.generation() != recipeGeneration
                || !Ae2ReferencedInventory.matchesLive(program, inventory, grid, source, output)
                || !topology.remainsValid(grid)) {
            return null;
        }
        return prepareCompiledRoot(
                output,
                requestedAmount,
                plan,
                bytes,
                program,
                patternGeneration,
                recipeGeneration,
                maximumBits);
    }

    /**
     * 既に厳格検証済みのRoot Programから、再起動後も同じ安全幅を使うBig親Jobを作る。
     * 呼出側は返却後にProvider・recipe・在庫世代を再検証する。
     */
    @Nullable
    static PreparedBigRootPlan prepareCompiledRoot(
            AEKey output,
            BigInteger requestedAmount,
            BigCraftingPlan<AEKey> plan,
            BigInteger bytes,
            CompiledRootProgram<AEKey> program,
            long patternGeneration,
            long recipeGeneration,
            int maximumBits) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(program, "program");
        long safeWindow = maximumSafeRootWindow(
                program,
                requestedAmount,
                ACOConfig.getBigIntegerExecutionWindow(),
                maximumBits);
        // 一回分すらlong互換AE2計画へ写せないレシピは、値を丸めず明示的に対象外にする。
        if (safeWindow <= 0L) {
            return null;
        }
        BigCraftingJob<AEKey> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(),
                output,
                requestedAmount,
                bytes,
                patternGeneration,
                recipeGeneration,
                safeWindow,
                PlanningRuntimeEpoch.current(),
                programFingerprint(program));
        return new PreparedBigRootPlan(
                job,
                plan,
                bytes,
                patternGeneration,
                recipeGeneration,
                safeWindow);
    }

    /**
     * 子AE2計画の各カウンタがsigned longへ収まる最大完成品数を二分探索する。
     * CPU byte総量だけのlong超過はBigCapacityCraftingPlanで扱えるため、ここでは除外しない。
     */
    private static long maximumSafeRootWindow(
            CompiledRootProgram<AEKey> program,
            BigInteger requestedAmount,
            long configuredMaximum,
            int maximumBits) {
        long upper = requestedAmount
                .min(BigInteger.valueOf(configuredMaximum))
                .longValueExact();
        // 呼出元は正数注文だけを渡すが、境界を単独利用しても0幅を作らない。
        if (upper <= 0L) {
            return 0L;
        }
        CompiledRootProgram.BigInventorySnapshot<AEKey> emptyInventory =
                program.captureBigInventory(ignored -> BigInteger.ZERO, maximumBits);
        // 設定上限のまま全個別カウンタがlongへ収まる場合は探索を省略する。
        if (fitsLongChild(program, emptyInventory, upper, maximumBits)) {
            return upper;
        }
        // 一回分が収まらない場合、標準AE2 APIへ安全に分割できる正のWindowは存在しない。
        if (!fitsLongChild(program, emptyInventory, 1L, maximumBits)) {
            return 0L;
        }

        long low = 1L;
        long high = upper;
        // lowを安全、highを未確定または危険として保ちながら最大安全値へ収束させる。
        while (low < high) {
            long middle = low + ((high - low + 1L) >>> 1);
            // middleが安全なら下限を上げ、危険なら上限を一つ手前へ戻す。
            if (fitsLongChild(program, emptyInventory, middle, maximumBits)) {
                low = middle;
            } else {
                high = middle - 1L;
            }
        }
        return low;
    }

    /**
     * AEKey、Pattern fingerprint、入力辺、Emitter状態を順序非依存のSHA-256へまとめる。
     * JVM再起動後に世代番号が変わっても、同じ数式Programだけを再開するために使用する。
     */
    public static String programFingerprint(CompiledRootProgram<AEKey> program) {
        Objects.requireNonNull(program, "program");
        synchronized (PROGRAM_FINGERPRINTS) {
            String cached = PROGRAM_FINGERPRINTS.get(program);
            // 同じ不変Programでは計算済みFingerprintをそのまま再利用する。
            if (cached != null) {
                return cached;
            }
            List<String> nodes = new ArrayList<>(program.nodeCount());
            // ノードごとの完全な静的情報を記録し、後でsortして探索順の違いを消す。
            for (int node = 0; node < program.nodeCount(); node++) {
                StringBuilder descriptor =
                        new StringBuilder(FINGERPRINT_DESCRIPTOR_INITIAL_CAPACITY);
                descriptor.append(program.keyAt(node).toTagGeneric())
                        .append("|emitter=")
                        .append(program.isEmittableAt(node));
                CompiledPattern<AEKey> pattern = program.patternAt(node);
                descriptor.append("|pattern=")
                        .append(pattern == null ? "-" : pattern.id());
                // 入力slot順はレシピ意味の一部なので維持し、子AEKeyと量を無損失で含める。
                for (int input = 0; input < program.inputCountAt(node); input++) {
                    descriptor.append("|in:")
                            .append(program.inputKeyAt(node, input).toTagGeneric())
                            .append('@')
                            .append(program.inputAmountAt(node, input));
                }
                nodes.add(descriptor.toString());
            }
            nodes.sort(Comparator.naturalOrder());
            String fingerprint = StableFingerprint.sha256(
                    program.root().toTagGeneric() + "\n" + String.join("\n", nodes));
            PROGRAM_FINGERPRINTS.put(program, fingerprint);
            return fingerprint;
        }
    }

    private static boolean fitsLongChild(
            CompiledRootProgram<AEKey> program,
            CompiledRootProgram.BigInventorySnapshot<AEKey> emptyInventory,
            long requestedAmount,
            int maximumBits) {
        try {
            BigCraftingPlan<AEKey> child = program.planBig(
                    BigInteger.valueOf(requestedAmount),
                    emptyInventory,
                    PlanningGuard.none(),
                    maximumBits);
            return allFitSignedLong(child.patternExecutions())
                    && allFitSignedLong(child.usedInventory())
                    && allFitSignedLong(child.emitted())
                    && allFitSignedLong(child.missing());
        } catch (ArithmeticException | IllegalArgumentException unsafeMagnitude) {
            // 上限超過は探索上の「このWindow幅は使用不可」として扱い、値を切り捨てない。
            return false;
        }
    }

    private static boolean allFitSignedLong(Map<?, BigInteger> counts) {
        // MapはPlanner側で非負検証済みなので、signed long上限との比較だけで無損失性を判定する。
        for (BigInteger amount : counts.values()) {
            // 一つでもLong.MAX_VALUEを超える個別値があれば、そのWindowをAE2へ渡さない。
            if (amount.compareTo(LONG_MAX) > 0) {
                return false;
            }
        }
        return true;
    }

    public record PreparedBigRootPlan(
            BigCraftingJob<AEKey> job,
            BigCraftingPlan<AEKey> symbolicPlan,
            BigInteger reservedBytes,
            long patternGeneration,
            long recipeGeneration,
            long maximumExecutionsPerWindow) {
        public PreparedBigRootPlan {
            Objects.requireNonNull(job, "job");
            Objects.requireNonNull(symbolicPlan, "symbolicPlan");
            Objects.requireNonNull(reservedBytes, "reservedBytes");
            // 保存Jobと計画メタデータのWindow上限が食い違う結果を外へ出さない。
            if (maximumExecutionsPerWindow <= 0L
                    || maximumExecutionsPerWindow != job.maximumExecutionsPerWindow()) {
                throw new IllegalArgumentException("invalid prepared BigInteger execution-window limit");
            }
        }
    }
}
