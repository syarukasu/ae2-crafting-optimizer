package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import com.syaru.ae2craftingoptimizer.api.processing.ProcessingPatternBatchRequest;
import com.syaru.ae2craftingoptimizer.api.processing.ProcessingPatternBatchTarget;
import java.util.Optional;
import java.util.UUID;

/** Internal transport adapter only: no ledger, narrowing, recipe execution or fallback. */
record ProcessingPatternTargetBridge(ProcessingPatternBatchTarget target) implements CraftingTableBatchTarget {
    @Override public boolean aco$acceptCraftingTableBatch(CraftingTableBatchRequest request) {
        return target.aco$acceptProcessingPatternBatch(new ProcessingPatternBatchRequest(request));
    }
    @Override public boolean aco$ownsCraftingTableBatch(UUID id, String digest) {
        return target.aco$ownsProcessingPatternBatch(id, digest);
    }
    @Override public Optional<CraftingTableBatchSnapshot> aco$craftingTableBatchSnapshot(UUID id, String digest) {
        return target.aco$processingPatternBatchSnapshot(id, digest);
    }
    @Override public boolean aco$acknowledgeCraftingTableBatch(UUID id, String digest) {
        return target.aco$acknowledgeProcessingPatternBatch(id, digest);
    }
    @Override public boolean aco$forgetCraftingTableBatch(UUID id, String digest) {
        return target.aco$forgetProcessingPatternBatch(id, digest);
    }
    @Override public boolean aco$cancelCraftingTableBatch(UUID id, String digest) {
        return target.aco$cancelProcessingPatternBatch(id, digest);
    }
}
