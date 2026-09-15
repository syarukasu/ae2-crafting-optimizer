package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AsyncTerminalIsolationSourceTest {
    @Test
    void terminalUpdateOwnershipRemainsWithAe2() throws Exception {
        Path retiredMixin = Path.of(
                "src/main/java/com/syaru/ae2craftingoptimizer/mixin/ClientRepoUpdateCoalescingMixin.java");
        String mixinConfig = Files.readString(
                Path.of("src/main/resources/ae2_crafting_optimizer.mixins.json"),
                StandardCharsets.UTF_8);

        assertFalse(Files.exists(retiredMixin));
        assertFalse(mixinConfig.contains("ClientRepoUpdateCoalescingMixin"));
        assertFalse(mixinConfig.contains("Ae2ScrollbarReleaseSafetyMixin"));
    }
}
