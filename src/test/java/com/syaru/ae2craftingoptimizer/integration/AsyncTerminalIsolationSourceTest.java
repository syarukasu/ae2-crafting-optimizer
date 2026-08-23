package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AsyncTerminalIsolationSourceTest {
    @Test
    void workerSortUsesProjectedAmountAndNoMidMethodSortRedirect() throws Exception {
        String view = Files.readString(
                Path.of("src/main/java/com/syaru/ae2craftingoptimizer/client/AsyncTerminalView.java"),
                StandardCharsets.UTF_8);
        String mixin = Files.readString(
                Path.of("src/main/java/com/syaru/ae2craftingoptimizer/mixin/ClientRepoUpdateCoalescingMixin.java"),
                StandardCharsets.UTF_8);
        assertTrue(view.contains("Projection::normalizedAmount"));
        assertFalse(view.contains("projection.entry.getStoredAmount()"));
        assertFalse(mixin.contains("@Redirect"));
        assertTrue(mixin.contains("aco$viewGeneration != generation"));
    }
}
