package com.syaru.ae2craftingoptimizer.integration;

import java.util.Set;
import net.minecraftforge.fml.ModList;

/**
 * Optional ownership boundary for AE2-UELM.
 *
 * <p>UELM is an AE2 fork/extension and may own AE2's long-amount, storage,
 * and capacity surfaces. ACO keeps its normal optimizer and optional machine
 * integrations, but does not patch those same AE2 surfaces when UELM is
 * present.</p>
 */
public final class Ae2UelmCompatibility {
    /** Known IDs used by UEL/UELM-style AE2 distributions. */
    private static final Set<String> UELM_MOD_IDS = Set.of(
            "ae2_uelm",
            "ae2uelm",
            "ae2_uel",
            "ae2uel");

    /** Mixins that modify the AE2-owned long/storage/capacity surface. */
    private static final Set<String> UELM_OWNED_MIXINS = Set.of(
            "CraftAmountMenuLongAmountMixin",
            "CraftConfirmMenuLongAmountMixin",
            "CraftAmountScreenLongAmountMixin",
            "CraftConfirmScreenBigIntegerMixin",
            "CraftConfirmTableRendererBigIntegerMixin",
            "CraftingCpuClusterBigCapacityGuardMixin",
            "NetworkCraftingSimulationStateBigIntegerSnapshotMixin",
            "NetworkStorageBigIntegerSnapshotMixin",
            "NetworkStorageMountsAccessor",
            "NetworkCraftingSimulationStateAccessor",
            "KeyCounterBigIntegerSidecarLifecycleMixin",
            "ExtendedAePlusBigIntegerCellInventoryAccessor",
            "ExtendedAePlusBigIntegerCellConsistencyMixin",
            "ExtendedAePlusInfinityDataStorageConsistencyMixin");

    private Ae2UelmCompatibility() {
    }

    /** Returns whether a UELM-compatible AE2 distribution is loaded. */
    public static boolean isLoaded() {
        for (String modId : UELM_MOD_IDS) {
            if (ModList.get().isLoaded(modId)) {
                return true;
            }
        }
        return false;
    }

    /** Returns whether the named ACO Mixin belongs to UELM's ownership surface. */
    public static boolean ownsAe2SurfaceMixin(String mixinClassName) {
        int separator = mixinClassName.lastIndexOf('.');
        String simpleName = separator >= 0
                ? mixinClassName.substring(separator + 1)
                : mixinClassName;
        return UELM_OWNED_MIXINS.contains(simpleName);
    }

    /** ACO must leave AE2 root amount handling to UELM when it is installed. */
    public static boolean ownsAe2ExtendedAmountSurface() {
        return isLoaded();
    }

    /** ACO must leave exact AE2 network storage snapshots to UELM when installed. */
    public static boolean ownsAe2StorageSurface() {
        return isLoaded();
    }

    /** Exposed for the Mixin plugin without touching optional mod classes. */
    public static boolean isKnownUelmModId(String modId) {
        return UELM_MOD_IDS.contains(modId);
    }
}
