package com.syaru.ae2craftingoptimizer.mixin;

public final class NeoEcoMixinConfigPlugin extends ModPresenceMixinConfigPlugin {
    @Override
    protected String feature() {
        return "integration.neoecoae";
    }

    @Override
    protected String dependencyId() {
        return "neoecoae";
    }
}
