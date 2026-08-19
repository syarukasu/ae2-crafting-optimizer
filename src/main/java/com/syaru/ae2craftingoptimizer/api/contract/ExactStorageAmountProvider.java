package com.syaru.ae2craftingoptimizer.api.contract;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;

/**
 * Public boundary for storage add-ons that keep amounts beyond signed {@code long}.
 *
 * <p>The returned map is copied and validated by ACO. Every key exposed by the storage's
 * normal AE2 facade must be present with a positive exact amount. Implementations must not
 * clamp, truncate, or convert the authoritative amount through {@code long}.</p>
 */
public interface ExactStorageAmountProvider {
    /** Returns one consistent snapshot of the exact stored amount for every exposed key. */
    Map<AEKey, BigInteger> exactStoredAmounts();
}
