package com.syaru.ae2craftingoptimizer.mixin;

public final class MekanismMixinConfigPlugin extends ModPresenceMixinConfigPlugin {
    @Override
    protected String feature() {
        return "integration.mekanism";
    }

    @Override
    protected String dependencyId() {
        return "mekanism";
    }
}
