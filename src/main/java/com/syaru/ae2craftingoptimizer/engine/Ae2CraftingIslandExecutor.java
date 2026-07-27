package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.api.execution.CraftingIslandExecutionOwner;
import com.syaru.ae2craftingoptimizer.api.execution.CraftingIslandRuntime;
import com.syaru.ae2craftingoptimizer.api.execution.CraftingIslandStateUncertainException;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics.CraftingIslandDecision;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;

/**
 * 一つの準備済みCrafting Islandを、入力抽出、Task会計、出力反映の順で原子的に処理する。
 */
public final class Ae2CraftingIslandExecutor {
    /** double電力APIの丸め誤差だけを許容し、実質的な不足を通さない比較幅。 */
    private static final double ENERGY_COMPARISON_EPSILON = 0.01D;
    private static final Set<String> LOGGED_FAILURES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private Ae2CraftingIslandExecutor() {
    }

    /** データパック再読込またはサーバー停止時に、待機Jobの弱参照コンパイル結果を破棄する。 */
    public static void clearCompilationCache() {
        CraftingIslandCompilationCache.clear();
        // 失敗fingerprintも世代をまたいで保持せず、長期稼働時の診断Set増加を防ぐ。
        LOGGED_FAILURES.clear();
    }

    /**
     * 入力が揃った最初の島を一Waveで実行する。
     *
     * <p>対応島が無い場合も入力待ちの場合もNOT_HANDLEDを返し、同じtickの機械Patternを止めない。</p>
     */
    public static int tryExecute(
            CraftingIslandRuntime runtime,
            IEnergyService energyService,
            Level level,
            int maximumPatterns,
            int maximumBits) {
        // 必須Portまたは実行予算が無い呼出しは元のCPUロジックへ戻す。
        if (runtime == null
                || energyService == null
                || level == null
                || maximumPatterns < 1) {
            return CraftingIslandExecutionOwner.NOT_HANDLED;
        }

        Object jobIdentity = runtime.acoIslandJobIdentity();
        Map<IPatternDetails, Object> tasks = runtime.acoIslandTasks();
        // Jobが切り替わった、またはTaskが空なら島を構築しない。
        if (jobIdentity == null || tasks == null || tasks.isEmpty()) {
            return CraftingIslandExecutionOwner.NOT_HANDLED;
        }
        OptimizationMetrics.recordCraftingIslandAttempt();

        long executionStartedAt = System.nanoTime();
        try {
            var compiled = CraftingIslandCompilationCache.getOrCompile(
                    jobIdentity,
                    tasks,
                    level,
                    maximumPatterns,
                    maximumBits,
                    RecipeGenerationTracker.generation());
            // 証明不能Jobは一部だけを触らず、Neo ECO本来のPattern配送へ戻す。
            if (compiled.isEmpty()) {
                OptimizationMetrics.recordCraftingIslandDecision(
                        CraftingIslandDecision.COMPILE_REJECTED);
                return CraftingIslandExecutionOwner.NOT_HANDLED;
            }
            List<CompiledCraftingIsland<AEKey, IPatternDetails>> islands =
                    compiled.get();
            // 実行可能な決定的Patternが一件も無いJobだけを既存経路へ戻す。
            if (islands.isEmpty()) {
                OptimizationMetrics.recordCraftingIslandDecision(
                        CraftingIslandDecision.COMPILE_REJECTED);
                return CraftingIslandExecutionOwner.NOT_HANDLED;
            }

            // Job順で最初に準備できた島だけを実行し、同一tickの独占を避ける。
            for (CompiledCraftingIsland<AEKey, IPatternDetails> island : islands) {
                // 現在のAE2在庫APIへ全差分を正確に渡せない島は通常Window実行へ戻す。
                if (!island.fitsSignedLongRuntime()) {
                    OptimizationMetrics.recordCraftingIslandDecision(
                            CraftingIslandDecision.CAPACITY_WAIT);
                    continue;
                }
                // Task回数がprepare時のコンパイル値と一致しない古い島は破棄する。
                if (!tasksStillMatch(island, tasks)) {
                    OptimizationMetrics.recordCraftingIslandDecision(
                            CraftingIslandDecision.STALE_TASK);
                    continue;
                }
                ICraftingInventory inventory = runtime.acoIslandInventory();
                // 全境界入力が現在CPU在庫へ揃うまで、この島は待機する。
                if (inventory == null || !hasAllInputs(island, inventory)) {
                    OptimizationMetrics.recordCraftingIslandDecision(
                            CraftingIslandDecision.INPUT_WAIT);
                    continue;
                }
                // 入力待ち中はBackend/Provider走査を省き、実行候補になった島だけ設備へ束縛する。
                if (!bindBackend(island, runtime)) {
                    OptimizationMetrics.recordCraftingIslandDecision(
                            CraftingIslandDecision.BACKEND_UNAVAILABLE);
                    continue;
                }
                long capacity = runtime.acoIslandRootExecutionCapacity();
                /*
                 * 容量は同時Transaction枠の存在確認にだけ使う。
                 * Pattern実行数は一つの数式係数なので、注文数量と比較してFallbackさせない。
                 */
                if (capacity <= 0L) {
                    OptimizationMetrics.recordCraftingIslandDecision(
                            CraftingIslandDecision.CAPACITY_WAIT);
                    continue;
                }
                // Backend束縛後にも全Patternの現在所有権を一件ずつ検証する。
                if (!allPatternsSupported(island, runtime)) {
                    OptimizationMetrics.recordCraftingIslandDecision(
                            CraftingIslandDecision.PROVIDER_REJECTED);
                    continue;
                }
                // 最終Requesterを含む全境界出力が今受理可能かを先に確認する。
                if (!canAcceptAllOutputs(island, runtime)) {
                    OptimizationMetrics.recordCraftingIslandDecision(
                            CraftingIslandDecision.OUTPUT_WAIT);
                    continue;
                }
                double requiredPower = requiredPower(island, runtime);
                // double電力範囲外または現在電力不足なら状態を変更せず次tickへ待つ。
                if (!Double.isFinite(requiredPower)
                        || requiredPower < 0.0D
                        || energyService.extractAEPower(
                                        requiredPower,
                                        Actionable.SIMULATE,
                                        PowerMultiplier.CONFIG)
                                + ENERGY_COMPARISON_EPSILON
                                < requiredPower) {
                    OptimizationMetrics.recordCraftingIslandDecision(
                            CraftingIslandDecision.ENERGY_WAIT);
                    continue;
                }
                // JobとAAC構造をcommit直前に再検証し、古いprepareを実行しない。
                if (!runtime.acoIslandJobStillActive(jobIdentity)
                        || !runtime.acoIslandBackendStillAvailable()) {
                    OptimizationMetrics.recordCraftingIslandDecision(
                            CraftingIslandDecision.BACKEND_UNAVAILABLE);
                    continue;
                }
                int result = commit(
                        island,
                        runtime,
                        energyService,
                        inventory,
                        tasks,
                        jobIdentity,
                        requiredPower);
                // 正常完了Waveだけを統計へ加え、待機や停止を処理量として数えない。
                if (result > 0) {
                    OptimizationMetrics.recordCraftingIslandWave(
                            island.tasks().size(),
                            island.logicalExecutions(),
                            System.nanoTime() - executionStartedAt);
                }
                return result;
            }
            // 入力待ち中も処理Patternや別経路を止めず、Neo ECO本体へ同じtickを譲る。
            return CraftingIslandExecutionOwner.NOT_HANDLED;
        } catch (RuntimeException failure) {
            logFailure(runtime, "compile", failure);
            return CraftingIslandExecutionOwner.NOT_HANDLED;
        }
    }

