package com.ae2vm.addon;

import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import java.util.ArrayList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Port the same fixture identities and encoded patterns to data components. */
public final class PortStacks {
    public static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private PortStacks() { }
    public static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
    public static void putInt(ItemStack stack, String key, int value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(key, value));
    }
    public static void putString(ItemStack stack, String key, String value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(key, value));
    }
    public static void put(ItemStack stack, String key, Tag value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(key, value));
        var data = tag(stack);
        if ((key.equals("in") || key.equals("out")) && data.contains("in") && data.contains("out")) {
            var inputs = new ArrayList<GenericStack>();
            var outputs = new ArrayList<GenericStack>();
            for (var entry : data.getList("in", Tag.TAG_COMPOUND)) inputs.add(GenericStack.readTag(REGISTRIES, (CompoundTag) entry));
            for (var entry : data.getList("out", Tag.TAG_COMPOUND)) outputs.add(GenericStack.readTag(REGISTRIES, (CompoundTag) entry));
            AEProcessingPattern.encode(stack, inputs, outputs);
        }
    }
}
