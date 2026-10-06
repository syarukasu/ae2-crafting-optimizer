package com.syaru.ae2craftingoptimizer.testsupport;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import net.minecraft.resources.ResourceLocation;

public final class TestKeyTypes {
    private TestKeyTypes() {}

    @SuppressWarnings("unchecked")
    public static synchronized void initialize() throws Exception {
        com.syaru.ae2craftingoptimizer.TestRegistries.initializeAe2KeyTypes();
    }
}
