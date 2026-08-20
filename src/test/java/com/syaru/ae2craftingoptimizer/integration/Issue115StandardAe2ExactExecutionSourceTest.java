package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.crafting.execution.CraftingCpuLogic;
import com.syaru.ae2craftingoptimizer.mixin.Ae2ExactCraftingLogicMixin;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Issue #115の標準AE2境界と、外部CPU非依存をソース契約として固定する。 */
class Issue115StandardAe2ExactExecutionSourceTest {
    private static final Path JAVA = Path.of("src", "main", "java", "com", "syaru",
            "ae2craftingoptimizer");

    @Test
    void standardManagerUsesThePhysicalReceiptTransactionWithoutAddonClasses() {
        String source = read(JAVA.resolve(Path.of(
                "integration", "Ae2BigCraftingExecutionManager.java")));
        assertTrue(source.contains("PhysicalCraftingTreeTransaction"));
        assertTrue(source.contains("CraftingCPUCluster"));
        assertFalse(source.contains("net.pedroksl"));
        assertFalse(source.contains("jp.main.taikun"));
        assertFalse(source.contains("insaneae$"));
    }

    @Test
    void submissionOnlyPromotesAnExactPlanAfterAe2AcceptedIt() {
        String source = read(JAVA.resolve(Path.of(
                "mixin", "Ae2BigCapacityPlanSubmissionMixin.java")));
        assertTrue(source.contains("if (exact == null)"));
        assertTrue(source.contains("!cir.getReturnValue().successful()"));
        assertTrue(source.contains("aco$singleExecutionFacade"));
        assertFalse(source.contains("registerExternalBigIntegerPlanConsumer"));
    }

    @Test
    void exactExecutorDoesNotCancelNormalAe2Execution() {
        String source = read(JAVA.resolve(Path.of(
                "mixin", "Ae2ExactCraftingLogicMixin.java")));
        assertTrue(source.contains("exact.aco$isExactJob()"));
        assertTrue(source.contains("state.hasPhysicalExecution()"));
        assertTrue(source.contains("state.requestCancellation()"));
    }

    @Test
    void forgeNbtHooksMatchAe2OneArgumentMethods() throws ReflectiveOperationException {
        CraftingCpuLogic.class.getDeclaredMethod("writeToNBT", CompoundTag.class);
        CraftingCpuLogic.class.getDeclaredMethod("readFromNBT", CompoundTag.class);
        Method save = declaredMethod("aco$saveExactJob");
        Method load = declaredMethod("aco$loadExactJob");
        assertArrayEquals(
                new Class<?>[] {CompoundTag.class, CallbackInfo.class},
                save.getParameterTypes());
        assertArrayEquals(
                new Class<?>[] {CompoundTag.class, CallbackInfo.class},
                load.getParameterTypes());
    }

    @Test
    void lifecycleTicksAndClearsTheStandardManager() {
        String source = read(JAVA.resolve(Path.of(
                "lifecycle", "ACOServerLifecycle.java")));
        assertTrue(source.contains("Ae2BigCraftingExecutionManager.tick(event.getServer())"));
        assertTrue(source.contains("Ae2BigCraftingExecutionManager.clear()"));
    }

    private static Method declaredMethod(String name) {
        // Mixinのprivate handlerから、指定した注入点だけを一意に取得する。
        return Arrays.stream(Ae2ExactCraftingLogicMixin.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing mixin handler: " + name));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
