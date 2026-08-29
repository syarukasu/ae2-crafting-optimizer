package com.syaru.ae2craftingoptimizer.issue125;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #151: PR #127の安全性不変条件をバイト列ではなく責務として固定する。 */
class Pr127StableBaselineSourceTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    @Test
    void standardAe2OwnsTheExactPhysicalJobLifecycle() throws IOException {
        String lifecycle = read("com/syaru/ae2craftingoptimizer/lifecycle/ACOServerLifecycle.java");
        String manager = read("com/syaru/ae2craftingoptimizer/integration/Ae2BigCraftingExecutionManager.java");

        assertTrue(lifecycle.contains("Ae2BigCraftingExecutionManager.tick"));
        assertTrue(lifecycle.contains("Ae2BigCraftingExecutionManager.clear"));
        assertTrue(manager.contains("PhysicalCraftingTreeTransaction"));
        assertFalse(lifecycle.contains("AqeBigCraftingExecutionManager"));
        assertFalse(lifecycle.contains("OptionalAqeBigCraftingExecution"));
    }

    @Test
    void genericTargetResolutionDoesNotOwnProviderSendBuffers() throws IOException {
        String resolver = read(
                "com/syaru/ae2craftingoptimizer/engine/craftingtable/CraftingTableBatchTargetResolver.java");
        String access = read(
                "com/syaru/ae2craftingoptimizer/access/PatternProviderTargetAccess.java");

        assertTrue(resolver.contains("PatternProviderTargetAccess"));
        assertTrue(resolver.contains("CraftingTableBatchTarget"));
        assertFalse(access.contains("aco$stageOwnedBatch"));
        assertFalse(access.contains("NativeBatchReceipt"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
