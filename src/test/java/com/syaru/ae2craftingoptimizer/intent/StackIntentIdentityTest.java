package com.syaru.ae2craftingoptimizer.intent;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.AEItemKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class StackIntentIdentityTest {
    @Test void identicalIdsWithDifferentNbtCannotCoalesce() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var first = new ItemStack(Items.PAPER);
        com.ae2vm.addon.PortStacks.putInt(first, "variant", 1);
        var second = first.copy(); com.ae2vm.addon.PortStacks.putInt(second, "variant", 2);
        var a = StackIntent.of(AEItemKey.of(first), 2);
        var b = StackIntent.of(AEItemKey.of(second), 2);
        assertEquals(a.keyId(), b.keyId());
        assertNotEquals(a, b);
        assertNotEquals(a.exactKey(), b.exactKey());
        assertNull(new StackIntent(a.keyId(), a.keyType(), 2).exactKey());
    }
}