    private static boolean bindBackend(
            CompiledCraftingIsland<AEKey, IPatternDetails> island,
            CraftingIslandRuntime runtime) {
        List<IPatternDetails> patterns =
                new ArrayList<>(island.tasks().size());
        // Task順を維持した一覧を渡し、設備側の選択結果を同じJobで決定的にする。
        for (CompiledCraftingIsland.Task<AEKey, IPatternDetails> task :
                island.tasks()) {
            patterns.add(task.pattern());
        }
        return runtime.acoIslandBindBackend(List.copyOf(patterns));
    }

    private static boolean tasksStillMatch(
            CompiledCraftingIsland<AEKey, IPatternDetails> island,
            Map<IPatternDetails, Object> liveTasks) {
        // 島を構成する全Patternが同じ残回数のままかを確認する。
        for (CompiledCraftingIsland.Task<AEKey, IPatternDetails> task :
                island.tasks()) {
            Object rawProgress = liveTasks.get(task.pattern());
            // Task消滅、Accessor欠落、残回数変更のいずれも古いSnapshotとして破棄する。
            if (!(rawProgress instanceof CraftingTaskProgressAccess progress)
                    || progress.aco$getTaskProgress()
                            != task.executions().longValueExact()) {
                return false;
            }
        }
        return true;
    }

