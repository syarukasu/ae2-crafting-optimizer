package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.parts.automation.ExportBusPart;
import com.syaru.ae2craftingoptimizer.optimization.BusFuzzySearchCache;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ExportBusPart.class, remap = false)
public abstract class ExportBusFuzzySearchCacheMixin {
    @Redirect(
            method = "doBusWork",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/stacks/KeyCounter;findFuzzy(Lappeng/api/stacks/AEKey;Lappeng/api/config/FuzzyMode;)Ljava/util/Collection;"),
            require = 0)
    private Collection<Object2LongMap.Entry<AEKey>> aco$reuseFuzzySearch(
            KeyCounter counter,
            AEKey key,
            FuzzyMode mode) {
        return BusFuzzySearchCache.find(counter, key, mode);
    }
}
