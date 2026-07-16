package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Settings;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.util.IConfigManager;
import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.core.definitions.AEItems;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IOPortBlockEntity.class, remap = false)
public abstract class IOPortIncrementalProcessingMixin {
    @Shadow
    @Final
    private appeng.util.inv.AppEngInternalInventory inputCells;

    @Shadow
    @Final
    private IUpgradeInventory upgrades;

    @Shadow
    public abstract IConfigManager getConfigManager();

    @Shadow
    public abstract boolean matchesFullnessMode(StorageCell inventory);

    @Shadow
    private long transferContents(IGrid grid, StorageCell inventory, long itemsToMove) {
        throw new AssertionError();
    }

    @Shadow
    private boolean moveSlot(int slot) {
        throw new AssertionError();
    }

    @Unique
    private int aco$nextCellSlot;

    @Inject(method = "tickingRequest", at = @At("HEAD"), cancellable = true)
    private void aco$processCellSlotsIncrementally(
            IGridNode node, int ticksSinceLastCall, CallbackInfoReturnable<TickRateModulation> cir) {
        if (!ACOConfig.incrementalIoPortProcessing()) {
            return;
        }

        IOPortBlockEntity self = (IOPortBlockEntity) (Object) this;
        if (!self.getMainNode().isActive()) {
            cir.setReturnValue(TickRateModulation.IDLE);
            return;
        }

        IGrid grid = self.getMainNode().getGrid();
        if (grid == null) {
            cir.setReturnValue(TickRateModulation.IDLE);
            return;
        }

        long itemsToMove = 256L;
        itemsToMove <<= Math.min(3, upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));

        TickRateModulation result = TickRateModulation.SLEEP;
        int slotsToInspect = ACOConfig.getIoPortCellSlotsPerTick();
        int start = Math.floorMod(aco$nextCellSlot, 6);
        for (int offset = 0; offset < slotsToInspect; offset++) {
            int slot = (start + offset) % 6;
            StorageCell cellInventory = StorageCells.getCellInventory(inputCells.getStackInSlot(slot), null);
            if (cellInventory == null) {
                moveSlot(slot);
                continue;
            }

            if (itemsToMove > 0) {
                itemsToMove = transferContents(grid, cellInventory, itemsToMove);
                result = itemsToMove > 0 ? TickRateModulation.IDLE : TickRateModulation.URGENT;
            }

            if (itemsToMove > 0 && matchesFullnessMode(cellInventory) && moveSlot(slot)) {
                result = TickRateModulation.URGENT;
            }
        }

        aco$nextCellSlot = (start + slotsToInspect) % 6;
        if (result == TickRateModulation.SLEEP && !inputCells.isEmpty()) {
            result = TickRateModulation.IDLE;
        }
        cir.setReturnValue(result);
    }
}
