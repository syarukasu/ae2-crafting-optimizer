package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.parts.automation.ExportBusPart;
import appeng.util.ConfigInventory;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ConfigInventoryGenerationAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ExportBusPart.class, remap = false)
public abstract class ExportBusCandidateKeyCacheMixin {
    @Unique
    private final AEKey[] aco$candidateKeys = new AEKey[63];
    @Unique
    private final boolean[] aco$candidateLoaded = new boolean[63];
    @Unique
    private long aco$configGeneration = Long.MIN_VALUE;

    @Inject(method = "doBusWork", at = @At("HEAD"))
    private void aco$checkConfigGeneration(CallbackInfoReturnable<Boolean> cir) {
        if (!ACOConfig.cacheExportBusCandidateKeys()) {
            return;
        }
        ConfigInventory config = ((ExportBusPart) (Object) this).getConfig();
        long generation = ((ConfigInventoryGenerationAccess) (Object) config).aco$getGeneration();
        if (generation != aco$configGeneration) {
            java.util.Arrays.fill(aco$candidateKeys, null);
            java.util.Arrays.fill(aco$candidateLoaded, false);
            aco$configGeneration = generation;
        }
    }

    @Redirect(
            method = "doBusWork",
            at = @At(value = "INVOKE", target = "Lappeng/util/ConfigInventory;getKey(I)Lappeng/api/stacks/AEKey;"),
            require = 0)
    private AEKey aco$reuseConfiguredCandidate(ConfigInventory config, int slot) {
        if (!ACOConfig.cacheExportBusCandidateKeys() || slot < 0 || slot >= aco$candidateKeys.length) {
            return config.getKey(slot);
        }
        if (!aco$candidateLoaded[slot]) {
            aco$candidateKeys[slot] = config.getKey(slot);
            aco$candidateLoaded[slot] = true;
        }
        return aco$candidateKeys[slot];
    }
}
