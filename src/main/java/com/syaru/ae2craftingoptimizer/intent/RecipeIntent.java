package com.syaru.ae2craftingoptimizer.intent;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public record RecipeIntent(
        ResourceLocation dimension,
        BlockPos providerPos,
        Direction providerSide,
        BlockPos targetPos,
        Direction targetSide,
        String patternDefinitionId,
        List<InputIntent> inputs,
        List<StackIntent> concreteInputs,
        List<StackIntent> outputs,
        long createdTick,
        long expiresTick
) {
    public RecipeIntent {
        providerPos = providerPos.immutable();
        targetPos = targetPos.immutable();
        inputs = List.copyOf(inputs);
        concreteInputs = List.copyOf(concreteInputs);
        outputs = List.copyOf(outputs);
    }

    public IntentLocation location() {
        return new IntentLocation(dimension, targetPos, targetSide);
    }

    public boolean isExpired(long now) {
        return now >= expiresTick;
    }
}
