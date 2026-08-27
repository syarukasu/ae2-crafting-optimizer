package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * AE2標準Plannerが分岐判断に使う静的情報だけを、Pattern世代ごとに固定する。
 *
 * <p>候補Patternの試行、在庫差分、循環判定、成功した子SimulationのcommitはAE2へ残す。
 * このCacheは候補順を変更せず、同じPattern metadataを注文ごとに再走査する費用だけを除く。</p>
 */
public final class Ae2DecisionProgramCache {
    private static final Set<String> SAFE_PATTERN_TYPES = Set.of(
            "appeng.crafting.pattern.AECraftingPattern",
            "appeng.crafting.pattern.AEProcessingPattern",
            "appeng.crafting.pattern.AEStonecuttingPattern",
            "appeng.crafting.pattern.AESmithingTablePattern");
    private static final Set<String> SAFE_INPUT_TYPES = Set.of(
            "appeng.crafting.pattern.AECraftingPattern$Input",
            "appeng.crafting.pattern.AEProcessingPattern$Input",
            "appeng.crafting.pattern.AEStonecuttingPattern$Input",
            "appeng.crafting.pattern.AESmithingTablePattern$Input");
    private static final String PURE_PROCESSING_INPUT_TYPE =
            "appeng.crafting.pattern.AEProcessingPattern$Input";
    private static final Map<ICraftingService, ServicePrograms> PROGRAMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Ae2DecisionProgramCache() {
    }

    /** 同じservice・世代・出力のDecision Programを再利用する。 */
    public static OutputDecisionProgram getOrCompile(
            ICraftingService service,
            AEKey output) {
        long patternGeneration = ProviderPatternGenerationTracker.generation();
        long recipeGeneration = RecipeGenerationTracker.generation();

        /*
         * 在庫量で候補順を変える実験設定では、注文をまたいだ順序固定を行わない。
         * その場合も一回の計算内ではCraftingCalculationMemoが結果を再利用する。
         */
        if (ACOConfig.deepPatternSelectionByAvailability()) {
            return compile(service, output);
        }

        ServicePrograms servicePrograms;
        synchronized (PROGRAMS) {
            servicePrograms = PROGRAMS.get(service);
            // serviceごとに一つのcacheを持ち、世代交換はcache内部で原子的に行う。
            if (servicePrograms == null) {
                servicePrograms = new ServicePrograms();
                PROGRAMS.put(service, servicePrograms);
            }
        }
        return servicePrograms.getOrCompile(
                service,
                output,
                patternGeneration,
                recipeGeneration);
    }

    /** Provider更新、データパック再読込、server停止時に旧Pattern参照を破棄する。 */
    public static void clear() {
        synchronized (PROGRAMS) {
            PROGRAMS.clear();
        }
    }

    static boolean isCrossCalculationSafePattern(String implementationName) {
        return SAFE_PATTERN_TYPES.contains(implementationName);
    }

    static boolean isCrossCalculationSafeInput(String implementationName) {
        return SAFE_INPUT_TYPES.contains(implementationName);
    }

    static boolean isCrossCalculationSafeValidation(String implementationName) {
        /*
         * Crafting、Stonecutting、Smithingは任意Recipe#matches(Level)へ到達できる。
         * world依存結果を注文間へ固定せず、AEProcessingPatternの純粋key比較だけを共有する。
         */
        return PURE_PROCESSING_INPUT_TYPE.equals(implementationName);
    }

    private static OutputDecisionProgram compile(
            ICraftingService service,
            AEKey output) {
        Collection<IPatternDetails> raw = service.getCraftingFor(output);
        Collection<IPatternDetails> pruned = PatternCandidatePruner.prune(raw, output);
        List<IPatternDetails> ordered = immutableListAllowingNull(pruned);
        IdentityHashMap<IPatternDetails.IInput, InputDecision> inputs = new IdentityHashMap<>();
        boolean crossCalculationSafe = true;

        // AE2が返した順序のまま各Patternを一度だけ読み、分岐順を変えない。
        for (IPatternDetails pattern : ordered) {
            // 外部・不明Patternはその注文のAE2呼出だけに限定し、世代cacheへ固定しない。
            if (pattern == null
                    || !isCrossCalculationSafePattern(pattern.getClass().getName())) {
                crossCalculationSafe = false;
                continue;
            }
            IPatternDetails.IInput[] patternInputs;
            try {
                patternInputs = pattern.getInputs();
            } catch (RuntimeException metadataFailure) {
                // 動的metadataはその計算内にも固定せず、AE2の実呼出へ戻す。
                crossCalculationSafe = false;
                continue;
            }
            // null入力配列はProgram化せず、AE2本体が元の経路で報告する失敗を保持する。
            if (patternInputs == null) {
                crossCalculationSafe = false;
                continue;
            }
            // Pattern内の各slotを一度だけ登録し、AE2の列挙順と配列内容を保存する。
            for (IPatternDetails.IInput input : patternInputs) {
                // 外部・不明Inputのworld依存判定は注文間cacheへ入れない。
                if (input == null
                        || !isCrossCalculationSafeInput(input.getClass().getName())) {
                    crossCalculationSafe = false;
                    continue;
                }
                try {
                    String inputType = input.getClass().getName();
                    inputs.putIfAbsent(input, new InputDecision(
                            input.getPossibleInputs().clone(),
                            isCrossCalculationSafeValidation(inputType),
                            !"appeng.crafting.pattern.AECraftingPattern$Input".equals(inputType)));
                } catch (RuntimeException metadataFailure) {
                    // 壊れた配列をProgramへ固定せず、元のAE2経路に判断させる。
                    crossCalculationSafe = false;
                }
            }
        }
        return new OutputDecisionProgram(ordered, inputs, crossCalculationSafe);
    }

