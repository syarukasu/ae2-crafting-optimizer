package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingPlan;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import net.minecraftforge.fml.ModList;

/**
 * AppliedE本家とTPS Fix forkに共通する動的パターン境界を扱う。
 * Optional MODのクラスを直接参照せず、未導入環境ではClassLoaderへ触れない。
 */
public final class AppliedECompatibility {
    public static final String MOD_ID = "appliede";
    static final String TRANSMUTATION_PATTERN_CLASS =
            "gripe._90.appliede.me.misc.TransmutationPattern";
    static final String EMC_MODULE_PROVIDER_CLASS =
            "gripe._90.appliede.part.EMCModulePart";

    private AppliedECompatibility() {
    }

    /** AppliedEの一時Pattern生成を迂回しないよう、数式Plannerの対象外にする。 */
    public static boolean requiresAe2Planner(IPatternDetails pattern) {
        return pattern != null
                && ACOConfig.forceAe2PlannerForAppliedEPatterns()
                && isTransmutationPatternClassName(pattern.getClass().getName());
    }

    /** EMC知識と値で内容が変わるProviderは、固定Patternの同値比較を行わない。 */
    public static boolean isDynamicProvider(ICraftingProvider provider) {
        return provider != null
                && ACOConfig.treatAppliedEProviderAsDynamic()
                && isEmcModuleProviderClassName(provider.getClass().getName());
    }

    /** 一時Patternを含む完成計画は、次の計算へ持ち越さずAppliedEに再生成させる。 */
    public static boolean requiresFreshCalculation(ICraftingPlan plan) {
        // 計画欠落または互換機能OFFなら、ACO 1.4.0までのキャッシュ判定を維持する。
        if (plan == null || !ACOConfig.enableAppliedECompatibility()) {
            return false;
        }
        return containsTransmutationPattern(plan);
    }

    static boolean containsTransmutationPattern(ICraftingPlan plan) {
        // 計画内に一つでも一時変換Patternがあれば、計画全体を再利用対象から外す。
        for (IPatternDetails pattern : plan.patternTimes().keySet()) {
            if (isTransmutationPatternClassName(pattern.getClass().getName())) {
                return true;
            }
        }
        return false;
    }

    public static void logDetectedVersion() {
        ModList.get().getModContainerById(MOD_ID).ifPresent(container -> AE2CraftingOptimizer.LOGGER.info(
                "ACO AppliedE compatibility: detected {}, AE2-authoritative TransmutationPattern planning {}, dynamic provider refresh {}",
                container.getModInfo().getVersion(),
                ACOConfig.forceAe2PlannerForAppliedEPatterns(),
                ACOConfig.treatAppliedEProviderAsDynamic()));
    }

    static boolean isTransmutationPatternClassName(String className) {
        return TRANSMUTATION_PATTERN_CLASS.equals(className);
    }

    static boolean isEmcModuleProviderClassName(String className) {
        return EMC_MODULE_PROVIDER_CLASS.equals(className);
    }
}
