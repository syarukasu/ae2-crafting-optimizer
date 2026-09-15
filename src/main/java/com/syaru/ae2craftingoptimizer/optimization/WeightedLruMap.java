package com.syaru.ae2craftingoptimizer.optimization;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * 件数と重みの両方を上限に持つ、外部同期用のaccess-order Map。
 *
 * <p>値の生成は呼び出し側で行い、この型は保持量だけを管理する。上限を超える単一値は
 * 呼び出し元へ返すがキャッシュしないため、計算結果を拒否または変更しない。</p>
 */
public final class WeightedLruMap<K, V> {
    /** JDK LinkedHashMapの標準初期容量。小規模cacheで事前確保を増やさない。 */
    private static final int INITIAL_CAPACITY = 16;
    /** JDK HashMapの標準負荷率。access-orderだけを変更し、hash特性は標準へ揃える。 */
    private static final float LOAD_FACTOR = 0.75F;

    private final int maximumEntries;
    private final int maximumWeight;
    private final ToIntFunction<V> weigher;
    private final LinkedHashMap<K, V> values =
            new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true);
    private int currentWeight;

    public WeightedLruMap(
            int maximumEntries,
            int maximumWeight,
            ToIntFunction<V> weigher) {
        // 0以下の上限では一件も保持できず、呼び出し側の再計算を常態化させるため拒否する。
        if (maximumEntries <= 0 || maximumWeight <= 0) {
            throw new IllegalArgumentException("cache limits must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.maximumWeight = maximumWeight;
        this.weigher = Objects.requireNonNull(weigher, "weigher");
    }

    /** Access順を更新しながら、現在保持している値を返す。 */
    public V get(K key) {
        return values.get(key);
    }

    /**
     * 同じキーの既存値を優先し、新規値を保持する場合だけ必要なLRU entryを退避する。
     *
     * @return 競合済みの既存値、または呼び出し側が渡した新規値
     */
    public V putIfAbsent(
            K key,
            V value,
            Consumer<K> evictionListener) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(evictionListener, "evictionListener");

        V existing = values.get(key);
        // 別計算が先に同じキーを登録した場合は、その一意な世代内結果を採用する。
        if (existing != null) {
            return existing;
        }

        int valueWeight = positiveWeight(value);
        // 単一値が総量上限を超えても結果は返し、このキャッシュだけを辞退する。
        if (valueWeight > maximumWeight) {
            return value;
        }

        // 件数または総量を超える間だけ、最も長く未使用のentryを一件ずつ退避する。
        while (!values.isEmpty()
                && (values.size() >= maximumEntries
                        || currentWeight > maximumWeight - valueWeight)) {
            Iterator<Map.Entry<K, V>> iterator = values.entrySet().iterator();
            Map.Entry<K, V> eldest = iterator.next();
            currentWeight -= positiveWeight(eldest.getValue());
            K evictedKey = eldest.getKey();
            iterator.remove();
            evictionListener.accept(evictedKey);
        }

        values.put(key, value);
        currentWeight += valueWeight;
        return value;
    }

    /** 指定した値オブジェクトが、現在もそのキーの正本として保持されているかを返す。 */
    public boolean containsExact(K key, V expected) {
        return values.get(key) == expected;
    }

    public int size() {
        return values.size();
    }

    public int currentWeight() {
        return currentWeight;
    }

    private int positiveWeight(V value) {
        int weight = weigher.applyAsInt(value);
        // 負数や0は総量上限を迂回できるため、weigherの契約違反として即時拒否する。
        if (weight <= 0) {
            throw new IllegalArgumentException("cache entry weight must be positive");
        }
        return weight;
    }
}
