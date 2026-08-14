package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * AE2のlong {@link KeyCounter}へ対応する、正確なBigInteger在庫Snapshot。
 *
 * <p>AE2と未対応アドオンへは飽和済みKeyCounterを渡し、ACOの計画だけがこの正本を読む。
 * KeyCounterの寿命を延ばさないよう、関連付けにはIdentity弱参照を使用する。</p>
 */
public final class BigKeyCounterSidecars {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final ReferenceQueue<KeyCounter> COLLECTED_COUNTERS =
            new ReferenceQueue<>();
    private static final Map<IdentityWeakReference, Snapshot> SIDECARS =
            new HashMap<>();

    private BigKeyCounterSidecars() {
    }

    /** ストレージ一基分の正確値を、ネットワーク全体のSnapshotへ加算する。 */
    public static void merge(KeyCounter target, Snapshot contribution) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(contribution, "contribution");
        synchronized (SIDECARS) {
            removeCollectedCounters();
            IdentityWeakReference lookup = new IdentityWeakReference(target);
            Snapshot current = SIDECARS.get(lookup);
            // 初回だけ、呼出側が既に入れていたlong在庫を正確値の初期値として取り込む。
            if (current == null) {
                current = fromFacade(target);
            }

            Map<AEKey, BigInteger> merged = new LinkedHashMap<>(current.amounts());
            Set<AEKey> exactKeys = new LinkedHashSet<>(current.exactKeys());
            // 同一キーをBigIntegerで加算し、long境界を越えても負数へ巻き戻さない。
            for (Map.Entry<AEKey, BigInteger> entry : contribution.amounts().entrySet()) {
                merged.merge(entry.getKey(), entry.getValue(), BigInteger::add);
                // 現在値と今回の寄与の両方が正確なキーだけ、合計値も正確と証明できる。
                if (current.isExact(entry.getKey())
                        && contribution.isExact(entry.getKey())) {
                    exactKeys.add(entry.getKey());
                } else {
                    exactKeys.remove(entry.getKey());
                }
            }
            SIDECARS.put(
                    new IdentityWeakReference(target, COLLECTED_COUNTERS),
                    new Snapshot(
                            merged,
                            current.complete() && contribution.complete(),
                            exactKeys));
        }
    }

    /** AE2が作った別KeyCounterへ、画面上で有効なキーだけ正確値を引き継ぐ。 */
    public static void copyVisible(KeyCounter source, KeyCounter target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Snapshot sourceSnapshot = snapshot(source).orElse(null);
        // 元Snapshotが存在しない場合はlong値だけが正本なので、追加情報を作らない。
        if (sourceSnapshot == null) {
            return;
        }

        // Issue #93: KeyCounter検索とJDK MapN反復を同じC2ループへ融合させない。
        Set<AEKey> visibleKeys = capturePositiveKeys(target);
        put(target, retainVisibleAmounts(sourceSnapshot, visibleKeys));
    }

    /** AE2のlong Facadeから、現在正量として見えているキーだけを独立Snapshotへ取り出す。 */
    private static Set<AEKey> capturePositiveKeys(KeyCounter target) {
        Set<AEKey> visibleKeys = new LinkedHashSet<>();
        // 可変KeyCounterの走査をBigInteger Mapの走査から分離し、二種類の実装を同時に触らない。
        for (var entry : target) {
            // 0以下はAE2側で在庫として見えていないため、Sidecarにも伝播しない。
            if (entry.getLongValue() <= 0L) {
                continue;
            }
            visibleKeys.add(entry.getKey());
        }
        return Collections.unmodifiableSet(visibleKeys);
    }

    /** 不変source Snapshotから、AE2のlong Facadeに残ったキーだけを抽出する。 */
    private static Snapshot retainVisibleAmounts(
            Snapshot sourceSnapshot,
            Set<AEKey> visibleKeys) {
        Map<AEKey, BigInteger> visible = new LinkedHashMap<>();
        Set<AEKey> exactVisible = new LinkedHashSet<>();
        // 可視キーSnapshotに存在する正確値だけを伝播し、AE2が除外したキーを復活させない。
        for (Map.Entry<AEKey, BigInteger> entry : sourceSnapshot.amounts().entrySet()) {
            AEKey key = entry.getKey();
            // targetに存在しないキーは、BigInteger側だけで再追加しない。
            if (!visibleKeys.contains(key)) {
                continue;
            }
            visible.put(key, entry.getValue());
            // sourceで値が証明済みのキーだけ、コピー先でもexactとして扱う。
            if (sourceSnapshot.isExact(key)) {
                exactVisible.add(key);
            }
        }
        return new Snapshot(visible, sourceSnapshot.complete(), exactVisible);
    }

    /** long FacadeとBigInteger Sidecarを一つの独立したKeyCounterへ複製する。 */
    public static KeyCounter copyOf(KeyCounter source) {
        Objects.requireNonNull(source, "source");
        KeyCounter copy = new KeyCounter();
        copy.addAll(source);
        copyVisible(source, copy);
        return copy;
    }

    /** KeyCounterに関連付けられた不変Snapshotを返す。 */
    public static Optional<Snapshot> snapshot(KeyCounter counter) {
        Objects.requireNonNull(counter, "counter");
        synchronized (SIDECARS) {
            removeCollectedCounters();
            return Optional.ofNullable(SIDECARS.get(new IdentityWeakReference(counter)));
        }
    }

    /** KeyCounter再利用時に古い正確値を残さない。 */
    public static void clear(KeyCounter counter) {
        Objects.requireNonNull(counter, "counter");
        synchronized (SIDECARS) {
            removeCollectedCounters();
            SIDECARS.remove(new IdentityWeakReference(counter));
        }
    }

    /** long Facadeしかない寄与を、正確性フラグ付きSnapshotへ変換する。 */
    public static Snapshot fromFacade(KeyCounter counter) {
        Objects.requireNonNull(counter, "counter");
        Map<AEKey, BigInteger> amounts = new LinkedHashMap<>();
        boolean complete = true;
        // ストレージ在庫は非負であるべきなので、負数は既発生overflowとして不完全扱いにする。
        for (var entry : counter) {
            long amount = entry.getLongValue();
            // 負数は正確値を復元できないため不完全、正数だけはBigIntegerへ無損失変換する。
            if (amount < 0L) {
                complete = false;
            } else if (amount > 0L) {
                amounts.put(entry.getKey(), BigInteger.valueOf(amount));
            }
        }
        return new Snapshot(amounts, complete, amounts.keySet());
    }

    /** 単体テスト間でstatic Sidecarを共有しないためのreset。 */
    static void clearForTests() {
        synchronized (SIDECARS) {
            SIDECARS.clear();
            // ReferenceQueueを空にして、次のテストへ回収済み参照を持ち越さない。
            while (COLLECTED_COUNTERS.poll() != null) {
                // Queueを空にすること自体が目的なので処理は不要。
            }
        }
    }

    private static void put(KeyCounter counter, Snapshot snapshot) {
        synchronized (SIDECARS) {
            removeCollectedCounters();
            SIDECARS.put(
                    new IdentityWeakReference(counter, COLLECTED_COUNTERS),
                    snapshot);
        }
    }

    private static void removeCollectedCounters() {
        IdentityWeakReference reference;
        // GC済みKeyCounterだけを除去し、稼働中ネットワークのSnapshotは保持する。
        while ((reference = (IdentityWeakReference) COLLECTED_COUNTERS.poll()) != null) {
            SIDECARS.remove(reference);
        }
    }

    /** 一回の在庫集計に対応する不変BigInteger Map。 */
    public record Snapshot(
            Map<AEKey, BigInteger> amounts,
            boolean complete,
            Set<AEKey> exactKeys) {
        /** 既存アダプター向けの簡略コンストラクター。false時はキー単位の証明も持たない。 */
        public Snapshot(Map<AEKey, BigInteger> amounts, boolean complete) {
            this(amounts, complete, complete ? amounts.keySet() : Set.of());
        }

        public Snapshot {
            Objects.requireNonNull(amounts, "amounts");
            Objects.requireNonNull(exactKeys, "exactKeys");
            Map<AEKey, BigInteger> checked = new LinkedHashMap<>();
            // 不正な負数やnullをSidecarへ入れず、計画時の在庫過大評価を防ぐ。
            for (Map.Entry<AEKey, BigInteger> entry : amounts.entrySet()) {
                AEKey key = Objects.requireNonNull(entry.getKey(), "inventory key");
                BigInteger amount = Objects.requireNonNull(
                        entry.getValue(),
                        "inventory amount");
                // 在庫量の負数は不足量との意味が混ざるため、Snapshot作成時点で拒否する。
                if (amount.signum() < 0) {
                    throw new IllegalArgumentException("inventory amount must not be negative");
                }
                // 0はMapへ保持せず、参照されなかったキーと同じ扱いに統一する。
                if (amount.signum() > 0) {
                    checked.put(key, amount);
                }
            }
            // Issue #93: MapNとfastutil検索のC2融合を避けるため、所有Mapを読み取り専用化する。
            amounts = Collections.unmodifiableMap(checked);

            Set<AEKey> checkedExactKeys = new LinkedHashSet<>();
            for (AEKey key : exactKeys) {
                Objects.requireNonNull(key, "exact inventory key");
                // 0量はMapへ保持しないため、正確性集合にも登録しない。
                if (checked.containsKey(key)) {
                    checkedExactKeys.add(key);
                }
            }
            exactKeys = Collections.unmodifiableSet(checkedExactKeys);
        }

        public BigInteger amount(AEKey key) {
            return amounts.getOrDefault(Objects.requireNonNull(key, "key"), BigInteger.ZERO);
        }

        /** 指定キーの値が、ネットワーク内の不完全な別キーに影響されず確定しているか返す。 */
        public boolean isExact(AEKey key) {
            Objects.requireNonNull(key, "key");
            // 完全Snapshotでは、Mapにないキーの0も正確に証明できる。
            return complete || exactKeys.contains(key);
        }

        public boolean containsWideValue() {
            // 一つでもLong.MAX_VALUEを越えれば、long在庫Snapshotでは正確に表せない。
            for (BigInteger amount : amounts.values()) {
                // 境界超過を見つけた時点で残りのキー走査を省略する。
                if (amount.compareTo(LONG_MAX) > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    /** KeyCounterのequalsではなく同一インスタンスだけを一致させる弱参照。 */
    private static final class IdentityWeakReference extends WeakReference<KeyCounter> {
        private final int identityHash;

        private IdentityWeakReference(
                KeyCounter referent,
                ReferenceQueue<KeyCounter> queue) {
            super(Objects.requireNonNull(referent, "referent"), queue);
            this.identityHash = System.identityHashCode(referent);
        }

        private IdentityWeakReference(KeyCounter referent) {
            super(Objects.requireNonNull(referent, "referent"));
            this.identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            // 同じ弱参照オブジェクトならreferent確認なしで一致する。
            if (this == other) {
                return true;
            }
            // Identity弱参照以外とは一致させない。
            if (!(other instanceof IdentityWeakReference reference)) {
                return false;
            }
            KeyCounter mine = get();
            // 回収済み参照同士を一致させると無関係なSidecarを消すため、nullは一致させない。
            return mine != null && mine == reference.get();
        }
    }
}
