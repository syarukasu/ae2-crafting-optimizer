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
        if (!isUelmLoadedDuringBootstrap()) {
            return true;
        }
        return !Ae2UelmCompatibility.ownsAe2SurfaceMixin(mixinClassName);
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
