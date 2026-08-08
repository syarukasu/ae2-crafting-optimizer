package com.syaru.ae2craftingoptimizer.mixin;

public final class ExtendedAeMixinConfigPlugin extends ModPresenceMixinConfigPlugin {
    @Override
    protected String feature() {
        return "integration.extendedae";
    }

    @Override
    protected String dependencyId() {
        return "expatternprovider";
    }
}
