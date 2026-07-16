package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.core.AELog;
import appeng.me.storage.ExternalStorageFacade;
import appeng.parts.automation.HandlerStrategy;
import appeng.parts.automation.StorageImportStrategy;
import appeng.util.BlockApiCache;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StorageImportStrategy.class, remap = false)
public abstract class StorageImportLastSuccessfulSlotMixin<C, S> {
    @Shadow
    @Final
    private BlockApiCache<C> apiCache;
    @Shadow
    @Final
    private Direction fromSide;
    @Shadow
    @Final
    private HandlerStrategy<C, S> conversion;

    @Unique
    private int aco$lastSuccessfulSlot = -1;

    @Inject(method = "transfer", at = @At("HEAD"), cancellable = true)
    private void aco$tryLastSuccessfulSlotFirst(
            StackTransferContext context, CallbackInfoReturnable<Boolean> cir) {
        if (!ACOConfig.cacheImportBusLastSuccessfulSlot()
                || !context.isKeyTypeEnabled(conversion.getKeyType())) {
            return;
        }

        C adjacentHandler = apiCache.find(fromSide);
        if (adjacentHandler == null) {
            aco$lastSuccessfulSlot = -1;
            cir.setReturnValue(false);
            return;
        }

        ExternalStorageFacade adjacentStorage = conversion.getFacade(adjacentHandler);
        int slotCount = adjacentStorage.getSlots();
        if (aco$lastSuccessfulSlot >= slotCount) {
            aco$lastSuccessfulSlot = -1;
        }

        long remainingTransferAmount = context.getOperationsRemaining()
                * (long) conversion.getKeyType().getAmountPerOperation();
        var inventory = context.getInternalStorage().getInventory();

        for (int pass = -1; pass < slotCount && remainingTransferAmount > 0; pass++) {
            int slot = pass < 0 ? aco$lastSuccessfulSlot : pass;
            if (slot < 0 || (pass >= 0 && slot == aco$lastSuccessfulSlot)) {
                continue;
            }

            var resource = adjacentStorage.getStackInSlot(slot);
            if (resource == null || context.isInFilter(resource.what()) == context.isInverted()) {
                continue;
            }

            long insertable = inventory.insert(
                    resource.what(), remainingTransferAmount, Actionable.SIMULATE, context.getActionSource());
            long extracted = adjacentStorage.extract(
                    resource.what(), insertable, Actionable.MODULATE, context.getActionSource());
            if (extracted <= 0) {
                continue;
            }

            long inserted = inventory.insert(
                    resource.what(), extracted, Actionable.MODULATE, context.getActionSource());
            if (inserted < extracted) {
                long leftover = extracted - inserted;
                leftover -= adjacentStorage.insert(
                        resource.what(), leftover, Actionable.MODULATE, context.getActionSource());
                if (leftover > 0) {
                    AELog.warn("Extracted %dx%s from adjacent storage and voided it because network refused insert",
                            leftover, resource.what());
                }
            }

            long used = Math.max(1, inserted / conversion.getKeyType().getAmountPerOperation());
            context.reduceOperationsRemaining(used);
            remainingTransferAmount -= inserted;
            if (inserted > 0) {
                aco$lastSuccessfulSlot = slot;
            }
        }

        cir.setReturnValue(false);
    }
}
