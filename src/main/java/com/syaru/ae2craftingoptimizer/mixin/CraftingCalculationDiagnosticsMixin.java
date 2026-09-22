package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import com.google.common.base.Stopwatch;
import com.syaru.ae2craftingoptimizer.engine.PlanningCancelledException;
import com.syaru.ae2craftingoptimizer.engine.PlanningServerTasks;
import com.syaru.ae2craftingoptimizer.engine.Ae2ImmutablePlanningGraphCache;
import com.syaru.ae2craftingoptimizer.access.CraftingCalculationThreadAccess;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDiagnostics;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationSnapshotContext;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.PlanningConfigurationRevisionTracker;
import com.syaru.ae2craftingoptimizer.optimization.ServerPlanningThreadGuard;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingShadowValidator;
import com.syaru.ae2craftingoptimizer.engine.Ae2AuthoritativeCraftingPlanner;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.Ae2PlanningCaptureCoordinator;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.StorageRevisionTracker;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationDiagnosticsMixin implements CraftingCalculationThreadAccess {
    @Invoker("handlePausing")
    protected abstract void aco$invokeHandlePausing() throws InterruptedException;

    @Shadow @Final private Object monitor;
    @Shadow @Final private Stopwatch watch;
    @Shadow private boolean running;
    @Shadow private int incTime;
    @Unique private boolean aco$detachedPlanning;
    @Unique private Ae2ImmutablePlanningGraphCache.RootCapture aco$nativeSnapshot;
    @Unique private boolean aco$usedNativeSnapshot;
    @Unique private boolean aco$workerRegistered;
    @Unique private boolean aco$nativeBindingCurrent;

    @Shadow
    @Final
    private AEKey output;

    @Shadow
    @Final
    private long requestedAmount;

    @Shadow
    @Final
    private CalculationStrategy strategy;

    @Shadow
    @Final
    private NetworkCraftingSimulationState networkInv;

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private ICraftingSimulationRequester simRequester;

    @Unique
    private long aco$calculationStartedAt;

    @Unique
    private long aco$calculationId;

    @Unique
    private int aco$gridIdentity;

    @Unique
    private Ae2CraftingShadowValidator.Capture aco$shadowCapture;

    @Unique
    private Ae2AuthoritativeCraftingPlanner.Capture aco$authoritativeCapture;

    @Unique
    private ICraftingPlan aco$authoritativePlan;

    @Unique
    private boolean aco$usedAuthoritativePlan;

    @Unique
    private StorageRevisionTracker.RevisionToken aco$storageRevision;

    @Unique
    private long aco$patternGeneration;

    @Unique
    private long aco$recipeGeneration;

    @Unique
    private long aco$configurationRevision;

    @Unique
    private CraftingCalculationSnapshotContext.CalculationRevision aco$calculationRevision;

    @Unique
    private IActionSource aco$actionSource;

    @Unique
    private boolean aco$capturePlanningRevision;

    @Unique
    private boolean aco$usesDedupFrame;

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/IGrid;getStorageService()Lappeng/api/networking/storage/IStorageService;"),
            require = 1)
    private IStorageService aco$captureStorageGenerationBeforeSnapshot(IGrid grid) {
        IStorageService storageService = grid.getStorageService();
        boolean decisionFlowLogging = ACOConfig.logCraftingDecisionFlow();
        // 診断OFFでは、全クラフト計算で共有Atomic IDを更新しない。
        aco$calculationId = decisionFlowLogging
                ? CraftingCalculationDiagnostics.nextCalculationId()
                : 0L;
        aco$gridIdentity = decisionFlowLogging
                ? System.identityHashCode(grid)
                : 0;
        aco$usesDedupFrame = CraftingCalculationSnapshotContext.matches(simRequester);
        aco$capturePlanningRevision = aco$usesDedupFrame
                || Ae2AuthoritativeCraftingPlanner.planningEnabled()
                || ACOConfig.enableCraftingEngineShadowMode();
        /*
         * Issue #167: 互換呼出しがworkerからCraftingCalculationを構築しても、ACOは
         * live Gridを追加走査しない。AE2標準constructorの責務は変更しない。
         */
        if (!aco$capturePlanningRevision
                || !ServerPlanningThreadGuard.canCapture(level)) {
            aco$capturePlanningRevision = false;
            return storageService;
        }
        // Config変更をstorage/pattern/recipe captureの前後で検出する基準を先に固定する。
        aco$configurationRevision = PlanningConfigurationRevisionTracker.current();
        // Issue #167: AE2の遅延在庫cacheをserver threadで確定してからsnapshot世代を固定する。
        aco$storageRevision = StorageRevisionTracker.refreshAndCapture(grid);
        aco$patternGeneration = ProviderPatternGenerationTracker.generation();
        aco$recipeGeneration = RecipeGenerationTracker.generation();
        return storageService;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aco$captureGrid(
            Level level,
            IGrid grid,
            ICraftingSimulationRequester requester,
            GenericStack output,
            CalculationStrategy strategy,
            CallbackInfo ci) {
        // revisionを要求しない通常AE2計算では、追加captureを構築しない。
        if (!aco$capturePlanningRevision || aco$storageRevision == null) {
            return;
        }
        /*
         * Issue #167: dedup keyにはrequester getterを再呼出しせず、AE2が実際に
         * NetworkCraftingSimulationStateへ渡した同一ActionSource参照を保存する。
         */
        aco$calculationRevision = new CraftingCalculationSnapshotContext.CalculationRevision(
                aco$storageRevision,
                aco$patternGeneration,
                aco$recipeGeneration,
                aco$configurationRevision,
                aco$actionSource);
        KeyCounter networkSnapshot =
                ((NetworkCraftingSimulationStateAccessor) (Object) networkInv).aco$getNetworkSnapshot();
        // Issue #167: 在庫列挙中に変化したSnapshotを高速経路へ渡さず、AE2標準結果だけを使う。
        if (!StorageRevisionTracker.isCurrent(aco$storageRevision)) {
            CraftingCalculationDiagnostics.logCapture(
                    aco$calculationId,
                    aco$gridIdentity,
                    this.output,
                    this.requestedAmount,
                    aco$storageRevision.revision(),
                    aco$patternGeneration,
                    aco$recipeGeneration,
                    aco$configurationRevision,
                    false);
            aco$publishDedupRevision();
            return;
        }
        // Issue #167: constructor中にPattern/Recipe世代が変わったsnapshotを高速経路へ渡さない。
        if (aco$patternGeneration != ProviderPatternGenerationTracker.generation()
                || aco$recipeGeneration != RecipeGenerationTracker.generation()
                || !PlanningConfigurationRevisionTracker.isCurrent(
                        aco$configurationRevision)) {
            CraftingCalculationDiagnostics.logCapture(
                    aco$calculationId,
                    aco$gridIdentity,
                    this.output,
                    this.requestedAmount,
                    aco$storageRevision.revision(),
                    aco$patternGeneration,
                    aco$recipeGeneration,
                    aco$configurationRevision,
                    false);
            aco$publishDedupRevision();
            return;
        }
        long captureStartedAt = System.nanoTime();
        Ae2PlanningCaptureCoordinator.CaptureBundle captureBundle =
                Ae2PlanningCaptureCoordinator.capture(
                level,
                grid,
                aco$actionSource,
                networkSnapshot,
                this.output,
                this.requestedAmount,
                aco$storageRevision,
                aco$patternGeneration,
                aco$recipeGeneration,
                aco$configurationRevision);
        OptimizationMetrics.recordPlanningCapture(
                captureBundle.shadow() != null || captureBundle.authoritative() != null,
                System.nanoTime() - captureStartedAt);
        aco$shadowCapture = captureBundle.shadow();
        aco$authoritativeCapture = captureBundle.authoritative();
        CraftingCalculationDiagnostics.logCapture(
                aco$calculationId,
                aco$gridIdentity,
                this.output,
                this.requestedAmount,
                aco$storageRevision.revision(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.patternGeneration(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.recipeGeneration(),
                aco$configurationRevision,
                aco$authoritativeCapture != null);
        aco$publishDedupRevision();
    }

    /** AE2がNetworkCraftingSimulationStateへ渡すものと同じActionSourceを一度だけ固定する。 */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingSimulationRequester;getActionSource()Lappeng/api/networking/security/IActionSource;"),
            require = 1)
    private IActionSource aco$captureActualActionSource(ICraftingSimulationRequester requester) {
        // dedup lookupで取得済みなら同一参照を再利用し、状態を持つgetterを二度呼ばない。
        aco$actionSource = aco$usesDedupFrame
                ? CraftingCalculationSnapshotContext.actionSource(requester)
                : requester.getActionSource();
        return aco$actionSource;
    }

    @Unique
    private void aco$publishDedupRevision() {
        // 例外で残った別requesterのframeへ、この計算のrevisionを書き込まない。
        if (!aco$usesDedupFrame) {
            return;
        }
        CraftingCalculationSnapshotContext.capture(aco$calculationRevision);
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void aco$startCalculationTimer(CallbackInfoReturnable<ICraftingPlan> cir) {
        aco$calculationStartedAt = System.nanoTime();
        CraftingCalculationDiagnostics.logStarted(
                aco$calculationId,
                aco$gridIdentity,
                output,
                requestedAmount,
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.storageGeneration(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.patternGeneration(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.recipeGeneration(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.configurationRevision());
    }

    /**
     * Issue #179: AE2の登録・外部Planner選択・finallyは変更せず、不変計算区間だけ
     * tickへ所有権を返す。AE2の可変状態へ戻る前に必ず次のpause handshakeを取得する。
     */
    @Inject(method = "computePlan", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$tryAuthoritativePlan(CallbackInfoReturnable<ICraftingPlan> cir) {
        // capture不在や無効設定は、元のAE2 handshakeのまま処理する。
        if (!Ae2AuthoritativeCraftingPlanner.planningEnabled() || aco$authoritativeCapture == null) {
            return;
        }
        long plannerStartedAt = System.nanoTime();
        boolean completed = false;
        boolean adopted = false;
        PlanningServerTasks.registerWorker(aco$authoritativeCapture.server());
        aco$workerRegistered = true;
        synchronized (monitor) {
            aco$detachedPlanning = true;
            running = false;
            watch.reset();
            monitor.notifyAll();
        }
        aco$logPlanningHandoff("detached");
        try {
            ICraftingPlan accelerated = Ae2AuthoritativeCraftingPlanner.tryPlanDetached(
                    aco$authoritativeCapture, output, requestedAmount, strategy,
                    this::aco$reattachPlanning);
            completed = true;
            // 証明済みの計画だけ返し、辞退時のThread境界はfinallyで判定する。
            if (accelerated != null) {
                adopted = true;
                aco$usedAuthoritativePlan = true;
                aco$authoritativePlan = accelerated;
                cir.setReturnValue(accelerated);
            }
        } finally {
            OptimizationMetrics.recordAuthoritativePlanner(adopted, System.nanoTime() - plannerStartedAt);
            aco$logPlanningHandoff(!completed ? "failed" : adopted ? "planned" : "ae2-standard");
            /*
             * Issue #179: 数量の置換を辞退しても、不変Pattern上のAE2標準計算はworkerで続ける。
             * 初期化が完了しなかった場合はrunのfinallyへ進み、部分結果を返さない。
             */
            if (aco$detachedPlanning && completed && !adopted) {
                var snapshot = aco$authoritativeCapture.planningGraphCapture();
                if (snapshot != null && snapshot.supportsDetachedAe2Planning(expanded -> {
                    // 適格性検査中の取消も、通常AE2計算への辞退へ読み替えない。
                    if (Thread.currentThread().isInterrupted()) {
                        throw new PlanningCancelledException(expanded);
                    }
                })) {
                    aco$nativeSnapshot = snapshot;
                    aco$nativeBindingCurrent = true;
                    aco$usedNativeSnapshot = true;
                    aco$logPlanningHandoff("ae2-snapshot-detached");
                }
            }
            // 例外・取消時はrunのfinallyへ直行し、存在しない次tickを待たない。
            if (aco$detachedPlanning && completed && aco$nativeSnapshot == null) {
                try {
                    aco$reattachPlanning();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new PlanningCancelledException(0);
                }
            }
            // 失敗時もflagを戻す。running/doneの終了通知はAE2のfinishが所有する。
            synchronized (monitor) {
                aco$detachedPlanning = aco$nativeSnapshot != null;
            }
        }
    }

    @Inject(method = "handlePausing", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$pauseOnlyForLivePlanning(CallbackInfo ci) throws InterruptedException {
        // 元のAE2同期かACO数量Plannerなら、既存のpause実装を変更しない。
        if (aco$nativeSnapshot == null) {
            return;
        }
        // native計算中も、AE2 FutureやServer停止からの取消を受け取る。
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        // 世代更新後は旧資格で可変Patternを読まず、同じ標準計算を元の同期で継続する。
        if (!aco$nativeSnapshot.isCurrent()) {
            aco$nativeSnapshot = null;
            aco$logPlanningHandoff("ae2-generation-reattach");
            aco$reattachPlanning();
        }
        ci.cancel();
    }

    @Override
    public void aco$checkpointIngredientSearch() throws InterruptedException {
        // Issue #179: do not wait another tick after cancellation of a long candidate scan.
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        incTime = Integer.MAX_VALUE;
        aco$invokeHandlePausing();
    }

    @Override
    public boolean aco$runPatternLookup(Runnable lookup) {
        var snapshot = aco$nativeSnapshot;
        // Server側へ委譲した同じメソッドへの再入は、そのまま元の処理へ通す。
        if (snapshot == null || Thread.currentThread() == aco$authoritativeCapture.serverThread()) {
            return false;
        }
        boolean executed = PlanningServerTasks.call(aco$authoritativeCapture.server(), () -> {
            // lookup前後を検証し、lookup中の世代変更もworkerへ通知する。
            if (!snapshot.isCurrent()) {
                return false;
            }
            lookup.run();
            return true;
        });
        // 委譲中に世代が変わっていなければ、workerの非待機計算を継続する。
        if (snapshot.isCurrent() && aco$nativeBindingCurrent) {
            return executed;
        }
        aco$nativeSnapshot = null;
        aco$logPlanningHandoff("ae2-generation-reattach");
        try {
            aco$reattachPlanning();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PlanningCancelledException(0);
        }
        // 世代変更前に実行済みの返却物計上を、復帰先で二重に実行しない。
        return executed;
    }

    @Override
    public void aco$observeCraftingService(appeng.api.networking.crafting.ICraftingService service) {
        // lookup中に別Gridへ移った場合は、workerへ戻る前に元の同期へ復帰させる。
        if (aco$nativeSnapshot != null) {
            aco$nativeBindingCurrent &= aco$nativeSnapshot.matchesService(service);
        }
    }

    @Inject(method = "computePlan", at = @At("RETURN"), require = 1)
    private void aco$finishNativePlanning(CallbackInfoReturnable<ICraftingPlan> cir)
            throws InterruptedException {
        // 外側の外部Planner、AE2ログ、finallyへ戻る前に元の同期へ復帰する。
        if (aco$nativeSnapshot != null) {
            aco$nativeSnapshot = null;
            aco$logPlanningHandoff("ae2-snapshot-complete");
            aco$reattachPlanning();
        }
    }

    @Inject(method = "finish", at = @At("HEAD"), require = 1)
    private void aco$clearPlanningHandoff(CallbackInfo ci) {
        // AE2 finallyと同時に解除し、poolへ戻ったworkerへ停止通知が遅れて届かないようにする。
        if (aco$workerRegistered) {
            PlanningServerTasks.releaseWorker(aco$authoritativeCapture.server());
            aco$workerRegistered = false;
        }
        // 例外終了もAE2のfinallyが所有する。取消後に次tickを待たない。
        if (aco$nativeSnapshot != null) {
            aco$logPlanningHandoff("ae2-snapshot-failed");
        }
        synchronized (monitor) {
            aco$nativeSnapshot = null;
            aco$detachedPlanning = false;
        }
    }

    @Unique
    private void aco$reattachPlanning() throws InterruptedException {
        synchronized (monitor) {
            aco$detachedPlanning = false;
            running = false;
            watch.reset();
            // AE2の次のhandlePausingで必ずhandshakeを行う初期値。時間上限ではない。
            incTime = Integer.MAX_VALUE;
            monitor.notifyAll();
        }
        // 取消後に新しいtickを待たない。
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        aco$invokeHandlePausing();
    }

    @Redirect(method = "simulateFor", at = @At(value = "INVOKE",
            target = "Ljava/lang/Object;wait()V"), require = 1)
    private void aco$waitOnlyForLiveAe2Work(Object monitor) throws InterruptedException {
        // AE2の同じmonitor内で判定するため、workerの切離し開始とのcheck-then-act競合がない。
        if (aco$detachedPlanning) {
            running = false;
            return;
        }
        monitor.wait();
    }

    @Unique
    private void aco$logPlanningHandoff(String phase) {
        // 区間の開始・終了時だけ記録し、simulateForの毎tickログは追加しない。
        if (ACOConfig.logCraftingDecisionFlow()) {
            com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer.LOGGER.debug(
                    "ACO-DIAG event=planning_handoff calculationId={} grid={} phase={} output={} requested={} thread={}",
                    aco$calculationId, aco$gridIdentity, phase, output, requestedAmount,
                    Thread.currentThread().getName());
        }
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void aco$logSlowCalculation(CallbackInfoReturnable<ICraftingPlan> cir) {
        ICraftingPlan returned = cir.getReturnValue();
        // AE2の外側がFacadeを再構築しても、同じ計算インスタンスのSidecarだけを引き継ぐ。
        if (aco$authoritativePlan != null && returned != null && returned != aco$authoritativePlan) {
            Ae2CraftingPlanSidecars.alias(returned, aco$authoritativePlan);
        }
        CraftingCalculationDiagnostics.logIfSlow(
                output,
                requestedAmount,
                returned,
                System.nanoTime() - aco$calculationStartedAt,
                aco$usedAuthoritativePlan
                        ? "compiled-strict"
                        : "ae2-fallback");
        CraftingCalculationDiagnostics.logDecision(
                aco$calculationId,
                aco$gridIdentity,
                output,
                requestedAmount,
                returned,
                System.nanoTime() - aco$calculationStartedAt,
                aco$usedAuthoritativePlan ? "compiled-strict"
                        : aco$usedNativeSnapshot ? "ae2-snapshot" : "ae2-standard",
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.storageGeneration(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.patternGeneration(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.recipeGeneration(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.configurationRevision());
        // Authoritative結果を自分自身と比較して一致回数を水増しせず、AE2標準結果だけを教材にする。
        if (!aco$usedAuthoritativePlan) {
            Ae2CraftingShadowValidator.validate(
                    aco$shadowCapture,
                    output,
                    requestedAmount,
                    strategy,
                    returned);
        }
    }
}
