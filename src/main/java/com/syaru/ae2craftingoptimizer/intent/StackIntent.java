package com.syaru.ae2craftingoptimizer.intent;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

public record StackIntent(String keyId, String keyType, long amount) {
    public static StackIntent of(GenericStack stack) {
        if (stack == null || stack.what() == null) {
            return new StackIntent("empty", "empty", 0L);
        }
        return of(stack.what(), stack.amount());
    }

    public static StackIntent of(AEKey key, long amount) {
        if (key == null) {
            return new StackIntent("empty", "empty", 0L);
        }
        String type = key.getType() == null ? "unknown" : key.getType().toString();
        return new StackIntent(key.getId().toString(), type, amount);
    }
}
