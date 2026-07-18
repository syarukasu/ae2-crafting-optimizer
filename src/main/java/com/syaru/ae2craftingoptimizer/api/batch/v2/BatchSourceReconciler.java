package com.syaru.ae2craftingoptimizer.api.batch.v2;

import com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public interface BatchSourceReconciler {
    ResourceLocation id();

    SourceRecoveryResult rollbackPrepared(ServerLevel level, BatchTransactionRecord record);

    SourceRecoveryResult accountAccepted(ServerLevel level, BatchTransactionRecord record);

    /** Called after the matching durable journal record reached a terminal phase. */
    default void forgetResolvedSource(ServerLevel level, BatchTransactionRecord record) {
    }
}
