package com.syaru.ae2craftingoptimizer.craftingamount;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.OptionalLong;

/**
 * AE2本来のint注文と、ACOが追加するlong注文の境界を一か所で管理する。
 * GUI、Packet、Menuが別々の条件を持たないよう、すべてこの判定を使用する。
 */
public final class LongCraftAmountRules {
    /** AE2 15.4.10の既存ConfirmAutoCraftPacketが無損失で扱える最大値。 */
    public static final long VANILLA_AE2_MAXIMUM = Integer.MAX_VALUE;

    private LongCraftAmountRules() {
    }

    public static boolean usesExtendedPath(long amount) {
        return amount > VANILLA_AE2_MAXIMUM;
    }

    public static boolean isValidExtendedRequest(long amount) {
        return usesExtendedPath(amount);
    }

    /**
     * 「=」入力では、既存在庫を作成要求から引く。
     * availableがrequested以上なら0、未満なら正数同士の減算なのでoverflowしない。
     */
    public static long subtractAvailable(long requested, long available) {
        // 呼出側の不正値を0へ丸めると別の注文へ変わるため、明示的に拒否する。
        if (requested <= 0L) {
            throw new IllegalArgumentException("requested must be positive");
        }
        // KeyCounterの異常な負値は在庫として数えず、要求数を増やさない。
        long nonNegativeAvailable = Math.max(0L, available);
        // 既存在庫だけで目標数を満たす場合は、新しいクラフト要求を作らない。
        if (nonNegativeAvailable >= requested) {
            return 0L;
        }
        return requested - nonNegativeAvailable;
    }

    /**
     * AE2のNumberEntryTypeが使う「表示単位あたりの内部量」を掛け、
     * Long範囲へ正確に変換する。longValue()の折返しは許可しない。
     */
    public static OptionalLong toExactExternalAmount(
            BigDecimal enteredAmount,
            int amountPerUnit) {
        // 値欠落または壊れた単位倍率は、演算せず無効入力として返す。
        if (enteredAmount == null || amountPerUnit <= 0) {
            return OptionalLong.empty();
        }
        try {
            BigDecimal scaled = enteredAmount.multiply(
                    BigDecimal.valueOf(amountPerUnit),
                    MathContext.DECIMAL128);
            BigDecimal rounded = scaled.setScale(0, RoundingMode.UP);
            long exact = rounded.longValueExact();
            // クラフト量は1以上だけを許可し、0と負数は本家画面と同様に無効扱いにする。
            if (exact <= 0L) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(exact);
        } catch (ArithmeticException outOfLongRange) {
            return OptionalLong.empty();
        }
    }
}
