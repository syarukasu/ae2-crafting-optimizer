package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.api.vector.ExactStorageMutationResult;
import com.syaru.ae2craftingoptimizer.mixin.ExtendedAeMixinConfigPlugin;
import com.syaru.ae2craftingoptimizer.mixin.ModPresenceMixinConfigPlugin;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issues #118から#120で判明した起動・保存・在庫変更回帰を固定する。 */
class Issues118To120RegressionTest {
    private static final Path JAVA = Path.of(
            "src", "main", "java", "com", "syaru", "ae2craftingoptimizer");

    @Test
    void successfulMutationStillRequiresPositiveCommittedStock() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactStorageMutationResult.success(BigInteger.ZERO));
    }

    @Test
    void emptyRecoveryIsNotRepresentedAsSuccessfulZeroMutation() {
        String source = read(JAVA.resolve(Path.of(
                "integration", "ExactNetworkStorageBridge.java")));
        assertTrue(source.contains("recoverPendingInterruption"));
        assertTrue(source.contains("return Optional.empty();"));
        assertTrue(source.contains(
                "exact storage recovery completed; retry after reconciliation"));
        assertFalse(source.contains("ExactStorageMutationResult.success(BigInteger.ZERO)"));
    }

    @Test
    void exactJobPersistenceUsesTheRegistryProviderFromAe2() {
        String state = read(JAVA.resolve(Path.of(
                "engine", "ExactCraftingJobState.java")));
        String ae2Mixin = read(JAVA.resolve(Path.of(
                "mixin", "Ae2ExactCraftingLogicMixin.java")));
        String advancedAeMixin = read(JAVA.resolve(Path.of(
                "mixin", "AdvancedAeExactCraftingLogicMixin.java")));
        assertTrue(state.contains("HolderLookup.Provider registries"));
        assertFalse(state.contains("ACORegistryAccess.require()"));
        assertTrue(ae2Mixin.contains("jobTag,\n                registries"));
        assertTrue(advancedAeMixin.contains("jobTag,\n                registries"));
    }

    @Test
    void registryProviderIsClearedOnlyAfterServerStopped() {
        String lifecycle = read(JAVA.resolve(Path.of(
                "lifecycle", "ACOServerLifecycle.java")));
        int stopping = lifecycle.indexOf("private static void onServerStopping");
        int stopped = lifecycle.indexOf("private static void onServerStopped");
        int clear = lifecycle.indexOf("ACORegistryAccess.clear()");
        assertTrue(stopping >= 0);
        assertTrue(stopped > stopping);
        assertTrue(clear > stopped);
    }

    @Test
    void optionalMixinClassProbeWorksBeforeModListIsReady() {
        ClassLoader loader = getClass().getClassLoader();
        assertTrue(ModPresenceMixinConfigPlugin.isClassResourcePresent(
                "com.syaru.ae2craftingoptimizer.integration.Issues118To120RegressionTest",
                loader));
        assertFalse(ModPresenceMixinConfigPlugin.isClassResourcePresent(
                "missing.aco.DependencyMarker",
                loader));
    }

    @Test
    void optionalIntegrationAppliesWhenOnlyItsTargetClassIsOnTheClasspath() {
        /*
         * ModListが未完成なままshouldApplyMixinへ来る環境では、markerClassを
         * 持たない連携が全てapplied=falseになっていた。ExtendedAE連携が外れると
         * ExtendedAE PlusのBigIntegerセルにAccessorが付かず、ACOはfail-closedで
         * 「BigInteger inventory sidecar is incomplete」として全ての広域計画を降りる。
         */
        ExtendedAeMixinConfigPlugin plugin = new ExtendedAeMixinConfigPlugin();
        assertTrue(plugin.shouldApplyMixin(
                "com.syaru.ae2craftingoptimizer.integration.Issues118To120RegressionTest",
                "com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellInventoryAccessor"));
        // 連携先が未導入なら、対象クラスが無いので従来どおり選択しない。
        assertFalse(plugin.shouldApplyMixin(
                "com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory",
                "com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellInventoryAccessor"));
    }

    @Test
    void advancedAePluginUsesARealClasspathMarker() {
        String source = read(JAVA.resolve(Path.of(
                "mixin", "AdvancedAeMixinConfigPlugin.java")));
        assertTrue(source.contains(
                "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
