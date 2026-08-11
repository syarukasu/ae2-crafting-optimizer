package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Add-on向けの正確な量会計。
 *
 * <p>AE2の通常カウンタはlongなので、ここでは値をBigIntegerで保持し、
 * 実際の搬入出時だけlong単位の窓へ取り出す。これにより、計算中の掛け算で
 * longが折り返して不足や消失を起こすことを防ぐ。</p>
 *
 * <p>このクラスはAE2のレシピやクラフト規則を変更しない。AE2へ渡す量の上限は
 * 呼び出し側が決め、{@link #drain(Object, long)} はその上限を越えない一回分だけ返す。</p>
 */
public final class BigIntegerAmountLedger<K> {
    public static final int SCHEMA_VERSION = 1;
    /** 破損NBTによるメモリ枯渇を防ぐ固定エントリ上限。 */
    public static final int MAX_ENTRIES = 1_048_576;

    private static final String NBT_SCHEMA = "schemaVersion";
    private static final String NBT_ENTRIES = "entries";
    private static final String NBT_KEY = "key";
    private static final String NBT_AMOUNT = "amount";

    private final BigCraftingKeyCodec<K> keyCodec;
    private final int maximumBits;
    private final Map<K, BigInteger> amounts = new LinkedHashMap<>();

    BigIntegerAmountLedger(BigCraftingKeyCodec<K> keyCodec, int maximumBits) {
        this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec");
        if (maximumBits < 1) {
            throw new IllegalArgumentException("maximumBits must be positive");
        }
        this.maximumBits = maximumBits;
    }

    /** 正確な量を加算する。0以下の量は会計ミスとして受け付けない。 */
    public synchronized void add(K key, BigInteger amount) {
        K checkedKey = Objects.requireNonNull(key, "key");
        BigInteger checkedAmount = requirePositive(amount, "amount");
        BigInteger current = amounts.getOrDefault(checkedKey, BigInteger.ZERO);
        BigInteger next = checked(current.add(checkedAmount), "ledger amount");
        // 新しいキーだけエントリ上限を消費するため、既存キーの加算は許可する。
        if (current.signum() == 0 && amounts.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("BigInteger amount ledger entry limit exceeded");
        }
        amounts.put(checkedKey, next);
    }

    /** long入力を正確な会計へ昇格する補助メソッド。 */
    public synchronized void add(K key, long amount) {
        // 正の量だけを会計し、0以下の入力は空の搬入として無視する。
        if (amount <= 0L) {
            return;
        }
        add(key, BigInteger.valueOf(amount));
    }

    /** 現在の正確な量を返す。存在しないキーは0。 */
    public synchronized BigInteger get(K key) {
        return amounts.getOrDefault(Objects.requireNonNull(key, "key"), BigInteger.ZERO);
    }

    /**
     * AE2へ渡す一回分をlongで取り出す。
     *
     * <p>remainingがlongを越えていても、ここでは最大Long.MAX_VALUEだけを取り出すので、
     * 変換途中の符号反転や負数化は発生しない。残りは次の搬出窓に残る。</p>
     */
    public synchronized long drain(K key, long maximum) {
        if (maximum <= 0L) {
            throw new IllegalArgumentException("maximum must be positive");
        }
        K checkedKey = Objects.requireNonNull(key, "key");
        BigInteger current = amounts.get(checkedKey);
        // 未登録または使い切ったキーには搬出量がない。
        if (current == null || current.signum() <= 0) {
            return 0L;
        }
        BigInteger requested = BigInteger.valueOf(maximum);
        BigInteger drained = current.min(requested);
        BigInteger remaining = current.subtract(drained);
        // 残量がゼロならキー自体を削除し、台帳を膨らませない。
        if (remaining.signum() == 0) {
            amounts.remove(checkedKey);
        } else {
            amounts.put(checkedKey, remaining);
        }
        return drained.longValueExact();
    }

    /** 全量のスナップショットを返す。返却Mapは変更できない。 */
    public synchronized Map<K, BigInteger> snapshot() {
        return Map.copyOf(amounts);
    }

    public synchronized boolean isEmpty() {
        return amounts.isEmpty();
    }

    public synchronized int size() {
        return amounts.size();
    }

    public synchronized void clear() {
        amounts.clear();
    }

    /** NBTへは符号付きBigIntegerのbyte[]を保存し、longへ縮めない。 */
    public synchronized CompoundTag save() {
        CompoundTag saved = new CompoundTag();
        saved.putInt(NBT_SCHEMA, SCHEMA_VERSION);
        ListTag entries = new ListTag();
        for (Map.Entry<K, BigInteger> entry : amounts.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.put(NBT_KEY, keyCodec.encode(entry.getKey()));
            value.putByteArray(NBT_AMOUNT, entry.getValue().toByteArray());
            entries.add(value);
        }
        saved.put(NBT_ENTRIES, entries);
        return saved;
    }

    /** 保存済み台帳を置き換える。壊れた値は黙って0にせず読み込みを失敗させる。 */
    public synchronized void load(CompoundTag saved) {
        Objects.requireNonNull(saved, "saved");
        int schema = saved.getInt(NBT_SCHEMA);
        // schema=0は初期試験版の無記録NBTを許容し、未知版は破損として拒否する。
        if (schema != 0 && schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported BigInteger amount ledger schema: " + schema);
        }
        ListTag entries = saved.getList(NBT_ENTRIES, Tag.TAG_COMPOUND);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("BigInteger amount ledger contains too many entries");
        }
        Map<K, BigInteger> loaded = new LinkedHashMap<>();
        // 既存台帳を変更せず全件検証してから、最後に原子的に置き換える。
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag value = entries.getCompound(index);
            K key = keyCodec.decode(value.getCompound(NBT_KEY));
            byte[] encoded = value.getByteArray(NBT_AMOUNT);
            BigInteger amount = new BigInteger(encoded);
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("BigInteger amount ledger contains a non-positive amount");
            }
            BigInteger checked = checked(amount, "saved ledger amount");
            if (loaded.put(key, checked) != null) {
                throw new IllegalArgumentException("BigInteger amount ledger contains a duplicate key");
            }
        }
        amounts.clear();
        amounts.putAll(loaded);
    }

    private BigInteger requirePositive(BigInteger value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked(value, name);
    }

    private BigInteger checked(BigInteger value, String context) {
        if (value.signum() < 0 || value.bitLength() > maximumBits) {
            throw new ArithmeticException(context + " exceeds the configured BigInteger bit limit");
        }
        return value;
    }
}
