package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #109の責務境界を、Mixin設定と公開APIのソース上でも固定する。 */
class Issue109MixinBoundarySourceTest {
    private static final Path MAIN = Path.of("src", "main");

    @Test
    void externalConsumerRegistrationDoesNotInstallTheRemovedBroadCpuGuard() {
        String mixins = read(MAIN.resolve(Path.of(
                "resources", "ae2_crafting_optimizer.mixins.json")));
        assertFalse(mixins.contains("CraftingCpuClusterBigCapacityGuardMixin"));
        String issue115Boundary = read(MAIN.resolve(Path.of(
                "java", "com", "syaru", "ae2craftingoptimizer", "mixin",
                "Ae2BigCapacityPlanSubmissionMixin.java")));
        assertFalse(issue115Boundary.contains("hasExternalBigIntegerPlanConsumer"));
        assertTrue(issue115Boundary.contains("if (exact == null)"));
    }

    @Test
    void meTerminalInventoryEnumerationIsNotRedirected() {
        String mixins = read(MAIN.resolve(Path.of(
                "resources", "aco.performance.mixins.json")));
        assertFalse(mixins.contains("MEStorageMenuGridSnapshotReuseMixin"));
    }

    @Test
    void externalConsumerRegistrationDoesNotClaimSubmissionOwnership() {
        String source = read(MAIN.resolve(Path.of(
                "java", "com", "syaru", "ae2craftingoptimizer", "api", "big",
                "BigCraftingEngineApi.java")));
        assertTrue(source.contains("登録してもAE2標準CPUの提出経路は変更せず"));
    }

    @Test
    void masterSwitchOwnsTheBigIntegerBackend() {
        String source = read(MAIN.resolve(Path.of(
                "java", "com", "syaru", "ae2craftingoptimizer", "config", "ACOConfig.java")));
        assertTrue(source.contains("OptimizationFeature.BIG_INTEGER_BACKEND"));
        assertTrue(source.contains("ENABLE_BIG_INTEGER_CRAFTING_BACKEND.get()"));
        assertTrue(source.contains("return ENABLE_OPTIMIZER.get();"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
