package com.syaru.ae2craftingoptimizer.access;

import appeng.api.storage.MEStorage;

/**
 * Runtime contract exposed by the AE2 DelegatingMEInventory mixin.
 *
 * <p>This contract deliberately lives outside the mixin package. Regular
 * integration code may reference it without asking Forge to load a mixin
 * class directly.</p>
 */
public interface DelegatingMEInventoryAccess {
    MEStorage aco$getDelegateStorage();
}
