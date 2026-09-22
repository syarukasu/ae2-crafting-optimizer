package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VisiblePlanningDiagnosticsSourceTest {
    @Test
    void terminalObservationDoesNotDependOnAnotherMixinsCancellableReturn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/syaru/ae2craftingoptimizer/mixin/CraftingCalculationDiagnosticsMixin.java"));
        assertTrue(source.contains("method = \"logCraftingJob\""),
                "Issue #209: observe AE2's plan before third-party RETURN wrappers");
        assertTrue(source.contains("CraftingCalculationDiagnostics.finishSummary("));
    }

    @Test
    void normalServerLogGetsPeriodicBoundedStatistics() throws Exception {
        String lifecycle = Files.readString(Path.of("src/main/java/com/syaru/ae2craftingoptimizer/lifecycle/ACOServerLifecycle.java"));
        assertTrue(lifecycle.contains("CraftingCalculationDiagnostics.logPeriodicSummary("));
        assertTrue(lifecycle.contains("CraftingCalculationDiagnostics.resetSummary("));
    }
}
