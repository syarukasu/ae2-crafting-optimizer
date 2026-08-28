package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #164: debug.logだけでACOの計画・実行境界を追跡できる契約を固定する。 */
class Issue164DiagnosticsSourceTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    @Test
    void planningAndGraphEventsUseStableStructuredNames() throws IOException {
        String calculation = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationDiagnostics.java");
        String decline = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/BigIntegerPlanDiagnostics.java");
        String graph = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2CompiledCraftingGraphCache.java");

        assertTrue(calculation.contains("ACO-DIAG event=planning_complete"));
        assertTrue(calculation.contains("ACO-DIAG event=planning_slow"));
        assertTrue(decline.contains("ACO-DIAG event=planning_declined"));
        assertTrue(graph.contains("ACO-DIAG event=graph_compiled"));
        assertTrue(graph.contains("ACO-DIAG event=graph_recompiled"));
    }

    @Test
    void exactLifecycleEventsAreCorrelatedAndWaitingIsRateLimited() throws IOException {
        String manager = readMain(
                "com/syaru/ae2craftingoptimizer/integration/Ae2BigCraftingExecutionManager.java");

        assertTrue(manager.contains("\"exact_restored\""));
        assertTrue(manager.contains("\"exact_started\""));
        assertTrue(manager.contains("ACO-DIAG event=exact_waiting"));
        assertTrue(manager.contains("\"exact_completed\""));
        assertTrue(manager.contains("\"exact_cancelled\""));
        assertTrue(manager.contains("ACO-DIAG event=exact_quarantined"));
        assertTrue(manager.contains("stallTicksSinceLog >= 600"));
        assertTrue(manager.contains("jobId={} cpu={} transactionId={} state={}"));
    }

    @Test
    void diagnosticsRemainConfigurationControlledAndDoNotLogFullCollections() throws IOException {
        String config = readMain(
                "com/syaru/ae2craftingoptimizer/config/ACOConfig.java");
        String calculation = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationDiagnostics.java");

        assertTrue(config.contains("define(\"logCraftingDecisionFlow\", true)"));
        assertTrue(calculation.contains("sidecar.getClass().getSimpleName()"));
        assertTrue(calculation.contains("sidecar.exactBytes()"));
        assertTrue(calculation.contains("amount.bitLength()"));
    }

    private static String readMain(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
