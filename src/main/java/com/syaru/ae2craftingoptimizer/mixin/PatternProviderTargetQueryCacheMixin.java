package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.optimization.PatternTargetQueryScope;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.helpers.patternprovider.PatternProviderTargetCache", remap = false)
public abstract class PatternProviderTargetQueryCacheMixin {
    @Shadow @Final private Map<AEKeyType, ExternalStorageStrategy> strategies;

    @Inject(method = "find", at = @At("HEAD"), require = 1)
    private void aco$bindQuery(CallbackInfoReturnable<Object> cir) {
        PatternTargetQueryScope.bind(this);
    }

    @Redirect(method = "find", at = @At(value = "INVOKE", target =
            "Lappeng/api/behaviors/ExternalStorageStrategy;createWrapper(ZLjava/lang/Runnable;)Lappeng/api/storage/MEStorage;"), require = 1)
    private MEStorage aco$skipUnrequestedType(ExternalStorageStrategy strategy,
            boolean extractableOnly, Runnable invalidationListener) {
        return PatternTargetQueryScope.needs(this, strategies, strategy)
                ? strategy.createWrapper(extractableOnly, invalidationListener) : null;
    }
}
