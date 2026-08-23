package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.mixin.MixinFeatureCatalog;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeature;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeatureRegistry;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationImplementationStatus;
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
    private static final Set<Path> MIXIN_CONFIGS = Set.of(
            Path.of("src/main/resources/ae2_crafting_optimizer.mixins.json"),
            Path.of("src/main/resources/aco.performance.mixins.json"));

    @Test
    void everyConfiguredMixinHasAnExplicitFeatureContract() throws IOException {
        Set<String> configured = new HashSet<>();
        // coreとperformanceの両設定を一つの責務台帳へ照合する。
        for (Path config : MIXIN_CONFIGS) {
            String json = Files.readString(config, StandardCharsets.UTF_8);
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

    @Test
    void retiredCompatibilityKeyCanNeverHaveAConfiguredMixin() {
        // 互換キーだけの機能へMixinを戻す場合は、先に台帳と回帰試験をACTIVEへ更新させる。
        for (var entry : MixinFeatureCatalog.snapshot().entrySet()) {
            assertEquals(
                    OptimizationImplementationStatus.ACTIVE,
                    entry.getValue().implementationStatus(),
                    entry.getKey());
        }
    }
}
