package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CraftingProviderRefreshGenerationContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/syaru/ae2craftingoptimizer/mixin/"
                    + "CraftingProviderRefreshCoalescingMixin.java");

    @Test
    void providerGenerationIsPublishedAfterAe2Mutation() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains(
                "@Inject(method = \"refreshNodeCraftingProvider\", at = @At(\"TAIL\"))"));
        assertTrue(source.contains(
                "private void aco$publishProviderRefresh(IGridNode node, CallbackInfo ci)"));

        int queueStart = source.indexOf("private void aco$queueProviderRefresh");
        int queueEnd = source.indexOf(
                "@Inject(method = \"refreshNodeCraftingProvider\", at = @At(\"TAIL\"))");
        assertTrue(queueStart >= 0 && queueEnd > queueStart);
        assertFalse(
                source.substring(queueStart, queueEnd)
                        .contains("ProviderPatternGenerationTracker.shouldRefresh(node)"),
                "refresh HEAD must not publish a generation before AE2 updates its index");

        int flushStart = source.indexOf("private void aco$flushProviderRefreshes()");
        assertTrue(flushStart >= 0);
        assertFalse(
                source.substring(flushStart)
                        .contains(
                                "ProviderPatternGenerationTracker.shouldRefresh(node);\n"
                                        + "                service.refreshNodeCraftingProvider(node);"),
                "coalesced refresh must not publish before calling AE2");
    }

    @Test
    void nodeAddAndRemovePublishAtReturn() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("@Inject(method = \"addNode\", at = @At(\"RETURN\"))"));
        assertTrue(source.contains("@Inject(method = \"removeNode\", at = @At(\"RETURN\"))"));

        int addHead = source.indexOf("private void aco$dropPendingRefreshOnNodeAdd");
        int addReturn = source.indexOf("private void aco$publishProviderAfterNodeAdd");
        assertTrue(addHead >= 0 && addReturn > addHead);
        assertFalse(
                source.substring(addHead, addReturn)
                        .contains("ProviderPatternGenerationTracker.forget(node)"));

        int removeHead = source.indexOf("private void aco$dropPendingRefreshOnNodeRemove");
        int removeReturn = source.indexOf("private void aco$publishProviderAfterNodeRemove");
        assertTrue(removeHead >= 0 && removeReturn > removeHead);
        assertFalse(
                source.substring(removeHead, removeReturn)
                        .contains("ProviderPatternGenerationTracker.forget(node)"));
    }
}
