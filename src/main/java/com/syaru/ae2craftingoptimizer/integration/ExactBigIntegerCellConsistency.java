package com.syaru.ae2craftingoptimizer.integration;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * ACOが直接更新したExtendedAE Plus在庫Mapと、同MODの保存用総量を結ぶ弱Sidecar。
 *
 * <p>対象MODの通常insert/extractを数量Windowとして呼び直さず、次のcache refreshと
 * SavedData直列化で同じ正確値を採用させる。</p>
 */
public final class ExactBigIntegerCellConsistency {
    private static final ReferenceQueue<Object> COLLECTED_MAPS =
            new ReferenceQueue<>();
    private static final Map<IdentityWeakReference, BigInteger>
            EXPECTED_TOTALS = new HashMap<>();

    private ExactBigIntegerCellConsistency() {
    }

    public static void record(Object amounts, BigInteger exactTotal) {
        Objects.requireNonNull(amounts, "amounts");
        BigInteger checked = Objects.requireNonNull(
                exactTotal, "exactTotal");
        if (checked.signum() < 0) {
            throw new IllegalArgumentException(
                    "exact cell total must not be negative");
        }
        synchronized (EXPECTED_TOTALS) {
            removeCollectedMaps();
            EXPECTED_TOTALS.put(
                    new IdentityWeakReference(
                            amounts, COLLECTED_MAPS),
                    checked);
        }
    }

    public static Optional<BigInteger> expectedTotal(Object amounts) {
        Objects.requireNonNull(amounts, "amounts");
        synchronized (EXPECTED_TOTALS) {
            removeCollectedMaps();
            return Optional.ofNullable(EXPECTED_TOTALS.get(
                    new IdentityWeakReference(amounts)));
        }
    }

    /**
     * 同じ共有Mapを参照する全Inventory wrapperで使う正確な総量を返す。
     *
     * <p>ACOがまだ触れていないMapだけは全キーを一巡して正本を作る。それ以降は
     * 通常搬入出とACO直接変更の双方が{@link #record(Object, BigInteger)}を更新するため、
     * wrapperごとに古くなり得るcached totalを参照しない。</p>
     */
    public static BigInteger authoritativeTotal(
            Map<?, BigInteger> amounts) {
        Objects.requireNonNull(
                amounts,
                "amounts");
        Optional<BigInteger> recorded =
                expectedTotal(
                        amounts);
        // 同じ共有Mapで既に証明した総量があれば、登録キー全件を再走査しない。
        if (recorded.isPresent()) {
            return recorded.get();
        }

        BigInteger total =
                BigInteger.ZERO;
        // 初回だけ全登録キーを加算し、第三者MODが保存したcached totalの不整合を修復する。
        for (BigInteger amount :
                amounts.values()) {
            // 保存Mapにnullまたは非正数があれば、推測した総量で直接変更を続けない。
            if (amount == null
                    || amount.signum()
                            <= 0) {
                throw new IllegalStateException(
                        "exact cell map contains a non-positive amount");
            }
            total =
                    total.add(
                            amount);
        }
        record(
                amounts,
                total);
        return total;
    }

    public static void clear() {
        synchronized (EXPECTED_TOTALS) {
            EXPECTED_TOTALS.clear();
            // World切替時は回収済み参照も捨て、次のSessionへ持ち越さない。
            while (COLLECTED_MAPS.poll() != null) {
                // Queueを空にすること自体が目的なので追加処理は不要。
            }
        }
    }

    private static void removeCollectedMaps() {
        IdentityWeakReference reference;
        // GCされたセルMapだけを除去し、稼働中セルの正確な総量は保持する。
        while ((reference =
                        (IdentityWeakReference)
                                COLLECTED_MAPS.poll())
                != null) {
            EXPECTED_TOTALS.remove(reference);
        }
    }

    private static final class IdentityWeakReference
            extends WeakReference<Object> {
        private final int identityHash;

        private IdentityWeakReference(
                Object referent,
                ReferenceQueue<Object> queue) {
            super(Objects.requireNonNull(referent, "referent"), queue);
            identityHash = System.identityHashCode(referent);
        }

        private IdentityWeakReference(Object referent) {
            super(Objects.requireNonNull(referent, "referent"));
            identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            // Map自身が同じ弱参照なら、referent確認なしで一致する。
            if (this == other) {
                return true;
            }
            // Map内容のequalsではなく、同一インスタンスだけを一致させる。
            if (!(other
                    instanceof IdentityWeakReference reference)) {
                return false;
            }
            Object mine = get();
            // GC済み参照同士を一致させると別セルの記録を消すため、nullは一致させない。
            return mine != null && mine == reference.get();
        }
    }
}
