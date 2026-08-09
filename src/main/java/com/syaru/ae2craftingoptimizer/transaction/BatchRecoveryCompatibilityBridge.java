package com.syaru.ae2craftingoptimizer.transaction;

import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchRecoveryResult;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReconciler;
import com.syaru.ae2craftingoptimizer.api.batch.v2.SourceRecoveryResult;
import com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;

/**
 * Keeps 1.5.x recovery implementations callable without exposing the journal
 * record from any public API type.
 */
final class BatchRecoveryCompatibilityBridge {
    private BatchRecoveryCompatibilityBridge() {
    }

    static BatchRecoveryResult reconcileTarget(
            TransactionalPatternBatchAdapter adapter,
            ServerLevel level,
            BatchTransactionRecord record) {
        Method publicMethod = publicMethod(
                adapter,
                "reconcileTarget",
                ServerLevel.class,
                com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord.class);
        if (publicMethod.getDeclaringClass() != TransactionalPatternBatchAdapter.class) {
            return adapter.reconcileTarget(level, record.toPublicView());
        }
        return invokeLegacy(
                adapter,
                "reconcileTarget",
                BatchRecoveryResult.class,
                level,
                record);
    }

    static SourceRecoveryResult rollbackPrepared(
            BatchSourceReconciler source,
            ServerLevel level,
            BatchTransactionRecord record) {
        Method publicMethod = publicMethod(
                source,
                "rollbackPrepared",
                ServerLevel.class,
                com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord.class);
        if (publicMethod.getDeclaringClass() != BatchSourceReconciler.class) {
            return source.rollbackPrepared(level, record.toPublicView());
        }
        return invokeLegacy(
                source,
                "rollbackPrepared",
                SourceRecoveryResult.class,
                level,
                record);
    }

    static SourceRecoveryResult accountAccepted(
            BatchSourceReconciler source,
            ServerLevel level,
            BatchTransactionRecord record) {
        Method publicMethod = publicMethod(
                source,
                "accountAccepted",
                ServerLevel.class,
                com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord.class);
        if (publicMethod.getDeclaringClass() != BatchSourceReconciler.class) {
            return source.accountAccepted(level, record.toPublicView());
        }
        return invokeLegacy(
                source,
                "accountAccepted",
                SourceRecoveryResult.class,
                level,
                record);
    }

    static void forgetTarget(
            TransactionalPatternBatchAdapter adapter,
            ServerLevel level,
            BatchTransactionRecord record) {
        Method publicMethod = publicMethod(
                adapter,
                "forgetResolvedTarget",
                ServerLevel.class,
                com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord.class);
        if (publicMethod.getDeclaringClass() != TransactionalPatternBatchAdapter.class) {
            adapter.forgetResolvedTarget(level, record.toPublicView());
            return;
        }
        invokeLegacyVoid(adapter, "forgetResolvedTarget", level, record);
    }

    static void forgetSource(
            BatchSourceReconciler source,
            ServerLevel level,
            BatchTransactionRecord record) {
        Method publicMethod = publicMethod(
                source,
                "forgetResolvedSource",
                ServerLevel.class,
                com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord.class);
        if (publicMethod.getDeclaringClass() != BatchSourceReconciler.class) {
            source.forgetResolvedSource(level, record.toPublicView());
            return;
        }
        invokeLegacyVoid(source, "forgetResolvedSource", level, record);
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes) {
        try {
            return target.getClass().getMethod(name, parameterTypes);
        } catch (NoSuchMethodException missing) {
            throw new IllegalStateException("recovery implementation has no " + name, missing);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeLegacy(
            Object target,
            String name,
            Class<T> resultType,
            ServerLevel level,
            BatchTransactionRecord record) {
        try {
            Method method = target.getClass().getMethod(
                    name,
                    ServerLevel.class,
                    BatchTransactionRecord.class);
            return (T) method.invoke(target, level, record);
        } catch (ReflectiveOperationException failure) {
            throw unwrap(name, failure);
        }
    }

    private static void invokeLegacyVoid(
            Object target,
            String name,
            ServerLevel level,
            BatchTransactionRecord record) {
        try {
            Method method = target.getClass().getMethod(
                    name,
                    ServerLevel.class,
                    BatchTransactionRecord.class);
            method.invoke(target, level, record);
        } catch (ReflectiveOperationException failure) {
            throw unwrap(name, failure);
        }
    }

    private static RuntimeException unwrap(String name, ReflectiveOperationException failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation
                ? invocation.getCause()
                : failure;
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("legacy recovery method failed: " + name, cause);
    }
}
