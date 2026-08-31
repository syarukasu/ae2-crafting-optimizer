package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.WideCraftingPlan;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class CompletedCraftingPlanSnapshotTest {
    @Test
    void materializesIndependentCountersForEveryCacheHit() {
        TestKey iron = new TestKey("iron");
        TestKey gold = new TestKey("gold");
        KeyCounter used = new KeyCounter();
        used.add(iron, 9L);
        CraftingPlan source = new CraftingPlan(
                new GenericStack(gold, 1L),
                8L,
                true,
                false,
                used,
                new KeyCounter(),
                new KeyCounter(),
                new LinkedHashMap<>());

        CompletedCraftingPlanSnapshot snapshot = CompletedCraftingPlanSnapshot.capture(source);
        assertNotNull(snapshot);
        used.add(iron, 90L);
        CraftingPlan first = snapshot.materialize();
        first.usedItems().add(iron, 1L);
        CraftingPlan second = snapshot.materialize();

        assertNotSame(first.usedItems(), second.usedItems());
        assertEquals(10L, first.usedItems().get(iron));
        assertEquals(9L, second.usedItems().get(iron));
    }

    @Test
    void preservesWideMetadataWithoutReusingTheSourceFacade() {
        TestKey output = new TestKey("wide_output");
        TestWidePlan metadata = new TestWidePlan(output);
        CraftingPlan source = Ae2CraftingPlanSidecars.expose(metadata);

        CompletedCraftingPlanSnapshot snapshot = CompletedCraftingPlanSnapshot.capture(source);
        assertNotNull(snapshot);
        CraftingPlan copy = snapshot.materialize();

        assertNotSame(source, copy);
        assertSame(metadata, Ae2CraftingPlanSidecars.metadata(copy).orElseThrow());
    }

    private static final class TestWidePlan implements WideCraftingPlan {
        private final GenericStack output;

        private TestWidePlan(AEKey key) {
            this.output = new GenericStack(key, 1L);
        }

        @Override
        public BigInteger exactBytes() {
            return BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        }

        @Override
        public GenericStack finalOutput() {
            return output;
        }

        @Override
        public long bytes() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean simulation() {
            return true;
        }

        @Override
        public boolean multiplePaths() {
            return false;
        }

        @Override
        public KeyCounter usedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter emittedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter missingItems() {
            return new KeyCounter();
        }

        @Override
        public java.util.Map<appeng.api.crafting.IPatternDetails, Long> patternTimes() {
            return java.util.Map.of();
        }
    }

    /** Minecraft Registry初期化なしでcacheのCounter分離を検証する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final ResourceLocation id;

        private TestKey(String path) {
            id = ResourceLocation.fromNamespaceAndPath("ae2_crafting_optimizer", path);
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
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf buffer) {
            // Packetを使わない完了計画cache単体試験である。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id.toString());
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
            // ワールド内ドロップを作らない完了計画cache単体試験である。
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}
