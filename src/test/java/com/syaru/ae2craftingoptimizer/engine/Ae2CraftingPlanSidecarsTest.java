package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class Ae2CraftingPlanSidecarsTest {
    private static final TestKey TEST_KEY = new TestKey();
    private static final GenericStack FINAL_OUTPUT = new GenericStack(TEST_KEY, 1L);
    private static final KeyCounter USED = new KeyCounter();
    private static final KeyCounter EMITTED = new KeyCounter();
    private static final KeyCounter MISSING = new KeyCounter();

    @AfterEach
    void clearSidecars() {
        Ae2CraftingPlanSidecars.clearForTests();
    }

    @Test
    void exposesWideMetadataAsConcreteAe2CraftingPlan() {
        FakeWidePlan metadata = new FakeWidePlan("first");

        CraftingPlan exposed = Ae2CraftingPlanSidecars.expose(metadata);

        /*
         * 実環境で落ちたものと同じ具体型Castを実行する。
         * ACO独自Planが外へ漏れれば、この行がClassCastExceptionで失敗する。
         */
        CraftingPlan internalAe2Plan = (CraftingPlan) exposed;
        assertSame(exposed, internalAe2Plan);
        assertSame(
                metadata,
                Ae2CraftingPlanSidecars.metadata(internalAe2Plan).orElseThrow());
        assertEquals(Long.MAX_VALUE, internalAe2Plan.bytes());
    }

    @Test
    void keepsEqualFacadeRecordsSeparatedByIdentity() {
        FakeWidePlan firstMetadata = new FakeWidePlan("first");
        FakeWidePlan secondMetadata = new FakeWidePlan("second");

        CraftingPlan firstFacade = Ae2CraftingPlanSidecars.expose(firstMetadata);
        CraftingPlan secondFacade = Ae2CraftingPlanSidecars.expose(secondMetadata);

        // CraftingPlan recordの内容が同じでも、同時計算のSidecarは別Jobとして保持する。
        assertEquals(firstFacade, secondFacade);
        assertNotSame(firstFacade, secondFacade);
        assertSame(
                firstMetadata,
                Ae2CraftingPlanSidecars.metadata(firstFacade).orElseThrow());
        assertSame(
                secondMetadata,
                Ae2CraftingPlanSidecars.metadata(secondFacade).orElseThrow());
    }

    private record FakeWidePlan(String id) implements WideCraftingPlan {
        @Override
        public GenericStack finalOutput() {
            return FINAL_OUTPUT;
        }

        @Override
        public long bytes() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean simulation() {
            return false;
        }

        @Override
        public boolean multiplePaths() {
            return false;
        }

        @Override
        public KeyCounter usedItems() {
            return USED;
        }

        @Override
        public KeyCounter emittedItems() {
            return EMITTED;
        }

        @Override
        public KeyCounter missingItems() {
            return MISSING;
        }

        @Override
        public Map<IPatternDetails, Long> patternTimes() {
            return Map.of();
        }
    }

    /** GenericStackをMinecraft Registry初期化なしで作るための最小AEKey。 */
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
            return new ResourceLocation("ae2_crafting_optimizer", "sidecar_test");
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {
            // このテストはPacket同期を行わないため書き込みは不要。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal("ACO sidecar test");
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
            // このテストはワールド内ドロップを作らないため処理は不要。
        }
    }
}
