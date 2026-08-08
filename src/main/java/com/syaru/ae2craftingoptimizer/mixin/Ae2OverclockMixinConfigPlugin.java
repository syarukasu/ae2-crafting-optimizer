package com.syaru.ae2craftingoptimizer.mixin;

public final class Ae2OverclockMixinConfigPlugin extends ModPresenceMixinConfigPlugin {
    @Override
    protected String feature() {
        return "integration.ae2_overclocked";
    }

    @Override
    protected String dependencyId() {
        return "ae2_overclocked";
    }
}
