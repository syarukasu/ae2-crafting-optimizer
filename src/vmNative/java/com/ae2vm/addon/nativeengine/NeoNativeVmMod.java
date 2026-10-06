package com.ae2vm.addon.nativeengine;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod("ae2vm_aco")
public final class NeoNativeVmMod {
    public NeoNativeVmMod() {
        NeoForge.EVENT_BUS.addListener(this::stopping);
    }
    private void stopping(ServerStoppingEvent event) {
        NativeVm.cancelServer(event.getServer());
    }
}
