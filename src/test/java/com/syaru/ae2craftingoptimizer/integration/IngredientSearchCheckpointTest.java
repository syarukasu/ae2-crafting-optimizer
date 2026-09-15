package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.syaru.ae2craftingoptimizer.mixin.CraftingCalculationDiagnosticsMixin;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Issue #179: exercise the callback, not just the presence of a pause annotation. */
class IngredientSearchCheckpointTest {
    @Test
    void ingredientCheckpointForcesAe2BudgetCheckWithoutChangingQuantityState() throws Exception {
        var calculation = new Harness();
        var checkpoint = CraftingCalculationDiagnosticsMixin.class.getMethod("aco$checkpointIngredientSearch");
        var incTime = CraftingCalculationDiagnosticsMixin.class.getDeclaredField("incTime");
        incTime.setAccessible(true);
        for (int initial : new int[] {0, 1, 100}) {
            incTime.setInt(calculation, initial);
            checkpoint.invoke(calculation);
            assertEquals(Integer.MAX_VALUE, incTime.getInt(calculation));
        }
        assertEquals(3, calculation.pauses.get());
    }

    @Test
    void cancellationDoesNotWaitForAnotherTick() throws Exception {
        var calculation = new Harness();
        var checkpoint = CraftingCalculationDiagnosticsMixin.class.getMethod("aco$checkpointIngredientSearch");
        try {
            Thread.currentThread().interrupt();
            var failure = assertThrows(InvocationTargetException.class, () -> checkpoint.invoke(calculation));
            assertInstanceOf(InterruptedException.class, failure.getCause());
            assertEquals(0, calculation.pauses.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void pausesAreConnectedBeforeCallbacksNotInsideMutableRecipeFrames() throws Exception {
        Path main = Path.of("src/main/java/com/syaru/ae2craftingoptimizer");
        var simulation = Files.readString(main.resolve("mixin/CraftingSimulationIngredientSearchMixin.java"));
        assertTrue(simulation.contains("method = \"cacheFuzzy\""));
        assertTrue(simulation.contains("simulateExtractParent(Lappeng/api/stacks/AEKey;J)J"));
        assertTrue(simulation.contains("CraftingCalculationMemo.checkpointIngredientSearch()"));
        var helper = Files.readString(main.resolve("mixin/CraftingCpuHelperCalculationMemoMixin.java"));
        assertTrue(helper.contains("Ljava/util/Iterator;next()Ljava/lang/Object;"));
        assertTrue(helper.indexOf("CraftingCalculationMemo.checkpointIngredientSearch()",
                helper.indexOf("aco$memoizePureInputValidation"))
                < helper.indexOf("return CraftingCalculationMemo.inputValid("));
        assertFalse(simulation.contains("testFrame"));
    }

    private static final class Harness extends CraftingCalculationDiagnosticsMixin {
        final AtomicInteger pauses = new AtomicInteger();
        @Override protected void aco$invokeHandlePausing() { pauses.incrementAndGet(); }
    }
}
