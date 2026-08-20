package com.syaru.ae2craftingoptimizer.mixin;

public final class AdvancedAeMixinConfigPlugin extends ModPresenceMixinConfigPlugin {
    @Override
    protected String feature() {
        return "integration.advanced_ae";
    }

    @Override
    protected String dependencyId() {
        return "advanced_ae";
    }

    @Override
    protected String dependencyMarkerClass() {
        return "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster";
    }
}
