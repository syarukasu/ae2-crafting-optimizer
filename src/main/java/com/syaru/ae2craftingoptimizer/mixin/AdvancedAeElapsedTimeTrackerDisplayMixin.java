package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.access.AqeVectorElapsedTimeDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Advanced AEの実Trackerを変更せず、Exact Vector中だけlong表示値を返す。
 */
@Pseudo
@Mixin(
        targets = "net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker",
        remap = false)
public abstract class AdvancedAeElapsedTimeTrackerDisplayMixin
        implements AqeVectorElapsedTimeDisplay {
    @Unique
    private boolean aco$vectorDisplayActive;

    @Unique
    private long aco$vectorDisplayStart;

    @Unique
    private long aco$vectorDisplayRemaining;

    @Unique
    private float aco$vectorDisplayProgress;

    @Override
    public void aco$setVectorDisplay(
            long startItemCount,
            long remainingItemCount,
            float progress) {
        /*
         * 表示値も非負・単調範囲に限定し、異常値をAdvanced AE画面へ
         * 負数として流さない。
         */
        if (startItemCount < 0L
                || remainingItemCount < 0L
                || remainingItemCount > startItemCount
                || !Float.isFinite(progress)
                || progress < 0.0F
                || progress > 1.0F) {
            throw new IllegalArgumentException(
                    "invalid AQE Exact Vector display projection");
        }
        aco$vectorDisplayStart = startItemCount;
        aco$vectorDisplayRemaining = remainingItemCount;
        aco$vectorDisplayProgress = progress;
        aco$vectorDisplayActive = true;
    }

    @Override
    public void aco$clearVectorDisplay() {
        aco$vectorDisplayActive = false;
        aco$vectorDisplayStart = 0L;
        aco$vectorDisplayRemaining = 0L;
        aco$vectorDisplayProgress = 0.0F;
    }

    @Inject(
            method = "getStartItemCount",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$readVectorDisplayStart(
            CallbackInfoReturnable<Long> cir) {
        // Exact Vector所有中だけ表示Facadeを返し、それ以外はAdvanced AE本来の値を使う。
        if (aco$vectorDisplayActive) {
            cir.setReturnValue(aco$vectorDisplayStart);
        }
    }

    @Inject(
            method = "getRemainingItemCount",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$readVectorDisplayRemaining(
            CallbackInfoReturnable<Long> cir) {
        // Exact Vector所有中だけ表示Facadeを返し、それ以外はAdvanced AE本来の値を使う。
        if (aco$vectorDisplayActive) {
            cir.setReturnValue(aco$vectorDisplayRemaining);
        }
    }

    @Inject(
            method = "getProgress",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$readVectorDisplayProgress(
            CallbackInfoReturnable<Float> cir) {
        // 進捗バーも同じ論理tickを使い、件数表示と別速度にしない。
        if (aco$vectorDisplayActive) {
            cir.setReturnValue(aco$vectorDisplayProgress);
        }
    }
}
