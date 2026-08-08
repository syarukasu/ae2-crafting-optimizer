package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.integration.MixinTransformationReport;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Selects an integration config only when its exact target mod is loaded. */
public abstract class ModPresenceMixinConfigPlugin implements IMixinConfigPlugin {
    protected abstract String feature();

    protected abstract String dependencyId();

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean loaded;
        String version;
        try {
            var container = ModList.get().getModContainerById(dependencyId());
            loaded = container.isPresent();
            version = container.map(value -> value.getModInfo().getVersion().toString()).orElse("absent");
        } catch (RuntimeException failure) {
            loaded = false;
            version = "unknown";
        }
        MixinTransformationReport.record(
                feature(),
                dependencyId(),
                version,
                targetClassName,
                mixinClassName,
                loaded,
                true);
        return loaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
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
