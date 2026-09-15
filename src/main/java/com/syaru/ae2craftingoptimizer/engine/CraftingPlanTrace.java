package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Issue #185: calculation-local byte charges, never an execution or persistence ledger. */
public record CraftingPlanTrace<K>(List<Charge<K>> charges) {
    public CraftingPlanTrace {
        charges = List.copyOf(charges);
    }

    /** Null key denotes an integer byte charge; stack amounts retain AE2's template quantum. */
    public record Charge<K>(K key, BigInteger amount, long templateAmount) {
        public Charge {
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() < 0 || templateAmount <= 0
                    || !amount.mod(BigInteger.valueOf(templateAmount)).equals(BigInteger.ZERO)) {
                throw new IllegalArgumentException("invalid byte charge");
            }
        }
    }
}
