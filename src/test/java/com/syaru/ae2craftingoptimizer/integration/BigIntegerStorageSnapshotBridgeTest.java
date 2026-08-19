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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BigIntegerStorageSnapshotBridgeTest {
    private static final TestKey TEST_KEY = new TestKey();
    private static final TestKey UNRELATED_KEY = new TestKey();
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    /** tick番号自体に意味を持たせず、同一tick判定だけを検証する固定値。 */
    private static final long CACHE_TEST_TICK = 42L;
    /** invalidation前後を同一tickとして比較する固定値。 */
    private static final long INVALIDATION_TEST_TICK = 7L;
    /** 入れ子Networkのcapture順序を検証する固定値。 */
    private static final long NESTED_TEST_TICK = 90L;

    @AfterEach
    void resetExactNetworkSnapshotCache() {
        ExactNetworkStorageSnapshotCache.resetForTests();
    }

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

    @Test
    void reusesOneExactNetworkSnapshotWithinTheSameTick() {
        Object storage = new Object();
        BigInteger exactAmount = BigInteger.TEN.pow(64);
        KeyCounter first = new KeyCounter();

        assertFalse(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                storage,
                first,
                true,
                CACHE_TEST_TICK));
        BigKeyCounterSidecars.merge(
                first,
                new BigKeyCounterSidecars.Snapshot(
                        Map.of(TEST_KEY, exactAmount),
                        true));
        first.set(TEST_KEY, Long.MAX_VALUE);
        ExactNetworkStorageSnapshotCache.finishForTests(
                storage,
                first,
                CACHE_TEST_TICK);

        KeyCounter reused = new KeyCounter();
        assertTrue(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                storage,
                reused,
                true,
                CACHE_TEST_TICK));
        assertEquals(Long.MAX_VALUE, reused.get(TEST_KEY));
        assertEquals(
                exactAmount,
                BigKeyCounterSidecars.snapshot(reused)
                        .orElseThrow()
                        .amount(TEST_KEY));
    }

    @Test
    void invalidationRejectsAnEarlierSnapshotInTheSameTick() {
        Object storage = new Object();
        KeyCounter first = new KeyCounter();

        assertFalse(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                storage,
                first,
                true,
                INVALIDATION_TEST_TICK));
        first.set(TEST_KEY, 12L);
        ExactNetworkStorageSnapshotCache.finishForTests(
                storage,
                first,
                INVALIDATION_TEST_TICK);
        ExactNetworkStorageSnapshotCache.invalidateForTests();

        assertFalse(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                storage,
                new KeyCounter(),
                true,
                INVALIDATION_TEST_TICK));
    }

    @Test
    void invalidationDuringCapturePreventsPublishingAPartialSnapshot() {
        Object storage = new Object();
        KeyCounter inProgress = new KeyCounter();

        assertFalse(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                storage,
                inProgress,
                true,
                INVALIDATION_TEST_TICK));
        inProgress.set(TEST_KEY, 12L);

        // 集計途中の実在庫変更を再現し、その後のRETURNで古い値を公開させない。
        ExactNetworkStorageSnapshotCache.invalidateForTests();
        ExactNetworkStorageSnapshotCache.finishForTests(
                storage,
                inProgress,
                INVALIDATION_TEST_TICK);

        assertFalse(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                storage,
                new KeyCounter(),
                true,
                INVALIDATION_TEST_TICK));
    }

    @Test
    void neverReusesANetworkSnapshotAcrossServerTicks() {
        Object storage = new Object();
        KeyCounter first = new KeyCounter();

        assertFalse(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                storage,
                first,
                true,
                CACHE_TEST_TICK));
        first.set(TEST_KEY, 3L);
        ExactNetworkStorageSnapshotCache.finishForTests(
                storage,
                first,
                CACHE_TEST_TICK);

        assertFalse(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                storage,
                new KeyCounter(),
                true,
                CACHE_TEST_TICK + 1L));
    }

    @Test
    void nestedNetworkSnapshotCanBeReusedBeforeTheOuterScanFinishes() {
        Object outerStorage = new Object();
        Object innerStorage = new Object();
        KeyCounter outer = new KeyCounter();
        KeyCounter inner = new KeyCounter();

        assertFalse(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                outerStorage,
                outer,
                true,
                NESTED_TEST_TICK));
        assertFalse(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                innerStorage,
                inner,
                true,
                NESTED_TEST_TICK));
        inner.set(TEST_KEY, 64L);
        ExactNetworkStorageSnapshotCache.finishForTests(
                innerStorage,
                inner,
                NESTED_TEST_TICK);

        KeyCounter repeatedInner = new KeyCounter();
        assertTrue(ExactNetworkStorageSnapshotCache.reuseOrBeginForTests(
                innerStorage,
                repeatedInner,
                true,
                NESTED_TEST_TICK));
        assertEquals(64L, repeatedInner.get(TEST_KEY));

        outer.set(TEST_KEY, 64L);
        ExactNetworkStorageSnapshotCache.finishForTests(
                outerStorage,
                outer,
                NESTED_TEST_TICK);
    }

    @Test
    void gridTerminalUsesAe2CachedSnapshotOnlyForTheSameInventory() {
        AtomicInteger menuScans = new AtomicInteger();
        CountingStorage gridStorage = new CountingStorage(8L, menuScans);
        BigInteger exactAmount = BigInteger.TEN.pow(32);
        KeyCounter cached = new KeyCounter();
        BigKeyCounterSidecars.merge(
                cached,
                new BigKeyCounterSidecars.Snapshot(
                        Map.of(TEST_KEY, exactAmount),
                        true));
        cached.set(TEST_KEY, Long.MAX_VALUE);

        KeyCounter result = GridStorageSnapshotBridge.availableStacksForTests(
                gridStorage,
                gridStorage,
                () -> cached,
                true);

        assertEquals(0, menuScans.get());
        assertEquals(Long.MAX_VALUE, result.get(TEST_KEY));
        assertEquals(
                exactAmount,
                BigKeyCounterSidecars.snapshot(result)
                        .orElseThrow()
                        .amount(TEST_KEY));
    }

    @Test
    void gridTerminalDoesNotReplaceAddonSpecificInventory() {
        AtomicInteger menuScans = new AtomicInteger();
        CountingStorage menuStorage = new CountingStorage(5L, menuScans);
        MEStorage differentGridStorage = new LongStorage(99L);
        KeyCounter cached = new KeyCounter();
        cached.set(TEST_KEY, 99L);

        KeyCounter result = GridStorageSnapshotBridge.availableStacksForTests(
                menuStorage,
                differentGridStorage,
                () -> cached,
                true);

        assertEquals(1, menuScans.get());
        assertEquals(5L, result.get(TEST_KEY));
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

    private record CountingStorage(
            long amount,
            AtomicInteger scans) implements MEStorage {
        @Override
        public void getAvailableStacks(KeyCounter out) {
            scans.incrementAndGet();
            out.add(TEST_KEY, amount);
        }

        @Override
        public Component getDescription() {
            return Component.literal("counting storage");
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
