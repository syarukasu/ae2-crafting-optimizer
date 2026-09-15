package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import appeng.api.networking.IGridNode;
import com.syaru.ae2craftingoptimizer.mixin.CraftingProviderRefreshCoalescingMixin;
import java.lang.reflect.Proxy;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

class ProviderPatternGenerationTrackerTest {
    @Test
    void unrelatedNodeLifecycleDoesNotInvalidatePlanningSnapshots() throws Exception {
        var node = (IGridNode) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IGridNode.class }, (proxy, method, args) -> {
                    assertEquals("getService", method.getName());
                    return null;
                });
        var mixin = new CraftingProviderRefreshCoalescingMixin() {};
        var type = CraftingProviderRefreshCoalescingMixin.class;
        long generation = ProviderPatternGenerationTracker.generation();
        var add = type.getDeclaredMethod("aco$rememberProviderAfterNodeAdd",
                IGridNode.class, CompoundTag.class, CallbackInfo.class);
        add.setAccessible(true);
        add.invoke(mixin, node, null, null);
        // removeの前後とも非Providerはlive Levelにも公開索引にも触れない。
        for (String name : new String[] { "aco$dropPendingRefreshOnNodeRemove", "aco$forgetProviderAfterNodeRemove" }) {
            var remove = type.getDeclaredMethod(name, IGridNode.class, CallbackInfo.class);
            remove.setAccessible(true);
            remove.invoke(mixin, node, null);
        }
        assertEquals(generation, ProviderPatternGenerationTracker.generation());
    }

    @Test
    void coalescesOnlyAe2OwnedProviderImplementations() {
        assertTrue(ProviderPatternGenerationTracker.isRefreshCoalescingSafe(
                "appeng.helpers.patternprovider.PatternProviderLogic"));
        assertFalse(ProviderPatternGenerationTracker.isRefreshCoalescingSafe(
                "example.addon.CustomPatternProviderLogic"));
        assertFalse(ProviderPatternGenerationTracker.isRefreshCoalescingSafe((String) null));
    }
}
