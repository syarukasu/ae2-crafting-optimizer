package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.integration.MixinTransformationReport;
import java.util.List;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Reports mandatory AE2 transformations selected by the fail-closed core config. */
public final class CoreMixinConfigPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        MixinTransformationReport.record(
                "core",
                "ae2",
                "required",
                targetClassName,
                mixinClassName,
                true,
                true);
        return true;
    }

    @Override
    public void acceptTargets(java.util.Set<String> myTargets, java.util.Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
