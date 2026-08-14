package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Issue #93のJava 25 C2クラッシュとSidecar可視性契約を固定する試験。 */
class BigKeyCounterSidecarsTest {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    /** 実際のfatal errorで処理中だった約11,892キーを上回るストレス試験件数。 */
    private static final int STRESS_KEY_COUNT = 12_000;
    /** C2のloop最適化を促しつつ、単体試験時間を抑える反復回数。 */
    private static final int STRESS_COPY_ROUNDS = 8;

    @AfterEach
    void clearSidecars() {
        BigKeyCounterSidecars.clearForTests();
    }

    @Test
    void copiesOnlyPositiveVisibleKeysWithoutTruncatingExactAmounts() {
        TestKey visibleKey = new TestKey("visible");
        TestKey hiddenKey = new TestKey("hidden");
        BigInteger exactAmount = LONG_MAX.add(BigInteger.ONE);
        KeyCounter source = new KeyCounter();
        BigKeyCounterSidecars.merge(
                source,
                new BigKeyCounterSidecars.Snapshot(
                        Map.of(visibleKey, exactAmount, hiddenKey, BigInteger.TEN),
                        true));

        KeyCounter target = new KeyCounter();
        target.set(visibleKey, Long.MAX_VALUE);
        BigKeyCounterSidecars.copyVisible(source, target);

        BigKeyCounterSidecars.Snapshot copied =
                BigKeyCounterSidecars.snapshot(target).orElseThrow();
        assertEquals(exactAmount, copied.amount(visibleKey));
        assertEquals(BigInteger.ZERO, copied.amount(hiddenKey));
        assertTrue(copied.isExact(visibleKey));
        assertTrue(copied.complete());
    }

    @Test
    void snapshotOwnsItsCollectionsAndExposesReadOnlyViews() {
        TestKey key = new TestKey("owned");
        Map<AEKey, BigInteger> mutableAmounts = new LinkedHashMap<>();
        Set<AEKey> mutableExactKeys = new LinkedHashSet<>();
        mutableAmounts.put(key, BigInteger.TEN);
        mutableExactKeys.add(key);

        BigKeyCounterSidecars.Snapshot snapshot =
                new BigKeyCounterSidecars.Snapshot(
                        mutableAmounts,
                        false,
                        mutableExactKeys);
        mutableAmounts.clear();
        mutableExactKeys.clear();

        assertEquals(BigInteger.TEN, snapshot.amount(key));
        assertTrue(snapshot.isExact(key));
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.amounts().put(key, BigInteger.ONE));
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.exactKeys().clear());
    }

    @Test
    void repeatedlyCopiesAProductionSizedVisibleSnapshot() {
        Map<AEKey, BigInteger> exactAmounts = new LinkedHashMap<>();
        KeyCounter source = new KeyCounter();
        KeyCounter target = new KeyCounter();
        TestKey first = null;
        TestKey last = null;

        // fatal error時より多いキーを用意し、同じ可視コピー経路をloop最適化対象にする。
        for (int index = 0; index < STRESS_KEY_COUNT; index++) {
            TestKey key = new TestKey("stress_" + index);
            // 境界キーを後段の正確値検証へ残す。
            if (index == 0) {
                first = key;
            }
            last = key;
            BigInteger amount = LONG_MAX.add(BigInteger.valueOf(index + 1L));
            exactAmounts.put(key, amount);
            target.set(key, Long.MAX_VALUE);
        }
        BigKeyCounterSidecars.merge(
                source,
                new BigKeyCounterSidecars.Snapshot(exactAmounts, true));

        // 同一targetのSidecarを繰り返し置換し、値・exactness・寿命管理を同時に検証する。
        for (int round = 0; round < STRESS_COPY_ROUNDS; round++) {
            BigKeyCounterSidecars.copyVisible(source, target);
        }

        BigKeyCounterSidecars.Snapshot copied =
                BigKeyCounterSidecars.snapshot(target).orElseThrow();
        assertEquals(STRESS_KEY_COUNT, copied.amounts().size());
        assertEquals(LONG_MAX.add(BigInteger.ONE), copied.amount(first));
        assertEquals(
                LONG_MAX.add(BigInteger.valueOf(STRESS_KEY_COUNT)),
                copied.amount(last));
        assertTrue(copied.isExact(first));
        assertTrue(copied.isExact(last));
        assertFalse(copied.amounts().isEmpty());
    }

    /** Minecraft Registry初期化なしで多数の異なるAEKeyを作る最小実装。 */
    private static final class TestKey extends AEKey {
        private final ResourceLocation id;

        private TestKey(String path) {
            this.id = new ResourceLocation("ae2_crafting_optimizer", path);
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
        public CompoundTag toTag() {
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
        public void writeToPacket(FriendlyByteBuf buffer) {
            // Packet同期を行わない単体試験なので書き込みは不要。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id.toString());
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
            // ワールド内ドロップを扱わない単体試験なので処理は不要。
        }
    }
}
