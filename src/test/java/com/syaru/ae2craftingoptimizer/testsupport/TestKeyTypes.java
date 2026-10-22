package com.syaru.ae2craftingoptimizer.testsupport;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

public final class TestKeyTypes {
    private TestKeyTypes() {}

    @SuppressWarnings("unchecked")
    public static synchronized void initialize() throws Exception {
        var registryField = AEKeyTypesInternal.class.getDeclaredField("registry");
        registryField.setAccessible(true);
        if (registryField.get(null) == null) {
            var builder = new RegistryBuilder<AEKeyType>()
                    .setName(ResourceLocation.fromNamespaceAndPath("ae2", "aco_test_keys"))
                    .disableSaving().disableSync();
            var create = RegistryBuilder.class.getDeclaredMethod("create");
            create.setAccessible(true);
            var keys = (IForgeRegistry<AEKeyType>) create.invoke(builder);
            AEKeyTypesInternal.setRegistry(() -> keys);
        }
        for (var type : java.util.List.of(AEKeyType.items(), AEKeyType.fluids())) {
            if (!AEKeyTypesInternal.getRegistry().containsKey(type.getId())) AEKeyTypes.register(type);
        }
    }
}
