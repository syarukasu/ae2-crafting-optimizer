package com.syaru.ae2craftingoptimizer.integration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Neo ECOの実行API世代を、Mixinが参照する前に文字列だけで判定する。 */
public final class NeoEcoVersionCompatibility {
    // 20.3系と20.4系はexecuteCraftingの記述子が異なるため、別のMixinプロファイルとして扱う。
    private static final Pattern SUPPORTED_VERSION = Pattern.compile("^20\\.(3|4)(?:\\..*)?$");

    private NeoEcoVersionCompatibility() {
    }

    public static ExecutionProfile executionProfile(String version) {
        // Neo ECOが未導入、またはForgeがまだ版を公開していない段階ではMixinを適用しない。
        if (version == null || version.isBlank()) {
            return ExecutionProfile.NONE;
        }

        Matcher matcher = SUPPORTED_VERSION.matcher(version.trim());
        // 未検証のAPI世代へ推測でMixinを適用しない。
        if (!matcher.matches()) {
            return ExecutionProfile.NONE;
        }

        return "3".equals(matcher.group(1))
                ? ExecutionProfile.NEO_ECO_20_3
                : ExecutionProfile.NEO_ECO_20_4;
    }

    public enum ExecutionProfile {
        NONE,
        NEO_ECO_20_3,
        NEO_ECO_20_4
    }
}
