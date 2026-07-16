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
        long patternExecutions,
        long createdTick,
        long expiresTick
) {
    public RecipeIntent {
        providerPos = providerPos.immutable();
        targetPos = targetPos.immutable();
        inputs = List.copyOf(inputs);
        concreteInputs = List.copyOf(concreteInputs);
        outputs = List.copyOf(outputs);
        patternExecutions = Math.max(1L, patternExecutions);
    }

    public IntentLocation location() {
        return new IntentLocation(dimension, targetPos, targetSide);
    }

    public boolean isExpired(long now) {
        return now >= expiresTick;
    }

    public RecipeIntent withPatternExecutions(long executions, long expiration) {
        return new RecipeIntent(
                dimension,
                providerPos,
                providerSide,
                targetPos,
                targetSide,
                patternDefinitionId,
                inputs,
                concreteInputs,
                outputs,
                Math.max(1L, executions),
                createdTick,
                Math.max(expiresTick, expiration));
    }
}
