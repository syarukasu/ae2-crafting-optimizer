package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
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
    /** 再現報告で在庫に存在した鉄塊数。signed long内の境界値として使う。 */
    private static final long USED_AMOUNT = 8_600_000_000_000_000_000L;
    /** 再現報告で要求した鉄ブロック数。Pattern回数自体はsigned long内に収まる。 */
    private static final long PATTERN_EXECUTIONS = 106_000_000_000_000_000L;
    /** 再現報告相当のCPU容量。Long.MAX_VALUEを超えるためBigCapacity計画になる。 */
    private static final BigInteger EXACT_BYTES =
            new BigInteger("10700000000000000000");
    private static final TestKey INPUT = new TestKey("iron_ingot");
    private static final TestKey OUTPUT = new TestKey("iron_block");
    private static final IPatternDetails PATTERN = new TestPattern();

    @Test
    void exposesCapacityOnlyOverflowThroughPublicExactPlanView() {
        KeyCounter used = new KeyCounter();
        used.add(INPUT, USED_AMOUNT);
        KeyCounter emitted = new KeyCounter();
        emitted.add(OUTPUT, PATTERN_EXECUTIONS);
        BigCapacityCraftingPlan metadata = new BigCapacityCraftingPlan(
                new GenericStack(OUTPUT, PATTERN_EXECUTIONS),
                false,
                false,
                used,
                emitted,
                new KeyCounter(),
                Map.of(PATTERN, PATTERN_EXECUTIONS),
                EXACT_BYTES,
                0L,
                0L);

        CraftingPlan facade = Ae2CraftingPlanSidecars.expose(metadata);
        BigIntegerCraftingPlanView view =
                BigCraftingEngineApi.inspectAttachedExactPlan(facade).orElseThrow();

        assertFalse(view.simulation());
        assertEquals(EXACT_BYTES, view.exactBytes());
        assertEquals(BigInteger.valueOf(PATTERN_EXECUTIONS), view.patternTimes().get(PATTERN));
        assertEquals(BigInteger.valueOf(USED_AMOUNT), view.usedItems().get(INPUT));
        assertEquals(BigInteger.valueOf(PATTERN_EXECUTIONS), view.emittedItems().get(OUTPUT));
        assertEquals(Map.of(), view.missingItems());
    }

    @Test
    void doesNotInventSidecarForUnrelatedSaturatedAe2Plan() {
        CraftingPlan ordinary = new CraftingPlan(
                new GenericStack(OUTPUT, 1L),
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

    /** Patternの識別だけを試すため、実レシピ処理を持たないテスト用定義。 */
    private static final class TestPattern implements IPatternDetails {
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
