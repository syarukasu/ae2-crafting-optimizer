package com.syaru.ae2craftingoptimizer.integration;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Resolves MEGACells' Java context independently of the caller's language loader. */
public final class MegaCellsModContext {
    private MegaCellsModContext() {
    }

    public static FMLJavaModLoadingContext get() {
        var owner = ModList.get().getModContainerById("megacells")
                .orElseThrow(() -> new IllegalStateException("MEGACells mod container is unavailable"));
        // #218: never change the loading thread's context, including during Kotlin mod construction.
        var local = new ModLoadingContext();
        local.setActiveContainer(owner);
        return local.extension();
    }
}
