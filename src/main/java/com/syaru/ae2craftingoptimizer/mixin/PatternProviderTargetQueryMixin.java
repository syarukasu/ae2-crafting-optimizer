package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.CountedNativeContracts;
import com.syaru.ae2craftingoptimizer.optimization.PatternTargetQueryScope;
import java.util.Set;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderTargetQueryMixin {
    @Shadow @Final private Set<AEKey> patternInputs;
    @Shadow private PatternProviderTarget findAdapter(Direction side) { throw new AssertionError(); }

    @Redirect(method = "pushPattern", at = @At(value = "INVOKE", target =
            "Lappeng/helpers/patternprovider/PatternProviderLogic;findAdapter(Lnet/minecraft/core/Direction;)Lappeng/helpers/patternprovider/PatternProviderTarget;"), require = 1)
    private PatternProviderTarget aco$resolveRelevantTypes(PatternProviderLogic provider, Direction side,
            IPatternDetails pattern, KeyCounter[] inputs) {
        if (!ACOConfig.enableCountedProcessingTransfers()
                || !CountedNativeContracts.defaultExternalPush(pattern)) return findAdapter(side);
        // #245: retain native find/insert and close even when an addon callback throws.
        try (var scope = PatternTargetQueryScope.open(inputs, patternInputs)) {
            return findAdapter(side);
        }
    }
}
