package com.syaru.ae2craftingoptimizer.access;

import java.util.Map;
import java.util.UUID;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;

/** Version-pinned view of Advanced AE's active child CPUs. */
public interface AdvancedAeClusterExecutionAccess {
    Map<UUID, AdvCraftingCPU> aco$getActiveCpuSnapshot();
}
