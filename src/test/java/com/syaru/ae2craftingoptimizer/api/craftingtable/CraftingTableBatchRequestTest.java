package com.syaru.ae2craftingoptimizer.api.craftingtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class CraftingTableBatchRequestTest {
    /** 作業台の入力枠数。 */
    private static final int CRAFTING_GRID_SLOTS = 9;
    private static final IPatternDetails TEST_PATTERN =
            new IPatternDetails() {
                @Override
                public AEItemKey getDefinition() {
                    return null;
                }

                @Override
                public IInput[] getInputs() {
                    return new IInput[0];
                }

                @Override
                public List<GenericStack> getOutputs() {
                    return List.of();
                }
            };

    @Test
    void acceptsNineLongMaximumSlotsEvenWhenTheirKeyTotalExceedsLong() {
        AEKey input =
                new TestKey(
                        "input");
        AEKey output =
                new TestKey(
                        "output");
        KeyCounter[] perExecution =
                new KeyCounter[CRAFTING_GRID_SLOTS];
        List<ExactStack> aggregateSlots =
                new ArrayList<>(
                        CRAFTING_GRID_SLOTS);
        // 同じ入力キーを九枠へ置き、各slotだけがsigned longへ収まる境界を作る。
        for (int slot = 0;
                slot < CRAFTING_GRID_SLOTS;
                slot++) {
            KeyCounter counter =
                    perExecution[slot] =
                            new KeyCounter();
            counter.add(
                    input,
                    1L);
            aggregateSlots.add(
                    new ExactStack(
                            input,
                            BigInteger.valueOf(
                                    Long.MAX_VALUE)));
        }

        CraftingTableBatchRequest request =
                request(
                        perExecution,
                        aggregateSlots,
                        output,
                        BigInteger.valueOf(
                                Long.MAX_VALUE));

        assertTrue(
                request.countsFitSignedLong());
        assertTrue(
                request.aggregateInputTotals()
                                .get(
                                        input)
                                .compareTo(
                                        BigInteger.valueOf(
                                                Long.MAX_VALUE))
                        > 0);
    }

    @Test
    void rejectsOnePhysicalThreadStackThatExceedsSignedLong() {
        AEKey input =
                new TestKey(
                        "oversized_input");
        AEKey output =
                new TestKey(
                        "oversized_output");
        KeyCounter counter =
                new KeyCounter();
        counter.add(
                input,
                2L);
        BigInteger executions =
                BigInteger.valueOf(
                        Long.MAX_VALUE);

        CraftingTableBatchRequest request =
                request(
                        new KeyCounter[] {
                            counter
                        },
                        List.of(
                                new ExactStack(
                                        input,
                                        executions.multiply(
                                                BigInteger.TWO))),
                        output,
                        executions);

        assertFalse(
                request.countsFitSignedLong());
    }

    @Test
    void bigIntegerJobKeepsTheExactCoefficientBeyondSignedLong() {
        AEKey input =
                new TestKey(
                        "big_input");
        AEKey output =
                new TestKey(
                        "big_output");
        KeyCounter counter =
                new KeyCounter();
        counter.add(
                input,
                1L);
        BigInteger executions =
                BigInteger.valueOf(
                                Long.MAX_VALUE)
                        .add(
                                BigInteger.ONE);

        CraftingTableBatchRequest request =
                request(
                        new KeyCounter[] {
                            counter
                        },
                        List.of(
                                new ExactStack(
                                        input,
                                        executions)),
                        output,
                        executions,
                        CraftingTableBatchMode.BIG_INTEGER_JOB);

        /*
         * 物理Thread数やint予算へ縮小せず、実レシピ一回分へ掛ける
         * BigInteger係数と成果物会計をRequest境界でそのまま維持する。
         */
        assertEquals(
                executions,
                request.executions());
        assertEquals(
                Map.of(
                        output,
                        executions),
                request.aggregateExpectedOutputs());
        assertFalse(
                request.countsFitSignedLong());
    }

    private static CraftingTableBatchRequest request(
            KeyCounter[] inputs,
            List<ExactStack> aggregateSlots,
            AEKey output,
            BigInteger executions) {
        return request(
                inputs,
                aggregateSlots,
                output,
                executions,
                CraftingTableBatchMode.AE2_JOB);
    }

    private static CraftingTableBatchRequest request(
            KeyCounter[] inputs,
            List<ExactStack> aggregateSlots,
            AEKey output,
            BigInteger executions,
            CraftingTableBatchMode mode) {
        GenericStack outputPerExecution =
                new GenericStack(
                        output,
                        1L);
        return new CraftingTableBatchRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "aco:test-request",
                0,
                mode,
                TEST_PATTERN,
                executions,
                inputs,
                aggregateSlots,
                List.of(
                        outputPerExecution),
                List.of(),
                Map.of(
                        output,
                        executions));
    }

    /** Minecraft Registryを起動せず数量契約だけを試験する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final String id;

        private TestKey(
                String id) {
            this.id =
                    id;
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
            return ResourceLocation.fromNamespaceAndPath(
                    "ae2_crafting_optimizer",
                    id);
        }

        @Override
        public void writeToPacket(
                RegistryFriendlyByteBuf buffer) {
            // Packet経路を試験しないため書き込まない。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(
                    id);
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
            // ワールド内ドロップを試験しないため追加しない。
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}
