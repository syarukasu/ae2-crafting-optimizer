package com.syaru.ae2craftingoptimizer.access;

import appeng.api.storage.MEStorage;
import java.util.List;
import java.util.NavigableMap;

/** Runtime contract for the AE2 NetworkStorage priority mount accessor. */
public interface NetworkStorageMountsAccess {
    NavigableMap<Integer, List<MEStorage>> aco$getPriorityInventory();
}
