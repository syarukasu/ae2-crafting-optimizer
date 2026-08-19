package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CraftingCpuClusterBigCapacityGuardContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/syaru/ae2craftingoptimizer/mixin/"
                    + "CraftingCpuClusterBigCapacityGuardMixin.java");

    @Test
    void missingExactBackingIsNotReportedAsCpuCapacityFailure() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("BigIntegerPlanDeclineReason.SUBMISSION_BACKING_MISSING"));
        assertTrue(source.contains("CraftingSubmitResult.INCOMPLETE_PLAN"));
        assertTrue(source.contains("This is not a CPU storage-capacity failure"));
        assertFalse(source.contains("cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL)"));
    }
}
