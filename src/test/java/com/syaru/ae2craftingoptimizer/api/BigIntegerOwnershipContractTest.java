package com.syaru.ae2craftingoptimizer.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BigIntegerOwnershipContractTest {
    @Test
    void publicApiExposesExternalConsumerRegistration() throws IOException {
        String source = readSource("src/main/java/com/syaru/ae2craftingoptimizer/api/big/BigCraftingEngineApi.java");

        assertTrue(source.contains("registerExternalBigIntegerPlanConsumer"));
        assertTrue(source.contains("EXTERNAL_CONSUMER_API_VERSION"));
    }

    @Test
    void mixinPluginDoesNotOwnInsaneAeExecution() throws IOException {
        String source = readSource("src/main/java/com/syaru/ae2craftingoptimizer/mixin/AcoMixinPlugin.java");

        // ACO自身のMixin選択に、外部CPU実装のmod IDや実行分岐を戻さない。
        assertFalse(source.contains("InsaneAE"));
        assertFalse(source.contains("insaneae"));
    }

    private static String readSource(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }
}
