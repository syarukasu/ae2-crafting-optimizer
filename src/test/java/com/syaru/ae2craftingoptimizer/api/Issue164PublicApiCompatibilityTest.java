package com.syaru.ae2craftingoptimizer.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #164: クリーンブレイク後も外部アドオン用の公開契約を保持する。 */
class Issue164PublicApiCompatibilityTest {
    private static final Path API_ROOT = Path.of(
            "src", "main", "java", "com", "syaru", "ae2craftingoptimizer", "api");

    @Test
    void exactCountAndHostApisRemainAvailable() {
        assertApiExists("big/BigCraftingEngineApi.java");
        assertApiExists("big/BigCraftingHostRuntime.java");
        assertApiExists("big/BigCraftingHostRegistry.java");
        assertApiExists("big/BigCraftingHostRegistration.java");
        assertApiExists("contract/ExactCountLimits.java");
    }

    @Test
    void externalWorkerContractsRemainAvailable() {
        assertApiExists("batch/v2/PatternBatchV2Api.java");
        assertApiExists("batch/v2/TransactionalPatternBatchAdapter.java");
        assertApiExists("batch/v2/ProviderOwnedPatternBatchTarget.java");
        assertApiExists("craftingtable/CraftingTableBatchTarget.java");
        assertApiExists("vector/ExactVectorExecutionBudget.java");
    }

    private static void assertApiExists(String relativePath) {
        assertTrue(Files.isRegularFile(API_ROOT.resolve(relativePath)), relativePath);
    }
}
