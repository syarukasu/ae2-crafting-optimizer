package com.syaru.ae2craftingoptimizer.api.batch.v2;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public interface BatchSourceReconciler {
    ResourceLocation id();

    default SourceRecoveryResult rollbackPrepared(
            ServerLevel level,
            BatchTransactionRecord record) {
        throw new UnsupportedOperationException(
                "source reconciler does not implement public transaction recovery");
    }

    default SourceRecoveryResult accountAccepted(
            ServerLevel level,
            BatchTransactionRecord record) {
        throw new UnsupportedOperationException(
                "source reconciler does not implement public transaction recovery");
    }

    /** Called after the matching durable journal record reached a terminal phase. */
    default void forgetResolvedSource(ServerLevel level, BatchTransactionRecord record) {
    }
}
