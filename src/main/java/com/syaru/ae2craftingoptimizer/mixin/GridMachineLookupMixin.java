package com.syaru.ae2craftingoptimizer.mixin;

import appeng.me.Grid;
import com.google.common.collect.SetMultimap;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.GridMachineLookup;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Grid.class, remap = false)
public class GridMachineLookupMixin {
    @Unique private final GridMachineLookup aco$machineOwners = new GridMachineLookup();

    @Inject(method = "getMachines", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$reuseMachineOwners(Class<?> type, CallbackInfoReturnable<Set<?>> cir) {
        if (aco$machineOwners == null || !ACOConfig.enableCountedProcessingTransfers()) return;
        var cached = aco$machineOwners.cached(type);
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(method = "getMachines", at = @At("RETURN"), require = 1)
    private void aco$rememberMachineOwners(Class<?> type, CallbackInfoReturnable<Set<?>> cir) {
        if (aco$machineOwners != null && ACOConfig.enableCountedProcessingTransfers())
            aco$machineOwners.remember(type, cir.getReturnValue());
    }

    @Redirect(method = "add", at = @At(value = "INVOKE", target =
            "Lcom/google/common/collect/SetMultimap;put(Ljava/lang/Object;Ljava/lang/Object;)Z"), require = 1)
    private boolean aco$invalidateAddedOwner(SetMultimap<Object, Object> machines, Object type, Object node) {
        if (aco$machineOwners != null) aco$machineOwners.invalidate();
        try { return machines.put(type, node); }
        finally { if (aco$machineOwners != null) aco$machineOwners.invalidate(); }
    }

    @Redirect(method = "remove", at = @At(value = "INVOKE", target =
            "Lcom/google/common/collect/SetMultimap;remove(Ljava/lang/Object;Ljava/lang/Object;)Z"), require = 1)
    private boolean aco$invalidateRemovedOwner(SetMultimap<Object, Object> machines, Object type, Object node) {
        if (aco$machineOwners != null) aco$machineOwners.invalidate();
        try { return machines.remove(type, node); }
        finally { if (aco$machineOwners != null) aco$machineOwners.invalidate(); }
    }
}

