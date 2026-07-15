package com.syaru.ae2craftingoptimizer.intent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public record IntentLocation(ResourceLocation dimension, BlockPos targetPos, Direction targetSide) {
    public IntentLocation {
        targetPos = targetPos.immutable();
    }
}