    private static List<IPatternDetails> immutableListAllowingNull(
            Collection<IPatternDetails> patterns) {
        return Collections.unmodifiableList(new ArrayList<>(patterns));
    }

    /** 一出力について、AE2が試す候補順と安全に固定できた入力metadataを保持する。 */
    public static final class OutputDecisionProgram {
        private final List<IPatternDetails> patterns;
        private final IdentityHashMap<IPatternDetails.IInput, InputDecision> inputs;
        private final boolean crossCalculationSafe;

        private OutputDecisionProgram(
                List<IPatternDetails> patterns,
                IdentityHashMap<IPatternDetails.IInput, InputDecision> inputs,
                boolean crossCalculationSafe) {
            this.patterns = patterns;
            this.inputs = new IdentityHashMap<>(inputs);
            this.crossCalculationSafe = crossCalculationSafe;
        }

        public List<IPatternDetails> patterns() {
            return patterns;
        }

        void copyInputsInto(IdentityHashMap<IPatternDetails.IInput, InputDecision> target) {
            target.putAll(inputs);
        }

        private boolean crossCalculationSafe() {
            return crossCalculationSafe;
        }
    }

    /** AE2標準Inputの不変metadataと、同一世代で不変な判定結果を保持する。 */
    static final class InputDecision {
        private final GenericStack[] possibleInputs;
        private final boolean shareValidationResults;
        private final boolean shareRemainingResults;
        private final Map<LevelCandidate, Boolean> valid = new ConcurrentHashMap<>();
        private final Map<LevelCandidate, Optional<AEKey>> fuzzyCraftable = new ConcurrentHashMap<>();
        private final Map<AEKey, Optional<AEKey>> remaining = new ConcurrentHashMap<>();

        InputDecision(
                GenericStack[] possibleInputs,
                boolean shareValidationResults,
                boolean shareRemainingResults) {
            this.possibleInputs = possibleInputs;
            this.shareValidationResults = shareValidationResults;
            this.shareRemainingResults = shareRemainingResults;
        }

        GenericStack[] possibleInputs() {
            return possibleInputs;
        }

        boolean shareValidationResults() {
            return shareValidationResults;
        }

        boolean shareRemainingResults() {
            return shareRemainingResults;
        }

        boolean inputValid(
                AEKey candidate,
                Level level,
                BooleanSupplier lookup) {
            return valid.computeIfAbsent(
                    new LevelCandidate(level.dimension(), candidate),
                    ignored -> lookup.getAsBoolean());
        }

        @Nullable
        AEKey fuzzyCraftable(
                AEKey candidate,
                Level level,
                Supplier<AEKey> lookup) {
            return fuzzyCraftable.computeIfAbsent(
                            new LevelCandidate(level.dimension(), candidate),
                            ignored -> Optional.ofNullable(lookup.get()))
                    .orElse(null);
        }

        @Nullable
        AEKey remainingKey(
                AEKey template,
                Supplier<AEKey> lookup) {
            return remaining.computeIfAbsent(
                            template,
                            ignored -> Optional.ofNullable(lookup.get()))
                    .orElse(null);
        }
    }

    private static final class ServicePrograms {
        private final GenerationScopedDecisionCache<AEKey, OutputDecisionProgram> byOutput =
                new GenerationScopedDecisionCache<>(
                        Math.max(1, ACOConfig.getPatternLookupCacheSize()));

        private ServicePrograms() {
        }

        private OutputDecisionProgram getOrCompile(
                ICraftingService service,
                AEKey output,
                long patternGeneration,
                long recipeGeneration) {
            GenerationScopedDecisionCache.Lookup<OutputDecisionProgram> lookup =
                    byOutput.getOrCompute(
                            output,
                            patternGeneration,
                            recipeGeneration,
                            () -> patternGeneration == ProviderPatternGenerationTracker.generation()
                                    && recipeGeneration == RecipeGenerationTracker.generation(),
                            () -> {
                                OutputDecisionProgram compiled = compile(service, output);
                                return new GenerationScopedDecisionCache.CompiledValue<>(
                                        compiled,
                                        compiled.crossCalculationSafe());
                            });
            OptimizationMetrics.recordDecisionProgramCache(lookup.hit());
            return lookup.value();
        }
    }

    private record LevelCandidate(ResourceKey<Level> dimension, AEKey candidate) {
    }
}
