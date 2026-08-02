package com.syaru.ae2craftingoptimizer.access;

import appeng.api.storage.MEStorage;
import java.util.List;
import java.util.NavigableMap;

/** NetworkStorageのmount優先順へ接続する、Mixinではない内部契約。 */
public interface NetworkStorageMountsAccess {
    NavigableMap<Integer, List<MEStorage>> aco$getPriorityInventory();
}
