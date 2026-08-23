package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.parts.automation.ExportBusPart;
import appeng.util.ConfigInventory;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ConfigInventoryGenerationAccess;
import com.syaru.ae2craftingoptimizer.optimization.GenerationSlotCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Export Busの設定keyだけを設定世代内で再利用し、搬出判定と転送は毎回AE2へ委譲する。 */
@Mixin(value = ExportBusPart.class, remap = false)
public abstract class ExportBusCandidateCacheMixin {
    @Unique
    private final GenerationSlotCache<AEKey> aco$candidateCache = new GenerationSlotCache<>();

    @Redirect(
            method = "doBusWork",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/util/ConfigInventory;getKey(I)Lappeng/api/stacks/AEKey;"),
            require = 1)
    private AEKey aco$readCandidate(ConfigInventory config, int slot) {
        // 機能OFF時は設定Inventoryを直接読む元の経路を維持する。
        if (!ACOConfig.cacheExportBusCandidateKeys()) {
            return config.getKey(slot);
        }
        // AE2派生実装が世代Accessorを持たない場合は、cacheせず正本を直接読む。
        if (!(config instanceof ConfigInventoryGenerationAccess generationAccess)) {
            return config.getKey(slot);
        }
        long generation = generationAccess.aco$getGeneration();
        return aco$candidateCache.get(generation, config.size(), slot, config::getKey);
    }
}
