package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Issue129ArchitectureSourceTest {
    private static final Path CONFIG = Path.of(
            "src/main/java/com/syaru/ae2craftingoptimizer/config/ACOConfig.java");

    @Test
    void invasiveEntryPointsUseTheCentralGate() throws IOException {
        String source = Files.readString(CONFIG, StandardCharsets.UTF_8);
        assertTrue(source.contains("OptimizationFeatureGate.allows("));
        assertTrue(source.contains("OptimizationFeature.BIG_INTEGER_BACKEND"));
        assertTrue(source.contains("OptimizationFeature.EXACT_VECTOR_CRAFTING"));
        assertTrue(source.contains("OptimizationFeature.CRAFTING_EXECUTION_BUDGET"));
        assertTrue(source.contains("OptimizationFeature.TERMINAL_UPDATE_COALESCING"));
        assertTrue(source.contains("OptimizationFeature.P2P_TOPOLOGY_DEDUPLICATION"));
    }

    @Test
    void issueSpecificationKeepsOwnershipAndProhibitionsVisible() throws IOException {
        String issue = Files.readString(Path.of("docs/issues/ISSUE-129.md"), StandardCharsets.UTF_8);
        assertTrue(issue.contains("ACO無効時は通常AE2"));
        assertTrue(issue.contains("AE2 GUIやvirtual slotのクリックを横取りする"));
        assertTrue(issue.contains("ownership取得後にAE2標準計算へfallback"));
        assertTrue(issue.contains("無制限cache、無制限queue、1tick全走査"));
    }

    @Test
    void removedMutableStorageMixinsCannotReturnSilently() throws IOException {
        String mixinConfig = Files.readString(
                Path.of("src/main/resources/ae2_crafting_optimizer.mixins.json"),
                StandardCharsets.UTF_8);
        String status = Files.readString(
                Path.of("docs/optimization/IMPLEMENTATION_STATUS.md"),
                StandardCharsets.UTF_8);
        String[] prohibitedMixins = {
                "StorageImportLastSuccessfulSlotMixin",
                "StorageImportSimulationCacheMixin",
                "StorageExportSimulationCacheMixin",
                "IOPortIncrementalProcessingMixin",
                "BlockApiCacheTickCacheMixin",
                "GridTickBudgetMixin"
        };
        // 1.2.2で消失・停止回帰のため撤去したMixinを、監査なしで再登録させない。
        for (String mixin : prohibitedMixins) {
            assertFalse(mixinConfig.contains('"' + mixin + '"'), mixin);
            assertTrue(status.contains('`' + mixin + '`'), mixin);
        }
    }

    @Test
    void safeExportCraftBackoffDoesNotDependOnRemovedGridTickRewrite() throws IOException {
        String config = Files.readString(CONFIG, StandardCharsets.UTF_8);
        int methodStart = config.indexOf("public static boolean throttleExportBusCraftRequests()");
        int methodEnd = config.indexOf("public static int getExportBusCraftFailureCooldownTicks()", methodStart);
        assertTrue(methodStart >= 0 && methodEnd > methodStart);
        String method = config.substring(methodStart, methodEnd);
        assertTrue(method.contains("OptimizationFeature.EXPORT_CRAFT_REQUEST_BACKOFF"));
        assertFalse(method.contains("enableGridTickBudget()"));
    }

    @Test
    void exportCandidateCacheFallsBackForUnknownConfigInventoryImplementations() throws IOException {
        String mixin = Files.readString(
                Path.of("src/main/java/com/syaru/ae2craftingoptimizer/mixin/ExportBusCandidateCacheMixin.java"),
                StandardCharsets.UTF_8);
        assertTrue(mixin.contains("instanceof ConfigInventoryGenerationAccess"));
        assertTrue(mixin.contains("return config.getKey(slot);"));
    }
}
