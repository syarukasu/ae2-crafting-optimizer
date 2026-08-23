package com.syaru.ae2craftingoptimizer.optimization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Issue #129で定義した最適化機能の中央レジストリ。
 *
 * <p>機能一覧、ID検索、domain別一覧をこの型へ集約し、Mixin、設定、診断が
 * それぞれ独自の機能一覧を持たないようにする。
 */
public final class OptimizationFeatureRegistry {
    private static final List<OptimizationFeature> ALL = List.of(OptimizationFeature.values());
    private static final Map<String, OptimizationFeature> BY_ID = createById();
    private static final Map<OptimizationDomain, List<OptimizationFeature>> BY_DOMAIN = createByDomain();

    private OptimizationFeatureRegistry() {
    }

    public static List<OptimizationFeature> all() {
        return ALL;
    }

    public static Optional<OptimizationFeature> findById(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static List<OptimizationFeature> forDomain(OptimizationDomain domain) {
        return BY_DOMAIN.getOrDefault(domain, List.of());
    }

    private static Map<String, OptimizationFeature> createById() {
        Map<String, OptimizationFeature> byId = new LinkedHashMap<>();
        // 設定、診断、文書で同じIDを参照できるよう、全機能を一度だけ登録する。
        for (OptimizationFeature feature : ALL) {
            OptimizationFeature previous = byId.putIfAbsent(feature.id(), feature);
            // ID重複は別機能の設定・診断を混同するため、起動前の型初期化で停止する。
            if (previous != null) {
                throw new IllegalStateException("Duplicate optimization feature id: " + feature.id());
            }
        }
        return Collections.unmodifiableMap(byId);
    }

    private static Map<OptimizationDomain, List<OptimizationFeature>> createByDomain() {
        EnumMap<OptimizationDomain, List<OptimizationFeature>> mutable = new EnumMap<>(OptimizationDomain.class);
        // 空domainも明示的に保持し、版間でdomainの意味が欠落した場合に試験で検出する。
        for (OptimizationDomain domain : OptimizationDomain.values()) {
            mutable.put(domain, new ArrayList<>());
        }
        // 各機能を宣言済みのdomainへ一度だけ割り当てる。
        for (OptimizationFeature feature : ALL) {
            mutable.get(feature.domain()).add(feature);
        }

        EnumMap<OptimizationDomain, List<OptimizationFeature>> immutable = new EnumMap<>(OptimizationDomain.class);
        // 呼び出し側から中央台帳を書き換えられないよう、domainごとのlistも不変化する。
        for (Map.Entry<OptimizationDomain, List<OptimizationFeature>> entry : mutable.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }
}
