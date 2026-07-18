package com.syaru.ae2craftingoptimizer.access;

import appeng.api.networking.IGrid;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface CraftingOwnerTransactionAccess {
    Object aco$getCraftingLogic();

    void aco$markCraftingOwnerDirty();

    @Nullable
    IGrid aco$getCraftingGrid();

    BlockPos aco$getCraftingClusterPosition();

    String aco$getCraftingOwnerKind();

    @Nullable
    UUID aco$getCraftingOwnerId();
}
