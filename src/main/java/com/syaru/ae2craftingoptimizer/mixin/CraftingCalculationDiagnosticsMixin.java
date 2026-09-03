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
public abstract class CraftingCalculationDiagnosticsMixin {
    @Invoker("handlePausing")
    protected abstract void aco$invokeHandlePausing() throws InterruptedException;

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
     * 計画本体だけを高速経路へ差し替える。run()を直接キャンセルすると、AE2が行う
     * 計算スレッド登録とfinally内の終了通知まで飛ばしてしまうため、ここより外側は必ず
     * AE2標準の生命周期を通す。
     */
    @Inject(method = "computePlan", at = @At("HEAD"), cancellable = true)
    private void aco$tryAuthoritativePlan(CallbackInfoReturnable<ICraftingPlan> cir) {
        // 高速Plannerが無効なら、計時も追加分岐も行わずAE2標準計算へ進む。
        if (!Ae2AuthoritativeCraftingPlanner.planningEnabled()) {
            return;
        }
        long plannerStartedAt = System.nanoTime();
        boolean adopted = false;
        try {
            ICraftingPlan accelerated = Ae2AuthoritativeCraftingPlanner.tryPlan(
                    aco$authoritativeCapture,
                    output,
                    requestedAmount,
                    strategy,
                    this::aco$invokeHandlePausing);
            // Shadow認定済みProgramが結果を返した場合だけAE2計画本体を置き換える。
            if (accelerated == null) {
                return;
            }
            adopted = true;
            aco$usedAuthoritativePlan = true;
            aco$authoritativePlan = accelerated;
            cir.setReturnValue(accelerated);
        } finally {
            OptimizationMetrics.recordAuthoritativePlanner(
                    adopted,
                    System.nanoTime() - plannerStartedAt);
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
                aco$usedAuthoritativePlan ? "compiled-strict" : "ae2-standard",
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
