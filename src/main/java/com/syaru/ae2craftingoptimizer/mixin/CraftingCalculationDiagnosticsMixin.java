package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDiagnostics;
import com.syaru.ae2craftingoptimizer.optimization.CraftingFallbackDiagnostics;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingShadowValidator;
import com.syaru.ae2craftingoptimizer.engine.Ae2AuthoritativeCraftingPlanner;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationDiagnosticsMixin {
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

    @Unique
    private long aco$calculationStartedAt;

    @Unique
    private long aco$calculationId;

    @Unique
    private Ae2CraftingShadowValidator.Capture aco$shadowCapture;

    @Unique
    private Ae2AuthoritativeCraftingPlanner.Capture aco$authoritativeCapture;

    @Unique
    private ICraftingPlan aco$authoritativePlan;

    @Unique
    private boolean aco$usedAuthoritativePlan;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aco$captureGrid(
            Level level,
            IGrid grid,
            ICraftingSimulationRequester requester,
            GenericStack output,
            CalculationStrategy strategy,
            CallbackInfo ci) {
        KeyCounter networkSnapshot =
                ((NetworkCraftingSimulationStateAccessor) (Object) networkInv).aco$getNetworkSnapshot();
        var actionSource = requester.getActionSource();
        aco$shadowCapture = Ae2CraftingShadowValidator.capture(
                level,
                grid,
                actionSource,
                networkSnapshot,
                this.output);
        aco$authoritativeCapture = Ae2AuthoritativeCraftingPlanner.capture(
                level,
                grid,
                actionSource,
                networkSnapshot,
                this.output,
                this.requestedAmount);
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void aco$startCalculationTimer(CallbackInfoReturnable<ICraftingPlan> cir) {
        aco$calculationStartedAt = System.nanoTime();
        aco$calculationId = CraftingCalculationDiagnostics.nextCalculationId();
        CraftingFallbackDiagnostics.reset();
        CraftingCalculationDiagnostics.logStarted(
                aco$calculationId,
                output,
                requestedAmount,
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.patternGeneration(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.recipeGeneration());
    }

    /**
     * 計画本体だけを高速経路へ差し替える。run()を直接キャンセルすると、AE2が行う
     * 計算スレッド登録とfinally内の終了通知まで飛ばしてしまうため、ここより外側は必ず
     * AE2標準の生命周期を通す。
     */
    @Inject(method = "computePlan", at = @At("HEAD"), cancellable = true)
    private void aco$tryAuthoritativePlan(CallbackInfoReturnable<ICraftingPlan> cir) {
        ICraftingPlan accelerated = Ae2AuthoritativeCraftingPlanner.tryPlan(
                aco$authoritativeCapture, output, requestedAmount, strategy);
        // Shadow認定済みProgramが結果を返した場合だけAE2計画本体を置き換える。
        if (accelerated != null) {
            aco$usedAuthoritativePlan = true;
            aco$authoritativePlan = accelerated;
            cir.setReturnValue(accelerated);
        }
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void aco$logSlowCalculation(CallbackInfoReturnable<ICraftingPlan> cir) {
        ICraftingPlan returned = cir.getReturnValue();
        // AE2の外側がFacadeを再構築しても、同じ計算インスタンスのSidecarだけを引き継ぐ。
        if (aco$authoritativePlan != null && returned != null && returned != aco$authoritativePlan) {
            Ae2CraftingPlanSidecars.alias(returned, aco$authoritativePlan);
        }
        CraftingFallbackDiagnostics.Observation fallback = CraftingFallbackDiagnostics.take();
        CraftingCalculationDiagnostics.logIfSlow(
                output,
                requestedAmount,
                returned,
                System.nanoTime() - aco$calculationStartedAt,
                aco$usedAuthoritativePlan
                        ? "compiled-strict"
                        : "ae2-fallback",
                fallback);
        CraftingCalculationDiagnostics.logDecision(
                aco$calculationId,
                output,
                requestedAmount,
                returned,
                System.nanoTime() - aco$calculationStartedAt,
                aco$usedAuthoritativePlan ? "compiled-strict" : "ae2-standard",
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.patternGeneration(),
                aco$authoritativeCapture == null
                        ? -1L
                        : aco$authoritativeCapture.recipeGeneration(),
                fallback);
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
