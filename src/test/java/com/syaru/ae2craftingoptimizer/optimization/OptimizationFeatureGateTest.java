package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OptimizationFeatureGateTest {
    @AfterEach
    void resetCounters() {
        OptimizationFeatureGate.resetDiagnostics();
    }

    @Test
    void masterSwitchDeniesBeforeDomainAndFeature() {
        assertEquals(
                OptimizationFeatureGate.Decision.MASTER_DISABLED,
                OptimizationFeatureGate.evaluate(false, true, true));
    }

    @Test
    void domainSwitchDeniesBeforeFeature() {
        assertEquals(
                OptimizationFeatureGate.Decision.DOMAIN_DISABLED,
                OptimizationFeatureGate.evaluate(true, false, true));
    }

    @Test
    void individualSwitchOnlyDeniesItsFeature() {
        assertEquals(
                OptimizationFeatureGate.Decision.FEATURE_DISABLED,
                OptimizationFeatureGate.evaluate(true, true, false));
        assertEquals(
                OptimizationFeatureGate.Decision.ENABLED,
                OptimizationFeatureGate.evaluate(true, true, true));
    }

    @Test
    void compatibilityNoopCannotBeEnabledByItsLegacyConfigKey() {
        assertEquals(
                OptimizationFeatureGate.Decision.IMPLEMENTATION_UNAVAILABLE,
                OptimizationFeatureGate.evaluate(
                        true,
                        true,
                        true,
                        OptimizationImplementationStatus.COMPATIBILITY_NOOP));
    }

    @Test
    void everyDomainHasAtLeastOneDeclaredFeature() {
        EnumSet<OptimizationDomain> covered = EnumSet.noneOf(OptimizationDomain.class);
        // domain漏れを検出するため、宣言済み機能を全件走査する。
        for (OptimizationFeature feature : OptimizationFeature.values()) {
            covered.add(feature.domain());
        }
        assertEquals(EnumSet.allOf(OptimizationDomain.class), covered);
    }

    @Test
    void featureIdsAreUniqueAndMachineReadable() {
        Set<String> ids = new HashSet<>();
        // 診断・文書・設定を同じIDで結ぶため、全機能IDを検査する。
        for (OptimizationFeature feature : OptimizationFeature.values()) {
            assertTrue(feature.id().matches("[a-z0-9-]+"), feature.name());
            assertTrue(ids.add(feature.id()), feature.id());
        }
    }

    @Test
    void highRiskFeaturesDeclareRegressionEvidence() {
        // 高risk機能だけを抽出し、過去Issueの再発防止根拠が空でないことを検査する。
        Arrays.stream(OptimizationFeature.values())
                .filter(feature -> feature.risk() == OptimizationRisk.HIGH)
                .forEach(feature -> assertFalse(
                        feature.regressionIssues().isEmpty(),
                        feature.id()));
    }

    @Test
    void transactionOwnershipNeverFallsBackAfterOwnership() {
        // ACOがtransactionを所有する機能は、取得後にAE2へ戻して二重会計してはいけない。
        Arrays.stream(OptimizationFeature.values())
                .filter(feature -> feature.ownership() == StateOwnership.ACO_TRANSACTION)
                .forEach(feature -> assertEquals(
                        FallbackBoundary.NEVER_AFTER_OWNERSHIP,
                        feature.fallbackBoundary(),
                        feature.id()));
    }

    @Test
    void activeOwnedStateDeclaresAnInvalidationContract() {
        // ACOが保持するcache/transactionは、正本変化または終端状態で必ず破棄できなければならない。
        Arrays.stream(OptimizationFeature.values())
                .filter(feature -> feature.implementationStatus() == OptimizationImplementationStatus.ACTIVE)
                .filter(feature -> feature.ownership() == StateOwnership.ACO_CACHE
                        || feature.ownership() == StateOwnership.ACO_TRANSACTION)
                .forEach(feature -> assertFalse(
                        feature.invalidationTriggers().isEmpty(),
                        feature.id()));
    }
}
