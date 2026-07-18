package com.syaru.ae2craftingoptimizer.access;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public interface CraftingClusterRecoveryAccess {
    @Nullable
    CraftingOwnerTransactionAccess aco$findCraftingOwner(String ownerKind, @Nullable UUID ownerId);
}
