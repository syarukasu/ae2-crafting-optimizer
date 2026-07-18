package com.syaru.ae2craftingoptimizer.api.batch.v2;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record PreparedPatternBatch(
        UUID transactionId,
        long offeredExecutions,
        List<GenericStack> aggregateInputs,
        List<GenericStack> expectedOutputs,
        CompoundTag adapterData) {
    public PreparedPatternBatch {
        Objects.requireNonNull(transactionId, "transactionId");
        if (offeredExecutions <= 0L) {
            throw new IllegalArgumentException("offeredExecutions must be positive");
        }
        aggregateInputs = List.copyOf(aggregateInputs);
        expectedOutputs = List.copyOf(expectedOutputs);
        adapterData = Objects.requireNonNull(adapterData, "adapterData").copy();
    }

    @Override
    public CompoundTag adapterData() {
        return adapterData.copy();
    }
}
