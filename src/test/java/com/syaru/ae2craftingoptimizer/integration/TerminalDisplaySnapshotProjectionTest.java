package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

    private record FixedStorage(long amount) implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0L;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0L;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.add(ENERGY, amount);
        }

        @Override
        public Component getDescription() {
            return Component.literal("fixed storage");
        }
    }

    private record SnapshotStorage(KeyCounter snapshot) implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0L;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0L;
        }

        @Override
        public KeyCounter getAvailableStacks() {
            return snapshot;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            // 元Snapshotの全キーを、AE2本来のlong値のまま複製する。
            for (var entry : snapshot) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal("snapshot storage");
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
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0L;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
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
        public Component getDescription() {
            return Component.literal("test network storage");
        }
    }

    /** Minecraft Registryを起動せずKeyCounterを試験する最小AEKey。 */
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
        public CompoundTag toTag(HolderLookup.Provider registries) {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "ae2_crafting_optimizer",
                    "terminal_display_test");
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf buffer) {
            // Packet同期を行わない単体試験なので書き込みは不要。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal("energy");
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
            // ワールド内ドロップを作らない単体試験なので処理は不要。
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}
