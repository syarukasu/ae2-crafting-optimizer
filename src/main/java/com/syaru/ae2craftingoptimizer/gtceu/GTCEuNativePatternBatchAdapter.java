package com.syaru.ae2craftingoptimizer.gtceu;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchRecoveryResult;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchCommit;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch;
import com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter;
import com.syaru.ae2craftingoptimizer.batch.NativePatternBatchSupport;
import com.syaru.ae2craftingoptimizer.batch.PatternProviderReceiptResolver;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public final class GTCEuNativePatternBatchAdapter implements TransactionalPatternBatchAdapter {
    public static final GTCEuNativePatternBatchAdapter INSTANCE = new GTCEuNativePatternBatchAdapter();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            AE2CraftingOptimizer.MODID, "gtceu_native_batch");

    private GTCEuNativePatternBatchAdapter() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int priority() {
        return 200;
    }

    @Override
    public boolean supports(PatternBatchContext context) {
        if (!ACOConfig.enableGtceuNativeBatching()
                || !GTCEuNativeBatchBridge.supportsTarget(context)) {
            return false;
        }
        NativeBatchReceiptStore store = PatternProviderReceiptResolver.fromContext(context);
        return store != null && store.aco$isNativeBatchReceiptLedgerHealthy();
    }

    @Override
    public long limitExecutions(PatternBatchContext context, long offeredExecutions) {
        long safe = Math.min(
                Math.min(offeredExecutions, ACOConfig.getNativeBatchMaximumExecutions()),
                NativePatternBatchSupport.safeExecutionLimit(context, offeredExecutions));
        var verification = GTCEuNativeBatchBridge.verify(context, safe);
        return verification == null ? 0L : Math.min(safe, verification.maximumExecutions());
    }

    @Override
    public PreparedPatternBatch prepare(
            PatternBatchContext context,
            PatternBatchBudget budget,
            UUID transactionId) {
        long executions = limitExecutions(context, budget.maximumExecutions());
        if (executions <= 0L) {
            throw new IllegalStateException("GTCEu native batch has no safe execution capacity");
        }
        CompoundTag adapterData = NativePatternBatchSupport.targetMetadata(context);
        adapterData.putString("pattern", NativePatternBatchSupport.fingerprint(context));
        var verification = GTCEuNativeBatchBridge.verify(context, executions);
        if (verification == null) {
            throw new IllegalStateException("GTCEu recipe no longer exactly matches the prepared pattern");
        }
        executions = Math.min(executions, verification.maximumExecutions());
        var scaledInputs = NativePatternBatchSupport.scaleInputs(context, executions);
        adapterData.putString("recipe", verification.recipeId().toString());
        adapterData.putInt("nativeParallelLimit", verification.maximumExecutions());
        return new PreparedPatternBatch(
                transactionId,
                executions,
                NativePatternBatchSupport.flatten(scaledInputs),
                NativePatternBatchSupport.scaleOutputs(context, executions),
                adapterData);
    }

    @Override
    public PatternBatchCommit commit(PatternBatchContext context, PreparedPatternBatch prepared) {
        NativeBatchReceiptStore store = PatternProviderReceiptResolver.fromContext(context);
        if (store == null || !store.aco$isNativeBatchReceiptLedgerHealthy()) {
            return new PatternBatchCommit(0L, "missing-receipt-store", prepared.adapterData());
        }
        String fingerprint = prepared.adapterData().getString("pattern");
        ResourceLocation recipeId = ResourceLocation.tryParse(prepared.adapterData().getString("recipe"));
        if (recipeId == null
                || !GTCEuNativeBatchBridge.revalidate(
                        context, recipeId, prepared.offeredExecutions())) {
            return new PatternBatchCommit(0L, "gtceu-revalidation-failed", prepared.adapterData());
        }
        NativeBatchReceipt existing = store.aco$getNativeBatchReceipt(prepared.transactionId());
        if (existing != null) {
            validateExisting(existing, prepared, fingerprint);
            return switch (existing.state()) {
                case ACCEPTED -> new PatternBatchCommit(
                        existing.executions(), receipt(existing), prepared.adapterData());
                case REJECTED -> new PatternBatchCommit(0L, receipt(existing), prepared.adapterData());
                case PENDING -> throw new IllegalStateException(
                        "GTCEu native batch transaction is already pending: " + prepared.transactionId());
            };
        }

        long now = context.level().getGameTime();
        NativeBatchReceipt pending = new NativeBatchReceipt(
                prepared.transactionId(),
                NativeBatchReceipt.State.PENDING,
                prepared.offeredExecutions(),
                fingerprint,
                now);
        if (!store.aco$prepareNativeBatchReceipt(pending)) {
            return new PatternBatchCommit(0L, "receipt-capacity", prepared.adapterData());
        }
        boolean accepted = context.provider().pushPattern(
                context.pattern(),
                NativePatternBatchSupport.scaleInputs(context, prepared.offeredExecutions()));
        NativeBatchReceipt.State state = accepted
                ? NativeBatchReceipt.State.ACCEPTED
                : NativeBatchReceipt.State.REJECTED;
        store.aco$finishNativeBatchReceipt(prepared.transactionId(), state, now);
        NativeBatchReceipt finished = store.aco$getNativeBatchReceipt(prepared.transactionId());
        return new PatternBatchCommit(
                accepted ? prepared.offeredExecutions() : 0L,
                receipt(finished),
                prepared.adapterData());
    }

    @Override
    public void rollback(PatternBatchContext context, PreparedPatternBatch prepared) {
        NativeBatchReceiptStore store = PatternProviderReceiptResolver.fromContext(context);
        NativeBatchReceipt receipt = store == null ? null : store.aco$getNativeBatchReceipt(prepared.transactionId());
        if (receipt != null && receipt.state() == NativeBatchReceipt.State.PENDING) {
            store.aco$finishNativeBatchReceipt(
                    prepared.transactionId(), NativeBatchReceipt.State.REJECTED, context.level().getGameTime());
        }
    }

    @Override
    public BatchRecoveryResult reconcileTarget(ServerLevel level, BatchTransactionRecord record) {
        CompoundTag metadata = record.adapterData();
        if (!PatternProviderReceiptResolver.validMetadata(metadata)) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE, 0L, "GTCEu provider metadata is malformed");
        }
        var providerPos = net.minecraft.core.BlockPos.of(metadata.getLong("providerPos"));
        if (!level.isLoaded(providerPos)) {
            return new BatchRecoveryResult(BatchRecoveryResult.TargetState.RETRY, 0L, "GTCEu provider chunk is not loaded");
        }
        NativeBatchReceiptStore store = PatternProviderReceiptResolver.fromRecord(level, metadata);
        if (store == null) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "GTCEu batch provider was removed or replaced while a transaction was unresolved");
        }
        if (!store.aco$isNativeBatchReceiptLedgerHealthy()) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "GTCEu provider receipt ledger is malformed");
        }
        NativeBatchReceipt receipt = store.aco$getNativeBatchReceipt(record.id());
        if (receipt == null) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.NOT_ACCEPTED,
                    0L,
                    "GTCEu target has no receipt");
        }
        if (receipt.state() == NativeBatchReceipt.State.PENDING) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "GTCEu receipt remained pending across a save boundary");
        }
        if (!receipt.patternFingerprint().equals(record.patternFingerprint())
                || receipt.executions() != record.offeredExecutions()) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "GTCEu receipt does not match journal metadata");
        }
        return new BatchRecoveryResult(
                receipt.state() == NativeBatchReceipt.State.ACCEPTED
                        ? BatchRecoveryResult.TargetState.ACCEPTED
                        : BatchRecoveryResult.TargetState.NOT_ACCEPTED,
                receipt.state() == NativeBatchReceipt.State.ACCEPTED ? receipt.executions() : 0L,
                receipt.state().name());
    }

    @Override
    public void forgetResolvedTarget(PatternBatchContext context, UUID transactionId) {
        NativeBatchReceiptStore store = PatternProviderReceiptResolver.fromContext(context);
        if (store != null) {
            store.aco$removeTerminalNativeBatchReceipt(transactionId);
        }
    }

    @Override
    public void forgetResolvedTarget(ServerLevel level, BatchTransactionRecord record) {
        NativeBatchReceiptStore store = PatternProviderReceiptResolver.fromRecord(level, record.adapterData());
        if (store != null) {
            store.aco$removeTerminalNativeBatchReceipt(record.id());
        }
    }

    private static void validateExisting(
            NativeBatchReceipt receipt,
            PreparedPatternBatch prepared,
            String fingerprint) {
        if (receipt.executions() != prepared.offeredExecutions()
                || !receipt.patternFingerprint().equals(fingerprint)) {
            throw new IllegalStateException("native batch transaction id was reused with different content");
        }
    }

    private static String receipt(NativeBatchReceipt receipt) {
        return receipt.transactionId() + ":" + receipt.state() + ":" + receipt.executions();
    }
}
