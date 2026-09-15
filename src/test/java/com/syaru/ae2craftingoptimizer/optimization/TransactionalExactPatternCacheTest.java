package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class TransactionalExactPatternCacheTest {
    private static final AEKey INPUT = new TestKey("input");
    private static final AEKey OUTPUT = new TestKey("output");

    @AfterEach
    void clearCache() {
        TransactionalExactPatternCache.clear();
    }

    @Test
    void reusesStaticMetadataWithinOneProviderGeneration() {
        PatternProbe probe = new PatternProbe(false);
        IPatternDetails pattern = probe.pattern();

        TransactionalExactPatternCache.Lookup first = TransactionalExactPatternCache.lookup(pattern);
        TransactionalExactPatternCache.Lookup second = TransactionalExactPatternCache.lookup(pattern);

        assertEquals(TransactionalExactPatternCache.State.SUPPORTED, first.state());
        assertEquals(TransactionalExactPatternCache.State.SUPPORTED, second.state());
        assertNotNull(first.pattern());
        assertEquals(1, probe.inputReads.get());
        assertEquals(1, probe.outputReads.get());
        // world・在庫依存の候補判定はcompile時に実行してはいけない。
        assertEquals(0, probe.validityChecks.get());
    }

    @Test
    void recompilesAfterProviderGenerationChanges() {
        PatternProbe probe = new PatternProbe(false);
        IPatternDetails pattern = probe.pattern();

        TransactionalExactPatternCache.lookup(pattern);
        ProviderPatternGenerationTracker.clear();
        TransactionalExactPatternCache.lookup(pattern);

        assertEquals(2, probe.inputReads.get());
        assertEquals(2, probe.outputReads.get());
    }

    @Test
    void marksPreviouslyReturnedLookupStaleAfterGenerationChange() {
        PatternProbe probe = new PatternProbe(false);
        TransactionalExactPatternCache.Lookup lookup =
                TransactionalExactPatternCache.lookup(probe.pattern());

        ProviderPatternGenerationTracker.clear();

        assertFalse(lookup.isCurrent());
    }

    @Test
    void tenThousandExecutionWavesCompileStaticMetadataOnce() {
        PatternProbe probe = new PatternProbe(false);
        // 10,000 waveを模擬し、注文回数ではなくPattern世代数に比例することを固定する。
        for (int wave = 0; wave < 10_000; wave++) {
            TransactionalExactPatternCache.lookup(probe.pattern());
        }

        assertEquals(1, probe.inputReads.get());
        assertEquals(1, probe.outputReads.get());
    }

    @Test
    void doesNotPublishMetadataCompiledAcrossGenerationChange() {
        PatternProbe probe = new PatternProbe(true);

        TransactionalExactPatternCache.Lookup unstable =
                TransactionalExactPatternCache.lookup(probe.pattern());
        TransactionalExactPatternCache.Lookup stable =
                TransactionalExactPatternCache.lookup(probe.pattern());

        assertEquals(TransactionalExactPatternCache.State.UNSTABLE, unstable.state());
        assertEquals(TransactionalExactPatternCache.State.SUPPORTED, stable.state());
        assertEquals(2, probe.inputReads.get());
    }

    @Test
    void uncachedCompilationDeclinesGenerationChange() {
        PatternProbe probe = new PatternProbe(true);

        TransactionalExactPatternCache.Lookup result =
                TransactionalExactPatternCache.compileUncached(probe.pattern());

        assertEquals(TransactionalExactPatternCache.State.UNSTABLE, result.state());
    }

    @Test
    void rejectsNonCraftingPatternsBeforeReadingTheirMetadata() {
        AtomicInteger reads = new AtomicInteger();
        IPatternDetails processingPattern = new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() {
                return null;
            }

            @Override
            public IInput[] getInputs() {
                reads.incrementAndGet();
                return new IInput[0];
            }

            @Override
            public GenericStack[] getOutputs() {
                reads.incrementAndGet();
                return new GenericStack[0];
            }
        };

        TransactionalExactPatternCache.Lookup result =
                TransactionalExactPatternCache.lookup(processingPattern);

        assertEquals(TransactionalExactPatternCache.State.UNSUPPORTED, result.state());
        assertEquals(0, reads.get());
    }

    private static final class PatternProbe {
        private final AtomicInteger inputReads = new AtomicInteger();
        private final AtomicInteger outputReads = new AtomicInteger();
        private final AtomicInteger validityChecks = new AtomicInteger();
        private final AtomicBoolean moveGenerationDuringFirstRead;
        private final IPatternDetails pattern;

        private PatternProbe(boolean moveGenerationDuringFirstRead) {
            this.moveGenerationDuringFirstRead = new AtomicBoolean(moveGenerationDuringFirstRead);
            IPatternDetails.IInput input = new IPatternDetails.IInput() {
                @Override
                public GenericStack[] getPossibleInputs() {
                    return new GenericStack[] {new GenericStack(INPUT, 2L)};
                }

                @Override
                public long getMultiplier() {
                    return 3L;
                }

                @Override
                public boolean isValid(AEKey key, Level level) {
                    validityChecks.incrementAndGet();
                    return true;
                }

                @Override
                public AEKey getRemainingKey(AEKey key) {
                    return null;
                }
            };
            pattern = new IMolecularAssemblerSupportedPattern() {
                @Override
                public AEItemKey getDefinition() {
                    return null;
                }

                @Override
                public IPatternDetails.IInput[] getInputs() {
                    inputReads.incrementAndGet();
                    // compile途中の世代変化を意図的に作り、stale metadataを公開しないことを確認する。
                    if (PatternProbe.this.moveGenerationDuringFirstRead.compareAndSet(true, false)) {
                        ProviderPatternGenerationTracker.clear();
                    }
                    return new IPatternDetails.IInput[] {input};
                }

                @Override
                public GenericStack[] getOutputs() {
                    outputReads.incrementAndGet();
                    return new GenericStack[] {new GenericStack(OUTPUT, 1L)};
                }

                @Override
                public ItemStack assemble(Container container, Level level) {
                    return ItemStack.EMPTY;
                }

                @Override
                public boolean isItemValid(int slot, AEItemKey key, Level level) {
                    return false;
                }

                @Override
                public boolean isSlotEnabled(int slot) {
                    return true;
                }

                @Override
                public void fillCraftingGrid(
                        appeng.api.stacks.KeyCounter[] inputs,
                        CraftingGridAccessor accessor) {
                }

                @Override
                public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
                    return NonNullList.create();
                }
            };
        }

        private IPatternDetails pattern() {
            return pattern;
        }
    }

    /** Minecraft Registryを起動せず、metadata数量だけを試験する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final String id;

        private TestKey(String id) {
            this.id = id;
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
            return ResourceLocation.fromNamespaceAndPath("ae2_crafting_optimizer", id);
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }
    }
}
