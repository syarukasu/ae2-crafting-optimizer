package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.optimization.ReflectionLookupCache;
import com.syaru.ae2craftingoptimizer.optimization.MethodHandleInvocationCache;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
        targets = {
                "net.pedroksl.advanced_ae.common.entities.ReactionChamberEntity",
                "com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter"
        },
        priority = 900,
        remap = false)
public abstract class Ae2OverclockMachineReflectionCacheMixin {
    @Dynamic("Targets methods merged by AE2 Overclock's higher-priority machine mixins")
    @Redirect(
            method = {
                    "ae2oc_headTick", "ae2oc_tailTick", "ae2oc_instantCraft", "ae2oc_doExtraOutputs",
                    "ae2oc_handleDirty", "ae2oc_calculateParallel", "ae2oc_getMinInputCount",
                    "ae2oc_getFluidOutputLimit", "ae2oc_consumeOnceWithRecipe", "ae2oc_consumeBatchWithRecipe",
                    "ae2oc_getAvailableEnergy", "ae2oc_tryConsumePower", "ae2oc_getField", "ae2oc_getMethod",
                    "ae2oc_markForUpdate", "ae2oc_saveChanges", "ae2oc_testIngredient",
                    "ae2oc_transferItemOutputToNetwork", "ae2oc_transferFluidOutputToNetwork",
                    "ae2oc_transferOutputToNetwork", "ae2oc_tryOutputToNetwork",
                    "ae2oc_tryFlushOutputSlots", "ae2oc_tryFlushOutputSlot"
            },
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;"),
            require = 0)
    private Field aco$cacheDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        return ReflectionLookupCache.getDeclaredField(owner, name);
    }

    @Dynamic("Targets methods merged by AE2 Overclock's higher-priority machine mixins")
    @Redirect(
            method = {
                    "ae2oc_headTick", "ae2oc_tailTick", "ae2oc_instantCraft", "ae2oc_doExtraOutputs",
                    "ae2oc_handleDirty", "ae2oc_calculateParallel", "ae2oc_getMinInputCount",
                    "ae2oc_getFluidOutputLimit", "ae2oc_consumeOnceWithRecipe", "ae2oc_consumeBatchWithRecipe",
                    "ae2oc_getAvailableEnergy", "ae2oc_tryConsumePower", "ae2oc_getField", "ae2oc_getMethod",
                    "ae2oc_markForUpdate", "ae2oc_saveChanges", "ae2oc_testIngredient",
                    "ae2oc_transferItemOutputToNetwork", "ae2oc_transferFluidOutputToNetwork",
                    "ae2oc_transferOutputToNetwork", "ae2oc_tryOutputToNetwork",
                    "ae2oc_tryFlushOutputSlots", "ae2oc_tryFlushOutputSlot"
            },
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"),
            require = 0)
    private Method aco$cacheDeclaredMethod(Class<?> owner, String name, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        return ReflectionLookupCache.getDeclaredMethod(owner, name, parameterTypes);
    }

    @Dynamic("Targets methods merged by AE2 Overclock's higher-priority machine mixins")
    @Redirect(
            method = {
                    "ae2oc_headTick", "ae2oc_tailTick", "ae2oc_instantCraft", "ae2oc_doExtraOutputs",
                    "ae2oc_handleDirty", "ae2oc_calculateParallel", "ae2oc_getMinInputCount",
                    "ae2oc_getFluidOutputLimit", "ae2oc_consumeOnceWithRecipe", "ae2oc_consumeBatchWithRecipe",
                    "ae2oc_getAvailableEnergy", "ae2oc_tryConsumePower", "ae2oc_getField", "ae2oc_getMethod",
                    "ae2oc_markForUpdate", "ae2oc_saveChanges", "ae2oc_testIngredient",
                    "ae2oc_transferItemOutputToNetwork", "ae2oc_transferFluidOutputToNetwork",
                    "ae2oc_transferOutputToNetwork", "ae2oc_tryOutputToNetwork",
                    "ae2oc_tryFlushOutputSlots", "ae2oc_tryFlushOutputSlot"
            },
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"),
            require = 0)
    private Method aco$cachePublicMethod(Class<?> owner, String name, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        return ReflectionLookupCache.getMethod(owner, name, parameterTypes);
    }

    @Dynamic("Targets Method.invoke calls merged by AE2 Overclock's higher-priority machine mixins")
    @Redirect(
            method = {
                    "ae2oc_headTick", "ae2oc_tailTick", "ae2oc_instantCraft", "ae2oc_doExtraOutputs",
                    "ae2oc_handleDirty", "ae2oc_calculateParallel", "ae2oc_getMinInputCount",
                    "ae2oc_getFluidOutputLimit", "ae2oc_consumeOnceWithRecipe", "ae2oc_consumeBatchWithRecipe",
                    "ae2oc_getAvailableEnergy", "ae2oc_tryConsumePower", "ae2oc_getField", "ae2oc_getMethod",
                    "ae2oc_markForUpdate", "ae2oc_saveChanges", "ae2oc_testIngredient",
                    "ae2oc_transferItemOutputToNetwork", "ae2oc_transferFluidOutputToNetwork",
                    "ae2oc_transferOutputToNetwork", "ae2oc_tryOutputToNetwork",
                    "ae2oc_tryFlushOutputSlots", "ae2oc_tryFlushOutputSlot"
            },
            at = @At(value = "INVOKE", target = "Ljava/lang/reflect/Method;invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 0)
    private Object aco$invokeCachedHandle(Method method, Object receiver, Object[] arguments)
            throws IllegalAccessException, InvocationTargetException {
        return MethodHandleInvocationCache.invoke(method, receiver, arguments);
    }
}
