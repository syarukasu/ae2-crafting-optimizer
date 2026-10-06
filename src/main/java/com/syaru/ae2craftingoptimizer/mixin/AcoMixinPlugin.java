package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.integration.Ae2UelmCompatibility;
import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Prevents ACO's AE2-owned surface Mixins from loading when AE2-UELM owns the
 * same behavior. The plugin has no hard reference to UELM classes.
 */
public final class AcoMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> NATIVE_VM_EXCLUDED = Set.of(
            "CraftingCalculationDiagnosticsMixin", "CraftingCalculationCheckedMathMixin",
            "CraftingCalculationMemoLifecycleMixin", "CraftingCpuHelperCalculationMemoMixin",
            "CraftingSimulationIngredientSearchMixin", "KeyCounterCalculationIndexMixin",
            "CraftingPatternTaggedValidationMixin", "CraftingProviderRefreshCoalescingMixin",
            "CraftingServiceCalculationDeduplicationMixin", "CraftingTreeCalculationMemoMixin",
            "CraftingTreeNodeCheckedMathMixin", "CraftingTreeProcessCheckedMathMixin",
            "CraftingSimulationStateCheckedMathMixin", "CraftingCpuLogicExecutionBudgetMixin");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = simpleMixinName(mixinClassName);
        // Issue #129: 責務台帳にないMixinは通常AE2へ刺さる根拠がないため適用しない。
        if (!MixinFeatureCatalog.contains(simpleName)) {
            return false;
        }
        // VM owns calculation; ordinary AE2 dispatch keeps its own waves and face rotation.
        if (NATIVE_VM_EXCLUDED.contains(simpleName)
                && FMLLoader.getLoadingModList().getModFileById("ae2vm_aco") != null) {
            return false;
        }
        if (!isUelmLoadedDuringBootstrap()) {
            return true;
        }
        return !Ae2UelmCompatibility.ownsAe2SurfaceMixin(mixinClassName);
    }

    /**
     * Mixinが渡す完全修飾名から、登録名との比較に使う単純名を取り出す。
     * 実行時の値はパッケージ付きになるため、単純名へ正規化しないと競合除外が働かない。
     */
    private static String simpleMixinName(String mixinClassName) {
        int separator = mixinClassName.lastIndexOf('.');
        return separator >= 0 ? mixinClassName.substring(separator + 1) : mixinClassName;
    }

    private static boolean isUelmLoadedDuringBootstrap() {
        try {
            var modFile = FMLLoader.getLoadingModList().getModFileById("ae2");
            if (modFile == null) {
                return false;
            }
            return modFile.getMods().stream()
                    .filter(mod -> "ae2".equals(mod.getModId()))
                    .map(mod -> mod.getVersion().toString())
                    .anyMatch(Ae2UelmCompatibility::isUelmVersion);
        } catch (RuntimeException | LinkageError ignored) {
            // Unknown bootstrap state must keep ACO's normal behavior intact.
        }
        return false;
    }

    @Override public void onLoad(String mixinPackage) {
    }

    @Override public String getRefMapperConfig() {
        return null;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override public List<String> getMixins() {
        return List.of();
    }

    @Override public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
