package com.syaru.ae2craftingoptimizer.lifecycle;

import java.util.Objects;
import net.minecraft.core.HolderLookup;

/**
 * 1.21.1でAEKeyとData ComponentをNBTへ保存する際に使う実Registry Provider。
 * サーバー開始前のダミーRegistryでは内容を欠落させるため、未初期化利用は拒否する。
 */
public final class ACORegistryAccess {
    private static volatile HolderLookup.Provider provider;

    private ACORegistryAccess() {
    }

    public static void install(HolderLookup.Provider newProvider) {
        provider = Objects.requireNonNull(newProvider, "newProvider");
    }

    public static HolderLookup.Provider require() {
        HolderLookup.Provider current = provider;
        if (current == null) {
            throw new IllegalStateException(
                    "ACO registry access is unavailable before the Minecraft server starts");
        }
        return current;
    }

    public static void clear() {
        provider = null;
    }
}
