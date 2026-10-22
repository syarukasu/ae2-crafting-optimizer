package appeng.crafting.pattern;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Uses AE2's actual NBT encoder without requiring mod item registration in a unit test. */
public final class ProcessingTestPatterns {
    private ProcessingTestPatterns() {}

    public static AEProcessingPattern encode(GenericStack[] inputs, GenericStack[] outputs) {
        var definition = new ItemStack(Items.PAPER);
        ProcessingPatternEncoding.encodeProcessingPattern(definition.getOrCreateTag(), inputs, outputs);
        return new AEProcessingPattern(AEItemKey.of(definition));
    }
}
