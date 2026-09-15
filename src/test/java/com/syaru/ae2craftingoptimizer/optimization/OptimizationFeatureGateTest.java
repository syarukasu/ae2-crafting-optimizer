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
    void diagnosticsSupportMoreThanSixtyFourFeatures() {
        // 130機能を仮定し、三つのlong wordをまたぐindexを正確に保持できることを検査する。
        OptimizationFeatureGate.ConcurrentFeatureBits bits =
                new OptimizationFeatureGate.ConcurrentFeatureBits(130);
        bits.mark(0);
        bits.mark(64);
        bits.mark(129);

        assertTrue(bits.contains(0));
        assertTrue(bits.contains(64));
        assertTrue(bits.contains(129));
        assertFalse(bits.contains(63));
        assertFalse(bits.contains(128));

        bits.clear();
        assertFalse(bits.contains(0));
        assertFalse(bits.contains(64));
        assertFalse(bits.contains(129));
    }

    @Test
    void denialSnapshotSeparatesReasonsAndResetClearsThem() {
        OptimizationFeatureGate.record(
                OptimizationFeature.BIG_INTEGER_BACKEND,
                OptimizationFeatureGate.Decision.MASTER_DISABLED);
        OptimizationFeatureGate.record(
                OptimizationFeature.LONG_ROOT_AMOUNTS,
                OptimizationFeatureGate.Decision.DOMAIN_DISABLED);
        OptimizationFeatureGate.record(
                OptimizationFeature.EXACT_INVENTORY_SNAPSHOT,
                OptimizationFeatureGate.Decision.FEATURE_DISABLED);
        var bigInteger = OptimizationFeatureGate.denialSnapshot().get(OptimizationDomain.BIG_INTEGER);
        assertEquals(1L, bigInteger.masterDisabled());
        assertEquals(1L, bigInteger.domainDisabled());
        assertEquals(1L, bigInteger.featureDisabled());

        OptimizationFeatureGate.resetDiagnostics();
        var resetBigInteger = OptimizationFeatureGate.denialSnapshot().get(OptimizationDomain.BIG_INTEGER);
        assertEquals(0L, resetBigInteger.masterDisabled());
        assertEquals(0L, resetBigInteger.domainDisabled());
        assertEquals(0L, resetBigInteger.featureDisabled());
    }

    @Test
    void everyDomainHasAtLeastOneDeclaredFeature() {
        EnumSet<OptimizationDomain> covered = EnumSet.noneOf(OptimizationDomain.class);
        // domain漏れを検出するため、中央レジストリのdomain一覧を検査する。
        for (OptimizationDomain domain : OptimizationDomain.values()) {
            if (!OptimizationFeatureRegistry.forDomain(domain).isEmpty()) {
                covered.add(domain);
            }
        }
        assertEquals(EnumSet.allOf(OptimizationDomain.class), covered);
    }

    @Test
    void registryResolvesEveryFeatureByStableId() {
        assertEquals(OptimizationFeature.values().length, OptimizationFeatureRegistry.all().size());
        // Mixin、設定、診断が同じ機能を引けるよう、全IDの往復を検査する。
        for (OptimizationFeature feature : OptimizationFeatureRegistry.all()) {
            assertEquals(feature, OptimizationFeatureRegistry.findById(feature.id()).orElseThrow());
        }
    }

    @Test
    void featureIdsAreUniqueAndMachineReadable() {
        Set<String> ids = new HashSet<>();
        // 診断・文書・設定を同じIDで結ぶため、全機能IDを検査する。
        for (OptimizationFeature feature : OptimizationFeatureRegistry.all()) {
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
                .filter(feature -> feature.ownership() == StateOwnership.ACO_CACHE
                        || feature.ownership() == StateOwnership.ACO_TRANSACTION)
                .forEach(feature -> assertFalse(
                        feature.invalidationTriggers().isEmpty(),
                        feature.id()));
    }
}
