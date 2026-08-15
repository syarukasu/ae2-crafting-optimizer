package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.optimization.FallbackReasonCode;
import org.junit.jupiter.api.Test;

class Ae2AuthoritativeCraftingPlannerPolicyTest {
    @Test
    void retainsLongFacadeWhenFullExpansionNeedsWideArithmetic() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                false,
                true));
    }

    @Test
    void leavesOrdinaryLongPlanOnAe2WhenAuthoritativePlannerIsDisabled() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                false,
                false));
    }

    @Test
    void retainsOrdinaryLongPlanWhenAuthoritativePlannerIsEnabled() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                true,
                false));
    }

    @Test
    void acceptsStrictlyProvenLongPlanWithoutShadowHistory() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                true,
                false,
                false));
    }

    @Test
    void rejectsOrdinaryLongPlanWhenNeitherProofNorShadowQualifiesIt() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                false,
                false,
                false));
    }

    @Test
    void doesNotReportASnapshotFailureAsAnAmbiguousProducer() {
        assertEquals(
                FallbackReasonCode.INCOMPLETE_GRAPH_SNAPSHOT,
                Ae2AuthoritativeCraftingPlanner.classifyRootProgramFailure(
                        RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT));
        assertEquals(
                FallbackReasonCode.INCOMPLETE_GRAPH_SNAPSHOT,
                Ae2AuthoritativeCraftingPlanner.classifyRootProgramFailure(
                        RootProgramFailure.MISSING_FROM_SNAPSHOT));
    }

    @Test
    void keepsEachStructuralReasonDistinct() {
        assertEquals(
                FallbackReasonCode.AMBIGUOUS_PRODUCER,
                Ae2AuthoritativeCraftingPlanner.classifyRootProgramFailure(
                        RootProgramFailure.MULTIPLE_PRODUCERS));
        assertEquals(
                FallbackReasonCode.CYCLE,
                Ae2AuthoritativeCraftingPlanner.classifyRootProgramFailure(
                        RootProgramFailure.CYCLE));
        assertEquals(
                FallbackReasonCode.UNSUPPORTED_PATTERN,
                Ae2AuthoritativeCraftingPlanner.classifyRootProgramFailure(
                        RootProgramFailure.MULTIPLE_OUTPUTS));
        assertEquals(
                FallbackReasonCode.PROGRAM_TOO_LARGE,
                Ae2AuthoritativeCraftingPlanner.classifyRootProgramFailure(
                        RootProgramFailure.PROGRAM_TOO_LARGE));
    }

    @Test
    void retriesOnlySnapshotShapedRootProgramFailures() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.shouldRetryRootProgram(
                RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT,
                true));
        assertTrue(Ae2AuthoritativeCraftingPlanner.shouldRetryRootProgram(
                RootProgramFailure.MISSING_FROM_SNAPSHOT,
                true));
        assertFalse(Ae2AuthoritativeCraftingPlanner.shouldRetryRootProgram(
                RootProgramFailure.MULTIPLE_PRODUCERS,
                true));
        assertFalse(Ae2AuthoritativeCraftingPlanner.shouldRetryRootProgram(
                RootProgramFailure.CYCLE,
                true));
        assertFalse(Ae2AuthoritativeCraftingPlanner.shouldRetryRootProgram(
                RootProgramFailure.NONE,
                true));
    }

    @Test
    void neverRetriesWhileTheConfigSwitchIsOff() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.shouldRetryRootProgram(
                RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT,
                false));
    }

    @Test
    void honorsWidePlanShadowRequirement() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                true,
                true,
                true));
        assertTrue(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                false,
                true,
                false));
    }
}
