package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class ReflectionLookupCache {
    private static final ClassValue<LookupTables> CACHE = new ClassValue<>() {
        @Override
        protected LookupTables computeValue(Class<?> type) {
            return new LookupTables();
        }
    };

    private ReflectionLookupCache() {
    }

    public static Field getDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        if (!ACOConfig.cacheAe2OverclockReflection()) {
            return owner.getDeclaredField(name);
        }
        LookupResult<Field> result = lookup(
                CACHE.get(owner).declaredFields,
                name,
                () -> declaredField(owner, name));
        if (result.member != null) {
            return result.member;
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    public static Field findFieldInHierarchy(Class<?> owner, String name) throws NoSuchFieldException {
        Class<?> current = owner;
        NoSuchFieldException failure = null;
        while (current != null) {
            try {
                return getDeclaredField(current, name);
            } catch (NoSuchFieldException exception) {
                failure = exception;
                current = current.getSuperclass();
            }
        }
        throw failure == null ? new NoSuchFieldException(owner.getName() + "." + name) : failure;
    }

    public static Method getDeclaredMethod(Class<?> owner, String name, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        if (!ACOConfig.cacheAe2OverclockReflection()) {
            return owner.getDeclaredMethod(name, parameterTypes);
        }
        MethodKey key = MethodKey.of(name, parameterTypes);
        LookupResult<Method> result = lookup(
                CACHE.get(owner).declaredMethods,
                key,
                () -> declaredMethod(owner, name, parameterTypes));
        if (result.member != null) {
            return result.member;
        }
        throw new NoSuchMethodException(owner.getName() + "." + key);
    }

    public static Method getMethod(Class<?> owner, String name, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        if (!ACOConfig.cacheAe2OverclockReflection()) {
            return owner.getMethod(name, parameterTypes);
        }
        MethodKey key = MethodKey.of(name, parameterTypes);
        LookupResult<Method> result = lookup(
                CACHE.get(owner).publicMethods,
                key,
                () -> publicMethod(owner, name, parameterTypes));
        if (result.member != null) {
            return result.member;
        }
        throw new NoSuchMethodException(owner.getName() + "." + key);
    }

    private static LookupResult<Field> declaredField(Class<?> owner, String name) {
        try {
            return new LookupResult<>(owner.getDeclaredField(name));
        } catch (NoSuchFieldException exception) {
            return new LookupResult<>(null);
        }
    }

    private static LookupResult<Method> declaredMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        try {
            return new LookupResult<>(owner.getDeclaredMethod(name, parameterTypes));
        } catch (NoSuchMethodException exception) {
            return new LookupResult<>(null);
        }
    }

    private static LookupResult<Method> publicMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        try {
            return new LookupResult<>(owner.getMethod(name, parameterTypes));
        } catch (NoSuchMethodException exception) {
            return new LookupResult<>(null);
        }
    }

    private static <K, V> V lookup(ConcurrentMap<K, V> cache, K key, Supplier<V> factory) {
        V cached = cache.get(key);
        if (cached != null) {
            OptimizationMetrics.recordReflectionLookup(true);
            return cached;
        }
        V created = factory.get();
        V raced = cache.putIfAbsent(key, created);
        OptimizationMetrics.recordReflectionLookup(raced != null);
        return raced == null ? created : raced;
    }

    private static final class LookupTables {
        private final ConcurrentMap<String, LookupResult<Field>> declaredFields = new ConcurrentHashMap<>();
        private final ConcurrentMap<MethodKey, LookupResult<Method>> declaredMethods = new ConcurrentHashMap<>();
        private final ConcurrentMap<MethodKey, LookupResult<Method>> publicMethods = new ConcurrentHashMap<>();
    }

    private record LookupResult<T>(T member) {
    }

    private record MethodKey(String name, List<Class<?>> parameterTypes) {
        private static MethodKey of(String name, Class<?>[] parameterTypes) {
            Class<?>[] copy = parameterTypes == null ? new Class<?>[0] : parameterTypes.clone();
            return new MethodKey(name, List.copyOf(Arrays.asList(copy)));
        }
    }
}
