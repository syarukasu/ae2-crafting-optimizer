package com.syaru.ae2craftingoptimizer.engine.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.syaru.ae2craftingoptimizer.engine.CompiledCraftingGraph;
import com.syaru.ae2craftingoptimizer.engine.CompiledPattern;
import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import java.math.BigInteger;
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

class VectorBatchPlannerTest {
    /** AQE既定上限より小さく、long九枠の合算を十分収める試験用bit上限。 */
    private static final int TEST_MAXIMUM_BITS = 512;
    /** 作業台の入力枠数。九枠を同じキーへ集約する境界条件として使う。 */
    private static final int CRAFTING_GRID_SLOTS = 9;
    /** Fingerprint生成でAEKeyの型IDを取得できる、Registry非依存の試験用Key型。 */
    private static final AEKeyType TEST_KEY_TYPE = new AEKeyType(
            ResourceLocation.fromNamespaceAndPath(
                    "ae2_crafting_optimizer",
                    "vector_planner_test"),
            TestKey.class,
            Component.literal("ACO vector planner test")) {
        @Override
        public AEKey readFromPacket(FriendlyByteBuf buffer) {
            return new TestKey(buffer.readUtf());
        }

        @Override
        public AEKey loadKeyFromTag(CompoundTag tag) {
            return new TestKey(tag.getString("name"));
        }
    };

    @Test
    void aggregatesNineLongMaximumInputsIntoOneExactBoundaryMutation() {
        TestKey raw = new TestKey("raw");
        TestKey output = new TestKey("output");
        List<CompiledPattern.InputSlot<AEKey>> inputs =
                new ArrayList<>(CRAFTING_GRID_SLOTS);
        // 九枠を同じAEKeyへ向け、slot数ではなくBigInteger合計へ畳まれることを固定する。
        for (int slot = 0; slot < CRAFTING_GRID_SLOTS; slot++) {
            inputs.add(new CompiledPattern.InputSlot<>(
                    List.of(new CompiledPattern.Stack<>(raw, 1L))));
        }
        CompiledPattern<AEKey> pattern = new CompiledPattern<>(
                "aco:test_nine_slot_long_max",
                inputs,
                Map.of(output, 1L),
                false);
        CompiledRootProgram<AEKey> program =
                CompiledRootProgram.tryCompile(
                                CompiledCraftingGraph.compile(
                                        1L,
                                        List.of(pattern)),
                                output,
                                ignored -> false)
                        .orElseThrow();

        BigInteger executions = BigInteger.valueOf(Long.MAX_VALUE);
        BigInteger exactInput = executions.multiply(
                BigInteger.valueOf(CRAFTING_GRID_SLOTS));
        CompiledRootProgram.BigInventorySnapshot<AEKey> inventory =
                program.captureBigInventory(
                        key -> key.equals(raw)
                                ? exactInput
                                : BigInteger.ZERO,
                        TEST_MAXIMUM_BITS);

        var plan = VectorBatchPlanner.prepare(
                UUID.randomUUID(),
                UUID.randomUUID(),
                program,
                inventory,
                executions,
                1,
                BigInteger.valueOf(640L),
                BigInteger.ZERO,
                "aco:test_nine_slot_long_max",
                1L,
                1L,
                TEST_MAXIMUM_BITS,
                (programFingerprint,
                                requestedAmount,
                                totalInputs,
                                totalOutputs) ->
                        "aco:test_nine_slot_long_max_fingerprint");

        assertEquals(1, plan.totalInputs().size());
        assertEquals(raw, plan.totalInputs().get(0).key());
        assertEquals(exactInput, plan.totalInputs().get(0).amount());
        assertEquals(1, plan.finalOutputs().size());
        assertEquals(output, plan.finalOutputs().get(0).key());
        assertEquals(executions, plan.finalOutputs().get(0).amount());
        assertEquals(executions, plan.logicalExecutions());
        assertEquals(1, plan.logicalStageCount());
    }

    /** Minecraft Registry初期化なしでPlannerを試験する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final String name;

        private TestKey(String name) {
            this.name = name;
        }

        @Override
        public AEKeyType getType() {
            return TEST_KEY_TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            return tag;
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "ae2_crafting_optimizer",
                    name);
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {
            buffer.writeUtf(name);
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(name);
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
            // この単体テストはワールド内ドロップを作らないため処理は不要。
        }
    }
}
