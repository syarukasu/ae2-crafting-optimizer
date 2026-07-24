package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellInventoryAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class BigIntegerStorageSnapshotBridgeTest {
    private static final TestKey TEST_KEY = new TestKey();
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Test
    void saturatesFacadeButKeepsExactSumAcrossMountedStorages() {
        KeyCounter network = new KeyCounter();

        BigIntegerStorageSnapshotBridge.collect(
                new LongStorage(Long.MAX_VALUE),
                network,
                true);
        BigIntegerStorageSnapshotBridge.collect(
                new LongStorage(1L),
                network,
                true);

        assertEquals(Long.MAX_VALUE, network.get(TEST_KEY));
        BigKeyCounterSidecars.Snapshot exact =
                BigKeyCounterSidecars.snapshot(network).orElseThrow();
        assertTrue(exact.complete());
        assertEquals(LONG_MAX.add(BigInteger.ONE), exact.amount(TEST_KEY));

        KeyCounter craftingSnapshot = new KeyCounter();
        craftingSnapshot.set(TEST_KEY, Long.MAX_VALUE);
        BigKeyCounterSidecars.copyVisible(network, craftingSnapshot);
        assertEquals(
                LONG_MAX.add(BigInteger.ONE),
                BigKeyCounterSidecars.snapshot(craftingSnapshot)
                        .orElseThrow()
                        .amount(TEST_KEY));
    }

    @Test
    void readsExactExtendedAePlusCellAmountInsteadOfItsLongFacade() {
        BigInteger exactAmount = BigInteger.TEN.pow(64).subtract(BigInteger.ONE);
        KeyCounter network = new KeyCounter();

        BigIntegerStorageSnapshotBridge.collect(
                new FakeInfinityBigIntegerCell(exactAmount),
                network,
                true);

        assertEquals(Long.MAX_VALUE, network.get(TEST_KEY));
        BigKeyCounterSidecars.Snapshot exact =
                BigKeyCounterSidecars.snapshot(network).orElseThrow();
        assertTrue(exact.complete());
        assertEquals(exactAmount, exact.amount(TEST_KEY));
    }

    @Test
    void neverPublishesAlreadyWrappedNegativeStorageAsMissing() {
        KeyCounter network = new KeyCounter();

        BigIntegerStorageSnapshotBridge.collect(
                new LongStorage(Long.MIN_VALUE),
                network,
                true);

        assertEquals(Long.MAX_VALUE, network.get(TEST_KEY));
        BigKeyCounterSidecars.Snapshot exact =
                BigKeyCounterSidecars.snapshot(network).orElseThrow();
        assertFalse(exact.complete());
    }

    private record LongStorage(long amount) implements MEStorage {
        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.add(TEST_KEY, amount);
        }

        @Override
        public Component getDescription() {
            return Component.literal("long storage");
        }
    }

    private static final class FakeInfinityBigIntegerCell
            implements MEStorage, ExtendedAePlusBigIntegerCellInventoryAccessor {
        private final Object2ObjectMap<AEKey, BigInteger> exact =
                new Object2ObjectOpenHashMap<>();

        private FakeInfinityBigIntegerCell(BigInteger amount) {
            exact.put(TEST_KEY, amount);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            // 実際のExtendedAE Plusと同じく、AE2へはLong.MAX_VALUEだけを公開する。
            out.set(TEST_KEY, Long.MAX_VALUE);
        }

        @Override
        public Object2ObjectMap<AEKey, BigInteger> aco$getExactStoredAmounts() {
            return exact;
        }

        @Override
        public Component getDescription() {
            return Component.literal("fake infinity BigInteger cell");
        }
    }

    /** Minecraft Registry初期化なしでKeyCounterを試験するための最小AEKey。 */
    private static final class TestKey extends AEKey {
        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation(
                    "ae2_crafting_optimizer",
                    "big_inventory_test");
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {
            // Packet同期を行わない単体試験なので書き込みは不要。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal("ACO Big inventory test");
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
            // ワールド内ドロップを作らない単体試験なので処理は不要。
        }
    }
}
