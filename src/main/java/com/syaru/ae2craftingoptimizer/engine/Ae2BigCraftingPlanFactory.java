package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
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
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/** 厳格に証明できるAE2 Pattern木から、実行量に依存しないBigInteger Jobを一度だけ構築する。 */
public final class Ae2BigCraftingPlanFactory {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    /** Fingerprint用ノード記述の通常長を再確保せず組み立てる初期容量。 */
    private static final int FINGERPRINT_DESCRIPTOR_INITIAL_CAPACITY = 256;
    /** 同一世代Programの正規化Fingerprintを、Windowごとに再構築しない。 */
    private static final Map<CompiledRootProgram<AEKey>, String> PROGRAM_FINGERPRINTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Ae2BigCraftingPlanFactory() {
    }

    /**
     * 既に厳格検証済みのRoot Programから、正確な計画メタデータを作る。
     * long子Jobへ分割できる場合だけ、再起動後も同じ安全幅を使う親Jobを添える。
     */
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
        /*
         * 回帰防止: ACO Issue #79
         * https://github.com/syarukasu/ae2-crafting-optimizer/issues/79
         * 詳細仕様: docs/issues/ISSUE-79.md
         *
         * 8倍圧縮21段では、完成品1個だけでも最下層需要が8^21 = 2^63となる。
         * 以前は「root個数でlong子Jobへ分割できない」ことを「exact計画を作れない」ことと
         * 同一視し、正確に計算済みのBigInteger計画まで破棄していた。
         *
         * exact計画の公開可否とlegacy root-windowの可否は必ず別々に扱うこと。
         * 一回分がlongへ収まらなくてもnullを返したり、偽のwindow=1へ丸めたりせず、
         * EXACT_PATTERN_EXECUTORとしてexact sidecarを保持し、rootWindowJobだけを省略する。
         */
        RootWindowDecision rootWindow = rootWindowDecision(
                program,
                requestedAmount,
                ACOConfig.getBigIntegerExecutionWindow(),
                maximumBits);
        String planningEpoch = PlanningRuntimeEpoch.current();
        String fingerprint = programFingerprint(program);
        BigCraftingJob<AEKey> rootWindowJob = null;
        // ルート個数で安全に分割できる計画だけ、旧checked-long子Job用の親Jobを用意する。
        if (rootWindow.mode() == ExecutionMode.ROOT_WINDOWS) {
            rootWindowJob = BigCraftingJob.rootWindowed(
                    UUID.randomUUID(),
                    output,
                    requestedAmount,
                    bytes,
                    patternGeneration,
                    recipeGeneration,
                    rootWindow.maximumRootExecutions(),
                    planningEpoch,
                    fingerprint);
        }
        return new PreparedBigRootPlan(
                rootWindowJob,
                plan,
                bytes,
                patternGeneration,
                recipeGeneration,
                rootWindow.mode(),
                rootWindow.maximumRootExecutions(),
                planningEpoch,
                fingerprint);
    }

    /**
     * 子AE2計画の各カウンタがsigned longへ収まる最大完成品数を二分探索する。
     * CPU byte総量だけのlong超過はBigCapacityCraftingPlanで扱えるため、ここでは除外しない。
     * Issue #79の再発を防ぐため、一回分すら収まらない場合は失敗ではなくExact方式を返す。
     */
    static <K> RootWindowDecision rootWindowDecision(
            CompiledRootProgram<K> program,
            BigInteger requestedAmount,
            long configuredMaximum,
            int maximumBits) {
        long upper = requestedAmount
                .min(BigInteger.valueOf(configuredMaximum))
                .longValueExact();
        // 呼出元は正数注文だけを渡すが、境界を単独利用しても0幅を作らない。
        if (upper <= 0L) {
            return new RootWindowDecision(ExecutionMode.EXACT_PATTERN_EXECUTOR, 0L);
        }
        CompiledRootProgram.BigInventorySnapshot<K> emptyInventory =
                program.captureBigInventory(ignored -> BigInteger.ZERO, maximumBits);
        // 設定上限のまま全個別カウンタがlongへ収まる場合は探索を省略する。
        if (fitsLongChild(program, emptyInventory, upper, maximumBits)) {
            return new RootWindowDecision(ExecutionMode.ROOT_WINDOWS, upper);
        }
        /*
         * 一回分が収まらない場合もBigInteger計画自体は正確である。
         * rootを0件へ丸めず、Pattern単位のExact executorが必要な計画として残す。
         */
        if (!fitsLongChild(program, emptyInventory, 1L, maximumBits)) {
            return new RootWindowDecision(ExecutionMode.EXACT_PATTERN_EXECUTOR, 0L);
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
        return new RootWindowDecision(ExecutionMode.ROOT_WINDOWS, low);
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
            String fingerprint = computeProgramFingerprint(
                    program,
                    key -> key.toTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()).toString());
            PROGRAM_FINGERPRINTS.put(program, fingerprint);
            return fingerprint;
        }
    }

    /**
     * 不変Programを、呼出側が与える安定キー表現から順序非依存の指紋へ変換する。
     */
    static <K> String computeProgramFingerprint(
            CompiledRootProgram<K> program,
            Function<? super K, String> encodeKey) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(encodeKey, "encodeKey");
        List<String> nodes = new ArrayList<>(program.nodeCount());
        // ノードごとの完全な静的情報を記録し、後でsortして探索順の違いを消す。
        for (int node = 0; node < program.nodeCount(); node++) {
            StringBuilder descriptor =
                    new StringBuilder(FINGERPRINT_DESCRIPTOR_INITIAL_CAPACITY);
            descriptor.append(encodeKey.apply(program.keyAt(node)))
                    .append("|emitter=")
                    .append(program.isEmittableAt(node));
            CompiledPattern<K> pattern = program.patternAt(node);
            descriptor.append("|pattern=")
                    .append(pattern == null ? "-" : pattern.id())
                    .append("|output=")
                    .append(program.outputAmountAt(node));
            // 入力slot順とslot内候補順を維持し、全候補のキーと量を指紋へ含める。
            for (int input = 0;
                    input < program.inputCountAt(node);
                    input++) {
                descriptor.append("|slot:")
                        .append(input);
                // 同じPattern世代で候補集合や順序が変わった場合も別Programとして検出する。
                for (int alternative = 0;
                        alternative
                                < program.inputAlternativeCountAt(
                                        node,
                                        input);
                        alternative++) {
                    descriptor.append("|alt:")
                            .append(encodeKey.apply(
                                    program.inputAlternativeKeyAt(
                                            node,
                                            input,
                                            alternative)))
                            .append('@')
                            .append(program.inputAlternativeAmountAt(
                                    node,
                                    input,
                                    alternative));
                }
            }
            nodes.add(descriptor.toString());
        }
        nodes.sort(Comparator.naturalOrder());
        return StableFingerprint.sha256(
                encodeKey.apply(program.root())
                        + "\n"
                        + String.join("\n", nodes));
    }

    private static <K> boolean fitsLongChild(
            CompiledRootProgram<K> program,
            CompiledRootProgram.BigInventorySnapshot<K> emptyInventory,
            long requestedAmount,
            int maximumBits) {
        try {
            BigCraftingPlan<K> child = program.planBig(
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

    public enum ExecutionMode {
        /** 同じ完成品の正数個を、AE2標準long子Jobへ安全に分割できる。 */
        ROOT_WINDOWS,
        /** 完成品一個の内部がlongを超えるため、正確なPattern単位executorが必要。 */
        EXACT_PATTERN_EXECUTOR
    }

    static record RootWindowDecision(
            ExecutionMode mode,
            long maximumRootExecutions) {
        RootWindowDecision {
            Objects.requireNonNull(mode, "mode");
            // root-window方式だけが正のWindowを持ち、Exact方式は0を明示する。
            if ((mode == ExecutionMode.ROOT_WINDOWS && maximumRootExecutions <= 0L)
                    || (mode == ExecutionMode.EXACT_PATTERN_EXECUTOR
                            && maximumRootExecutions != 0L)) {
                throw new IllegalArgumentException("invalid BigInteger root-window decision");
            }
        }
    }

    public record PreparedBigRootPlan(
            @Nullable BigCraftingJob<AEKey> rootWindowJob,
            BigCraftingPlan<AEKey> symbolicPlan,
            BigInteger reservedBytes,
            long patternGeneration,
            long recipeGeneration,
            ExecutionMode executionMode,
            long maximumRootExecutionsPerWindow,
            String planningEpoch,
            String programFingerprint) {
        public PreparedBigRootPlan {
            Objects.requireNonNull(symbolicPlan, "symbolicPlan");
            Objects.requireNonNull(reservedBytes, "reservedBytes");
            Objects.requireNonNull(executionMode, "executionMode");
            planningEpoch = Objects.requireNonNull(planningEpoch, "planningEpoch");
            programFingerprint = Objects.requireNonNull(programFingerprint, "programFingerprint");
            // root-window方式では、永続Jobと計画メタデータのWindow上限を一致させる。
            if (executionMode == ExecutionMode.ROOT_WINDOWS
                    && (rootWindowJob == null
                            || maximumRootExecutionsPerWindow <= 0L
                            || maximumRootExecutionsPerWindow
                                    != rootWindowJob.maximumExecutionsPerWindow())) {
                throw new IllegalArgumentException("invalid prepared BigInteger root-window job");
            }
            // Exact方式は誤ってchecked-long子Jobへ渡らないよう、JobとWindowを持たない。
            if (executionMode == ExecutionMode.EXACT_PATTERN_EXECUTOR
                    && (rootWindowJob != null || maximumRootExecutionsPerWindow != 0L)) {
                throw new IllegalArgumentException("invalid prepared BigInteger exact-pattern plan");
            }
            // 再計算に必要な識別子がない計画は、永続実行へ渡さない。
            if (planningEpoch.isBlank() || programFingerprint.isBlank()) {
                throw new IllegalArgumentException("missing prepared BigInteger planning identity");
            }
        }

        public boolean supportsRootWindows() {
            return executionMode == ExecutionMode.ROOT_WINDOWS;
        }
    }
}
