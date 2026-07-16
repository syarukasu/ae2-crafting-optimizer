package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.optimization.Ae2OverclockUpgradeCountCache;
import com.syaru.ae2craftingoptimizer.optimization.ReflectionLookupCache;
import com.syaru.ae2craftingoptimizer.optimization.MethodHandleInvocationCache;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "moakiee.support.OverclockCardRuntime", remap = false)
public abstract class Ae2OverclockRuntimeCacheMixin {
    @Inject(method = "getInstalledOverclockCards(Ljava/lang/Object;)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aco$reuseOverclockCardCount(Object host, CallbackInfoReturnable<Integer> cir) {
        Integer cached = Ae2OverclockUpgradeCountCache.get(host, Ae2OverclockUpgradeCountCache.Kind.OVERCLOCK);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getInstalledOverclockCards(Ljava/lang/Object;)I", at = @At("RETURN"), require = 0)
    private static void aco$storeOverclockCardCount(Object host, CallbackInfoReturnable<Integer> cir) {
        Ae2OverclockUpgradeCountCache.put(host, Ae2OverclockUpgradeCountCache.Kind.OVERCLOCK, cir.getReturnValue());
    }

    @Redirect(
            method = {"tryGetInstalledFromGetUpgrades", "tryInvokeNoArg"},
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"),
            require = 0)
    private static Method aco$cachePublicMethod(Class<?> owner, String name, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        return ReflectionLookupCache.getMethod(owner, name, parameterTypes);
    }

    @Redirect(
            method = "tryGetField",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;"),
            require = 0)
    private static Field aco$cacheDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        return ReflectionLookupCache.getDeclaredField(owner, name);
    }

    @Redirect(
            method = {"tryGetInstalledFromGetUpgrades", "tryInvokeNoArg"},
            at = @At(value = "INVOKE", target = "Ljava/lang/reflect/Method;invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 0)
    private static Object aco$invokeCachedHandle(Method method, Object receiver, Object[] arguments)
            throws IllegalAccessException, InvocationTargetException {
        return MethodHandleInvocationCache.invoke(method, receiver, arguments);
    }
}
