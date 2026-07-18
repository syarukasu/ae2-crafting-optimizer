package com.syaru.ae2craftingoptimizer.mekanism;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchRecoveryResult;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchPayloadFingerprint;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchCommit;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch;
import com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter;
import com.syaru.ae2craftingoptimizer.batch.NativePatternBatchSupport;
import com.syaru.ae2craftingoptimizer.batch.PatternProviderReceiptResolver;
import com.syaru.ae2craftingoptimizer.batch.PatternProviderBatchEscrow;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * MekanismのItem・Fluid・Chemical入力を完全一致でまとめて受理させるAdapter。
 * CachedRecipeとOperationTrackerが示す実行可能数を超えず、部分受理時は成功として会計しない。
 */
public final class MekanismNativePatternBatchAdapter implements TransactionalPatternBatchAdapter {
    public static final MekanismNativePatternBatchAdapter INSTANCE = new MekanismNativePatternBatchAdapter();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            AE2CraftingOptimizer.MODID, "mekanism_native_batch");

    private MekanismNativePatternBatchAdapter() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int priority() {
        return 190;
    }

    @Override
    public boolean supports(PatternBatchContext context) {
        if (!ACOConfig.enableMekanismNativeBatching()
                || !MekanismNativeBatchBridge.supportsTarget(context)) {
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
        var verification = MekanismNativeBatchBridge.verify(context, safe);
        return verification == null ? 0L : Math.min(safe, verification.maximumExecutions());
    }

    @Override
    public PreparedPatternBatch prepare(
            PatternBatchContext context,
            PatternBatchBudget budget,
            UUID transactionId) {
        long executions = limitExecutions(context, budget.maximumExecutions());
        if (executions <= 0L) {
            throw new IllegalStateException("Mekanism native batch has no safe execution capacity");
        }
        CompoundTag adapterData = NativePatternBatchSupport.targetMetadata(context);
        adapterData.putString("pattern", NativePatternBatchSupport.fingerprint(context));
        var verification = MekanismNativeBatchBridge.verify(context, executions);
        if (verification == null) {
            throw new IllegalStateException("Mekanism recipe no longer exactly matches the prepared pattern");
        }
        executions = Math.min(executions, verification.maximumExecutions());
        var scaledInputs = NativePatternBatchSupport.scaleInputs(context, executions);
        adapterData.putString("recipe", verification.recipeId().toString());
        adapterData.putInt("nativeOperationLimit", verification.maximumExecutions());
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
                || !MekanismNativeBatchBridge.revalidate(
                        context, recipeId, prepared.offeredExecutions())) {
            return new PatternBatchCommit(0L, "mekanism-revalidation-failed", prepared.adapterData());
        }
        // Item・Fluid・ChemicalをAEKeyのままProvider Escrowへ移す。機械への挿入が
        // 部分成功しても、残量はProvider send bufferが所有し続ける。
        return PatternProviderBatchEscrow.stage(
                context, prepared, fingerprint, "mekanism-provider-escrow-busy");
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
                    BatchRecoveryResult.TargetState.QUARANTINE, 0L, "Mekanism provider metadata is malformed");
        }
        var providerPos = net.minecraft.core.BlockPos.of(metadata.getLong("providerPos"));
        if (!level.isLoaded(providerPos)) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.RETRY, 0L, "Mekanism provider chunk is not loaded");
        }
        NativeBatchReceiptStore store = PatternProviderReceiptResolver.fromRecord(level, metadata);
        if (store == null) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "Mekanism batch provider was removed or replaced while a transaction was unresolved");
        }
        if (!store.aco$isNativeBatchReceiptLedgerHealthy()) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "Mekanism provider receipt ledger is malformed");
        }
        NativeBatchReceipt receipt = store.aco$getNativeBatchReceipt(record.id());
        if (receipt == null) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.NOT_ACCEPTED, 0L, "Mekanism target has no receipt");
        }
        if (receipt.state() == NativeBatchReceipt.State.PENDING) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "Mekanism receipt remained pending across a save boundary");
        }
        if (!receipt.patternFingerprint().equals(record.patternFingerprint())
                || receipt.executions() != record.offeredExecutions()
                || !receipt.payloadDigest().equals(BatchPayloadFingerprint.of(record))) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "Mekanism receipt does not match journal metadata");
        }
        boolean accepted = receipt.state() == NativeBatchReceipt.State.ACCEPTED;
        return new BatchRecoveryResult(
                accepted
                        ? BatchRecoveryResult.TargetState.ACCEPTED
                        : BatchRecoveryResult.TargetState.NOT_ACCEPTED,
                accepted ? receipt.executions() : 0L,
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

}
