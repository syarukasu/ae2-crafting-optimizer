package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.mixin.MixinFeatureCatalog;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeature;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeatureRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MixinFeatureCatalogTest {
    private static final Pattern QUOTED_MIXIN = Pattern.compile("\\\"([A-Za-z0-9_]+)\\\"");

    @Test
    void everyConfiguredMixinHasAnExplicitFeatureContract() throws IOException {
        String json = Files.readString(
                Path.of("src/main/resources/ae2_crafting_optimizer.mixins.json"),
                StandardCharsets.UTF_8);
        Set<String> configured = new HashSet<>();
        Matcher matcher = QUOTED_MIXIN.matcher(json);
        // JSON内の値から、実在するMixin型名だけを抽出する。
        while (matcher.find()) {
            String candidate = matcher.group(1);
            // 設定keyやJava versionはMixin型ではないため除外する。
            if (!candidate.endsWith("Mixin") && !candidate.endsWith("Accessor")) {
                continue;
            }
            configured.add(candidate);
        }
        assertEquals(configured, MixinFeatureCatalog.snapshot().keySet());
    }

    @Test
    void catalogNeverUsesAnUnregisteredFeature() {
        Set<OptimizationFeature> declared = Set.copyOf(OptimizationFeatureRegistry.all());
        assertFalse(MixinFeatureCatalog.snapshot().isEmpty());
        // 全Mixinの契約が列挙済みfeatureを参照することを検査する。
        for (OptimizationFeature feature : MixinFeatureCatalog.snapshot().values()) {
            assertTrue(declared.contains(feature));
        }
    }

}
