package com.syaru.ae2craftingoptimizer.access;

import appeng.api.storage.MEStorage;

/** Mixin実装を直接参照せず、AE2の委譲先Storageだけを公開する内部契約。 */
public interface DelegatingMEInventoryAccess {
    MEStorage aco$getDelegateStorage();
}
