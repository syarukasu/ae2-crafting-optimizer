package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.integration.Ae2UelmCompatibility;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Prevents ACO's AE2-owned surface Mixins from loading when AE2-UELM owns the
 * same behavior. The plugin has no hard reference to UELM classes.
 */
public final class AcoMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> UELM_MOD_IDS = Set.of(
            "ae2_uelm",
            "ae2uelm",
            "ae2_uel",
            "ae2uel");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!isUelmLoadedDuringBootstrap()) {
            return true;
        }
        return !Ae2UelmCompatibility.ownsAe2SurfaceMixin(mixinClassName);
    }

    private static boolean isUelmLoadedDuringBootstrap() {
        try {
            Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object loadingModList = loader.getMethod("getLoadingModList").invoke(null);
            var isLoaded = loadingModList.getClass().getMethod("isLoaded", String.class);
            for (String modId : UELM_MOD_IDS) {
                if (Boolean.TRUE.equals(isLoaded.invoke(loadingModList, modId))) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
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
