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
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // InsaneAEが同じAE2実行入口を所有する場合は、ACOの予算・通常Batch入口を重ねない。
        // BigInteger会計とAQE専用のAdvanced AE連携Mixinはこの除外対象に含めない。
        if (isInsaneAeLoadedDuringBootstrap()
                && isInsaneAeOwnedExecutionMixin(mixinClassName)) {
            return false;
        }
        if (!isUelmLoadedDuringBootstrap()) {
            return true;
        }
        return !Ae2UelmCompatibility.ownsAe2SurfaceMixin(mixinClassName);
    }

    static boolean isInsaneAeOwnedExecutionMixin(String mixinClassName) {
        String simpleName = simpleMixinName(mixinClassName);
        return switch (simpleName) {
            // 実行予算と実行入口だけをInsaneAEへ委譲する。
            // BatchSourceReceiptMixinは実行を変更せず、V2会計に必要な状態APIを付与するため残す。
            case "CraftingCpuLogicExecutionBudgetMixin",
                    "CraftingCpuLogicTransactionalBatchV2Mixin",
                    // Advanced AEの通常executeCraftingもInsaneAE側の互換Mixinを優先する。
                    "AdvancedAeCraftingCpuLogicExecutionBudgetMixin",
                    "AdvancedAeCraftingCpuLogicTransactionalBatchV2Mixin" -> true;
            default -> false;
        };
    }

    /**
     * Mixinが渡す完全修飾名から、登録名との比較に使う単純名を取り出す。
     * 実行時の値はパッケージ付きになるため、単純名へ正規化しないと競合除外が働かない。
     */
    private static String simpleMixinName(String mixinClassName) {
        int separator = mixinClassName.lastIndexOf('.');
        return separator >= 0 ? mixinClassName.substring(separator + 1) : mixinClassName;
    }

    private static boolean isInsaneAeLoadedDuringBootstrap() {
        try {
            return FMLLoader.getLoadingModList().getModFileById("insaneae") != null;
        } catch (RuntimeException | LinkageError ignored) {
            // 起動初期に判定できない場合は、既存のACO経路を維持する。
            return false;
        }
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
