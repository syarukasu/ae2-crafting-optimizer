package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.stacks.AEItemKey;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.extensions.IForgeItem;

/** Pure native remainder metadata only; never stores stock or mutable stacks. */
public final class CraftingExecutionRemainders {
    private CraftingExecutionRemainders() {}

    private static final String NATIVE_REMAINDER_PREDICATE = remainderPredicateName();

    private static final ClassValue<Boolean> NATIVE_ITEM = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                if (type.getMethod("hasCraftingRemainingItem", ItemStack.class).getDeclaringClass() != IForgeItem.class
                        || type.getMethod("getCraftingRemainingItem", ItemStack.class).getDeclaringClass() != IForgeItem.class
                        || type.getMethod("initCapabilities", ItemStack.class, net.minecraft.nbt.CompoundTag.class)
                                .getDeclaringClass() != IForgeItem.class) {
                    return false;
                }
                return NATIVE_REMAINDER_PREDICATE != null
                        && type.getMethod(NATIVE_REMAINDER_PREDICATE).getDeclaringClass() == Item.class;
            } catch (NoSuchMethodException absent) {
                return false;
            }
        }
    };

    private static String remainderPredicateName() {
        // Issue #249: official name and audited Forge 1.20.1 SRG name, not item IDs.
        for (String name : new String[]{"hasCraftingRemainingItem", "m_41470_"}) {
            try {
                if (Item.class.getMethod(name).getReturnType() == boolean.class) return name;
            } catch (NoSuchMethodException absent) { }
        }
        return null;
    }

    public static boolean nativeItem(Item item) {
        return NATIVE_ITEM.get(item.getClass());
    }

    public static boolean nativeRecipe(CraftingRecipe recipe) {
        return recipe != null && (recipe.getClass() == ShapedRecipe.class || recipe.getClass() == ShapelessRecipe.class);
    }

    public static boolean nativeFrame(CraftingRecipe recipe, CraftingContainer frame, int selectedSlot) {
        if (!nativeRecipe(recipe) || frame == null || frame.getClass() != TransientCraftingContainer.class
                || selectedSlot < 0 || selectedSlot >= frame.getContainerSize()) {
            return false;
        }
        // Issue #249: native exceptions can leave a substitute in this mutable frame.
        for (int slot = 0; slot < frame.getContainerSize(); slot++) {
            if (slot == selectedSlot) continue;
            ItemStack stack = frame.getItem(slot);
            if (!stack.isEmpty() && !nativeItem(stack.getItem())) return false;
        }
        return true;
    }

    public static ItemStack remainder(AEItemKey selected) {
        Item remainder = selected.getItem().getCraftingRemainingItem();
        return remainder == null ? ItemStack.EMPTY : new ItemStack(remainder);
    }
}

