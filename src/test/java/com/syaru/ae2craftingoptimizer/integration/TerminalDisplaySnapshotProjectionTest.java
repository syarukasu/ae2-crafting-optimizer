package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Issue #148の端末表示飽和と、Issue #109の非干渉境界を固定する。 */
class TerminalDisplaySnapshotProjectionTest {
    private static final AEKey ENERGY = new TestKey();

    @Test
    void saturatesMountedStorageSumAtLongMaximum() {
        TestNetworkStorage network = new TestNetworkStorage(List.of(
                new FixedStorage(Long.MAX_VALUE),
                new FixedStorage(Long.MAX_VALUE)));

        KeyCounter projected = TerminalDisplaySnapshotProjection.availableStacks(network, true);

        assertEquals(Long.MAX_VALUE, projected.get(ENERGY));
    }

    @Test
    void keepsOrdinaryMountedStorageSumExact() {
        TestNetworkStorage network = new TestNetworkStorage(List.of(
                new FixedStorage(2_000L),
                new FixedStorage(3_000L)));

        KeyCounter projected = TerminalDisplaySnapshotProjection.availableStacks(network, true);

        assertEquals(5_000L, projected.get(ENERGY));
    }

    @Test
    void disabledProjectionUsesTheOriginalAe2Snapshot() {
        KeyCounter original = new KeyCounter();
        original.set(ENERGY, 42L);
        MEStorage storage = new SnapshotStorage(original);

        KeyCounter projected = TerminalDisplaySnapshotProjection.availableStacks(storage, false);

        assertSame(original, projected);
    }

    @Test
    void saturatesStorageMonitorAmountFromMountedStorages() {
        TestNetworkStorage network = new TestNetworkStorage(List.of(
                new FixedStorage(Long.MAX_VALUE),
                new FixedStorage(Long.MAX_VALUE)));
        KeyCounter overflowedCachedInventory = new KeyCounter();
        overflowedCachedInventory.set(ENERGY, -2L);

        KeyCounter projected = TerminalDisplaySnapshotProjection.monitorStacks(
                network,
                overflowedCachedInventory,
                true);

        assertEquals(Long.MAX_VALUE, projected.get(ENERGY));
    }

    @Test
    void keepsOrdinaryStorageMonitorAmountExact() {
        TestNetworkStorage network = new TestNetworkStorage(List.of(
                new FixedStorage(2_000L),
                new FixedStorage(3_000L)));
        KeyCounter cachedInventory = new KeyCounter();
        cachedInventory.set(ENERGY, 5_000L);

        KeyCounter projected = TerminalDisplaySnapshotProjection.monitorStacks(
                network,
                cachedInventory,
                true);

        assertEquals(5_000L, projected.get(ENERGY));
    }

    @Test
    void disabledStorageMonitorProjectionReturnsOriginalCachedInventory() {
        KeyCounter cachedInventory = new KeyCounter();
        cachedInventory.set(ENERGY, -2L);
        TestNetworkStorage network = new TestNetworkStorage(List.of(new FixedStorage(Long.MAX_VALUE)));

        KeyCounter projected = TerminalDisplaySnapshotProjection.monitorStacks(
                network,
                cachedInventory,
                false);

        assertSame(cachedInventory, projected);
    }

    @Test
    void unsupportedStorageMonitorProjectionReturnsOriginalCachedInventory() {
        KeyCounter cachedInventory = new KeyCounter();
        cachedInventory.set(ENERGY, 42L);
        MEStorage unsupportedStorage = new SnapshotStorage(cachedInventory);

        KeyCounter projected = TerminalDisplaySnapshotProjection.monitorStacks(
                unsupportedStorage,
                cachedInventory,
                true);

        assertSame(cachedInventory, projected);
    }

    private record FixedStorage(long amount) implements MEStorage {
        @Override
        public long insert(
                AEKey what,
                long amount,
                Actionable mode,
                IActionSource source) {
            return 0L;
        }

        @Override
        public long extract(
                AEKey what,
                long amount,
                Actionable mode,
                IActionSource source) {
            return 0L;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.add(ENERGY, amount);
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("fixed storage");
        }
    }

    private record SnapshotStorage(KeyCounter snapshot) implements MEStorage {
        @Override
        public long insert(
                AEKey what,
                long amount,
                Actionable mode,
                IActionSource source) {
            return 0L;
        }

        @Override
        public long extract(
                AEKey what,
                long amount,
                Actionable mode,
                IActionSource source) {
            return 0L;
        }

        @Override
        public KeyCounter getAvailableStacks() {
            return snapshot;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (var entry : snapshot) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("snapshot storage");
        }
    }

    private static final class TestNetworkStorage
            implements MEStorage, NetworkStorageMountsAccess {
        private final NavigableMap<Integer, List<MEStorage>> mounts = new TreeMap<>();

        private TestNetworkStorage(List<MEStorage> mountedStorages) {
            mounts.put(0, List.copyOf(mountedStorages));
        }

        @Override
        public NavigableMap<Integer, List<MEStorage>> aco$getPriorityInventory() {
            return mounts;
        }

        @Override
        public long insert(
                AEKey what,
                long amount,
                Actionable mode,
                IActionSource source) {
            return 0L;
        }

        @Override
        public long extract(
                AEKey what,
                long amount,
                Actionable mode,
                IActionSource source) {
            return 0L;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            // AE2本来の単純加算を再現し、表示Projectionだけが飽和することを試験する。
            for (List<MEStorage> group : mounts.values()) {
                // 同じ優先度の全mountを通常のKeyCounterへ加算する。
                for (MEStorage mountedStorage : group) {
                    mountedStorage.getAvailableStacks(out);
                }
            }
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("test network storage");
        }
    }

    /** Minecraft Registryを起動せずKeyCounterを試験する最小AEKey。 */
    private static final class TestKey extends AEKey {
        @Override
        public appeng.api.stacks.AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public net.minecraft.nbt.CompoundTag toTag() {
            return new net.minecraft.nbt.CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public net.minecraft.resources.ResourceLocation getId() {
            return new net.minecraft.resources.ResourceLocation(
                    "ae2_crafting_optimizer",
                    "terminal_display_test");
        }

        @Override
        public void writeToPacket(net.minecraft.network.FriendlyByteBuf buffer) {
        }

        @Override
        protected net.minecraft.network.chat.Component computeDisplayName() {
            return net.minecraft.network.chat.Component.literal("energy");
        }

        @Override
        public void addDrops(
                long amount,
                List<net.minecraft.world.item.ItemStack> drops,
                net.minecraft.world.level.Level level,
                net.minecraft.core.BlockPos pos) {
            // ワールド内ドロップを作らない単体試験なので処理は不要。
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return 148;
        }
    }
}
