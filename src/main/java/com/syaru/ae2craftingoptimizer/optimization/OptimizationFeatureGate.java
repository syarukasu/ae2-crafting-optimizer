package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 最適化入口の共通gate。
 *
 * <p>Issue #129の回帰防止として、master、domain、個別設定の順で判定する。
 * どこか一つでも無効なら、呼び出し側はAE2の状態へ触れずreturnする。
 */
public final class OptimizationFeatureGate {
    /** 1機能1bitで保持するため、hot pathで拒否回数を加算し続けない。 */
    private static final AtomicLong MASTER_DENIAL_MASK = new AtomicLong();
    private static final AtomicLong DOMAIN_DENIAL_MASK = new AtomicLong();
    private static final AtomicLong FEATURE_DENIAL_MASK = new AtomicLong();
    private static final AtomicLong UNAVAILABLE_MASK = new AtomicLong();

    static {
        // 固定long bitsetの範囲を超えた場合は、黙って診断を欠落させず開発時に停止する。
        if (OptimizationFeatureRegistry.all().size() > Long.SIZE) {
            throw new IllegalStateException("OptimizationFeature exceeds the 64-bit diagnostics mask");
        }
    }

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
            return Decision.IMPLEMENTATION_UNAVAILABLE;
        }
        return Decision.ENABLED;
    }

    public static Map<OptimizationDomain, DenialSnapshot> denialSnapshot() {
        EnumMap<OptimizationDomain, DenialSnapshot> snapshot = new EnumMap<>(OptimizationDomain.class);
        long masterMask = MASTER_DENIAL_MASK.get();
        long domainMask = DOMAIN_DENIAL_MASK.get();
        long featureMask = FEATURE_DENIAL_MASK.get();
        long unavailableMask = UNAVAILABLE_MASK.get();
        // 診断値を安定したdomain順で返すため、enum全件を一度だけ走査する。
        for (OptimizationDomain domain : OptimizationDomain.values()) {
            long masterDenied = 0L;
            long domainDenied = 0L;
            long featureDenied = 0L;
            long unavailable = 0L;
            // 同じdomainへ属する拒否済みfeature bitだけを集計する。
            for (OptimizationFeature feature : OptimizationFeatureRegistry.forDomain(domain)) {
                long bit = bit(feature);
                masterDenied += (masterMask & bit) == 0L ? 0L : 1L;
                domainDenied += (domainMask & bit) == 0L ? 0L : 1L;
                featureDenied += (featureMask & bit) == 0L ? 0L : 1L;
                unavailable += (unavailableMask & bit) == 0L ? 0L : 1L;
            }
            snapshot.put(domain, new DenialSnapshot(masterDenied, domainDenied, featureDenied, unavailable));
        }
        return Map.copyOf(snapshot);
    }

    public static void resetDiagnostics() {
        MASTER_DENIAL_MASK.set(0L);
        DOMAIN_DENIAL_MASK.set(0L);
        FEATURE_DENIAL_MASK.set(0L);
        UNAVAILABLE_MASK.set(0L);
    }

    private static void record(OptimizationFeature feature, Decision decision) {
        long bit = bit(feature);
        switch (decision) {
            case MASTER_DISABLED -> markOnce(MASTER_DENIAL_MASK, bit);
            case DOMAIN_DISABLED -> markOnce(DOMAIN_DENIAL_MASK, bit);
            case FEATURE_DISABLED -> markOnce(FEATURE_DENIAL_MASK, bit);
            case IMPLEMENTATION_UNAVAILABLE -> markOnce(UNAVAILABLE_MASK, bit);
            case ENABLED -> {
                // 有効判定は通常経路なので、診断counterを増やさない。
            }
        }
    }

    private static long bit(OptimizationFeature feature) {
        return 1L << feature.ordinal();
    }

    private static void markOnce(AtomicLong mask, long bit) {
        long current = mask.get();
        // 同じ拒否は既に観測済みなので、CAS writeを繰り返さずreturnする。
        if ((current & bit) != 0L) {
            return;
        }
        // 別threadが別featureを同時記録してもbitを失わないよう、成功まで再読込する。
        while (!mask.compareAndSet(current, current | bit)) {
            current = mask.get();
            // 競合threadが同じbitを記録済みなら追加writeは不要。
            if ((current & bit) != 0L) {
                return;
            }
        }
    }

    enum Decision {
        ENABLED,
        MASTER_DISABLED,
        DOMAIN_DISABLED,
        FEATURE_DISABLED,
        IMPLEMENTATION_UNAVAILABLE
    }

    public record DenialSnapshot(
            long masterDisabled,
            long domainDisabled,
            long featureDisabled,
            long implementationUnavailable) {
    }
}
