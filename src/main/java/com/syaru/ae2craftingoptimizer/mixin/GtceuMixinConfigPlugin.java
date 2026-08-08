package com.syaru.ae2craftingoptimizer.mixin;

public final class GtceuMixinConfigPlugin extends ModPresenceMixinConfigPlugin {
    @Override
    protected String feature() {
        return "integration.gtceu";
    }

    @Override
    protected String dependencyId() {
        return "gtceu";
    }
}
