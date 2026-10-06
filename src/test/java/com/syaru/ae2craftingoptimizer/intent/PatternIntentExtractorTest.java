package com.syaru.ae2craftingoptimizer.intent;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import com.syaru.ae2craftingoptimizer.testsupport.TestKeyTypes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PatternIntentExtractorTest {
    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        var field = Bootstrap.class.getDeclaredField("isBootstrapped");
        field.setAccessible(true);
        field.setBoolean(null, true);
        com.syaru.ae2craftingoptimizer.TestRegistries.initialize();
        TestKeyTypes.initialize();
    }

    @Test void repeatedNativePatternDoesNotRebuildItsDescription() {
        var extractor = new PatternIntentExtractor();
        var pattern = pattern(3);
        var first = extractor.describe(pattern);
        for (int i = 0; i < 10000; i++) assertSame(first, extractor.describe(pattern));
        assertEquals(3, first.inputs().get(0).multiplier());
        assertEquals(2, first.outputs().get(0).amount());
    }

    @Test void oneDescriptionIsImmutableAndSharedByAllTargetSides() {
        var description = new PatternIntentExtractor().describe(pattern(3));
        assertSame(description.inputs(), List.copyOf(description.inputs()));
        assertSame(description.outputs(), List.copyOf(description.outputs()));
        var alternatives = description.inputs().get(0).possibleInputs();
        assertSame(alternatives, List.copyOf(alternatives));
        assertThrows(UnsupportedOperationException.class, alternatives::clear);
    }

    @Test void changingPatternIdentityReplacesTheSingleRetainedDescription() {
        var extractor = new PatternIntentExtractor();
        var original = pattern(3);
        var first = extractor.describe(original);
        var changed = pattern(7);
        var second = extractor.describe(changed);
        assertEquals(7, second.inputs().get(0).multiplier());
        assertNotEquals(first.inputs(), second.inputs());
        assertNotSame(first, extractor.describe(original), "Do not retain all former patterns");
        assertNotSame(second, extractor.describe(pattern(7)), "Pattern identity, not an item ID, owns reuse");
    }

    @Test void subclassOutputsAreReadAgainEvenForTheSameInstance() {
        var amount = new AtomicLong(2);
        var dynamic = new AEProcessingPattern(pattern(3).getDefinition()) {
            @Override public java.util.List<GenericStack> getOutputs() {
                return java.util.List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), amount.get()));
            }
        };
        var extractor = new PatternIntentExtractor();
        var first = extractor.describe(dynamic);
        amount.set(19);
        var second = extractor.describe(dynamic);
        assertEquals(2, first.outputs().get(0).amount());
        assertEquals(19, second.outputs().get(0).amount());
    }

    @Test void concreteItemsAndFluidsAlwaysReflectCurrentCounters() {
        var input = new KeyCounter();
        input.add(AEItemKey.of(Items.IRON_INGOT), 5);
        input.add(AEFluidKey.of(Fluids.WATER), 1000);
        var before = PatternIntentExtractor.concreteInputs(new KeyCounter[] {input});
        input.set(AEItemKey.of(Items.IRON_INGOT), 7);
        input.set(AEFluidKey.of(Fluids.WATER), 250);
        var after = PatternIntentExtractor.concreteInputs(new KeyCounter[] {input});
        assertEquals(Map.of("minecraft:iron_ingot", 5L, "minecraft:water", 1000L),
                before.stream().collect(Collectors.toMap(StackIntent::keyId, StackIntent::amount)));
        assertEquals(Map.of("minecraft:iron_ingot", 7L, "minecraft:water", 250L),
                after.stream().collect(Collectors.toMap(StackIntent::keyId, StackIntent::amount)));
        assertSame(after, List.copyOf(after));
        assertThrows(UnsupportedOperationException.class, after::clear);
    }

    private static AEProcessingPattern pattern(long amount) {
        var encoded = new ItemStack(Items.PAPER);
        var inputs = new ListTag();
        inputs.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(AEItemKey.of(Items.IRON_INGOT), amount)));
        var outputs = new ListTag();
        outputs.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2)));
        com.ae2vm.addon.PortStacks.put(encoded, "in", inputs);
        com.ae2vm.addon.PortStacks.put(encoded, "out", outputs);
        return new AEProcessingPattern(AEItemKey.of(encoded));
    }
}
