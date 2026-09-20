package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Wiring guard only; this is not a substitute for real CPU/worker completion tests. */
class SelectedBranchConsumerContractTest {
    @Test void bothConsumersSelectSavedBranchesBeforeUniqueProducerFallback() throws Exception {
        for (String file : new String[] {"api/big/BigCraftingPhysicalExecution.java",
                "integration/Ae2BigCraftingExecutionManager.java"}) {
            String code = Files.readString(Path.of("src/main/java/com/syaru/ae2craftingoptimizer/" + file));
            int prepare = code.indexOf("PreparedVectorBatch prepare(");
            if (prepare < 0) prepare = code.indexOf("BigCraftingPhysicalExecution prepare(");
            assertTrue(prepare >= 0);
            String body = code.substring(prepare);
            int branch = body.indexOf("state.selectedBranch().isPresent()");
            int legacy = body.indexOf(".rootProgram(state.requestedKey())");
            assertTrue(branch >= 0 && legacy > branch, file);
            assertTrue(body.substring(branch, legacy).contains("SelectedBranchPhysicalPlan.forJob("));
            assertTrue(body.substring(branch, legacy).contains("SelectedBranchPhysicalPlan.validateBindings("));
        }
    }

    @Test void physicalBranchCoversSurplusStorageAndNeverMaterializesMissingSimulationAsExecution() throws Exception {
        Path base = Path.of("src/main/java/com/syaru/ae2craftingoptimizer");
        String storage = Files.readString(base.resolve("integration/ExactBoundaryRoutePreflight.java"));
        assertTrue(storage.contains("branch.remainingOutputs()"));
        assertTrue(storage.contains("boundaryKeys.addAll(outputs.keySet())"));
        String planner = Files.readString(base.resolve("engine/Ae2AuthoritativeCraftingPlanner.java"));
        assertTrue(planner.contains("if (wide && plan.craftable())"));
        assertTrue(planner.contains("prepared, true, physicalBranch, completed.multiplePaths()"));
        assertFalse(planner.contains("physical execution requires a branching consumer"));
    }
}
