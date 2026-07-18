package com.syaru.ae2craftingoptimizer.integration;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api;
import com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraftforge.fml.ModList;

/** Loads optional typed bridges only for the exact dependency versions they were verified against. */
public final class OptionalNativeBatchIntegrations {
    private static final String GTCEU_ADAPTER =
            "com.syaru.ae2craftingoptimizer.gtceu.GTCEuNativePatternBatchAdapter";
    private static final String GTCEU_BRIDGE =
            "com.syaru.ae2craftingoptimizer.gtceu.GTCEuNativeBatchBridge";
    private static final String MEKANISM_ADAPTER =
            "com.syaru.ae2craftingoptimizer.mekanism.MekanismNativePatternBatchAdapter";
    private static final String MEKANISM_BRIDGE =
            "com.syaru.ae2craftingoptimizer.mekanism.MekanismNativeBatchBridge";

    private static volatile boolean gtceuRegistered;
    private static volatile boolean mekanismRegistered;

    private OptionalNativeBatchIntegrations() {
    }

    public static synchronized void registerEnabledVerifiedAdapters() {
        if (ACOConfig.enableGtceuNativeBatching() && !gtceuRegistered) {
            gtceuRegistered = registerIfExact(
                    "GTCEu",
                    Map.of("gtceu", "7.5.3"),
                    GTCEU_ADAPTER);
        }
        if (ACOConfig.enableMekanismNativeBatching() && !mekanismRegistered) {
            mekanismRegistered = registerIfExact(
                    "Mekanism + Applied Mekanistics",
                    Map.of("mekanism", "10.4.16.80", "appmek", "1.4.3"),
                    MEKANISM_ADAPTER);
        }
    }

    public static void clearRecipeCaches() {
        if (gtceuRegistered) {
            invokeStaticNoArgs(GTCEU_BRIDGE, "clearCache");
        }
        if (mekanismRegistered) {
            invokeStaticNoArgs(MEKANISM_BRIDGE, "clearCache");
        }
    }

    public static boolean gtceuRegistered() {
        return gtceuRegistered;
    }

    public static boolean mekanismRegistered() {
        return mekanismRegistered;
    }

    private static boolean registerIfExact(
            String integration,
            Map<String, String> expectedVersions,
            String adapterClassName) {
        for (var expected : expectedVersions.entrySet()) {
            String installed = installedVersion(expected.getKey());
            if (installed == null) {
                AE2CraftingOptimizer.LOGGER.info(
                        "ACO {} native batching unavailable: {} is not installed",
                        integration,
                        expected.getKey());
                return false;
            }
            if (!installed.equals(expected.getValue())) {
                AE2CraftingOptimizer.LOGGER.warn(
                        "ACO {} native batching disabled: verified {} {}, installed {}. Standard AE2 dispatch remains active.",
                        integration,
                        expected.getKey(),
                        expected.getValue(),
                        installed);
                return false;
            }
        }
        try {
            Class<?> adapterType = Class.forName(adapterClassName, true, OptionalNativeBatchIntegrations.class.getClassLoader());
            Field instanceField = adapterType.getField("INSTANCE");
            Object adapter = instanceField.get(null);
            if (!(adapter instanceof TransactionalPatternBatchAdapter typed)) {
                throw new IllegalStateException(adapterClassName + " INSTANCE does not implement the V2 adapter contract");
            }
            PatternBatchV2Api.registerAdapter(typed);
            AE2CraftingOptimizer.LOGGER.info(
                    "ACO {} native batching bridge registered for {}",
                    integration,
                    expectedVersions);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "ACO failed to initialize the verified " + integration
                            + " native batch bridge. Installed versions: " + expectedVersions,
                    exception);
        }
    }

    private static String installedVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static void invokeStaticNoArgs(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className, false, OptionalNativeBatchIntegrations.class.getClassLoader());
            Method method = type.getMethod(methodName);
            method.invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "ACO failed to invalidate native recipe cache " + className + "#" + methodName,
                    exception);
        }
    }
}
