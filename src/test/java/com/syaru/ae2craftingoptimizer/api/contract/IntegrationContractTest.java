package com.syaru.ae2craftingoptimizer.api.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class IntegrationContractTest {
    @Test
    void capabilitiesAreImmutableAndDoNotAdvertiseUnimplementedFeatures() {
        ExactCountLimits limits = ExactCountLimits.defaults();
        IntegrationCapabilities capabilities = IntegrationCapabilities.forAco(limits);
        assertSame(limits, capabilities.exactCountLimits());
        assertTrue(capabilities.supports(SupportedFeature.HOST_ATOMIC_SNAPSHOT));
        assertTrue(capabilities.supports(SupportedFeature.EXPLICIT_HOST_REGISTRATION));
        assertTrue(capabilities.supports(SupportedFeature.EXACT_STORAGE_AMOUNT_PROVIDER));
        assertFalse(capabilities.supports(SupportedFeature.EXACT_STORAGE_OPERATION_JOURNAL));
        assertThrows(IllegalStateException.class,
                () -> capabilities.requireFeatures(Set.of(SupportedFeature.LIVE_TRANSACTION_PROOF)));
    }

    @Test
    void registryInitializesOnce() {
        IntegrationCapabilitiesRegistry.initializeOnce(IntegrationCapabilities.forAco(ExactCountLimits.defaults()));
        assertSame(IntegrationCapabilitiesRegistry.peek().orElseThrow(), IntegrationCapabilitiesRegistry.snapshot());
        assertThrows(IllegalStateException.class,
                () -> IntegrationCapabilitiesRegistry.initializeOnce(
                        IntegrationCapabilities.forAco(ExactCountLimits.defaults())));
    }
}
