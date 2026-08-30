package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.CraftingCalculationCacheAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import com.syaru.ae2craftingoptimizer.integration.AppliedECompatibility;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class CraftingCalculationDeduplicator {
    /** ServiceStateはCraftingServiceを参照しないため、この弱集合はGrid破棄を妨げない。 */
    private static final Map<ServiceState, Boolean> REGISTERED_STATES = new WeakHashMap<>();
    private static final long NANOS_PER_TICK = 50_000_000L;

    private CraftingCalculationDeduplicator() {
    }

    /** Mixin field初期化時に、Service自身が所有する独立cacheを一つ作る。 */
    public static ServiceState createServiceState() {
        ServiceState state = new ServiceState();
        synchronized (REGISTERED_STATES) {
            REGISTERED_STATES.put(state, Boolean.TRUE);
        }
        return state;
    }

    public static Future<ICraftingPlan> findActive(
            CraftingService craftingService,
            IGrid grid,
            Level level,
            ICraftingSimulationRequester requester,
            IActionSource actionSource,
            AEKey output,
            long amount,
            CalculationStrategy strategy) {
        if (!ACOConfig.deduplicateActiveCraftingCalculations()) {
            return null;
        }

        RequestKey requestKey = RequestKey.of(
                grid,
                level,
                requester,
                actionSource,
                output,
                amount,
                strategy);
        long now = System.nanoTime();
        ServiceState state = state(craftingService);

        synchronized (state.lock) {
            Map<RequestKey, Entry> serviceEntries = state.active;
            cleanupActive(state, serviceEntries, now);
            Entry entry = serviceEntries.get(requestKey);
            if (entry == null || !entry.isReusable(now)) {
                return currentOrDiscard(
                        findCompletedLocked(state, requestKey, now),
                        requestKey,
                        grid,
                        true);
            }

            Future<ICraftingPlan> subscriber = entry.future.acquire();
            // 最後の購読者がキャンセルした直後は、古い共有Futureを再利用しない。
            if (subscriber == null) {
                return currentOrDiscard(
                        findCompletedLocked(state, requestKey, now),
                        requestKey,
                        grid,
                        true);
            }
            Future<ICraftingPlan> current = currentOrDiscard(
                    subscriber,
                    requestKey,
                    grid,
                    false);
            // 世代不一致で解放した購読者をcache hitとして数えない。
            if (current == null) {
                return null;
            }
            if (ACOConfig.logCraftingCalculationDeduplication()) {
                AE2CraftingOptimizer.LOGGER.debug(
                        "Reused active AE2 crafting calculation for {} x{} ({})",
                        output.getId(),
                        amount,
                        strategy);
            }
            OptimizationMetrics.recordActiveCalculationDedupHit();
            return current;
        }
    }

    private static Future<ICraftingPlan> currentOrDiscard(
            Future<ICraftingPlan> candidate,
            RequestKey requestKey,
            IGrid grid,
            boolean completed) {
        // cache missには解放すべき購読所有権がない。
        if (candidate == null) {
            return null;
        }
        // 取得中も三世代が同じなら、同一計算を共有してよい。
        if (requestKey.matchesCurrent(grid)) {
            // 完了cacheのhitは、世代検証を通過して実際に返す時だけ記録する。
            if (completed) {
                if (ACOConfig.logCraftingCalculationDeduplication()) {
                    AE2CraftingOptimizer.LOGGER.debug(
                            "Reused completed AE2 crafting plan for {} x{} ({})",
                            requestKey.output.getId(),
                            requestKey.amount,
                            requestKey.strategy);
                }
                OptimizationMetrics.recordCompletedPlanCacheHit();
            }
            return candidate;
        }
        /*
         * Issue #167: 世代変更後の新要求を旧Futureへ参加させない。active購読者のcancelは
         * この呼出しの所有権だけを解放し、他の購読者が残る元計算は停止しない。
         */
        candidate.cancel(false);
        OptimizationMetrics.recordCalculationDedupStaleRejection();
        return null;
    }

    public static Future<ICraftingPlan> remember(
            CraftingService craftingService,
            Level level,
            ICraftingSimulationRequester requester,
            AEKey output,
            long amount,
            CalculationStrategy strategy,
            CraftingCalculationSnapshotContext.CalculationRevision calculationRevision,
            Future<ICraftingPlan> future) {
        // 重複排除OFFまたは再利用不能な戻り値は、所有権を変更せずそのまま返す。
        if (!ACOConfig.deduplicateActiveCraftingCalculations()
                || future == null
                || future.isDone()
                || future.isCancelled()) {
            return future;
        }

        RequestKey requestKey = RequestKey.of(
                level,
                requester,
                output,
                amount,
                strategy,
                calculationRevision);
        long now = System.nanoTime();
        ServiceState state = state(craftingService);

        synchronized (state.lock) {
            Map<RequestKey, Entry> serviceEntries = state.active;
            cleanupActive(state, serviceEntries, now);
            Entry existing = serviceEntries.get(requestKey);
            if (existing != null && existing.isReusable(now)) {
                // HEAD注入ですでに取得した購読者は、RETURN注入で再取得・cancelしない。
                if (existing.future.owns(future)) {
                    return future;
                }
                // AE2が同じ要求を同時に作った場合は、最初のFutureの所有権を一つ増やす。
                Future<ICraftingPlan> subscriber = existing.future.acquire();
                if (subscriber != null) {
                    // 既存計算を返す場合、新しく生成された重複Futureだけを停止する。
                    future.cancel(false);
                    return subscriber;
                }
            }
            SharedCalculationFuture<ICraftingPlan> shared = new SharedCalculationFuture<>(future);
            int evicted = reserveActiveCalculationSlot(
                    serviceEntries,
                    ACOConfig.getActiveCalculationDeduplicationMaximumEntries());
            OptimizationMetrics.recordActiveCalculationEvictions(evicted);
            serviceEntries.put(requestKey, new Entry(shared, now));
            OptimizationMetrics.recordActiveCalculationRegistration();
            return shared.acquireOrDelegate();
        }
    }

    public static void clear(String reason) {
        for (ServiceState state : registeredStatesSnapshot()) {
            synchronized (state.lock) {
                // 再読込時は索引だけを破棄し、既に返したFutureの所有権やcancel状態を変えない。
                state.active.clear();
                state.completed.clear();
            }
        }
        if (ACOConfig.logCraftingCalculationDeduplication()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared active AE2 crafting calculation table: {}", reason);
        }
    }

    public static void clearCompleted(String reason) {
        for (ServiceState state : registeredStatesSnapshot()) {
            synchronized (state.lock) {
                state.completed.clear();
            }
        }
        if (ACOConfig.logCraftingCalculationDeduplication()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared completed AE2 crafting plan cache: {}", reason);
        }
    }

    private static Future<ICraftingPlan> findCompletedLocked(
            ServiceState state,
            RequestKey requestKey,
            long now) {
        if (!ACOConfig.cacheCompletedCraftingPlans()) {
            return null;
        }

        Map<RequestKey, CompletedEntry> serviceEntries = state.completed;
        cleanupCompleted(serviceEntries, now);
        CompletedEntry entry = serviceEntries.get(requestKey);
        if (entry == null || !entry.isReusable(now)) {
            return null;
        }

        return java.util.concurrent.CompletableFuture.completedFuture(entry.snapshot.materialize());
    }

    private static void cleanupActive(
            ServiceState state,
            Map<RequestKey, Entry> serviceEntries,
            long now) {
        Iterator<Map.Entry<RequestKey, Entry>> iterator = serviceEntries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RequestKey, Entry> mapEntry = iterator.next();
            Entry entry = mapEntry.getValue();
            if (entry.future.isDone() && !entry.future.isCancelled()) {
                rememberCompleted(state, mapEntry.getKey(), entry, now);
            }
            if (!entry.isReusable(now)) {
                iterator.remove();
            }
        }
    }

    private static void rememberCompleted(
            ServiceState state,
            RequestKey requestKey,
            Entry entry,
            long now) {
        if (!ACOConfig.cacheCompletedCraftingPlans()) {
            return;
        }

        ICraftingPlan plan;
        try {
            plan = entry.future.delegate().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException | CancellationException failedCalculation) {
            return;
        }

        if (!isCompletedPlanCacheable(plan)) {
            return;
        }
        CompletedCraftingPlanSnapshot snapshot = CompletedCraftingPlanSnapshot.capture(plan);
        // 外部Planを純正CraftingPlanへ変換せず、固有の提出契約を維持する。
        if (snapshot == null) {
            return;
        }

        Map<RequestKey, CompletedEntry> completedEntries = state.completed;
        cleanupCompleted(completedEntries, now);
        int maximumEntries = ACOConfig.getCompletedCraftingPlanCacheSize();
        // 上限0はcache無効として扱い、設定値に反して一件だけ残さない。
        if (!reserveCompletedPlanSlot(completedEntries, maximumEntries)) {
            return;
        }
        completedEntries.put(requestKey, new CompletedEntry(snapshot, now));
        OptimizationMetrics.recordCompletedPlanCacheStore();
    }

    private static ServiceState state(CraftingService craftingService) {
        if (!(craftingService instanceof CraftingCalculationCacheAccess access)) {
            throw new IllegalStateException(
                    "ACO crafting calculation cache access is unavailable on "
                            + craftingService.getClass().getName());
        }
        return access.aco$getCraftingCalculationCacheState();
    }

    private static java.util.List<ServiceState> registeredStatesSnapshot() {
        synchronized (REGISTERED_STATES) {
            return new ArrayList<>(REGISTERED_STATES.keySet());
        }
    }

    /** 新しい一件を入れた後も上限内になるよう、既存entryだけを破棄する。 */
    static boolean reserveCompletedPlanSlot(Map<?, ?> entries, int maximumEntries) {
        // 非正数上限では既存値も残さず、呼出側へ保存禁止を返す。
        if (maximumEntries <= 0) {
            entries.clear();
            return false;
        }
        Iterator<?> iterator = entries.keySet().iterator();
        // 実行中Futureには触れず、完了Snapshotだけを新上限の一件手前まで縮小する。
        while (entries.size() >= maximumEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    /** 実行中Futureをcancelせず、共有索引だけをaccess-order最古から外す。 */
    static int reserveActiveCalculationSlot(Map<?, ?> entries, int maximumEntries) {
        // Config最小値を破る呼出しは、暗黙の無制限cacheへ読み替えない。
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("active calculation cache size must be positive");
        }
        int evicted = 0;
        Iterator<?> iterator = entries.keySet().iterator();
        // 新しい一件を入れた後も固定上限内になるまで、索引だけを縮小する。
        while (entries.size() >= maximumEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
            evicted++;
        }
        return evicted;
    }

    private static boolean isCompletedPlanCacheable(ICraftingPlan plan) {
        if (plan == null) {
            return false;
        }

        // Wide実行計画は提出Claimと親Jobを一度だけ所有するため、成功計画キャッシュへ入れない。
        if (Ae2CraftingPlanSidecars.isWide(plan) && !plan.simulation()) {
            return false;
        }

        if (AppliedECompatibility.requiresFreshCalculation(plan)) {
            // 一時Patternを再利用するとKnowledgeServiceへの追加処理を通らないため保持しない。
            OptimizationMetrics.recordAppliedECompletedPlanCacheBypass();
            return false;
        }

        if (plan.simulation() || !plan.missingItems().isEmpty()) {
            return true;
        }

        return ACOConfig.cacheSuccessfulCompletedCraftingPlans();
    }

    private static void cleanupCompleted(Map<RequestKey, CompletedEntry> serviceEntries, long now) {
        Iterator<CompletedEntry> iterator = serviceEntries.values().iterator();
        while (iterator.hasNext()) {
            CompletedEntry entry = iterator.next();
            if (!entry.isReusable(now)) {
                iterator.remove();
            }
        }
    }

    private static long maximumAgeNanos() {
        return ACOConfig.getActiveCalculationDeduplicationWindowTicks() * NANOS_PER_TICK;
    }

    private static long completedMaximumAgeNanos() {
        return ACOConfig.getCompletedCraftingPlanCacheTtlTicks() * NANOS_PER_TICK;
    }

    private record RequestKey(
            ResourceLocation dimension,
            RequesterIdentity requester,
            ActionSourceIdentity actionSource,
            AEKey output,
            long amount,
            CalculationStrategy strategy,
            long storageGeneration,
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
        private boolean matchesCurrent(IGrid grid) {
            return StorageRevisionTracker.capture(grid).revision() == storageGeneration
                    && ProviderPatternGenerationTracker.generation() == patternGeneration
                    && RecipeGenerationTracker.generation() == recipeGeneration
                    && PlanningConfigurationRevisionTracker.isCurrent(configurationRevision);
        }

        private static RequestKey of(
                IGrid grid,
                Level level,
                ICraftingSimulationRequester requester,
                IActionSource actionSource,
                AEKey output,
                long amount,
                CalculationStrategy strategy) {
            ResourceLocation dimension = level.dimension().location();
            return new RequestKey(
                    dimension,
                    new RequesterIdentity(requester),
                    new ActionSourceIdentity(actionSource),
                    output,
                    amount,
                    strategy,
                    StorageRevisionTracker.refreshAndCapture(grid).revision(),
                    ProviderPatternGenerationTracker.generation(),
                    RecipeGenerationTracker.generation(),
                    PlanningConfigurationRevisionTracker.current());
        }

        private static RequestKey of(
                Level level,
                ICraftingSimulationRequester requester,
                AEKey output,
                long amount,
                CalculationStrategy strategy,
            CraftingCalculationSnapshotContext.CalculationRevision calculationRevision) {
            return new RequestKey(
                    level.dimension().location(),
                    new RequesterIdentity(requester),
                    new ActionSourceIdentity(calculationRevision.actionSource()),
                    output,
                    amount,
                    strategy,
                    calculationRevision.storage().revision(),
                    calculationRevision.patternGeneration(),
                    calculationRevision.recipeGeneration(),
                    calculationRevision.configurationRevision());
        }
    }

    /** Issue #167: requesterを衝突可能な32bit hashではなく参照同一性で識別する。 */
    static final class RequesterIdentity {
        private final Object requester;
        private final int hashCode;

        RequesterIdentity(Object requester) {
            this.requester = java.util.Objects.requireNonNull(requester, "requester");
            this.hashCode = System.identityHashCode(requester);
        }

        @Override
        public boolean equals(Object other) {
            // Issue #167: equals実装が同じ別requesterへsecurity contextを共有しない。
            return other instanceof RequesterIdentity identity
                    && requester == identity.requester;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /** Issue #167: 同requesterが返す異なるsecurity context間でFutureを共有しない。 */
    static final class ActionSourceIdentity {
        private final Object actionSource;
        private final int hashCode;

        ActionSourceIdentity(Object actionSource) {
            this.actionSource = actionSource;
            this.hashCode = System.identityHashCode(actionSource);
        }

        @Override
        public boolean equals(Object other) {
            // nullを含む参照同一性だけを、同じセキュリティ条件とみなす。
            return other instanceof ActionSourceIdentity identity
                    && actionSource == identity.actionSource;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private record Entry(SharedCalculationFuture<ICraftingPlan> future, long createdAtNanos) {
        private boolean isReusable(long now) {
            return !future.isDone()
                    && !future.isCancelled()
                    && now - createdAtNanos <= maximumAgeNanos();
        }
    }

    private record CompletedEntry(CompletedCraftingPlanSnapshot snapshot, long createdAtNanos) {
        private boolean isReusable(long now) {
            return now - createdAtNanos <= completedMaximumAgeNanos();
        }
    }

    /** 一つのCraftingServiceだけが所有し、別Gridとはロックも索引も共有しない。 */
    public static final class ServiceState {
        private final Object lock = new Object();
        private final LinkedHashMap<RequestKey, Entry> active =
                new LinkedHashMap<>(16, 0.75F, true);
        private final LinkedHashMap<RequestKey, CompletedEntry> completed =
                new LinkedHashMap<>(16, 0.75F, true);

        private ServiceState() {
        }
    }
}
