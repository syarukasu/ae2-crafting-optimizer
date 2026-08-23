package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 最適化入口の共通gate。
 *
 * <p>Issue #129の回帰防止として、master、domain、個別設定の順で判定する。
 * どこか一つでも無効なら、呼び出し側はAE2の状態へ触れずreturnする。
 */
public final class OptimizationFeatureGate {
    /** 機能数に応じて64bit wordを増やし、hot pathでは同じ拒否を一度だけ記録する。 */
    private static final ConcurrentFeatureBits MASTER_DENIALS = diagnosticsBits();
    private static final ConcurrentFeatureBits DOMAIN_DENIALS = diagnosticsBits();
    private static final ConcurrentFeatureBits FEATURE_DENIALS = diagnosticsBits();
    private static final ConcurrentFeatureBits RETIRED_COMPATIBILITY_KEYS = diagnosticsBits();

    private OptimizationFeatureGate() {
    }

    public static boolean allows(OptimizationFeature feature, boolean individualSwitch) {
        Decision decision = evaluate(
                ACOConfig.enableOptimizer(),
                ACOConfig.rawDomainEnabled(feature.domain()),
                individualSwitch,
                feature.implementationStatus());
        record(feature, decision);
        return decision == Decision.ENABLED;
    }

    static Decision evaluate(boolean masterEnabled, boolean domainEnabled, boolean featureEnabled) {
        return evaluate(
                masterEnabled,
                domainEnabled,
                featureEnabled,
                OptimizationImplementationStatus.ACTIVE);
    }

    static Decision evaluate(
            boolean masterEnabled,
            boolean domainEnabled,
            boolean featureEnabled,
            OptimizationImplementationStatus implementationStatus) {
        // masterが無効なら、domainや個別設定を評価せず全介入を停止する。
        if (!masterEnabled) {
            return Decision.MASTER_DISABLED;
        }
        // domainが無効なら、その責務領域に属する全機能を停止する。
        if (!domainEnabled) {
            return Decision.DOMAIN_DISABLED;
        }
        // 個別設定が無効なら、同じdomainの他機能には影響させず対象だけ停止する。
        if (!featureEnabled) {
            return Decision.FEATURE_DISABLED;
        }
        // 互換Configだけ残る機能は、trueでも実装済みのように振る舞わせない。
        if (implementationStatus != OptimizationImplementationStatus.ACTIVE) {
            return Decision.RETIRED_COMPATIBILITY_KEY;
        }
        return Decision.ENABLED;
    }

    public static Map<OptimizationDomain, DenialSnapshot> denialSnapshot() {
        EnumMap<OptimizationDomain, DenialSnapshot> snapshot = new EnumMap<>(OptimizationDomain.class);
        // 診断値を安定したdomain順で返すため、enum全件を一度だけ走査する。
        for (OptimizationDomain domain : OptimizationDomain.values()) {
            long masterDenied = 0L;
            long domainDenied = 0L;
            long featureDenied = 0L;
            long retired = 0L;
            // 同じdomainへ属する拒否済みfeature bitだけを集計する。
            for (OptimizationFeature feature : OptimizationFeatureRegistry.forDomain(domain)) {
                masterDenied += MASTER_DENIALS.contains(feature.ordinal()) ? 1L : 0L;
                domainDenied += DOMAIN_DENIALS.contains(feature.ordinal()) ? 1L : 0L;
                featureDenied += FEATURE_DENIALS.contains(feature.ordinal()) ? 1L : 0L;
                retired += RETIRED_COMPATIBILITY_KEYS.contains(feature.ordinal()) ? 1L : 0L;
            }
            snapshot.put(domain, new DenialSnapshot(masterDenied, domainDenied, featureDenied, retired));
        }
        return Map.copyOf(snapshot);
    }

