package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;

/** Cache immutable Java method ownership, never pattern values or live receiver state. */
public final class CountedNativeContracts {
    private CountedNativeContracts() {}
    private static final ClassValue<Boolean> PROVIDER = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("pushPattern", IPatternDetails.class, KeyCounter[].class)
                        .getDeclaringClass() == PatternProviderLogic.class;
            } catch (NoSuchMethodException absent) { return false; }
        }
    };
    private static final ClassValue<Boolean> INPUT_PUSH = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("pushInputsToExternalInventory", KeyCounter[].class,
                        IPatternDetails.PatternInputSink.class).getDeclaringClass() == IPatternDetails.class;
            } catch (NoSuchMethodException absent) { return false; }
        }
    };
    public static boolean nativeProvider(Object provider) { return PROVIDER.get(provider.getClass()); }
    public static boolean defaultExternalPush(IPatternDetails pattern) {
        return pattern.supportsPushInputsToExternalInventory() && INPUT_PUSH.get(pattern.getClass());
    }
}
