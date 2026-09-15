package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TransactionalV2NoAdapterBypassSourceTest {
    @Test
    void rejectsAnEmptyAdapterRegistryBeforeReadingJobState() throws IOException {
        Path source = Path.of(
                "src/main/java/com/syaru/ae2craftingoptimizer/optimization/TransactionalCraftingExecutorV2.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);

        int adapterGate = text.indexOf("if (!PatternBatchV2Api.hasRegisteredAdapters())");
        int receiptLookup = text.indexOf("Ae2BatchSourceReconciler.receiptStore(logic)");
        int jobLookup = text.indexOf("logicAccess.aco$getExecutingJob()");

        assertTrue(adapterGate >= 0, "Adapter 0件の早期bypassが必要です");
        assertTrue(receiptLookup > adapterGate, "Receipt取得はAdapter 0件判定より後である必要があります");
        assertTrue(jobLookup > adapterGate, "Job取得はAdapter 0件判定より後である必要があります");
    }
}
