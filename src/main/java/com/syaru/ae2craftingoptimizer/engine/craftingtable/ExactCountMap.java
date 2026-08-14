package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Issue #87: exact数量Mapの不変条件だけを所有する副作用なし共通部品。
 *
 * <p>クラフト状態、NBT、AE2在庫には触れず、正数検証、順序付き複製、包含判定、
 * BigInteger加算だけを行う。</p>
 */
final class ExactCountMap {
    private ExactCountMap() {
    }

    /** Escrow用。既存契約どおり空Mapを許可し、キー件数上限を新設しない。 */
    static <K> LinkedHashMap<K, BigInteger> mutablePositiveCopy(
            Map<K, BigInteger> source,
            String name) {
        return copyPositive(source, name, true, null);
    }

    /** 永続取引用。空Map契約と破損Mapの最大キー件数を呼出側が明示する。 */
    static <K> Map<K, BigInteger> immutablePositiveCopy(
            Map<K, BigInteger> source,
            String name,
            boolean allowEmpty,
            int maximumKeys) {
        // 0以下では全Mapを拒否してしまうため、呼出側の設定ミスとして扱う。
        if (maximumKeys < 1) {
            throw new IllegalArgumentException("maximumKeys must be positive");
        }
        return Collections.unmodifiableMap(
                copyPositive(source, name, allowEmpty, maximumKeys));
    }

    /** 既に検証済みの順序付きMapを、呼出元から変更できないsnapshotへ複製する。 */
    static <K> Map<K, BigInteger> immutableOrderedCopy(
            Map<K, BigInteger> source) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(source, "source")));
    }

    /** 正数だけを同一キーへBigIntegerのまま加算する。 */
    static <K> void mergePositive(
            Map<K, BigInteger> target,
            K key,
            BigInteger amount) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(key, "key");
        BigInteger checked = Objects.requireNonNull(amount, "amount");
        // 0以下は存在しないentryと区別できず、会計の正本にはできない。
        if (checked.signum() <= 0) {
            throw new IllegalArgumentException(
                    "exact crafting amount must be positive");
        }
        target.merge(key, checked, BigInteger::add);
    }

    /** 全要求を満たすかだけを確認し、どちらのMapも変更しない。 */
    static <K> boolean containsAll(
            Map<K, BigInteger> available,
            Map<K, BigInteger> required) {
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(required, "required");
        // 一件でも不足すれば、在庫やEscrowへ触る前にfalseを返す。
        for (Map.Entry<K, BigInteger> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), BigInteger.ZERO)
                    .compareTo(entry.getValue()) < 0) {
                return false;
            }
        }
        return true;
    }

    private static <K> LinkedHashMap<K, BigInteger> copyPositive(
            Map<K, BigInteger> source,
            String name,
            boolean allowEmpty,
            Integer maximumKeys) {
        Objects.requireNonNull(source, name);
        // 永続取引だけは、破損NBTによる巨大Map確保を既存上限で拒否する。
        if (maximumKeys != null && source.size() > maximumKeys) {
            throw new IllegalArgumentException(name + " has too many keys");
        }

        LinkedHashMap<K, BigInteger> result = new LinkedHashMap<>();
        // null、0、負数を入れず、保存済みMapの反復順序を維持する。
        for (Map.Entry<K, BigInteger> entry : source.entrySet()) {
            K key = Objects.requireNonNull(entry.getKey(), name + " key");
            BigInteger amount = Objects.requireNonNull(
                    entry.getValue(),
                    name + " amount");
            // 0以下は存在しないentryと区別できないため、Mapへ複製しない。
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException(
                        name + " contains a non-positive amount");
            }
            result.put(key, amount);
        }

        // 空Mapを禁止する台帳だけは、少なくとも一つのexact entryを要求する。
        if (!allowEmpty && result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return result;
    }
}
