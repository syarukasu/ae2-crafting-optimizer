package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.api.contract.ExactStorageAmountProvider;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import com.syaru.ae2craftingoptimizer.mixin.DelegatingMEInventoryAccessor;
import com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellInventoryAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private static final TestKey UNRELATED_KEY = new TestKey();
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
    void readsExactAmountFromThePublicStorageProviderContract() {
        BigInteger exactAmount = BigInteger.TEN.pow(256);
        KeyCounter network = new KeyCounter();

        BigIntegerStorageSnapshotBridge.collect(
                new FakePublicExactStorage(exactAmount, true),
                network,
                true);

        assertEquals(Long.MAX_VALUE, network.get(TEST_KEY));
        BigKeyCounterSidecars.Snapshot exact =
                BigKeyCounterSidecars.snapshot(network).orElseThrow();
        assertTrue(exact.complete());
        assertEquals(exactAmount, exact.amount(TEST_KEY));
    }

    @Test
    void planningSnapshotKeepsExactAmountsWithoutChangingTheSharedNetworkPath() {
        BigInteger exactAmount = BigInteger.TEN.pow(64);
        FakePublicExactStorage exactStorage =
                new FakePublicExactStorage(exactAmount, true);

        KeyCounter planning = PlanningExactInventorySnapshot.captureMountedStorages(
                List.of(
                        List.of(exactStorage),
                        List.of(exactStorage, new LongStorage(7L))));

        assertEquals(Long.MAX_VALUE, planning.get(TEST_KEY));
        BigKeyCounterSidecars.Snapshot exact =
                BigKeyCounterSidecars.snapshot(planning).orElseThrow();
        assertTrue(exact.complete());
        assertEquals(exactAmount.add(BigInteger.valueOf(7L)), exact.amount(TEST_KEY));
    }

    @Test
    void planningBatchRetainsIncompleteAndPerKeyEvidence() {
        var storages = List.<MEStorage>of(new LongStorage(7L),
                new LongStorage(UNRELATED_KEY, Long.MIN_VALUE),
                new FakePublicExactStorage(BigInteger.ZERO, false));
        var serial = new KeyCounter();
        // 既知の不正Providerとoverflow済みFacadeを、従来と同じ順序で取り込む。
        for (var storage : storages) {
            BigIntegerStorageSnapshotBridge.collect(storage, serial, true);
        }
        var batch = PlanningExactInventorySnapshot.captureMountedStorages(List.of(storages));
        assertEquals(serial.get(TEST_KEY), batch.get(TEST_KEY));
        assertEquals(serial.get(UNRELATED_KEY), batch.get(UNRELATED_KEY));
        assertEquals(BigKeyCounterSidecars.snapshot(serial), BigKeyCounterSidecars.snapshot(batch));
        assertFalse(BigKeyCounterSidecars.snapshot(batch).orElseThrow().isExact(TEST_KEY));
    }

    @Test
    void measureMountedPlanningCaptureAgainstIncrementalMerge() {
        // 256基に異なるキーを置き、同じキーしかない構成では隠れる累積コピーを測る。
        int mounts = 256;
        var storages = new ArrayList<MEStorage>();
        for (int index = 0; index < mounts; index++) {
            storages.add(new LongStorage(new TestKey(), index + 1L));
        }
        var wideAmount = BigInteger.TEN.pow(64);
        storages.add(new FakePublicExactStorage(wideAmount, true));
        storages.add(new LongStorage(7L));
        var baseline = new KeyCounter();
        // 従来のmountごとmergeを、同一fixtureの比較対象として保持する。
        for (var storage : storages) {
            BigIntegerStorageSnapshotBridge.collect(storage, baseline, true);
        }
        var expected = BigKeyCounterSidecars.snapshot(baseline);
        long[] nanos = new long[2];
        long[] allocated = new long[2];
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        // JIT暖機10回と計測20回を交互に行い、片方だけの先行暖機を避ける。
        int warmup = 10;
        int measured = 20;
        for (int round = -warmup; round < measured; round++) {
            // 各回の先行経路を交替させる。絶対時間は合否条件に使わない。
            for (int turn = 0; turn < 2; turn++) {
                int mode = (round + warmup + turn) % 2;
                long beforeBytes = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
                long before = System.nanoTime();
                KeyCounter result;
                // mode 0は従来集計、mode 1は実際のPlanning capture入口。
                if (mode == 0) {
                    result = new KeyCounter();
                    for (var storage : storages) {
                        BigIntegerStorageSnapshotBridge.collect(storage, result, true);
                    }
                } else {
                    result = PlanningExactInventorySnapshot.captureMountedStorages(List.of(storages));
                }
                long elapsed = System.nanoTime() - before;
                long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeBytes;
                assertEquals(expected, BigKeyCounterSidecars.snapshot(result));
                assertEquals(Long.MAX_VALUE, result.get(TEST_KEY));
                // Sidecarだけでなく、全キーのlong Facadeも従来集計と一致させる。
                for (var entry : baseline) {
                    assertEquals(entry.getLongValue(), result.get(entry.getKey()));
                }
                // 暖機を除いた時間とthread割り当て量だけを加算する。
                if (round >= 0) {
                    nanos[mode] += elapsed;
                    allocated[mode] += bytes;
                }
            }
        }
        assertEquals(wideAmount.add(BigInteger.valueOf(7L)), expected.orElseThrow().amount(TEST_KEY));
        System.out.printf("ACO-CAPTURE-PERF mounts=%d iterations=%d serialMs=%.3f batchMs=%.3f "
                        + "serialMiB=%.3f batchMiB=%.3f%n", storages.size(), measured,
                nanos[0] / 1_000_000.0D, nanos[1] / 1_000_000.0D,
                allocated[0] / 1_048_576.0D, allocated[1] / 1_048_576.0D);
    }

    @Test
    void rejectsAPublicProviderThatOmitsAnExposedFacadeKey() {
        KeyCounter network = new KeyCounter();

        BigIntegerStorageSnapshotBridge.collect(
                new FakePublicExactStorage(BigInteger.ZERO, false),
                network,
                true);

        assertEquals(Long.MAX_VALUE, network.get(TEST_KEY));
        assertFalse(BigKeyCounterSidecars.snapshot(network).orElseThrow().complete());
    }

    @Test
    void readsExactCellThroughAe2DriveWrapperWithoutBypassingItsVisibleKeys() {
        BigInteger exactAmount = BigInteger.TEN.pow(64);
        KeyCounter network = new KeyCounter();

        BigIntegerStorageSnapshotBridge.collect(
                new FakeDriveWrapper(
                        new FakeInfinityBigIntegerCell(exactAmount)),
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

    @Test
    void keepsExactnessForAReferencedKeyWhenAnUnrelatedContributionIsIncomplete() {
        KeyCounter network = new KeyCounter();

        BigKeyCounterSidecars.merge(
                network,
                new BigKeyCounterSidecars.Snapshot(
                        Map.of(TEST_KEY, BigInteger.TEN),
                        true));
        // 別キーのadapter失敗だけを再現し、TEST_KEYの正確値まで無効化しないことを確認する。
        BigKeyCounterSidecars.merge(
                network,
                new BigKeyCounterSidecars.Snapshot(
                        Map.of(UNRELATED_KEY, BigInteger.ONE),
                        false));

        BigKeyCounterSidecars.Snapshot snapshot =
                BigKeyCounterSidecars.snapshot(network).orElseThrow();
        assertFalse(snapshot.complete());
        assertTrue(snapshot.isExact(TEST_KEY));
        assertFalse(snapshot.isExact(UNRELATED_KEY));
    }

    private record LongStorage(AEKey key, long amount) implements MEStorage {
        private LongStorage(long amount) {
            this(TEST_KEY, amount);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.add(key, amount);
        }

        @Override
        public Component getDescription() {
            return Component.literal("long storage");
        }
    }

    private record FakePublicExactStorage(
            BigInteger amount,
            boolean exposeExactKey) implements MEStorage, ExactStorageAmountProvider {
        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.set(TEST_KEY, Long.MAX_VALUE);
        }

        @Override
        public Map<AEKey, BigInteger> exactStoredAmounts() {
            return exposeExactKey ? Map.of(TEST_KEY, amount) : Map.of();
        }

        @Override
        public Component getDescription() {
            return Component.literal("public exact storage provider");
        }
    }

    private static final class FakeInfinityBigIntegerCell
            implements MEStorage, ExtendedAePlusBigIntegerCellInventoryAccessor {
        private final Object2ObjectMap<AEKey, BigInteger> exact =
                new Object2ObjectOpenHashMap<>();
        private int exactTypes;
        private BigInteger exactTotal;
        private UUID storageUuid;

        private FakeInfinityBigIntegerCell(BigInteger amount) {
            exact.put(TEST_KEY, amount);
            exactTypes = 1;
            exactTotal = amount;
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
        public int aco$getExactStoredTypeCount() {
            return exactTypes;
        }

        @Override
        public void aco$setExactStoredTypeCount(int value) {
            exactTypes = value;
        }

        @Override
        public BigInteger aco$getExactStoredTotal() {
            return exactTotal;
        }

        @Override
        public void aco$setExactStoredTotal(BigInteger value) {
            exactTotal = value;
        }

        @Override
        public void aco$saveExactChanges() {
            // 単体試験セルはNBTを持たないため、保存通知だけを成功扱いにする。
        }

        @Override
        public boolean aco$hasExactStorageUuid() {
            return storageUuid != null;
        }

        @Override
        public UUID aco$getExactStorageUuid() {
            return storageUuid;
        }

        @Override
        public UUID aco$assignExactStorageUuid() {
            // 実セルと同じく、未割当時だけ一意な保存IDを作る。
            if (storageUuid == null) {
                storageUuid = UUID.randomUUID();
            }
            return storageUuid;
        }

        @Override
        public Component getDescription() {
            return Component.literal("fake infinity BigInteger cell");
        }
    }

    /** DriveWatcherと同じくFacade呼出しを内側のセルへ委譲する試験用Wrapper。 */
    private static final class FakeDriveWrapper
            implements MEStorage, DelegatingMEInventoryAccessor {
        private final MEStorage delegate;

        private FakeDriveWrapper(MEStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            delegate.getAvailableStacks(out);
        }

        @Override
        public MEStorage aco$getDelegateStorage() {
            return delegate;
        }

        @Override
        public Component getDescription() {
            return Component.literal("fake AE2 drive wrapper");
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
