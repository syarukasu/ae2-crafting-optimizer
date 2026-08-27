package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import java.util.IdentityHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * V2作業台Batchで注文間に共有できる、Patternの静的metadataだけを世代管理する。
 * 在庫、候補の有効性、返却物、Provider状態は動的なので、このCacheへ入れない。
 */
public final class TransactionalExactPatternCache {
    /** 有効なProvider世代と衝突しない、未初期化状態の印。 */
    private static final long NO_GENERATION = Long.MIN_VALUE;
    private static final Map<IPatternDetails, Compilation> CACHE = new IdentityHashMap<>();
    private static long cachedGeneration = NO_GENERATION;

    private TransactionalExactPatternCache() {
    }

    static Lookup lookup(IPatternDetails details) {
        long generation = ProviderPatternGenerationTracker.generation();
        synchronized (CACHE) {
            // Provider Pattern世代が変わった場合、異なるPattern集合のmetadataを混在させない。
            if (cachedGeneration != generation) {
                cachedGeneration = generation;
                CACHE.clear();
            }
            Compilation cached = CACHE.get(details);
            // 同一Pattern identityと同一世代の静的metadataだけを再利用する。
            if (cached != null) {
                OptimizationMetrics.recordTransactionalV2PatternMetadataCacheHit();
                return cached.lookup(generation);
            }
        }

        Compilation compiled = compile(details);
        synchronized (CACHE) {
            // compile中にProvider構成が変化したmetadataは公開せず、従来の直接読取へ戻す。
            if (generation != ProviderPatternGenerationTracker.generation()
                    || cachedGeneration != generation) {
                OptimizationMetrics.recordTransactionalV2PatternMetadataUnstable();
                return Lookup.unstable();
            }
            Compilation existing = CACHE.get(details);
            // 別threadが先に同じPatternを公開した場合は、その確定済み結果を使う。
            if (existing != null) {
                OptimizationMetrics.recordTransactionalV2PatternMetadataCacheHit();
                return existing.lookup(generation);
            }
            CACHE.put(details, compiled);
        }
        OptimizationMetrics.recordTransactionalV2PatternMetadataCacheMiss();
        return compiled.lookup(generation);
    }

    /** 世代が不安定なwaveで、metadataを共有せず現在のPatternを一度だけ直接読む。 */
    static Lookup compileUncached(IPatternDetails details) {
        long generation = ProviderPatternGenerationTracker.generation();
        Compilation compiled = compile(details);
        // 直接読取中にも世代が進んだ場合、そのwaveではV2が所有権を取らない。
        if (generation != ProviderPatternGenerationTracker.generation()) {
            OptimizationMetrics.recordTransactionalV2PatternMetadataUnstable();
            return Lookup.unstable();
        }
        return compiled.lookup(generation);
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
            cachedGeneration = NO_GENERATION;
        }
    }

    private static Compilation compile(IPatternDetails details) {
        // 現行V2 Executorが所有するのは作業台Patternだけであり、加工PatternはAE2へ残す。
        if (!(details instanceof IMolecularAssemblerSupportedPattern)) {
            return Compilation.unsupported();
        }

        IPatternDetails.IInput[] sourceInputs = details.getInputs();
        CompiledInput[] inputs = new CompiledInput[sourceInputs.length];
        // Pattern入力slotの順序を保持したまま、静的な候補と係数だけを一度コンパイルする。
        for (int index = 0; index < sourceInputs.length; index++) {
            IPatternDetails.IInput input = sourceInputs[index];
            GenericStack[] possibleInputs = input.getPossibleInputs();
            // 非正係数または候補なしは従来経路と同じくV2対象外にする。
            if (input.getMultiplier() <= 0L || possibleInputs.length == 0) {
                return Compilation.unsupported();
            }
            inputs[index] = compileInput(input, possibleInputs);
        }

        KeyCounter outputs = new KeyCounter();
        try {
            // 複数出力と同一キーの重複を、従来のKeyCounter会計と同じ順序で集約する。
            for (GenericStack output : details.getOutputs()) {
                // 非正出力はExact Batchとして証明できないため採用しない。
                if (output.amount() <= 0L) {
                    return Compilation.unsupported();
                }
                outputs.set(output.what(), Math.addExact(outputs.get(output.what()), output.amount()));
            }
        } catch (ArithmeticException overflow) {
            return Compilation.unsupported();
        }
        return Compilation.supported(new CompiledPattern(inputs, outputs));
    }

    private static CompiledInput compileInput(
            IPatternDetails.IInput input,
            GenericStack[] possibleInputs) {
        Candidate[] candidates = new Candidate[possibleInputs.length];
        // AE2が返した候補順を変更せず、各候補の一実行量だけをchecked longで固定する。
        for (int index = 0; index < possibleInputs.length; index++) {
            GenericStack candidate = possibleInputs[index];
            long perExecution = 0L;
            // 0は「静的に使用不能」を表す。isValidと在庫量は実行時に必ず再評価する。
            if (candidate != null && candidate.amount() > 0L) {
                try {
                    perExecution = Math.multiplyExact(candidate.amount(), input.getMultiplier());
                } catch (ArithmeticException overflow) {
                    perExecution = 0L;
                }
            }
            candidates[index] = new Candidate(candidate, perExecution);
        }
        return new CompiledInput(input, candidates);
    }

    enum State {
        SUPPORTED,
        UNSUPPORTED,
        UNSTABLE
    }

    record Lookup(State state, @Nullable CompiledPattern pattern, long generation) {
        private static Lookup unstable() {
            return new Lookup(State.UNSTABLE, null, NO_GENERATION);
        }

        boolean isCurrent() {
            return generation == ProviderPatternGenerationTracker.generation();
        }
    }

    record CompiledPattern(CompiledInput[] inputs, KeyCounter outputsPerExecution) {
        CompiledPattern {
            inputs = inputs.clone();
            outputsPerExecution = copyCounter(outputsPerExecution);
        }

        @Override
        public KeyCounter outputsPerExecution() {
            return copyCounter(outputsPerExecution);
        }
    }

    record CompiledInput(IPatternDetails.IInput input, Candidate[] candidates) {
        CompiledInput {
            candidates = candidates.clone();
        }
    }

    record Candidate(@Nullable GenericStack stack, long amountPerExecution) {
    }

    private record Compilation(State state, @Nullable CompiledPattern pattern) {
        private static Compilation supported(CompiledPattern pattern) {
            return new Compilation(State.SUPPORTED, pattern);
        }

        private static Compilation unsupported() {
            return new Compilation(State.UNSUPPORTED, null);
        }

        private Lookup lookup(long generation) {
            return new Lookup(state, pattern, generation);
        }
    }

    private static KeyCounter copyCounter(KeyCounter source) {
        KeyCounter copy = new KeyCounter();
        // mutableなKeyCounterをCache外へ共有せず、読取側ごとに同じ会計を複製する。
        for (var entry : source) {
            copy.add(entry.getKey(), entry.getLongValue());
        }
        return copy;
    }
}
