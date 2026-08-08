package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Keeps fail-closed accounting and fail-open optimization configs separate. */
class MixinConfigurationContractTest {
    private static final Path RESOURCE_ROOT = Path.of("src", "main", "resources");

    @Test
    void coreConfigIsRequiredWithNonZeroDefaultInjectionRequirement() throws IOException {
        String json = read("ae2_crafting_optimizer.mixins.json");
        assertTrue(json.contains("\"required\": true"));
        assertTrue(json.contains("CoreMixinConfigPlugin"));
        assertTrue(json.contains("\"defaultRequire\": 1"));
        assertFalse(json.contains("\"defaultRequire\": 0"));
    }

    @Test
    void optionalIntegrationConfigsAreSeparatedAndFailClosedWhenSelected() throws IOException {
        for (String name : List.of(
                "aco.integration.advanced_ae.mixins.json",
                "aco.integration.extendedae.mixins.json",
                "aco.integration.neoecoae.mixins.json",
                "aco.integration.ae2_overclocked.mixins.json",
                "aco.integration.gtceu.mixins.json",
                "aco.integration.mekanism.mixins.json")) {
            String json = read(name);
            assertTrue(json.contains("\"required\": false"), name);
            assertTrue(json.contains("ConfigPlugin"), name);
            assertTrue(json.contains("\"defaultRequire\": 1"), name);
        }
    }

    @Test
    void performanceConfigIsTheOnlyFailOpenConfig() throws IOException {
        String json = read("aco.performance.mixins.json");
        assertTrue(json.contains("PerformanceMixinConfigPlugin"));
        assertTrue(json.contains("\"defaultRequire\": 0"));
        assertFalse(json.contains("TransactionAccessMixin"));
        assertFalse(json.contains("NativeBatchReceiptMixin"));
    }

    private static String read(String name) throws IOException {
        return Files.readString(RESOURCE_ROOT.resolve(name));
    }
}
