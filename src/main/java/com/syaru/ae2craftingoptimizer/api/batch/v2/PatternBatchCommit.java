package com.syaru.ae2craftingoptimizer.api.batch.v2;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

public record PatternBatchCommit(long acceptedExecutions, String receipt, CompoundTag adapterData) {
    public PatternBatchCommit {
        if (acceptedExecutions < 0L) {
            throw new IllegalArgumentException("acceptedExecutions must not be negative");
        }
        receipt = Objects.requireNonNull(receipt, "receipt");
        if (receipt.length() > 4096) {
            throw new IllegalArgumentException("receipt must not exceed 4096 characters");
        }
        adapterData = Objects.requireNonNull(adapterData, "adapterData").copy();
    }

    @Override
    public CompoundTag adapterData() {
        return adapterData.copy();
    }
}
