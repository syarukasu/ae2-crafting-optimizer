package com.syaru.ae2craftingoptimizer.api.contract;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable startup handshake snapshot exposed to optional integration mods. */
public final class IntegrationCapabilities {
    public static final int API_VERSION = 1;

    private final int bigCraftingApiVersion;
    private final int patternBatchApiVersion;
    private final int craftingTableBatchApiVersion;
    private final int receiptProtocolVersion;
    private final int persistenceSchemaVersion;
    private final ExactCountLimits exactCountLimits;
    private final Set<SupportedFeature> supportedFeatures;

    public IntegrationCapabilities(
            int bigCraftingApiVersion,
            int patternBatchApiVersion,
            int craftingTableBatchApiVersion,
            int receiptProtocolVersion,
            int persistenceSchemaVersion,
            ExactCountLimits exactCountLimits,
            Set<SupportedFeature> supportedFeatures) {
        this.bigCraftingApiVersion = positiveVersion(bigCraftingApiVersion, "bigCraftingApiVersion");
        this.patternBatchApiVersion = positiveVersion(patternBatchApiVersion, "patternBatchApiVersion");
        this.craftingTableBatchApiVersion = positiveVersion(
                craftingTableBatchApiVersion, "craftingTableBatchApiVersion");
        this.receiptProtocolVersion = positiveVersion(receiptProtocolVersion, "receiptProtocolVersion");
        this.persistenceSchemaVersion = positiveVersion(persistenceSchemaVersion, "persistenceSchemaVersion");
        this.exactCountLimits = Objects.requireNonNull(exactCountLimits, "exactCountLimits");
        Objects.requireNonNull(supportedFeatures, "supportedFeatures");
        EnumSet<SupportedFeature> copy = EnumSet.noneOf(SupportedFeature.class);
        for (SupportedFeature feature : supportedFeatures) {
            copy.add(Objects.requireNonNull(feature, "supportedFeatures contains null"));
        }
        this.supportedFeatures = Set.copyOf(copy);
    }

    public static IntegrationCapabilities forAco(ExactCountLimits limits) {
        // These two bits are backed by the explicit ACO host registry and immutable snapshot API.
        return new IntegrationCapabilities(
                1,
                1,
                1,
                1,
                1,
                limits,
                Set.of(
                        SupportedFeature.HOST_ATOMIC_SNAPSHOT,
                        SupportedFeature.EXPLICIT_HOST_REGISTRATION));
    }

    public int bigCraftingApiVersion() {
        return bigCraftingApiVersion;
    }

    public int patternBatchApiVersion() {
        return patternBatchApiVersion;
    }

    public int craftingTableBatchApiVersion() {
        return craftingTableBatchApiVersion;
    }

    public int receiptProtocolVersion() {
        return receiptProtocolVersion;
    }

    public int persistenceSchemaVersion() {
        return persistenceSchemaVersion;
    }

    public ExactCountLimits exactCountLimits() {
        return exactCountLimits;
    }

    public Set<SupportedFeature> supportedFeatures() {
        return supportedFeatures;
    }

    public boolean supports(SupportedFeature feature) {
        return supportedFeatures.contains(Objects.requireNonNull(feature, "feature"));
    }

    public void requireFeatures(Set<SupportedFeature> required) {
        Objects.requireNonNull(required, "required");
        for (SupportedFeature feature : required) {
            if (!supports(feature)) {
                throw new IllegalStateException("required integration feature is unavailable: " + feature);
            }
        }
    }

    private static int positiveVersion(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
