package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.IncrementalUpdateHelper;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.DeepRangeUpdateHelper;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IncrementalUpdateHelper.class, remap = false)
public abstract class IncrementalUpdateHelperDeepRangeMixin implements DeepRangeUpdateHelper {
    @Shadow
    @Final
    private Set<AEKey> changes;

    @Shadow
    private boolean fullUpdate;

    @Unique
    private boolean aco$retainPendingOnCommit;

    @Override
    public Set<AEKey> aco$getMutableChanges() {
        return changes;
    }

    @Override
    public void aco$finishRangeBatch(boolean hasPendingChanges) {
        fullUpdate = false;
        aco$retainPendingOnCommit = hasPendingChanges;
    }

    @Inject(method = "commitChanges", at = @At("HEAD"), cancellable = true)
    private void aco$retainUnsentRange(CallbackInfo ci) {
        if (ACOConfig.deepVisibleTerminalRangeSync() && aco$retainPendingOnCommit) {
            aco$retainPendingOnCommit = false;
            ci.cancel();
        }
    }
}
