package com.syaru.ae2craftingoptimizer.mixin;

public final class NeoEcoMixinConfigPlugin extends ModPresenceMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // refs.8 never replaces another CPU's native operation limits.
        return false;
    }

    @Override
    protected String feature() {
        return "integration.neoecoae";
    }

    @Override
    protected String dependencyId() {
        return "neoecoae";
    }
}
