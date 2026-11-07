package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Caches AE2 Overclock reflective invocation handles while preserving Method.invoke semantics. */
public final class MethodHandleInvocationCache {
    private static final Object[] EMPTY_ARGUMENTS = new Object[0];
    private static final Map<Method, Optional<CachedHandle>> HANDLES = new ConcurrentHashMap<>();

    private MethodHandleInvocationCache() {
    }

    public static Object invoke(Method method, Object receiver, Object[] arguments)
            throws IllegalAccessException, InvocationTargetException {
        if (!ACOConfig.useAe2OverclockMethodHandles()) {
            return method.invoke(receiver, arguments);
        }

        Optional<CachedHandle> cached = HANDLES.computeIfAbsent(method, MethodHandleInvocationCache::unreflect);
        if (cached.isEmpty()) {
            return method.invoke(receiver, arguments);
        }

        CachedHandle target = cached.get();
        try {
            if (target.parameterCount == 0 && (arguments == null || arguments.length == 0)) {
                return target.isStatic
                        ? target.handle.invokeExact()
                        : target.handle.invokeExact(receiver);
            }
            Object[] actualArguments = arguments == null ? EMPTY_ARGUMENTS : arguments;
            return target.isStatic
                    ? target.handle.invokeExact(actualArguments)
                    : target.handle.invokeExact(receiver, actualArguments);
        } catch (Throwable throwable) {
            throw new InvocationTargetException(throwable);
        }
    }

    public static void clear() {
        HANDLES.clear();
    }

    private static Optional<CachedHandle> unreflect(Method method) {
        try {
            method.trySetAccessible();
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            int parameterCount = method.getParameterCount();
            MethodHandle raw = MethodHandles.lookup().unreflect(method).asFixedArity();
            MethodHandle adapted;
            if (parameterCount == 0) {
                adapted = isStatic
                        ? raw.asType(MethodType.methodType(Object.class))
                        : raw.asType(MethodType.methodType(Object.class, Object.class));
            } else {
                adapted = isStatic
                        ? raw.asSpreader(Object[].class, parameterCount)
                                .asType(MethodType.methodType(Object.class, Object[].class))
                        : raw.asSpreader(Object[].class, parameterCount)
                                .asType(MethodType.methodType(Object.class, Object.class, Object[].class));
            }
            return Optional.of(new CachedHandle(adapted, isStatic, parameterCount));
        } catch (IllegalAccessException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private record CachedHandle(MethodHandle handle, boolean isStatic, int parameterCount) {
    }
}
