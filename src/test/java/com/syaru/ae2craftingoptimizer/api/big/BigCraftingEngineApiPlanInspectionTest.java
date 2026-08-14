package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.Ae2BigCraftingPlanFactory;
import com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigExactCraftingByteCounter;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerSimulationPlan;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerSimulationPlanTestFactory;
import com.syaru.ae2craftingoptimizer.engine.CompiledCraftingGraph;
import com.syaru.ae2craftingoptimizer.engine.CompiledPattern;
import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import com.syaru.ae2craftingoptimizer.engine.LongCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.OverflowPromotingCraftingPlanner;
import com.syaru.ae2craftingoptimizer.engine.PlanningGuard;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class BigCraftingEngineApiPlanInspectionTest {
    /** 診断GameTestでME在庫へ投入される鉄ナゲット数。 */
    private static final long NUGGET_INVENTORY = 8_600_000_000_000_000_000L;
    /** 診断GameTestで注文される鉄ブロック数。 */
    private static final long BLOCK_REQUEST = 106_000_000_000_000_000L;
    /** 中間素材と不足数がsigned longを超えるsimulation用の鉄ブロック注文数。 */
    private static final long WIDE_MISSING_BLOCK_REQUEST = 1_200_000_000_000_000_000L;
    /** 9ナゲットから1鉄塊、9鉄塊から1鉄ブロックを作る圧縮比。 */
    private static final long COMPRESSION_RATIO = 9L;
    /** 二段圧縮が実際に消費する鉄ナゲット数。 */
    private static final long EXPECTED_NUGGET_USAGE = 8_586_000_000_000_000_000L;
    /** 全注文を満たした後にME在庫へ残る鉄ナゲット数。 */
    private static final long EXPECTED_NUGGET_REMAINDER = 14_000_000_000_000_000L;
    /** 鉄塊Patternの実行回数。 */
    private static final long EXPECTED_INGOT_EXECUTIONS = 954_000_000_000_000_000L;
    /** AE2 19.2.17でitem key 1 byteが表す個数。 */
    private static final long ITEM_AMOUNT_PER_BYTE = 8L;
    /** この試験のBigInteger演算に十分で、境界を小さく保てるbit上限。 */
    private static final int TEST_MAXIMUM_BITS = 256;
    /** 仮想Pattern/recipe snapshotが変化しないことを示す世代番号。 */
    private static final long TEST_GENERATION = 0L;
    /** AE2の容量式で二段圧縮ツリーを数えた正確なCPU bytes。 */
    private static final BigInteger EXACT_BYTES =
            new BigInteger("10706000000000000024");
    /** wide不足注文で必要になる鉄塊Pattern回数。 */
    private static final BigInteger WIDE_MISSING_INGOT_EXECUTIONS =
            new BigInteger("10800000000000000000");
    /** wide不足注文で不足する鉄ナゲット数。 */
    private static final BigInteger WIDE_MISSING_NUGGETS =
            new BigInteger("97200000000000000000");
    /** wide不足注文をAE2の容量式で数えた正確なCPU bytes。 */
    private static final BigInteger WIDE_MISSING_EXACT_BYTES =
            new BigInteger("121200000000000000024");
    private static final String INGOT_PATTERN_ID = "minecraft:iron_ingot_from_nuggets";
    private static final String BLOCK_PATTERN_ID = "minecraft:iron_block";
    private static final TestKey NUGGET = new TestKey("iron_nugget");
    private static final TestKey INGOT = new TestKey("iron_ingot");
    private static final TestKey BLOCK = new TestKey("iron_block");
    private static final IPatternDetails INGOT_PATTERN = new TestPattern(INGOT_PATTERN_ID);
    private static final IPatternDetails BLOCK_PATTERN = new TestPattern(BLOCK_PATTERN_ID);
    private static final Map<String, IPatternDetails> PATTERNS_BY_ID = Map.of(
            INGOT_PATTERN_ID, INGOT_PATTERN,
            BLOCK_PATTERN_ID, BLOCK_PATTERN);

    @Test
    void exposesVirtualTwoStageAe2AutocraftingPlanThroughPublicExactView() throws Exception {
        CompiledRootProgram<AEKey> program = twoStageCompressionProgram();
        var inventory = program.captureLongInventory(
                key -> key == NUGGET ? NUGGET_INVENTORY : 0L);
        var promoted = new OverflowPromotingCraftingPlanner<AEKey>(TEST_MAXIMUM_BITS).plan(
                program,
                BigInteger.valueOf(BLOCK_REQUEST),
                inventory,
                PlanningGuard.none());
        var longResult = assertInstanceOf(
                OverflowPromotingCraftingPlanner.LongResult.class,
                promoted);
        @SuppressWarnings("unchecked")
        LongCraftingPlan<AEKey> exactPlan =
                ((OverflowPromotingCraftingPlanner.LongResult<AEKey>) longResult).plan();

        assertTrue(exactPlan.craftable());
        assertEquals(EXPECTED_NUGGET_USAGE, exactPlan.usedInventory().get(NUGGET));
        assertEquals(EXPECTED_INGOT_EXECUTIONS, exactPlan.patternExecutions().get(INGOT_PATTERN_ID));
        assertEquals(BLOCK_REQUEST, exactPlan.patternExecutions().get(BLOCK_PATTERN_ID));
        assertEquals(
                EXPECTED_NUGGET_REMAINDER,
                NUGGET_INVENTORY - exactPlan.usedInventory().get(NUGGET));

        BigInteger exactBytes = BigExactCraftingByteCounter.calculate(
                BLOCK,
                BigInteger.valueOf(BLOCK_REQUEST),
                program.patternsByOutput(),
                widenPatternExecutions(exactPlan.patternExecutions()),
                ignored -> ITEM_AMOUNT_PER_BYTE,
                TEST_MAXIMUM_BITS);
        assertEquals(EXACT_BYTES, exactBytes);
        assertTrue(exactBytes.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);

        BigCapacityCraftingPlan metadata = new BigCapacityCraftingPlan(
                new GenericStack(BLOCK, BLOCK_REQUEST),
                false,
                false,
                keyCounter(exactPlan.usedInventory()),
                keyCounter(exactPlan.emitted()),
                keyCounter(exactPlan.missing()),
                resolvePatterns(exactPlan.patternExecutions()),
                exactBytes,
                TEST_GENERATION,
                TEST_GENERATION);

        CraftingPlan facade = Ae2CraftingPlanSidecars.expose(metadata);
        Future<appeng.api.networking.crafting.ICraftingPlan> simulatedCalculation =
                CompletableFuture.completedFuture(facade);
        var returnedPlan = simulatedCalculation.get();
        BigIntegerCraftingPlanView view =
                BigCraftingEngineApi.inspectAttachedExactPlan(returnedPlan).orElseThrow();

        assertInstanceOf(CraftingPlan.class, returnedPlan);
        assertEquals(Long.MAX_VALUE, returnedPlan.bytes());
        assertFalse(view.simulation());
        assertEquals(EXACT_BYTES, view.exactBytes());
        assertEquals(
                BigInteger.valueOf(EXPECTED_INGOT_EXECUTIONS),
                view.patternTimes().get(INGOT_PATTERN));
        assertEquals(BigInteger.valueOf(BLOCK_REQUEST), view.patternTimes().get(BLOCK_PATTERN));
        assertEquals(BigInteger.valueOf(EXPECTED_NUGGET_USAGE), view.usedItems().get(NUGGET));
        assertEquals(Map.of(), view.emittedItems());
        assertEquals(Map.of(), view.missingItems());
    }

    @Test
    void exposesWideMissingSimulationThroughPublicExactView() throws Exception {
        CompiledRootProgram<AEKey> program = twoStageCompressionProgram();
        var inventory = program.captureLongInventory(ignored -> 0L);
        var promoted = new OverflowPromotingCraftingPlanner<AEKey>(TEST_MAXIMUM_BITS).plan(
                program,
                BigInteger.valueOf(WIDE_MISSING_BLOCK_REQUEST),
                inventory,
                PlanningGuard.none());
        var bigResult = assertInstanceOf(
                OverflowPromotingCraftingPlanner.BigResult.class,
                promoted);
        @SuppressWarnings("unchecked")
        BigCraftingPlan<AEKey> exactPlan =
                ((OverflowPromotingCraftingPlanner.BigResult<AEKey>) bigResult).plan();

        assertFalse(exactPlan.craftable());
        assertEquals(WIDE_MISSING_INGOT_EXECUTIONS,
                exactPlan.patternExecutions().get(INGOT_PATTERN_ID));
        assertEquals(BigInteger.valueOf(WIDE_MISSING_BLOCK_REQUEST),
                exactPlan.patternExecutions().get(BLOCK_PATTERN_ID));
        assertEquals(WIDE_MISSING_NUGGETS, exactPlan.missing().get(NUGGET));

        BigInteger exactBytes = BigExactCraftingByteCounter.calculate(
                BLOCK,
                BigInteger.valueOf(WIDE_MISSING_BLOCK_REQUEST),
                program.patternsByOutput(),
                exactPlan.patternExecutions(),
                ignored -> ITEM_AMOUNT_PER_BYTE,
                TEST_MAXIMUM_BITS);
        assertEquals(WIDE_MISSING_EXACT_BYTES, exactBytes);

        BigIntegerSimulationPlan metadata = BigIntegerSimulationPlanTestFactory.create(
                new GenericStack(BLOCK, WIDE_MISSING_BLOCK_REQUEST),
                exactPlan,
                resolveBigPatterns(exactPlan.patternExecutions()),
                exactBytes,
                TEST_MAXIMUM_BITS);
        CraftingPlan facade = Ae2CraftingPlanSidecars.expose(metadata);
        Future<appeng.api.networking.crafting.ICraftingPlan> simulatedCalculation =
                CompletableFuture.completedFuture(facade);
        var returnedPlan = simulatedCalculation.get();
        BigIntegerCraftingPlanView view =
                BigCraftingEngineApi.inspectAttachedExactPlan(returnedPlan).orElseThrow();

        assertTrue(returnedPlan.simulation());
        assertEquals(Long.MAX_VALUE, returnedPlan.missingItems().get(NUGGET));
        assertTrue(view.simulation());
        assertEquals(WIDE_MISSING_EXACT_BYTES, view.exactBytes());
        assertEquals(WIDE_MISSING_NUGGETS, view.missingItems().get(NUGGET));
        assertEquals(WIDE_MISSING_INGOT_EXECUTIONS, view.patternTimes().get(INGOT_PATTERN));
        assertEquals(BigInteger.valueOf(WIDE_MISSING_BLOCK_REQUEST),
                view.patternTimes().get(BLOCK_PATTERN));
        assertEquals(Map.of(), view.usedItems());
        assertEquals(Map.of(), view.emittedItems());
    }

    @Test
    void exposesExactPatternPlanWhenNoRootWindowCanRepresentOneOutput() {
        BigInteger exactPatternExecutions = BigInteger.ONE.shiftLeft(63);
        BigInteger exactBytes = BigInteger.ONE.shiftLeft(64);
        BigCraftingPlan<AEKey> exactPlan = new BigCraftingPlan<>(
                BLOCK,
                BigInteger.ONE,
                Map.of(INGOT_PATTERN_ID, exactPatternExecutions),
                Map.of(NUGGET, exactPatternExecutions),
                Map.of(),
                Map.of(),
                1);
        var prepared = new Ae2BigCraftingPlanFactory.PreparedBigRootPlan(
                null,
                exactPlan,
                exactBytes,
                TEST_GENERATION,
                TEST_GENERATION,
                Ae2BigCraftingPlanFactory.ExecutionMode.EXACT_PATTERN_EXECUTOR,
                0L,
                "test-epoch",
                "test-fingerprint");
        BigIntegerCraftingPlan metadata = new BigIntegerCraftingPlan(
                new GenericStack(BLOCK, 1L),
                exactPlan,
                Map.of(INGOT_PATTERN, exactPatternExecutions),
                prepared,
                true);

        CraftingPlan facade = Ae2CraftingPlanSidecars.expose(metadata);
        BigIntegerCraftingPlanView view =
                BigCraftingEngineApi.inspectAttachedExactPlan(facade).orElseThrow();

        assertFalse(view.simulation());
        assertEquals(exactBytes, view.exactBytes());
        assertEquals(exactPatternExecutions, view.patternTimes().get(INGOT_PATTERN));
        assertEquals(exactPatternExecutions, view.usedItems().get(NUGGET));
    }

    @Test
    void doesNotInventSidecarForUnrelatedSaturatedAe2Plan() {
        CraftingPlan ordinary = new CraftingPlan(
                new GenericStack(BLOCK, 1L),
                Long.MAX_VALUE,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());

        // bytesが同じLong.MAX_VALUEでも、ACOが付けたIdentity Sidecarだけを信頼する。
        assertTrue(BigCraftingEngineApi.inspectAttachedExactPlan(ordinary).isEmpty());
    }

    private static CompiledRootProgram<AEKey> twoStageCompressionProgram() {
        var ingot = compressionPattern(INGOT_PATTERN_ID, NUGGET, INGOT);
        var block = compressionPattern(BLOCK_PATTERN_ID, INGOT, BLOCK);
        var graph = CompiledCraftingGraph.compile(
                TEST_GENERATION,
                List.of(ingot, block));
        return CompiledRootProgram.tryCompile(graph, BLOCK, ignored -> false).orElseThrow();
    }

    private static CompiledPattern<AEKey> compressionPattern(
            String id,
            AEKey input,
            AEKey output) {
        return new CompiledPattern<>(
                id,
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(input, COMPRESSION_RATIO)))),
                Map.of(output, 1L),
                false);
    }

    private static Map<String, BigInteger> widenPatternExecutions(
            Map<String, Long> executions) {
        Map<String, BigInteger> widened = new LinkedHashMap<>();
        // Plannerが算出した各Pattern回数を、byte式へ損失なく渡す。
        for (Map.Entry<String, Long> entry : executions.entrySet()) {
            widened.put(entry.getKey(), BigInteger.valueOf(entry.getValue()));
        }
        return Map.copyOf(widened);
    }

    private static Map<IPatternDetails, Long> resolvePatterns(Map<String, Long> executions) {
        Map<IPatternDetails, Long> resolved = new LinkedHashMap<>();
        // 実経路と同様に、コンパイル済みIDを同一世代の実Pattern参照へ戻す。
        for (Map.Entry<String, Long> entry : executions.entrySet()) {
            IPatternDetails pattern = PATTERNS_BY_ID.get(entry.getKey());
            // この固定テストグラフに未登録IDが混ざった場合は、曖昧な計画を作らず失敗させる。
            if (pattern == null) {
                throw new IllegalStateException("unresolved test pattern: " + entry.getKey());
            }
            resolved.put(pattern, entry.getValue());
        }
        return Map.copyOf(resolved);
    }

    private static Map<IPatternDetails, BigInteger> resolveBigPatterns(
            Map<String, BigInteger> executions) {
        Map<IPatternDetails, BigInteger> resolved = new LinkedHashMap<>();
        // BigInteger PlannerのPattern IDを、同じ仮想世代の実Pattern参照へ戻す。
        for (Map.Entry<String, BigInteger> entry : executions.entrySet()) {
            IPatternDetails pattern = PATTERNS_BY_ID.get(entry.getKey());
            // 未登録Patternを黙って落とすと不足計画の容量と表示がずれるため、試験を失敗させる。
            if (pattern == null) {
                throw new IllegalStateException("unresolved test pattern: " + entry.getKey());
            }
            resolved.put(pattern, entry.getValue());
        }
        return Map.copyOf(resolved);
    }

    private static KeyCounter keyCounter(Map<AEKey, Long> counts) {
        KeyCounter counter = new KeyCounter();
        // Plannerの正確なlong会計を、AE2互換FacadeのKeyCounterへ写す。
        for (Map.Entry<AEKey, Long> entry : counts.entrySet()) {
            counter.add(entry.getKey(), entry.getValue());
        }
        return counter;
    }

    /** Patternの識別だけを試すため、実レシピ処理を持たないテスト用定義。 */
    private static final class TestPattern implements IPatternDetails {
        private final String id;

        private TestPattern(String id) {
            this.id = id;
        }

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

        @Override
        public String toString() {
            return id;
        }
    }

    /** Minecraft Registryを起動せずKeyCounter変換を検証する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final String path;

        private TestKey(String path) {
            this.path = path;
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
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2_crafting_optimizer", path);
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf buffer) {
            // この試験はネットワーク同期を行わないため書き込みは不要。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(path);
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
            // この試験はワールド内ドロップを作らないため処理は不要。
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}
