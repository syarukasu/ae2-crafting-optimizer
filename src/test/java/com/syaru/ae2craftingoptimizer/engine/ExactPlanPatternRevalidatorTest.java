package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ExactPlanPatternRevalidatorTest {
    private static final long PLANNED_PATTERN_GENERATION = 10L;
    private static final long UPDATED_PATTERN_GENERATION = 11L;
    private static final long RECIPE_GENERATION = 20L;

    @Test
    void acceptsMatchingGenerationWithoutIndexScan() {
        StubPattern pattern = new StubPattern(new TestKey("iron_block"));
        AtomicInteger lookups = new AtomicInteger();

        var result = ExactPlanPatternRevalidator.validate(
                PLANNED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                PLANNED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                List.of(pattern),
                ignored -> {
                    lookups.incrementAndGet();
                    return List.of();
                });

        assertEquals(ExactPlanPatternRevalidator.Result.CURRENT_GENERATION, result);
        assertEquals(0, lookups.get());
    }

    @Test
    void acceptsUnrelatedProviderGenerationWhenReferencedPatternRemains() {
        StubPattern pattern = new StubPattern(new TestKey("iron_block"));

        var result = ExactPlanPatternRevalidator.validate(
                PLANNED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                UPDATED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                List.of(pattern),
                ignored -> List.of(pattern));

        assertEquals(
                ExactPlanPatternRevalidator.Result.REFERENCED_PATTERNS_REVALIDATED,
                result);
    }

    @Test
    void acceptsProviderGenerationChangeForInventoryOnlyPlan() {
        AtomicInteger lookups = new AtomicInteger();

        var result = ExactPlanPatternRevalidator.validate(
                PLANNED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                UPDATED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                List.of(),
                ignored -> {
                    lookups.incrementAndGet();
                    return List.of();
                });

        assertEquals(
                ExactPlanPatternRevalidator.Result.REFERENCED_PATTERNS_REVALIDATED,
                result);
        assertEquals(0, lookups.get());
    }

    @Test
    void rejectsEqualLookingReplacementPattern() {
        TestKey output = new TestKey("iron_block");
        StubPattern planned = new StubPattern(output);
        StubPattern replacement = new StubPattern(output);

        var result = ExactPlanPatternRevalidator.validate(
                PLANNED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                UPDATED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                List.of(planned),
                ignored -> List.of(replacement));

        assertEquals(
                ExactPlanPatternRevalidator.Result.REFERENCED_PATTERN_CHANGED,
                result);
    }

    @Test
    void rejectsMissingReferencedPattern() {
        StubPattern pattern = new StubPattern(new TestKey("iron_block"));

        var result = ExactPlanPatternRevalidator.validate(
                PLANNED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                UPDATED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                List.of(pattern),
                ignored -> List.of());

        assertEquals(
                ExactPlanPatternRevalidator.Result.REFERENCED_PATTERN_CHANGED,
                result);
    }

    @Test
    void rejectsRecipeGenerationChangeWithoutIndexScan() {
        StubPattern pattern = new StubPattern(new TestKey("iron_block"));
        AtomicInteger lookups = new AtomicInteger();

        var result = ExactPlanPatternRevalidator.validate(
                PLANNED_PATTERN_GENERATION,
                RECIPE_GENERATION,
                UPDATED_PATTERN_GENERATION,
                RECIPE_GENERATION + 1L,
                List.of(pattern),
                ignored -> {
                    lookups.incrementAndGet();
                    return List.of(pattern);
                });

        assertEquals(
                ExactPlanPatternRevalidator.Result.RECIPE_GENERATION_CHANGED,
                result);
        assertEquals(0, lookups.get());
    }

    private static final class StubPattern implements IPatternDetails {
        private final GenericStack[] outputs;

        private StubPattern(AEKey output) {
            this.outputs = new GenericStack[] {new GenericStack(output, 1L)};
        }

        @Override
        public appeng.api.stacks.AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public GenericStack[] getOutputs() {
            return outputs.clone();
        }
    }

    /** Minecraftレジストリを起動せず、Pattern主出力の識別だけを行う最小AEKey。 */
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
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("ae2_crafting_optimizer", path);
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {
            // この試験はネットワーク同期を行わない。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(path);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
            // この試験はワールド内ドロップを作らない。
        }
    }
}
