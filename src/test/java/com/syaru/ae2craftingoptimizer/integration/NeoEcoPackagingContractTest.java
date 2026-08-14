package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NeoEcoPackagingContractTest {
    @Test
    void metadataAcceptsBothVerifiedNeoEcoMinorLines() throws IOException {
        String metadata = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));
        assertTrue(metadata.contains("versionRange = \"[20.3.0,20.5.0)\""));
    }

    @Test
    void mixinConfigRegistersOnlyTheVersionSpecificIntegrations() throws IOException {
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2_crafting_optimizer.mixins.json"));
        assertTrue(mixinConfig.contains("\"NeoEco20_3CraftingCpuExecutionBudgetMixin\""));
        assertTrue(mixinConfig.contains("\"NeoEco20_4CraftingCpuExecutionBudgetMixin\""));
        assertFalse(mixinConfig.contains("\"NeoEcoCraftingCpuExecutionBudgetMixin\""));
    }
}
