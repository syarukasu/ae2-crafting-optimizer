package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class Ae2PlanningInventorySnapshotTest {
    @Test
    void capturesOnlyRootReferencedKeysAndExcludesRequestedOutput() {
        TestKey requestedOutput = new TestKey("requested_output");
        TestKey referenced = new TestKey("referenced");
        TestKey unrelated = new TestKey("unrelated");
        KeyCounter network = new KeyCounter();
        network.add(requestedOutput, 11L);
        network.add(referenced, 22L);
        network.add(unrelated, 33L);

        Ae2PlanningInventorySnapshot snapshot =
                Ae2PlanningInventorySnapshot.captureReferenced(
                        network,
                        List.of(requestedOutput, referenced),
                        requestedOutput);

        assertEquals(0L, snapshot.amount(requestedOutput));
        assertEquals(22L, snapshot.amount(referenced));
        assertEquals(0L, snapshot.amount(unrelated));
    }

    /** Minecraft Registry初期化なしでKeyCounterの参照キーを分離する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final ResourceLocation id;

        private TestKey(String path) {
            id = new ResourceLocation("ae2_crafting_optimizer", path);
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
            // Packetを使わない在庫Snapshot単体試験である。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id.toString());
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
            // ワールド内ドロップを作らない在庫Snapshot単体試験である。
        }
    }
}
