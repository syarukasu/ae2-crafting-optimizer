package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.RoundRobinSlotWindow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE2のIO Port搬送を残したまま、1tickに調べるセルスロットを巡回窓へ制限する。 */
@Mixin(value = IOPortBlockEntity.class, remap = false)
public abstract class IOPortSlotWindowMixin {
    /** AE2 IO Portが持つ入力セルスロット数。 */
    @Unique
    private static final int ACO$IO_PORT_SLOT_COUNT = 6;

    @Shadow
    @Final
    private AppEngInternalInventory inputCells;

    @Shadow
    private boolean moveSlot(int slot) {
        throw new AssertionError("Mixin shadow");
    }

    @Unique
    private int aco$cursor;

    @Unique
    private int aco$windowSize = ACO$IO_PORT_SLOT_COUNT;

    @Unique
    private boolean aco$windowEnabled;

    @Inject(method = "tickingRequest", at = @At("HEAD"))
    private void aco$beginWindow(
            IGridNode node,
            int ticksSinceLastCall,
            CallbackInfoReturnable<TickRateModulation> cir) {
        aco$windowEnabled = ACOConfig.incrementalIoPortProcessing();
        aco$windowSize = aco$windowEnabled
                ? ACOConfig.getIoPortCellSlotsPerTick()
                : ACO$IO_PORT_SLOT_COUNT;
    }

    @ModifyConstant(
            method = "tickingRequest",
            constant = @Constant(intValue = ACO$IO_PORT_SLOT_COUNT),
            require = 1)
    private int aco$limitInspectedSlots(int original) {
        return aco$windowSize;
    }

    @Redirect(
            method = "tickingRequest",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/util/inv/AppEngInternalInventory;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"),
            require = 1)
    private ItemStack aco$readWindowSlot(AppEngInternalInventory inventory, int windowIndex) {
        return inventory.getStackInSlot(aco$mapSlot(windowIndex));
    }

    @Redirect(
            method = "tickingRequest",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/blockentity/storage/IOPortBlockEntity;moveSlot(I)Z"),
            require = 2)
    private boolean aco$moveWindowSlot(IOPortBlockEntity self, int windowIndex) {
        return moveSlot(aco$mapSlot(windowIndex));
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), cancellable = true)
    private void aco$finishWindow(
            IGridNode node,
            int ticksSinceLastCall,
            CallbackInfoReturnable<TickRateModulation> cir) {
        // 機能OFFまたは全スロット走査時はAE2の戻り値と順序を維持する。
        if (!aco$windowEnabled || aco$windowSize >= ACO$IO_PORT_SLOT_COUNT) {
            return;
        }
        aco$cursor = RoundRobinSlotWindow.advance(
                aco$cursor,
                aco$windowSize,
                ACO$IO_PORT_SLOT_COUNT);
        // 未走査スロットが残る間にSLEEPすると永久待機になるため、AE2へ再確認を依頼する。
        if (cir.getReturnValue() == TickRateModulation.SLEEP && !inputCells.isEmpty()) {
            cir.setReturnValue(TickRateModulation.IDLE);
        }
    }

    @Unique
    private int aco$mapSlot(int windowIndex) {
        // 全走査時はAE2の元のindexを保持する。
        if (!aco$windowEnabled) {
            return windowIndex;
        }
        return RoundRobinSlotWindow.map(
                aco$cursor,
                windowIndex,
                ACO$IO_PORT_SLOT_COUNT);
    }
}
