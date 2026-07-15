package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.core.sync.packets.MEInventoryUpdatePacket;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IncrementalUpdateHelper;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.DeepRangeUpdateHelper;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEInventoryUpdatePacket.Builder.class, remap = false)
public abstract class MEInventoryUpdatePacketBuilderRangeMixin {
    @Shadow
    @Nullable
    private AEKeyFilter filter;

    @Shadow
    public abstract void add(GridInventoryEntry entry);

    @Inject(method = "addChanges", at = @At("HEAD"), cancellable = true)
    private void aco$sendBoundedRange(
            IncrementalUpdateHelper updateHelper,
            KeyCounter networkStorage,
            Set<AEKey> craftables,
            KeyCounter requestables,
            CallbackInfo ci) {
        if (!ACOConfig.deepVisibleTerminalRangeSync()) {
            return;
        }

        var rangeHelper = (DeepRangeUpdateHelper) (Object) updateHelper;
        var changes = rangeHelper.aco$getMutableChanges();
        int maximumEntries = ACOConfig.getDeepTerminalRangeEntriesPerTick();
        int sentEntries = 0;

        var iterator = changes.iterator();
        while (iterator.hasNext() && sentEntries < maximumEntries) {
            AEKey key = iterator.next();
            if (filter != null && !filter.matches(key)) {
                iterator.remove();
                continue;
            }

            AEKey serializedKey;
            Long serial = updateHelper.getSerial(key);
            if (serial == null) {
                serializedKey = key;
                serial = updateHelper.getOrAssignSerial(key);
            } else {
                serializedKey = null;
            }

            long storedAmount = networkStorage.get(key);
            long requestableAmount = requestables.get(key);
            boolean craftable = craftables.contains(key);
            if (storedAmount <= 0 && requestableAmount <= 0 && !craftable) {
                add(new GridInventoryEntry(serial, serializedKey, 0, 0, false));
                updateHelper.removeSerial(key);
            } else {
                add(new GridInventoryEntry(
                        serial,
                        serializedKey,
                        storedAmount,
                        requestableAmount,
                        craftable));
            }

            iterator.remove();
            sentEntries++;
        }

        rangeHelper.aco$finishRangeBatch(!changes.isEmpty());
        ci.cancel();
    }
}
