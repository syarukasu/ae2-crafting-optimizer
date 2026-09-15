package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.syaru.ae2craftingoptimizer.access.CraftingCalculationThreadAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.mixin.KeyCounterCalculationIndexMixin;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.IConfigSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Calls the actual Mixin handler against real AE2 counters; transformation is a separate runtime check. */
class CalculationIndexReuseTest {
    private ModConfigSpec spec;
    private IConfigSpec.ILoadedConfig previousConfig;
    private ModConfigSpec.BooleanValue enabled;
    private final Object calculation = new Object();

    @BeforeEach void setup() throws Exception {
        spec = (ModConfigSpec) field(ACOConfig.class, "SPEC").get(null);
        previousConfig = (IConfigSpec.ILoadedConfig) field(ModConfigSpec.class, "loadedConfig").get(spec);
        var defaults = CommentedConfig.inMemory();
        spec.correct(defaults);
        var loaded = Class.forName("net.neoforged.fml.config.LoadedConfig")
                .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        loaded.setAccessible(true);
        spec.acceptConfig((IConfigSpec.ILoadedConfig) loaded.newInstance(defaults, null, null));
        enabled = (ModConfigSpec.BooleanValue) field(ACOConfig.class, "MEMOIZE_CRAFTING_CALCULATION_QUERIES").get(null);
    }

    @AfterEach void cleanup() {
        CraftingCalculationMemo.end(calculation);
        spec.acceptConfig(previousConfig);
    }

    @Test void existingIndexSkipsDurabilityButFirstIndexAndRemovedIndexUseAe2() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        var counter = new KeyCounter();
        var key = new TestKey(new Object());
        assertFalse(callback(counter, key).isCancelled());
        counter.add(key, 11);
        assertEquals(1, key.durabilityQueries);
        Object original = ((java.util.Map<?, ?>) field(KeyCounter.class, "lists").get(counter)).get(key.primary);
        for (int index = 0; index < 200; index++) {
            var result = callback(counter, key);
            assertTrue(result.isCancelled());
            assertSame(original, result.getReturnValue());
        }
        assertEquals(1, key.durabilityQueries);
        assertEquals(11, counter.get(key));
        counter.remove(key);
        int queriesAfterRemoval = key.durabilityQueries;
        assertFalse(callback(counter, key).isCancelled());
        counter.set(key, Long.MAX_VALUE);
        assertEquals(queriesAfterRemoval + 1, key.durabilityQueries);
        assertEquals(Long.MAX_VALUE, counter.get(key));
    }

    @Test void preservesDistinctSecondaryKeysAndPrimaryIdentity() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        Object primary = new String("same-id");
        var first = new TestKey(primary);
        var second = new TestKey(primary);
        var unrelated = new TestKey(new String("same-id"));
        var counter = new KeyCounter();
        counter.add(first, 123);
        counter.add(second, 456);
        assertSame(callback(counter, first).getReturnValue(), callback(counter, second).getReturnValue());
        assertFalse(callback(counter, unrelated).isCancelled());
        assertEquals(123, counter.get(first));
        assertEquals(456, counter.get(second));
        assertEquals(2, counter.size());
    }

    @Test void retainsTheFirstFuzzyIndexWhenDurabilityRulesChange() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        var key = new TestKey(new Object());
        key.maxDurability = 100;
        var counter = new KeyCounter();
        counter.add(key, 5);
        var fuzzy = callback(counter, key).getReturnValue();
        assertEquals("FuzzyVariantMap", fuzzy.getClass().getSimpleName());
        key.maxDurability = 0;
        assertSame(fuzzy, callback(counter, key).getReturnValue());
        assertEquals(1, key.durabilityQueries);
        assertEquals(5, counter.get(key));
        counter.clear();
        counter.removeEmptySubmaps();
        assertFalse(callback(counter, key).isCancelled());
        counter.add(key, 7);
        assertEquals("UnorderedVariantMap", callback(counter, key).getReturnValue().getClass().getSimpleName());
        assertEquals(2, key.durabilityQueries);
    }

    @Test void disabledAndFinishedCalculationsLeaveNormalCountersUntouched() throws Exception {
        var key = new TestKey(new Object());
        var counter = new KeyCounter();
        counter.add(key, 1);
        assertFalse(callback(counter, key).isCancelled());
        CraftingCalculationMemo.begin(calculation);
        assertTrue(callback(counter, key).isCancelled());
        enabled.set(false);
        assertFalse(callback(counter, key).isCancelled());
        enabled.set(true);
        CraftingCalculationMemo.end(calculation);
        assertFalse(callback(counter, key).isCancelled());
        assertEquals(1, counter.get(key));
    }

    @Test void checkpointsAreCalculationLocalAndDoNotLeakIntoCpuOrServerThreads() throws Exception {
        class Owner implements CraftingCalculationThreadAccess {
            int checkpoints;
            public void aco$checkpointIngredientSearch() { checkpoints++; }
            public boolean aco$runPatternLookup(Runnable task) { throw new AssertionError(); }
            public void aco$observeCraftingService(ICraftingService service) { throw new AssertionError(); }
        }
        var owner = new Owner();
        CraftingCalculationMemo.checkpointIngredientSearch();
        CraftingCalculationMemo.begin(owner);
        try {
            CraftingCalculationMemo.checkpointIngredientSearch();
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var server = new Thread(() -> {
                try { CraftingCalculationMemo.checkpointIngredientSearch(); }
                catch (Throwable thrown) { failure.set(thrown); }
            });
            server.start();
            server.join(5000);
            assertFalse(server.isAlive());
            assertNull(failure.get());
            assertEquals(1, owner.checkpoints);
            enabled.set(false);
            CraftingCalculationMemo.checkpointIngredientSearch();
            assertEquals(1, owner.checkpoints);
        } finally {
            CraftingCalculationMemo.end(owner);
        }
        assertFalse(CraftingCalculationMemo.isActive());
    }

    private static CallbackInfoReturnable<Object> callback(KeyCounter counter, AEKey key) throws Exception {
        var mixin = new KeyCounterCalculationIndexMixin();
        field(KeyCounterCalculationIndexMixin.class, "lists").set(mixin, field(KeyCounter.class, "lists").get(counter));
        Method handler = KeyCounterCalculationIndexMixin.class.getDeclaredMethod(
                "aco$reuseExistingCalculationIndex", AEKey.class, CallbackInfoReturnable.class);
        handler.setAccessible(true);
        var result = new CallbackInfoReturnable<Object>("getSubIndex", true);
        handler.invoke(mixin, key, result);
        return result;
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class TestKey extends AEKey {
        final Object primary;
        int durabilityQueries;
        int maxDurability;
        TestKey(Object primary) { this.primary = primary; }
        @Override public int getFuzzySearchMaxValue() { durabilityQueries++; return maxDurability; }
        @Override public Object getPrimaryKey() { return primary; }
        @Override public AEKeyType getType() { return null; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag(HolderLookup.Provider registries) { return new CompoundTag(); }
        @Override public ResourceLocation getId() { return ResourceLocation.fromNamespaceAndPath("aco", "test"); }
        @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) { }
        @Override public boolean hasComponents() { return false; }
        @Override protected Component computeDisplayName() { return Component.literal("test"); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
    }
}
