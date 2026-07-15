package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;

public final class AdvancedAeCraftingInvoker {
    private static final Map<Class<?>, Method> EXECUTE_CRAFTING_METHODS = new ConcurrentHashMap<>();

    private AdvancedAeCraftingInvoker() {
    }

    public static int executeCrafting(
            Object logic,
            int maxOperations,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        Method method = EXECUTE_CRAFTING_METHODS.computeIfAbsent(logic.getClass(), AdvancedAeCraftingInvoker::findExecuteCrafting);
        try {
            return (Integer) method.invoke(logic, maxOperations, craftingService, energyService, level);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to invoke Advanced AE crafting execution method.", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Advanced AE crafting execution failed.", cause);
        }
    }

    private static Method findExecuteCrafting(Class<?> logicClass) {
        try {
            Method method = logicClass.getMethod(
                    "executeCrafting",
                    int.class,
                    CraftingService.class,
                    IEnergyService.class,
                    Level.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(
                    "Advanced AE executeCrafting method was not found on " + logicClass.getName(),
                    exception);
        }
    }
}
