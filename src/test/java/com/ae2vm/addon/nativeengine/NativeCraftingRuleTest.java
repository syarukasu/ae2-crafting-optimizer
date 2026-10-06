package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeCraftingRuleTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void shapedOffsetsMirrorAndNbtMatchRealAe2Input() throws Exception {
        var recipe = new ShapedRecipe("", CraftingBookCategory.MISC,
                new ShapedRecipePattern(2, 1, NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.PAPER), Ingredient.of(Items.GOLD_INGOT)), Optional.empty()),
                new ItemStack(Items.DIAMOND));
        var stacks = emptyGrid();
        stacks[4] = new ItemStack(Items.GOLD_INGOT);
        stacks[5] = new ItemStack(Items.PAPER);
        checkAgainstAe2(recipe, stacks);
    }

    @Test void overlappingShapelessIngredientsMatchRealAe2Input() throws Exception {
        var recipe = new ShapelessRecipe("", CraftingBookCategory.MISC,
                new ItemStack(Items.DIAMOND), NonNullList.of(Ingredient.EMPTY,
                        Ingredient.of(Items.PAPER, Items.GOLD_INGOT), Ingredient.of(Items.PAPER), Ingredient.of(Items.BOOK)));
        var stacks = emptyGrid();
        stacks[0] = new ItemStack(Items.GOLD_INGOT);
        stacks[3] = new ItemStack(Items.PAPER);
        stacks[8] = new ItemStack(Items.BOOK);
        checkAgainstAe2(recipe, stacks);
    }

    private void checkAgainstAe2(CraftingRecipe recipe, ItemStack[] stacks) throws Exception {
        var pattern = pattern(recipe, stacks);
        var level = mock(Level.class);
        var rules = NativeCraftingRule.capture(pattern, level);
        assertNotNull(rules);
        var inputs = pattern.getInputs();
        for (int i = 0; i < inputs.length; i++) {
            assertNotNull(rules[i]);
            for (var item : List.of(Items.PAPER, Items.GOLD_INGOT, Items.IRON_INGOT, Items.BOOK, Items.DIAMOND)) {
                for (int variant = 0; variant < 8; variant++) {
                    var stack = new ItemStack(item);
                    if (variant > 0) com.ae2vm.addon.PortStacks.putInt(stack, "variant", variant);
                    if (variant == 2) com.ae2vm.addon.PortStacks.putInt(stack, "Damage", 7);
                    if (variant == 3) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named input"));
                    if (variant == 4) {
                        var enchantments = new net.minecraft.nbt.ListTag();
                        var enchantment = new net.minecraft.nbt.CompoundTag();
                        enchantment.putString("id", "minecraft:mending"); enchantment.putShort("lvl", (short) 1);
                        enchantments.add(enchantment); com.ae2vm.addon.PortStacks.put(stack, "Enchantments", enchantments);
                    }
                    var key = AEItemKey.of(stack);
                    var decision = rules[i].evaluate(key);
                    if (decision == null) {
                        assertFalse(rules[i].covers(key), "Custom item returns remain opaque");
                        continue;
                    }
                    assertEquals(inputs[i].isValid(key, level), decision.accepted(),
                            "slot=" + i + " item=" + item + " variant=" + variant);
                    if (decision.accepted()) assertEquals(inputs[i].getRemainingKey(key), decision.remainder());
                }
            }
        }
    }

    @Test void unknownReturnedNbtUsesFrozenRuleWithWorldBoundaryClosed() throws Exception {
        var recipe = new ShapedRecipe("", CraftingBookCategory.MISC,
                new ShapedRecipePattern(2, 1, NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.BOOK), Ingredient.of(Items.PAPER)), Optional.empty()),
                new ItemStack(Items.DIAMOND));
        var stacks = emptyGrid();
        stacks[0] = new ItemStack(Items.BOOK);
        stacks[1] = new ItemStack(Items.PAPER);
        var pattern = pattern(recipe, stacks);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var paper = new ItemStack(Items.PAPER);
        com.ae2vm.addon.PortStacks.putInt(paper, "new_state", 27);
        var returned = AEItemKey.of(paper);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(iron, 1)});
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(any(), any())).thenAnswer(call -> iron.equals(call.getArgument(0)));
        when(input.getRemainingKey(iron)).thenReturn(returned);
        var producer = mock(IPatternDetails.class);
        when(producer.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        when(producer.getOutputs()).thenReturn(java.util.Arrays.asList(new GenericStack[]{new GenericStack(AEItemKey.of(Items.BOOK), 1)}));
        var grid = mock(IGrid.class);
        var service = mock(ICraftingService.class);
        var requester = mock(ICraftingSimulationRequester.class);
        var node = mock(IGridNode.class);
        when(grid.getCraftingService()).thenReturn(service);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        var output = AEItemKey.of(Items.DIAMOND);
        when(service.getCraftingFor(output)).thenReturn(List.of(pattern));
        when(service.getCraftingFor(AEItemKey.of(Items.BOOK))).thenReturn(List.of(producer));
        var access = new NativeVmAccess();
        var level = mock(Level.class);
        var context = new NativeVmPlanningContext(grid, level, requester,
                new VmAccounting.Stock(Map.of(iron, BigInteger.ONE), true, Set.of()), access, new NativeVmTiming());
        context.prepare(output);
        assertFalse(context.requiresLiveAccess());
        access.finish();
        when(level.registryAccess()).thenThrow(new AssertionError("No live world after capture"));
        var pool = Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit(() -> context.plan(output, BigInteger.ONE, false, ignored -> { })).get(5, TimeUnit.SECONDS);
            assertEquals(Map.of(iron, BigInteger.ONE), result.used());
            assertTrue(result.missing().isEmpty());
            assertEquals(2, result.crafts().size());
            assertEquals(0, access.metrics().reads());
        } finally { pool.shutdownNow(); }
    }

    @Test void customRecipeCannotBeInferredFromItsIngredientList() throws Exception {
        var recipe = mock(CraftingRecipe.class);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.PAPER)));
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.DIAMOND));
        var stacks = emptyGrid(); stacks[0] = new ItemStack(Items.PAPER);
        assertNull(NativeCraftingRule.capture(pattern(recipe, stacks), mock(Level.class)));
    }

    private static ItemStack[] emptyGrid() {
        var grid = new ItemStack[9]; Arrays.fill(grid, ItemStack.EMPTY); return grid;
    }

    private static AECraftingPattern pattern(CraftingRecipe recipe, ItemStack[] stacks) throws Exception {
        var pattern = mock(AECraftingPattern.class, CALLS_REAL_METHODS);
        set(pattern, "canSubstitute", true);
        set(pattern, "recipe", recipe);
        var output = recipe.getResultItem(null);
        set(pattern, "output", output);
        var sparse = new GenericStack[9];
        var frame = new net.minecraft.world.inventory.TransientCraftingContainer(new appeng.menu.AutoCraftingMenu(), 3, 3);
        for (int i = 0; i < 9; i++) {
            sparse[i] = GenericStack.fromItemStack(stacks[i]); frame.setItem(i, stacks[i].copy());
        }
        set(pattern, "sparseInputs", Arrays.asList(sparse));
        set(pattern, "positionedPattern", net.minecraft.world.item.crafting.CraftingInput.ofPositioned(3, 3, Arrays.asList(stacks)));
        set(pattern, "isValidCache", new Map[9]);
        var type = Class.forName("appeng.crafting.pattern.AECraftingPattern$Input");
        var constructor = type.getDeclaredConstructor(AECraftingPattern.class, int.class, GenericStack.class);
        constructor.setAccessible(true);
        var inputs = new ArrayList<IPatternDetails.IInput>();
        try (var ignored = mockStatic(appeng.api.behaviors.ContainerItemStrategies.class)) {
            for (int i = 0; i < 9; i++) if (sparse[i] != null)
                inputs.add((IPatternDetails.IInput) constructor.newInstance(pattern, i, sparse[i]));
        }
        doReturn(inputs.toArray(IPatternDetails.IInput[]::new)).when(pattern).getInputs();
        doReturn(List.of(GenericStack.fromItemStack(output))).when(pattern).getOutputs();
        return pattern;
    }

    private static void set(AECraftingPattern pattern, String name, Object value) throws Exception {
        var field = AECraftingPattern.class.getDeclaredField(name); field.setAccessible(true); field.set(pattern, value);
    }
}
