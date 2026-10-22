package com.syaru.ae2craftingoptimizer.api.processing;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable wide processing request using the existing exact-accounting validator. */
public final class ProcessingPatternBatchRequest {
    private final CraftingTableBatchRequest accounting;

    public ProcessingPatternBatchRequest(CraftingTableBatchRequest accounting) {
        this.accounting = Objects.requireNonNull(accounting, "accounting");
        if (accounting.mode() != CraftingTableBatchMode.BIG_INTEGER_JOB
                || accounting.pattern() instanceof IMolecularAssemblerSupportedPattern) {
            throw new IllegalArgumentException("processing requests require a BigInteger processing pattern");
        }
    }

    public UUID transactionId() { return accounting.transactionId(); }
    public UUID ownerTransactionId() { return accounting.ownerTransactionId(); }
    public UUID craftingJobId() { return accounting.craftingJobId(); }
    public String payloadDigest() { return accounting.payloadDigest(); }
    public int stageIndex() { return accounting.stageIndex(); }
    public IPatternDetails pattern() { return accounting.pattern(); }
    public BigInteger executions() { return accounting.executions(); }
    public KeyCounter[] inputsPerExecution() { return accounting.inputsPerExecution(); }
    public List<ExactStack> aggregateSlotInputs() { return accounting.aggregateSlotInputs(); }
    public List<GenericStack> outputsPerExecution() { return accounting.outputsPerExecution(); }
    public List<GenericStack> remainingPerExecution() { return accounting.remainingPerExecution(); }
    public Map<AEKey, BigInteger> aggregateInputTotals() { return accounting.aggregateInputTotals(); }
    public Map<AEKey, BigInteger> aggregateExpectedOutputs() { return accounting.aggregateExpectedOutputs(); }
}
