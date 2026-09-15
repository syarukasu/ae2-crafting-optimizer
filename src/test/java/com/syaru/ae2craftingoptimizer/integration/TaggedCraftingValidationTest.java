package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import java.lang.reflect.Field;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Issue #179: exercise real AEItemKey equality, including NBT, in the calculation scope. */
class TaggedCraftingValidationTest {
    private ForgeConfigSpec spec;
    private ForgeConfigSpec.BooleanValue enabled;
    private final Object calculation = new Object();

    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        var initialized = field(Bootstrap.class, "isBootstrapped");
        // Item class initialization suffices here; do not repeat global registry population.
        initialized.setBoolean(null, true);
    }

    @BeforeEach void setup() throws Exception {
        spec = (ForgeConfigSpec) field(ACOConfig.class, "SPEC").get(null);
        spec.setConfig(CommentedConfig.inMemory());
        enabled = (ForgeConfigSpec.BooleanValue) field(ACOConfig.class,
                "MEMOIZE_CRAFTING_CALCULATION_QUERIES").get(null);
    }

    @AfterEach void cleanup() {
        CraftingCalculationMemo.end(calculation);
        spec.setConfig(null);
    }

    @Test void fullKeySlotAndPatternAreIndependentAndFalseIsCached() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        var recipe = recipe();
        var pattern = new Object();
        assertNull(get(pattern, recipe, 0, key(1)));
        put(pattern, recipe, 0, key(1), true);
        assertEquals(Boolean.TRUE, get(pattern, recipe, 0, key(1)));
        assertNull(get(pattern, recipe, 0, key(2)));
        assertNull(get(pattern, recipe, 1, key(1)));
        assertNull(get(new Object(), recipe, 0, key(1)));
        put(pattern, recipe, 0, key(2), false);
        assertEquals(Boolean.FALSE, get(pattern, recipe, 0, key(2)));
        assertEquals(Boolean.TRUE, get(pattern, recipe, 0, key(1)));
    }

    @Test void disabledEndedAndReloadedCalculationsDoNotReuseResults() throws Exception {
        var recipe = recipe();
        var pattern = new Object();
        put(pattern, recipe, 0, key(1), true);
        assertNull(get(pattern, recipe, 0, key(1)));
        CraftingCalculationMemo.begin(calculation);
        put(pattern, recipe, 0, key(1), false);
        enabled.set(false);
        assertNull(get(pattern, recipe, 0, key(1)));
        enabled.set(true);
        RecipeGenerationTracker.invalidate();
        assertNull(get(pattern, recipe, 0, key(1)));
        put(pattern, recipe, 0, key(1), true);
        CraftingCalculationMemo.end(calculation);
        assertNull(get(pattern, recipe, 0, key(1)));
        CraftingCalculationMemo.begin(calculation);
        assertNull(get(pattern, recipe, 0, key(1)));
    }

    @Test void vanillaNonTaggedCacheAndUnknownRecipesStayOwnedByAe2() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        var pattern = new Object();
        var vanilla = recipe();
        var plain = AEItemKey.of(Items.STONE);
        put(pattern, vanilla, 0, plain, true);
        assertNull(get(pattern, vanilla, 0, plain));
        var dynamic = new ShapelessRecipe(id(), "", CraftingBookCategory.MISC,
                new ItemStack(Items.STICK), ingredients()) { };
        put(pattern, dynamic, 0, key(1), true);
        assertNull(get(pattern, dynamic, 0, key(1)));
        assertNull(get(pattern, vanilla, 0, null));
    }

    @Test void anotherThreadNeverSeesTheCurrentCalculationsCache() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        var pattern = new Object();
        var recipe = recipe();
        put(pattern, recipe, 0, key(1), true);
        var result = new java.util.concurrent.atomic.AtomicReference<Object>();
        var thread = new Thread(() -> {
            try { result.set(get(pattern, recipe, 0, key(1))); }
            catch (Exception e) { result.set(e); }
        });
        thread.start();
        thread.join(5000);
        assertFalse(thread.isAlive());
        assertNull(result.get());
        assertEquals(Boolean.TRUE, get(pattern, recipe, 0, key(1)));
    }

    @Test void oneThousandIdenticalCandidatesNeedOneInitialValidation() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        var pattern = new Object();
        var recipe = recipe();
        int evaluations = 0;
        for (int i = 0; i < 1000; i++) {
            if (get(pattern, recipe, 0, key(1)) == null) {
                evaluations++;
                put(pattern, recipe, 0, key(1), true);
            }
        }
        assertEquals(1, evaluations);
        com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker.clear();
        assertNull(get(pattern, recipe, 0, key(1)));
    }

    @Test void customIngredientIsNotEligibleEvenInAnOtherwiseVanillaRecipe() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        var custom = new Ingredient(java.util.stream.Stream.of(
                new Ingredient.ItemValue(new ItemStack(Items.STONE)))) { };
        var recipe = new ShapelessRecipe(id(), "", CraftingBookCategory.MISC,
                new ItemStack(Items.STICK), NonNullList.of(Ingredient.EMPTY, custom));
        var pattern = new Object();
        put(pattern, recipe, 0, key(1), true);
        assertNull(get(pattern, recipe, 0, key(1)));
    }

    @Test void shapedVanillaRecipeIsEligible() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        var shaped = new net.minecraft.world.item.crafting.ShapedRecipe(id(), "", CraftingBookCategory.MISC,
                1, 1, ingredients(), new ItemStack(Items.STICK));
        var pattern = new Object();
        put(pattern, shaped, 0, key(1), true);
        assertEquals(Boolean.TRUE, get(pattern, shaped, 0, key(1)));
    }

    @Test void actualMixinHandlersPreserveAnExistingAe2Result() throws Exception {
        CraftingCalculationMemo.begin(calculation);
        var type = com.syaru.ae2craftingoptimizer.mixin.CraftingPatternTaggedValidationMixin.class;
        var mixin = type.getConstructor().newInstance();
        field(type, "recipe").set(mixin, recipe());
        var read = type.getDeclaredMethod("aco$reuseTaggedValidation", int.class, AEItemKey.class,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable.class);
        var write = type.getDeclaredMethod("aco$rememberTaggedValidation", int.class, AEItemKey.class,
                boolean.class, org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        read.setAccessible(true);
        write.setAccessible(true);
        var miss = new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>("test", true);
        read.invoke(mixin, 0, key(1), miss);
        assertFalse(miss.isCancelled());
        write.invoke(mixin, 0, key(1), false,
                new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("test", false));
        var hit = new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>("test", true);
        read.invoke(mixin, 0, key(1), hit);
        assertEquals(Boolean.FALSE, hit.getReturnValue());
        var nativeResult = new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>(
                "test", true, Boolean.TRUE);
        read.invoke(mixin, 0, key(1), nativeResult);
        assertEquals(Boolean.TRUE, nativeResult.getReturnValue());
        assertFalse(nativeResult.isCancelled());
    }

    private static Boolean get(Object pattern, CraftingRecipe recipe, int slot, AEItemKey key) throws Exception {
        return (Boolean) CraftingCalculationMemo.class.getMethod("taggedCraftingResult",
                Object.class, CraftingRecipe.class, int.class, AEItemKey.class).invoke(null, pattern, recipe, slot, key);
    }

    private static void put(Object pattern, CraftingRecipe recipe, int slot, AEItemKey key, boolean value)
            throws Exception {
        CraftingCalculationMemo.class.getMethod("rememberTaggedCraftingResult", Object.class,
                CraftingRecipe.class, int.class, AEItemKey.class, boolean.class)
                .invoke(null, pattern, recipe, slot, key, value);
    }

    private static ShapelessRecipe recipe() {
        return new ShapelessRecipe(id(), "", CraftingBookCategory.MISC,
                new ItemStack(Items.STICK), ingredients());
    }

    private static NonNullList<Ingredient> ingredients() {
        return NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE));
    }

    private static ResourceLocation id() { return new ResourceLocation("aco", "validation_test"); }

    @Test void multiOutputPlanningDoesNotMergeDifferentDamageOrNbt() {
        var source = new ItemStack(Items.IRON_PICKAXE);
        source.setDamageValue(7);
        source.getOrCreateTag().putString("owner", "first");
        var required = AEItemKey.of(source);
        var differentDamage = source.copy();
        differentDamage.setDamageValue(8);
        var differentNbt = source.copy();
        differentNbt.getOrCreateTag().putString("owner", "second");
        var root = AEItemKey.of(Items.STONE);
        var pattern = new com.syaru.ae2craftingoptimizer.engine.CompiledPattern<>("processing",
                java.util.List.of(new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.InputSlot<>(java.util.List.of(
                        new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.Stack<>(required, 2L)))),
                java.util.Map.of(root, 1L, AEItemKey.of(Items.STICK), 1L), true);
        var graph = com.syaru.ae2craftingoptimizer.engine.CompiledCraftingGraph.compile(1, java.util.List.of(pattern));
        var program = com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram
                .tryCompile(graph, root, ignored -> false).orElseThrow();
        var inventory = java.util.Map.of(required, 1L, AEItemKey.of(differentDamage), 100L,
                AEItemKey.of(differentNbt), 100L);
        var plan = program.planLong(1, program.captureLongInventory(key -> inventory.getOrDefault(key, 0L)),
                com.syaru.ae2craftingoptimizer.engine.PlanningGuard.none());
        assertEquals(java.util.Map.of(required, 1L), plan.usedInventory());
        assertEquals(java.util.Map.of(required, 1L), plan.missing());
    }

    private static AEItemKey key(int value) {
        var stack = new ItemStack(Items.STONE);
        stack.getOrCreateTag().putInt("variant", value);
        return AEItemKey.of(stack);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
