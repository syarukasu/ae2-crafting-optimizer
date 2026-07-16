package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Caches AE2 Overclock reflective invocation handles while preserving Method.invoke semantics. */
public final class MethodHandleInvocationCache {
    private static final Map<Method, Optional<MethodHandle>> HANDLES = new ConcurrentHashMap<>();

    private MethodHandleInvocationCache() {
    }

    public static Object invoke(Method method, Object receiver, Object[] arguments)
            throws IllegalAccessException, InvocationTargetException {
        if (!ACOConfig.useAe2OverclockMethodHandles()) {
            return method.invoke(receiver, arguments);
        }

        Optional<MethodHandle> cached = HANDLES.computeIfAbsent(method, MethodHandleInvocationCache::unreflect);
        if (cached.isEmpty()) {
            return method.invoke(receiver, arguments);
        }

        var invocationArguments = new ArrayList<Object>(
                (arguments == null ? 0 : arguments.length) + (Modifier.isStatic(method.getModifiers()) ? 0 : 1));
        if (!Modifier.isStatic(method.getModifiers())) {
            invocationArguments.add(receiver);
        }
        if (arguments != null) {
            java.util.Collections.addAll(invocationArguments, arguments);
        }

        try {
            return cached.get().invokeWithArguments(invocationArguments);
        } catch (Throwable throwable) {
            throw new InvocationTargetException(throwable);
        }
    }

    public static void clear() {
        HANDLES.clear();
    }

    private static Optional<MethodHandle> unreflect(Method method) {
        try {
            method.trySetAccessible();
            return Optional.of(MethodHandles.lookup().unreflect(method).asFixedArity());
        } catch (IllegalAccessException | RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