    private static boolean allPatternsSupported(
            CompiledCraftingIsland<AEKey, IPatternDetails> island,
            CraftingIslandRuntime runtime) {
        // 一件でもAACなどの原子実行設備から提供されていなければ、島全体を通常経路へ戻す。
        for (CompiledCraftingIsland.Task<AEKey, IPatternDetails> task :
                island.tasks()) {
            if (!runtime.acoIslandSupportsPattern(task.pattern())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAllInputs(
            CompiledCraftingIsland<AEKey, IPatternDetails> island,
            ICraftingInventory inventory) {
        // 全キーをSIMULATEし、途中までしか抜けない在庫状態をcommit前に検出する。
        for (Map.Entry<AEKey, BigInteger> entry :
                island.boundaryInputs().entrySet()) {
            long requested = entry.getValue().longValueExact();
            long available = inventory.extract(
                    entry.getKey(),
                    requested,
                    Actionable.SIMULATE);
            // 一つでも不足する島は他の処理Pattern出力を待つ。
            if (available != requested) {
                return false;
            }
        }
        return true;
    }

    private static boolean canAcceptAllOutputs(
            CompiledCraftingIsland<AEKey, IPatternDetails> island,
            CraftingIslandRuntime runtime) {
        // 出力をwaitingForへ登録する前に、Requester側を含めて全量受理を検証する。
        for (Map.Entry<AEKey, BigInteger> entry :
                island.boundaryOutputs().entrySet()) {
            // 一件でも受理不能なら入力抽出を開始しない。
            if (!runtime.acoIslandCanAcceptOutput(
                    entry.getKey(),
                    entry.getValue().longValueExact())) {
                return false;
            }
        }
        return true;
    }

    private static double requiredPower(
            CompiledCraftingIsland<AEKey, IPatternDetails> island,
            CraftingIslandRuntime runtime) {
        double perPatternNode = runtime.acoIslandEnergyPerPatternNode();
        // 負、NaN、Infinityは設備実装の破損値なので実行不能として返す。
        if (!Double.isFinite(perPatternNode) || perPatternNode < 0.0D) {
            return Double.NaN;
        }
        /*
         * 注文数量は一括会計する係数であり、設備の物理処理回数ではない。
         * 固有Patternノード数だけを掛け、Long/BigInteger注文で電力を線形増加させない。
         */
        return island.tasks().size() * perPatternNode;
    }

    private static int commit(
            CompiledCraftingIsland<AEKey, IPatternDetails> island,
            CraftingIslandRuntime runtime,
            IEnergyService energyService,
            ICraftingInventory inventory,
            Map<IPatternDetails, Object> liveTasks,
            Object expectedJob,
            double requiredPower) {
        List<Map.Entry<AEKey, Long>> extractedInputs = new ArrayList<>();
        List<Map.Entry<AEKey, Long>> stagedOutputs = new ArrayList<>();
        Map<IPatternDetails, Object> originalTaskOrder =
                new LinkedHashMap<>(liveTasks);
        Map<CraftingTaskProgressAccess, Long> originalProgress =
                new IdentityHashMap<>();
        boolean irreversibleOutputStarted = false;
        double chargedPower = 0.0D;

        try {
            // 入力や電力へ触れる前に全Task参照と残回数を固定する。
            for (CompiledCraftingIsland.Task<AEKey, IPatternDetails> task :
                    island.tasks()) {
                Object rawProgress = liveTasks.get(task.pattern());
                // prepare後のTask差替えは二重会計になるため抽出前に中断する。
                if (!(rawProgress instanceof CraftingTaskProgressAccess progress)
                        || progress.aco$getTaskProgress()
                                != task.executions().longValueExact()) {
                    throw new IllegalStateException(
                            "crafting-island task changed before commit");
                }
                originalProgress.put(progress, progress.aco$getTaskProgress());
            }

            runtime.acoIslandMarkDirty();
            // prepareで検証した境界入力を一件ずつ抜き、失敗時の正確なRollback量を記録する。
            for (Map.Entry<AEKey, BigInteger> entry :
                    island.boundaryInputs().entrySet()) {
                long requested = entry.getValue().longValueExact();
                long before = inventory.extract(
                        entry.getKey(),
                        Long.MAX_VALUE,
                        Actionable.SIMULATE);
                // commit直前の全量が要求未満なら、状態を変更せず通常の入力待ちへ戻す。
                if (before < requested) {
                    throw new IllegalStateException(
                            "crafting-island input changed before extraction");
                }
                long extracted;
                try {
                    extracted = inventory.extract(
                            entry.getKey(),
                            requested,
                            Actionable.MODULATE);
                } catch (RuntimeException extractionFailure) {
                    // listener例外後も実在庫の減少量を観測し、予定量ではなく実数だけを戻す。
                    long observed = observeExtractedAmount(
                            inventory,
                            entry.getKey(),
                            before,
                            requested,
                            extractionFailure);
                    if (observed > 0L) {
                        extractedInputs.add(Map.entry(entry.getKey(), observed));
                    }
                    throw extractionFailure;
                }
                long observed = observeExtractedAmount(
                        inventory,
                        entry.getKey(),
                        before,
                        requested,
                        null);
                if (observed > 0L) {
                    extractedInputs.add(Map.entry(entry.getKey(), observed));
                }
                // 戻り値と実在庫差分の両方が全量一致した場合だけ次の入力へ進む。
                if (extracted != requested || observed != requested) {
                    throw new IllegalStateException(
                            "crafting-island input changed between simulation and extraction");
                }
            }

            // 入力所有権取得後にもJobと設備を再確認し、構造解除中のcommitを止める。
            if (!runtime.acoIslandJobStillActive(expectedJob)
                    || !runtime.acoIslandBackendStillAvailable()
                    || !allPatternsSupported(island, runtime)) {
                throw new IllegalStateException(
                        "crafting-island job, backend, or provider changed during prepare");
            }
            double extractedPower = energyService.extractAEPower(
                    requiredPower,
                    Actionable.MODULATE,
                    PowerMultiplier.CONFIG);
            // 不正な戻り値をinjectへ渡さないよう、復元対象は有限の正数だけを保持する。
            chargedPower = Double.isFinite(extractedPower) && extractedPower > 0.0D
                    ? extractedPower
                    : 0.0D;
            // SIMULATE後に電力が変化した場合はItemと取得済み電力を戻し、出力会計へ進まない。
            if (!Double.isFinite(extractedPower)
                    || extractedPower < 0.0D
                    || extractedPower + ENERGY_COMPARISON_EPSILON < requiredPower) {
                throw new IllegalStateException(
                        "crafting-island energy changed during commit");
            }
            // 電力callbackでJobまたは構造が変わった場合も、Task会計へ進む前に戻す。
            if (!runtime.acoIslandJobStillActive(expectedJob)
                    || !runtime.acoIslandBackendStillAvailable()
                    || !allPatternsSupported(island, runtime)) {
                throw new IllegalStateException(
                        "crafting-island job, backend, or provider changed after energy charge");
            }

            // 島内全Taskを一括完了する。参照と値は抽出前に検証済み。
            for (CompiledCraftingIsland.Task<AEKey, IPatternDetails> task :
                    island.tasks()) {
                Object rawProgress = liveTasks.get(task.pattern());
                CraftingTaskProgressAccess progress =
                        (CraftingTaskProgressAccess) rawProgress;
                progress.aco$setTaskProgress(0L);
                liveTasks.remove(task.pattern());
            }

            List<Map.Entry<AEKey, BigInteger>> outputs =
                    new ArrayList<>(island.boundaryOutputs().entrySet());
            // 最終出力でJobが完了しても他の余剰出力を失わないよう、Requester向けを最後にする。
            outputs.sort((left, right) -> Boolean.compare(
                    runtime.acoIslandIsFinalOutput(left.getKey()),
                    runtime.acoIslandIsFinalOutput(right.getKey())));
            // 全出力をwaiting/in-flightへ先に登録し、配送開始前の失敗なら全件を巻き戻せるようにする。
            for (Map.Entry<AEKey, BigInteger> entry : outputs) {
                long amount = entry.getValue().longValueExact();
                runtime.acoIslandStageOutput(entry.getKey(), amount);
                stagedOutputs.add(Map.entry(entry.getKey(), amount));
            }
            // stage callback後にも同じJobと設備であることを確認してから不可逆配送へ入る。
            if (!runtime.acoIslandJobStillActive(expectedJob)
                    || !runtime.acoIslandBackendStillAvailable()
                    || !allPatternsSupported(island, runtime)) {
                throw new IllegalStateException(
                        "crafting-island job, backend, or provider changed after output staging");
            }

            /*
             * ここから先はCPUの進捗・Requesterへ反映されるため、例外時に通常配送へ戻さず
             * Jobを停止する。全APIはprepare済みの同一server threadで呼ばれる。
             */
            irreversibleOutputStarted = true;
            // Task Map変更を一回の全体通知へまとめ、巨大島でlistenerと同期Packetを連打しない。
            runtime.acoIslandNotifyTaskChanges();
            // 実体化しない中間出力ぶんだけ、通常insertが行う時間進捗減算を代行する。
            for (Map.Entry<AEKey, BigInteger> entry :
                    island.internalOutputs().entrySet()) {
                runtime.acoIslandDecrementInternalOutput(
                        entry.getKey(),
                        entry.getValue().longValueExact());
            }

            // 境界出力だけを通常waitingFor/insert経路へ戻し、内部素材は作らない。
            for (Map.Entry<AEKey, BigInteger> entry : outputs) {
                long amount = entry.getValue().longValueExact();
                long accepted = runtime.acoIslandInsertOutput(entry.getKey(), amount);
                // SIMULATEで証明した全量と異なる結果は推測せず、Job停止へ送る。
                if (accepted != amount) {
                    throw new IllegalStateException(
                            "crafting-island output acceptance changed during commit");
                }
            }
            runtime.acoIslandMarkDirty();
            return 1;
        } catch (RuntimeException failure) {
            // 出力反映前ならTaskとItemを元へ戻し、Neo ECO標準経路が次回継続できる。
            if (!irreversibleOutputStarted) {
                boolean outputsRestored = rollbackStagedOutputs(runtime, stagedOutputs);
                rollbackTasks(liveTasks, originalTaskOrder, originalProgress);
                boolean restored = rollbackInputs(inventory, extractedInputs);
                boolean powerRestored = rollbackPower(energyService, chargedPower);
                runtime.acoIslandMarkDirty();
                // listener状態を証明できない失敗も、通常配送へ戻さず二重実行を防ぐ。
                boolean stateCertain =
                        !(failure instanceof CraftingIslandStateUncertainException);
                if (!outputsRestored || !restored || !stateCertain) {
                    runtime.acoIslandSuspend(
                            "crafting-island reversible accounting could not be proven",
                            failure);
                    logFailure(runtime, island.fingerprint(), failure);
                    return 0;
                }
                // 電力だけ戻せない場合はItem/Task会計を継続可能なまま、一度だけ診断へ残す。
                if (!powerRestored) {
                    logFailure(
                            runtime,
                            island.fingerprint() + "/power-rollback",
                            failure);
                }
                logFailure(runtime, island.fingerprint(), failure);
                return CraftingIslandExecutionOwner.NOT_HANDLED;
            }

            // Requester反映開始後は結果を推測せずJobを停止し、再配送による複製を防ぐ。
            runtime.acoIslandSuspend(
                    "crafting-island commit failed after output accounting started",
                    failure);
            runtime.acoIslandMarkDirty();
            logFailure(runtime, island.fingerprint(), failure);
            return 0;
        }
    }

    private static boolean rollbackStagedOutputs(
            CraftingIslandRuntime runtime,
            List<Map.Entry<AEKey, Long>> stagedOutputs) {
        boolean complete = true;
        // stageと逆順でwaiting/in-flightを外し、配送前のJob会計へ戻す。
        for (int index = stagedOutputs.size() - 1; index >= 0; index--) {
            Map.Entry<AEKey, Long> entry = stagedOutputs.get(index);
            try {
                runtime.acoIslandUnstageOutput(entry.getKey(), entry.getValue());
            } catch (RuntimeException rollbackFailure) {
                // 一件でも戻せなければ呼出側がJobを停止し、通常配送による複製を防ぐ。
                complete = false;
            }
        }
        return complete;
    }

    private static long observeExtractedAmount(
            ICraftingInventory inventory,
            AEKey key,
            long amountBefore,
            long maximumExpected,
            RuntimeException originalFailure) {
        try {
            long amountAfter = inventory.extract(
                    key,
                    Long.MAX_VALUE,
                    Actionable.SIMULATE);
            // 増加または予定以上の減少は別callbackの介入を示し、正確なRollback量を証明できない。
            if (amountAfter > amountBefore) {
                throw new ArithmeticException(
                        "crafting-island input increased during extraction");
            }
            long observed = amountBefore - amountAfter;
            if (observed > maximumExpected) {
                throw new ArithmeticException(
                        "crafting-island input decreased beyond the requested amount");
            }
            return observed;
        } catch (RuntimeException observationFailure) {
            // 元例外がある場合も原因を失わず、不確定会計としてJob停止へ伝える。
            if (originalFailure != null) {
                observationFailure.addSuppressed(originalFailure);
            }
            throw new CraftingIslandStateUncertainException(
                    "crafting-island input mutation could not be measured exactly",
                    observationFailure);
        }
    }

    private static void rollbackTasks(
            Map<IPatternDetails, Object> liveTasks,
            Map<IPatternDetails, Object> originalTaskOrder,
            Map<CraftingTaskProgressAccess, Long> originalProgress) {
        // 値を先に戻し、Map再構築後にNeo ECOが同じ進捗を読むようにする。
        for (Map.Entry<CraftingTaskProgressAccess, Long> entry :
                originalProgress.entrySet()) {
            entry.getKey().aco$setTaskProgress(entry.getValue());
        }
        liveTasks.clear();
        liveTasks.putAll(originalTaskOrder);
    }

    private static boolean rollbackInputs(
            ICraftingInventory inventory,
            List<Map.Entry<AEKey, Long>> extractedInputs) {
        boolean complete = true;
        // 抽出と逆順で戻し、同じキーが複数回現れても実数だけを復元する。
        for (int index = extractedInputs.size() - 1; index >= 0; index--) {
            Map.Entry<AEKey, Long> entry = extractedInputs.get(index);
            try {
                // ICraftingInventoryは容量制限なしの内部在庫なので、例外なく戻れば全量復元となる。
                inventory.insert(
                        entry.getKey(),
                        entry.getValue(),
                        Actionable.MODULATE);
            } catch (RuntimeException rollbackFailure) {
                // 一件でも例外になれば呼出側へ不確定状態を伝え、Jobを停止する。
                complete = false;
            }
        }
        return complete;
    }

    private static boolean rollbackPower(
            IEnergyService energyService,
            double chargedPower) {
        // 電力を一切抜いていない失敗は復元操作を必要としない。
        if (chargedPower <= 0.0D) {
            return true;
        }
        double remainder = energyService.injectPower(
                chargedPower,
                Actionable.MODULATE);
        // extract直後の同一Gridなら通常は全量戻る。残量だけを失敗として扱う。
        return remainder <= ENERGY_COMPARISON_EPSILON;
    }

    private static void logFailure(
            CraftingIslandRuntime runtime,
            String stage,
            Throwable failure) {
        // 診断OFFでは失敗記録Setも増やさず静かにFallbackする。
        if (!ACOConfig.logCompiledCraftingIslands()) {
            return;
        }
        String backend = runtime == null
                ? "unknown"
                : runtime.acoIslandBackendName();
        String key = backend + ':' + stage + ':' + failure.getClass().getName();
        // 同じ設備・段階・例外型は一度だけ記録し、失敗ループでlogを汚染しない。
        if (LOGGED_FAILURES.add(key)) {
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO compiled crafting island fell back or suspended on {} at {}",
                    backend,
                    stage,
                    failure);
        }
    }
}