    public static void resetDiagnostics() {
        MASTER_DENIALS.clear();
        DOMAIN_DENIALS.clear();
        FEATURE_DENIALS.clear();
        RETIRED_COMPATIBILITY_KEYS.clear();
    }

    static void record(OptimizationFeature feature, Decision decision) {
        switch (decision) {
            case MASTER_DISABLED -> MASTER_DENIALS.mark(feature.ordinal());
            case DOMAIN_DISABLED -> DOMAIN_DENIALS.mark(feature.ordinal());
            case FEATURE_DISABLED -> FEATURE_DENIALS.mark(feature.ordinal());
            case RETIRED_COMPATIBILITY_KEY -> RETIRED_COMPATIBILITY_KEYS.mark(feature.ordinal());
            case ENABLED -> {
                // 有効判定は通常経路なので、診断counterを増やさない。
            }
        }
    }

    private static ConcurrentFeatureBits diagnosticsBits() {
        return new ConcurrentFeatureBits(OptimizationFeatureRegistry.all().size());
    }

    /** 64機能を超えても拡張できる、固定長・lock-freeの診断bit集合。 */
    static final class ConcurrentFeatureBits {
        /** 一つのlong wordが保持するbit数。 */
        private static final int BITS_PER_WORD = Long.SIZE;
        /** 64bit wordのindexへ変換する右shift数。 */
        private static final int WORD_SHIFT = 6;
        /** word内bit indexを0から63へ収めるmask。 */
        private static final int WORD_BIT_MASK = BITS_PER_WORD - 1;

        private final int featureCount;
        private final AtomicLongArray words;

        ConcurrentFeatureBits(int featureCount) {
            // 空または負の台帳は診断契約を成立させないため拒否する。
            if (featureCount <= 0) {
                throw new IllegalArgumentException("featureCount must be positive");
            }
            this.featureCount = featureCount;
            this.words = new AtomicLongArray(((featureCount - 1) / BITS_PER_WORD) + 1);
        }

        void mark(int featureIndex) {
            validateIndex(featureIndex);
            int wordIndex = featureIndex >>> WORD_SHIFT;
            long bit = 1L << (featureIndex & WORD_BIT_MASK);
            long current = this.words.get(wordIndex);
            // 同じ拒否は既に観測済みなので、CAS writeを繰り返さずreturnする。
            if ((current & bit) != 0L) {
                return;
            }
            // 別threadが別機能を同時記録してもbitを失わないよう、成功まで再読込する。
            while (!this.words.compareAndSet(wordIndex, current, current | bit)) {
                current = this.words.get(wordIndex);
                // 競合threadが同じbitを記録済みなら追加writeは不要。
                if ((current & bit) != 0L) {
                    return;
                }
            }
        }

        boolean contains(int featureIndex) {
            validateIndex(featureIndex);
            int wordIndex = featureIndex >>> WORD_SHIFT;
            long bit = 1L << (featureIndex & WORD_BIT_MASK);
            return (this.words.get(wordIndex) & bit) != 0L;
        }

        void clear() {
            // server lifecycleをまたいで古い拒否診断を残さないため、全wordを初期化する。
            for (int wordIndex = 0; wordIndex < this.words.length(); wordIndex++) {
                this.words.set(wordIndex, 0L);
            }
        }

        private void validateIndex(int featureIndex) {
            // 台帳外indexは別機能の診断bitを壊すため、呼出側の不整合として拒否する。
            if (featureIndex < 0 || featureIndex >= this.featureCount) {
                throw new IndexOutOfBoundsException("featureIndex=" + featureIndex);
            }
        }
    }

    enum Decision {
        ENABLED,
        MASTER_DISABLED,
        DOMAIN_DISABLED,
        FEATURE_DISABLED,
        RETIRED_COMPATIBILITY_KEY
    }

    public record DenialSnapshot(
            long masterDisabled,
            long domainDisabled,
            long featureDisabled,
            long retiredCompatibilityKey) {
    }
}
