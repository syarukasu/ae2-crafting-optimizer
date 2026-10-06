package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.integration.MegaCellsModContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "gripe._90.megacells.forge.ForgePlatform", remap = false)
public abstract class MegaCellsForgePlatformContextMixin {
    // #218: replace only the foreign-context lookup, preserving the original initializer.
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext;get()Lnet/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext;"),
            require = 0, allow = 1)
    private static FMLJavaModLoadingContext aco$ownedModContext() {
        return MegaCellsModContext.get();
    }
}
