package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.integration.MixinTransformationReport;
import java.net.URL;
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

    /** Mixin選択時にModListより先に確認できる、任意連携先の実クラス。 */
    protected String dependencyMarkerClass() {
        return "";
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String markerClass = dependencyMarkerClass();
        boolean loaded = markerClass.isBlank()
                ? isDependencyLoadedByModList()
                : isClassResourcePresent(
                        markerClass,
                        Thread.currentThread().getContextClassLoader(),
                        getClass().getClassLoader());
        String version = dependencyVersion();
        // Mixin初期化中はModListが未完成でも、実クラスが見つかった事実を診断へ残す。
        if (loaded && "absent".equals(version)) {
            version = "classpath-present";
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

    private boolean isDependencyLoadedByModList() {
        try {
            return ModList.get()
                    .getModContainerById(
                            dependencyId())
                    .isPresent();
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private String dependencyVersion() {
        try {
            return ModList.get()
                    .getModContainerById(
                            dependencyId())
                    .map(value -> value.getModInfo().getVersion().toString())
                    .orElse("absent");
        } catch (RuntimeException failure) {
            return "unknown";
        }
    }

    public static boolean isClassResourcePresent(
            String className,
            ClassLoader... classLoaders) {
        String resourceName = className.replace('.', '/') + ".class";
        // Issue #120: ModList完成前でも、各候補ClassLoaderへ副作用なしで実クラスを照会する。
        for (ClassLoader classLoader : classLoaders) {
            // nullのContext ClassLoaderは候補から外し、次の実Loaderを確認する。
            if (classLoader == null) {
                continue;
            }
            URL resource = classLoader.getResource(resourceName);
            // 一つでも対象class resourceがあれば、任意連携Mixinを選択する。
            if (resource != null) {
                return true;
            }
        }
        return false;
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
