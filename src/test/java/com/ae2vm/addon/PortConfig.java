package com.ae2vm.addon;

public final class PortConfig {
    private PortConfig() { }
    public static void set(net.neoforged.neoforge.common.ModConfigSpec spec,
            com.electronwill.nightconfig.core.CommentedConfig config) {
        if (config == null) { spec.acceptConfig(null); return; }
        try {
            var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructor(
                    com.electronwill.nightconfig.core.CommentedConfig.class, java.nio.file.Path.class,
                    net.neoforged.fml.config.ModConfig.class);
            constructor.setAccessible(true);
            spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
