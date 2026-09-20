package com.syaru.ae2craftingoptimizer;

import appeng.core.definitions.AEItems;
import appeng.items.misc.MissingContentItem;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.neoforged.fml.loading.LoadingModList;

/** Registry-only fixtures share this bootstrap; neither the game nor networking is started. */
public final class TestRegistries {
    private static boolean initialized;
    private static boolean keyTypesInitialized;

    private TestRegistries() {
    }

    public static synchronized void initialize() throws Exception {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        var bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Registry.register(BuiltInRegistries.ITEM, AEItems.MISSING_CONTENT.id(),
                new MissingContentItem(new Item.Properties()));
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2", "encoded_processing_pattern"),
                appeng.api.ids.AEComponents.ENCODED_PROCESSING_PATTERN);
        BuiltInRegistries.bootStrap();
        initialized = true;
    }

    public static synchronized void initializeAe2KeyTypes() throws Exception {
        initialize();
        if (keyTypesInitialized) return;
        var types = new net.neoforged.neoforge.registries.RegistryBuilder<appeng.api.stacks.AEKeyType>(
                net.minecraft.resources.ResourceKey.createRegistryKey(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2", "test_key_types")))
                .disableRegistrationCheck().create();
        appeng.api.stacks.AEKeyTypesInternal.setRegistry(types);
        appeng.api.stacks.AEKeyTypes.register(appeng.api.stacks.AEKeyType.items());
        appeng.api.stacks.AEKeyTypes.register(appeng.api.stacks.AEKeyType.fluids());
        keyTypesInitialized = true;
    }
}
