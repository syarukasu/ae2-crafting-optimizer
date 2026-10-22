package com.syaru.ae2craftingoptimizer.engine.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void collapsesMultiStageCraftingAndReturnsOnlyItsNetStorageBoundary() {
        TestKey raw = new TestKey("multi_raw");
        TestKey middle = new TestKey("multi_middle");
        TestKey output = new TestKey("multi_output");
        CompiledPattern<AEKey> rootPattern = new CompiledPattern<>(
                "aco:test_multi_root",
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(middle, 3L)))),
                Map.of(output, 1L),
                false);
        CompiledPattern<AEKey> middlePattern = new CompiledPattern<>(
                "aco:test_multi_middle",
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(raw, 2L)))),
                Map.of(middle, 4L),
                false);
        CompiledRootProgram<AEKey> program =
                CompiledRootProgram.tryCompile(
                                CompiledCraftingGraph.compile(
                                        2L,
                                        List.of(rootPattern, middlePattern)),
                                output,
                                ignored -> false)
                        .orElseThrow();

        /*
         * output 5個にはmiddle 15個が必要。
         * 在庫middle 2個を使い、4個出力Patternを4回だけ実行して余剰1個を返す。
         */
        CompiledRootProgram.BigInventorySnapshot<AEKey> inventory =
                program.captureBigInventory(
                        key -> {
                            // 中間在庫を一部だけ使い、丸め余剰との正味会計を検証する。
                            if (key.equals(middle)) {
                                return BigInteger.valueOf(2L);
                            }
                            // middle Pattern 4回分のraw 8個だけを供給する。
                            if (key.equals(raw)) {
                                return BigInteger.valueOf(8L);
                            }
                            return BigInteger.ZERO;
                        },
                        TEST_MAXIMUM_BITS);

        var plan = VectorBatchPlanner.prepare(
                UUID.randomUUID(),
                UUID.randomUUID(),
                program,
                inventory,
                BigInteger.valueOf(5L),
                "aco:test_multi_stage",
                2L,
                2L,
                TEST_MAXIMUM_BITS,
                (programFingerprint,
                                requestedAmount,
                                totalInputs,
                                totalOutputs) ->
                        "aco:test_multi_stage_fingerprint");

        assertEquals(1, plan.totalInputs().size());
        assertEquals(raw, plan.totalInputs().get(0).key());
        assertEquals(BigInteger.valueOf(8L), plan.totalInputs().get(0).amount());
        assertEquals(1, plan.finalOutputs().size());
        assertEquals(output, plan.finalOutputs().get(0).key());
        assertEquals(BigInteger.valueOf(5L), plan.finalOutputs().get(0).amount());
        assertEquals(1, plan.remainingOutputs().size());
        assertEquals(middle, plan.remainingOutputs().get(0).key());
        assertEquals(
                BigInteger.ONE,
                plan.remainingOutputs().get(0).amount());
        assertEquals(BigInteger.valueOf(9L), plan.logicalExecutions());
        assertEquals(2, plan.logicalStageCount());
    }

    @Test
    void rejectsMultiStageVectorBeforeMutationWhenOneRawItemIsMissing() {
        TestKey raw = new TestKey("missing_raw");
        TestKey middle = new TestKey("missing_middle");
        TestKey output = new TestKey("missing_output");
        CompiledPattern<AEKey> rootPattern = new CompiledPattern<>(
                "aco:test_missing_root",
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(middle, 3L)))),
                Map.of(output, 1L),
                false);
        CompiledPattern<AEKey> middlePattern = new CompiledPattern<>(
                "aco:test_missing_middle",
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(raw, 2L)))),
                Map.of(middle, 4L),
                false);
        CompiledRootProgram<AEKey> program =
                CompiledRootProgram.tryCompile(
                                CompiledCraftingGraph.compile(
                                        3L,
                                        List.of(rootPattern, middlePattern)),
                                output,
                                ignored -> false)
                        .orElseThrow();
        CompiledRootProgram.BigInventorySnapshot<AEKey> inventory =
                program.captureBigInventory(
                        key -> {
                            // 途中素材2個は存在するため、下段ではraw 8個が必要になる。
                            if (key.equals(middle)) {
                                return BigInteger.valueOf(2L);
                            }
                            // 必要数8個に対して7個だけ置き、不足1個を作る。
                            if (key.equals(raw)) {
                                return BigInteger.valueOf(7L);
                            }
                            return BigInteger.ZERO;
                        },
                        TEST_MAXIMUM_BITS);

        var deterministic = program.tryPlanDeterministicCraftingBig(
                        BigInteger.valueOf(5L),
                        inventory,
                        TEST_MAXIMUM_BITS)
                .orElseThrow();
        assertEquals(BigInteger.ONE, deterministic.missing().get(raw));
        assertTrue(!deterministic.craftable());
        assertThrows(
                IllegalArgumentException.class,
                () -> VectorBatchPlanner.prepare(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        program,
                        inventory,
                        BigInteger.valueOf(5L),
                        "aco:test_missing",
                        3L,
                        3L,
                        TEST_MAXIMUM_BITS,
                        (programFingerprint,
                                        requestedAmount,
                                        totalInputs,
                                        totalOutputs) ->
                                "aco:test_missing_fingerprint"));
    }

    @Test
    void collapsesTwentyLongMaximumStagesIntoTwentyPatternEvaluations() {
        /** 実環境の圧縮試験と同じ、作業台Patternの依存段数。 */
        final int compressionStages = 20;
        List<TestKey> keys = new ArrayList<>(compressionStages + 1);
        // 最終成果物から原料まで、各段を表す一意なAEKeyを作る。
        for (int stage = 0; stage <= compressionStages; stage++) {
            keys.add(new TestKey("twenty_stage_" + stage));
        }
        List<CompiledPattern<AEKey>> patterns =
                new ArrayList<>(compressionStages);
        // 各段を1入力から1出力の作業台Patternとして直列接続する。
        for (int stage = 0; stage < compressionStages; stage++) {
            patterns.add(new CompiledPattern<>(
                    "aco:test_twenty_stage_" + stage,
                    List.of(new CompiledPattern.InputSlot<>(
                            List.of(new CompiledPattern.Stack<>(
                                    keys.get(stage + 1),
                                    1L)))),
                    Map.of(keys.get(stage), 1L),
                    false));
        }
        CompiledRootProgram<AEKey> program =
                CompiledRootProgram.tryCompile(
                                CompiledCraftingGraph.compile(
                                        4L,
                                        patterns),
                                keys.get(0),
                                ignored -> false)
                        .orElseThrow();
        BigInteger requested = BigInteger.valueOf(Long.MAX_VALUE);
        CompiledRootProgram.BigInventorySnapshot<AEKey> inventory =
                program.captureBigInventory(
                        key -> key.equals(keys.get(compressionStages))
                                ? requested
                                : BigInteger.ZERO,
                        TEST_MAXIMUM_BITS);

        var plan = VectorBatchPlanner.prepare(
                UUID.randomUUID(),
                UUID.randomUUID(),
                program,
                inventory,
                requested,
                "aco:test_twenty_stage",
                4L,
                4L,
                TEST_MAXIMUM_BITS,
                (programFingerprint,
                                requestedAmount,
                                totalInputs,
                                totalOutputs) ->
                        "aco:test_twenty_stage_fingerprint");

        assertEquals(1, plan.totalInputs().size());
        assertEquals(
                keys.get(compressionStages),
                plan.totalInputs().get(0).key());
        assertEquals(requested, plan.totalInputs().get(0).amount());
        assertEquals(1, plan.finalOutputs().size());
        assertEquals(keys.get(0), plan.finalOutputs().get(0).key());
        assertEquals(requested, plan.finalOutputs().get(0).amount());
        assertTrue(plan.remainingOutputs().isEmpty());
        assertEquals(
                requested.multiply(BigInteger.valueOf(compressionStages)),
                plan.logicalExecutions());
        assertEquals(compressionStages, plan.logicalStageCount());
        assertEquals(compressionStages, plan.craftingSteps().size());
        // Vector計画にも材料側20から完成品側1までの実Worker順が保存される。
        for (int step = 0;
                step < compressionStages;
                step++) {
            assertEquals(
                    compressionStages - step,
                    plan.craftingSteps().get(step).depth());
            assertEquals(
                    requested,
                    plan.craftingSteps().get(step).executions());
        }
    }

    @Test
    void persistsTheExactTagAlternativeSelectedFromBigIntegerInventory() {
        TestKey unavailableLog =
                new TestKey(
                        "unavailable_log");
        TestKey availableLog =
                new TestKey(
                        "available_log");
        TestKey output =
                new TestKey(
                        "tag_output");
        CompiledPattern<AEKey> pattern =
                new CompiledPattern<>(
                        "aco:test_tag_alternative",
                        List.of(
                                new CompiledPattern.InputSlot<>(
                                        List.of(
                                                new CompiledPattern.Stack<>(
                                                        unavailableLog,
                                                        1L),
                                                new CompiledPattern.Stack<>(
                                                        availableLog,
                                                        1L)))),
                        Map.of(
                                output,
                                1L),
                        false);
        CompiledRootProgram<AEKey> program =
                CompiledRootProgram.tryCompile(
                                CompiledCraftingGraph.compile(
                                        5L,
                                        List.of(
                                                pattern)),
                                output,
                                ignored -> false)
                        .orElseThrow();
        /** AQE既定64桁と同じ桁数で、longを十分に越える選択入力試験値。 */
        BigInteger requested =
                BigInteger.TEN
                        .pow(64)
                        .subtract(
                                BigInteger.ONE);
        CompiledRootProgram.BigInventorySnapshot<AEKey> inventory =
                program.captureBigInventory(
                        key ->
                                key.equals(
                                                availableLog)
                                        ? requested
                                        : BigInteger.ZERO,
                        TEST_MAXIMUM_BITS);

        var plan =
                VectorBatchPlanner.prepare(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        program,
                        inventory,
                        requested,
                        "aco:test_tag_alternative",
                        5L,
                        5L,
                        TEST_MAXIMUM_BITS,
                        (programFingerprint,
                                        requestedAmount,
                                        totalInputs,
                                        totalOutputs) ->
                                "aco:test_tag_alternative_fingerprint");

        assertEquals(
                1,
                plan.totalInputs()
                        .size());
        assertEquals(
                availableLog,
                plan.totalInputs()
                        .get(0)
                        .key());
        assertEquals(
                requested,
                plan.totalInputs()
                        .get(0)
                        .amount());
        assertEquals(
                1,
                plan.craftingSteps()
                        .size());
        assertEquals(
                availableLog,
                plan.craftingSteps()
                        .get(0)
                        .selectedInputs()
                        .get(0)
                        .key());
        assertEquals(
                1L,
                plan.craftingSteps()
                        .get(0)
                        .selectedInputs()
                        .get(0)
                        .amountPerExecution());
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
