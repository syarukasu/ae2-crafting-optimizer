package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class Ae2DecisionProgramCacheTest {
    private static final TestKey TEST_KEY = new TestKey("input");

    @Test
    void onlyKnownAe2PatternImplementationsAreSharedAcrossCalculations() {
        assertTrue(Ae2DecisionProgramCache.isCrossCalculationSafePattern(
                "appeng.crafting.pattern.AECraftingPattern"));
        assertTrue(Ae2DecisionProgramCache.isCrossCalculationSafePattern(
                "appeng.crafting.pattern.AEProcessingPattern"));
        assertFalse(Ae2DecisionProgramCache.isCrossCalculationSafePattern(
                "example.addon.DynamicPattern"));
        assertFalse(Ae2DecisionProgramCache.isCrossCalculationSafePattern(
                "appeng.crafting.pattern.AECraftingPattern$Subclass"));
    }

    @Test
    void onlyKnownImmutableAe2InputsAreSharedAcrossCalculations() {
        assertTrue(Ae2DecisionProgramCache.isCrossCalculationSafeInput(
                "appeng.crafting.pattern.AECraftingPattern$Input"));
        assertTrue(Ae2DecisionProgramCache.isCrossCalculationSafeInput(
                "appeng.crafting.pattern.AEProcessingPattern$Input"));
        assertFalse(Ae2DecisionProgramCache.isCrossCalculationSafeInput(
                "example.addon.WorldDependentInput"));
    }

    @Test
    void onlyPureProcessingInputValidationIsSharedAcrossCalculations() {
        assertTrue(Ae2DecisionProgramCache.isCrossCalculationSafeValidation(
                "appeng.crafting.pattern.AEProcessingPattern$Input"));
        assertFalse(Ae2DecisionProgramCache.isCrossCalculationSafeValidation(
                "appeng.crafting.pattern.AECraftingPattern$Input"));
        assertFalse(Ae2DecisionProgramCache.isCrossCalculationSafeValidation(
                "appeng.crafting.pattern.AEStonecuttingPattern$Input"));
        assertFalse(Ae2DecisionProgramCache.isCrossCalculationSafeValidation(
                "appeng.crafting.pattern.AESmithingTablePattern$Input"));
    }

    @Test
    void preservesPossibleInputArrayAndMemoizesNullRemainder() {
        GenericStack[] possibleInputs = {new GenericStack(TEST_KEY, 3L)};
        var decision = new Ae2DecisionProgramCache.InputDecision(possibleInputs, true, true);
        AtomicInteger lookups = new AtomicInteger();

        assertSame(possibleInputs, decision.possibleInputs());
        assertNull(decision.remainingKey(TEST_KEY, () -> {
            lookups.incrementAndGet();
            return null;
        }));
        assertNull(decision.remainingKey(TEST_KEY, () -> {
            lookups.incrementAndGet();
            return new TestKey("unexpected");
        }));
        assertEquals(1, lookups.get());
    }

    private static final class TestKey extends AEKey {
        private final String path;

        private TestKey(String path) {
            this.path = path;
        }

        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(HolderLookup.Provider registries) {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return path;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2_crafting_optimizer", path);
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf buffer) {
            // Packetを使わない単体試験のため、書き込みは行わない。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(path);
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
            // ワールド内dropを使わない単体試験のため、処理は行わない。
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}
